package com.axvyn.app.data.voicepacks

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class VoicePackManager(context: Context) {
    private val app = context.applicationContext
    private val rootDir = File(app.filesDir, "voice-packs").apply { mkdirs() }
    private val downloadDir = File(app.cacheDir, "voice-pack-downloads").apply { mkdirs() }
    private val activeDownloads = ConcurrentHashMap<String, HttpURLConnection>()

    private val _statuses = MutableStateFlow(initialStatuses())
    val statuses: StateFlow<Map<String, VoicePackStatus>> = _statuses.asStateFlow()

    fun getStatus(packId: String): VoicePackStatus? = _statuses.value[packId]

    fun getInstalledPack(info: VoicePackInfo): InstalledVoicePack? {
        val dir = installedDir(info)
        return if (isInstalled(info)) InstalledVoicePack(info, dir) else null
    }

    fun installedPacks(): List<InstalledVoicePack> =
        VoicePackCatalog.packs.mapNotNull { getInstalledPack(it) }

    fun refresh() {
        _statuses.value = VoicePackCatalog.packs.associate { info ->
            val current = _statuses.value[info.id]
            if (current?.state == VoicePackInstallState.DOWNLOADING || current?.state == VoicePackInstallState.VERIFYING) {
                info.id to current
            } else {
                info.id to statusFor(info)
            }
        }
    }

    suspend fun download(packId: String) = withContext(Dispatchers.IO) {
        val info = VoicePackCatalog.byId(packId) ?: error("Unknown voice pack: $packId")
        if (isInstalled(info)) {
            updateStatus(info, VoicePackInstallState.INSTALLED, progress = 1f, message = "Installed")
            return@withContext
        }

        val archiveFile = File(downloadDir, info.archiveFileName)
        val partFile = File(downloadDir, info.archiveFileName + ".part")
        val targetDir = installedDir(info)
        val tmpExtractDir = File(downloadDir, info.id + "-extract-" + System.currentTimeMillis())

        try {
            updateStatus(info, VoicePackInstallState.DOWNLOADING, progress = 0f, bytesDownloaded = partFile.length().coerceAtLeast(0L), totalBytes = info.archiveSizeBytes, message = "Downloading…")
            downloadArchive(info, partFile)

            updateStatus(info, VoicePackInstallState.VERIFYING, progress = 0.97f, bytesDownloaded = partFile.length(), totalBytes = info.archiveSizeBytes, message = "Verifying…")
            val actualSha = sha256(partFile)
            if (!actualSha.equals(info.archiveSha256, ignoreCase = true)) {
                partFile.delete()
                error("Download verification failed for ${info.language}. Please try again.")
            }

            if (archiveFile.exists()) archiveFile.delete()
            if (!partFile.renameTo(archiveFile)) {
                partFile.copyTo(archiveFile, overwrite = true)
                partFile.delete()
            }

            updateStatus(info, VoicePackInstallState.VERIFYING, progress = 0.985f, message = "Installing…")
            if (tmpExtractDir.exists()) tmpExtractDir.deleteRecursively()
            tmpExtractDir.mkdirs()
            extractTarBz2(archiveFile, tmpExtractDir)

            val extractedRoot = File(tmpExtractDir, info.rootDirName)
            val sourceDir = if (extractedRoot.isDirectory) extractedRoot else tmpExtractDir
            validateExtractedPack(info, sourceDir)

            if (targetDir.exists()) targetDir.deleteRecursively()
            val parent = targetDir.parentFile ?: rootDir
            parent.mkdirs()
            if (!sourceDir.renameTo(targetDir)) {
                sourceDir.copyRecursively(targetDir, overwrite = true)
            }

            File(targetDir, INSTALLED_MARKER).writeText(info.id)
            archiveFile.delete()
            tmpExtractDir.deleteRecursively()

            updateStatus(
                info,
                VoicePackInstallState.INSTALLED,
                progress = 1f,
                bytesDownloaded = info.archiveSizeBytes,
                totalBytes = info.archiveSizeBytes,
                installedBytes = dirSize(targetDir),
                message = "Installed",
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            activeDownloads.remove(info.id)?.disconnect()
            val msg = "Download cancelled"
            val existing = partFile.length().coerceAtLeast(0L)
            updateStatus(info, VoicePackInstallState.NOT_INSTALLED, progress = 0f, bytesDownloaded = existing, totalBytes = info.archiveSizeBytes, message = msg)
            throw ce
        } catch (e: Exception) {
            activeDownloads.remove(info.id)?.disconnect()
            tmpExtractDir.deleteRecursively()
            // Keep .part for resume, unless it's obviously bad/corrupt size overrun.
            if (partFile.exists() && info.archiveSizeBytes > 0 && partFile.length() > info.archiveSizeBytes) {
                partFile.delete()
            }
            updateStatus(
                info,
                if (isInstalled(info)) VoicePackInstallState.INSTALLED else VoicePackInstallState.FAILED,
                progress = if (isInstalled(info)) 1f else 0f,
                bytesDownloaded = partFile.length().coerceAtLeast(0L),
                totalBytes = info.archiveSizeBytes,
                installedBytes = if (isInstalled(info)) dirSize(targetDir) else 0L,
                message = e.message ?: "Download failed",
            )
            if (!isInstalled(info)) throw e
        } finally {
            activeDownloads.remove(info.id)
        }
    }

    fun cancelDownload(packId: String) {
        activeDownloads.remove(packId)?.disconnect()
    }

    fun delete(packId: String) {
        val info = VoicePackCatalog.byId(packId) ?: return
        cancelDownload(packId)
        val dir = installedDir(info)
        if (dir.exists()) dir.deleteRecursively()
        val part = File(downloadDir, info.archiveFileName + ".part")
        if (part.exists()) part.delete()
        val archive = File(downloadDir, info.archiveFileName)
        if (archive.exists()) archive.delete()
        updateStatus(info, VoicePackInstallState.NOT_INSTALLED, progress = 0f, bytesDownloaded = 0L, totalBytes = info.archiveSizeBytes, installedBytes = 0L, message = "Deleted")
    }

    private fun initialStatuses(): Map<String, VoicePackStatus> =
        VoicePackCatalog.packs.associate { it.id to statusFor(it) }

    private fun statusFor(info: VoicePackInfo): VoicePackStatus {
        val installed = isInstalled(info)
        val dir = installedDir(info)
        return VoicePackStatus(
            info = info,
            state = if (installed) VoicePackInstallState.INSTALLED else VoicePackInstallState.NOT_INSTALLED,
            progress = if (installed) 1f else 0f,
            bytesDownloaded = if (installed) info.archiveSizeBytes else File(downloadDir, info.archiveFileName + ".part").length().coerceAtLeast(0L),
            totalBytes = info.archiveSizeBytes,
            installedBytes = if (installed) dirSize(dir) else 0L,
            message = if (installed) "Installed" else null,
        )
    }

    private fun updateStatus(
        info: VoicePackInfo,
        state: VoicePackInstallState,
        progress: Float = 0f,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = info.archiveSizeBytes,
        installedBytes: Long = 0L,
        message: String? = null,
    ) {
        _statuses.update { old ->
            old + (info.id to VoicePackStatus(info, state, progress.coerceIn(0f, 1f), bytesDownloaded, totalBytes, installedBytes, message))
        }
    }

    private fun installedDir(info: VoicePackInfo): File = File(rootDir, info.id)

    private fun isInstalled(info: VoicePackInfo): Boolean {
        val dir = installedDir(info)
        if (!File(dir, INSTALLED_MARKER).isFile) return false
        return requiredFiles(info).all { File(dir, it).exists() }
    }

    private fun requiredFiles(info: VoicePackInfo): List<String> = listOf(
        info.modelFile,
        info.tokensFile,
        info.dataDirName,
    )

    private suspend fun downloadArchive(info: VoicePackInfo, partFile: File) {
        var existing = if (partFile.exists()) partFile.length().coerceAtLeast(0L) else 0L
        if (info.archiveSizeBytes > 0 && existing > info.archiveSizeBytes) {
            partFile.delete()
            existing = 0L
        }

        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        activeDownloads[info.id] = connection

        try {
            val code = connection.responseCode
            val append: Boolean
            val total: Long
            when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                    val contentLength = connection.contentLengthLong
                    total = if (contentLength > 0) existing + contentLength else info.archiveSizeBytes
                }
                HttpURLConnection.HTTP_OK -> {
                    append = false
                    existing = 0L
                    if (partFile.exists()) partFile.delete()
                    val contentLength = connection.contentLengthLong
                    total = if (contentLength > 0) contentLength else info.archiveSizeBytes
                }
                else -> error("Download failed (${code}) for ${info.language}")
            }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = existing
                    var read: Int
                    var lastEmit = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        currentCoroutineContext().ensureActive()
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150 || read < 0) {
                            lastEmit = now
                            val progress = if (total > 0) (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 0.96).toFloat() else 0f
                            updateStatus(info, VoicePackInstallState.DOWNLOADING, progress = progress, bytesDownloaded = downloaded, totalBytes = total, message = "Downloading…")
                        }
                    }
                    output.flush()
                    updateStatus(info, VoicePackInstallState.DOWNLOADING, progress = 0.96f, bytesDownloaded = downloaded, totalBytes = total, message = "Downloading…")
                }
            }
        } finally {
            connection.disconnect()
            activeDownloads.remove(info.id)
        }
    }

    private fun extractTarBz2(archiveFile: File, targetDir: File) {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bzis ->
                    TarArchiveInputStream(bzis).use { tar ->
                        var entry = tar.nextTarEntry
                        val canonicalTarget = targetDir.canonicalFile
                        while (entry != null) {
                            val outFile = File(targetDir, entry.name)
                            val canonicalOut = outFile.canonicalFile
                            if (!canonicalOut.path.startsWith(canonicalTarget.path + File.separator) && canonicalOut != canonicalTarget) {
                                throw IOException("Unsafe archive entry: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                canonicalOut.mkdirs()
                            } else {
                                canonicalOut.parentFile?.mkdirs()
                                FileOutputStream(canonicalOut).use { out -> tar.copyTo(out) }
                            }
                            entry = tar.nextTarEntry
                        }
                    }
                }
            }
        }
    }

    private fun validateExtractedPack(info: VoicePackInfo, sourceDir: File) {
        requiredFiles(info).forEach { rel ->
            val f = File(sourceDir, rel)
            if (!f.exists()) error("Installed pack is missing required file: $rel")
        }
        val model = File(sourceDir, info.modelFile)
        if (!model.isFile || model.length() < 1024) error("Installed model file is invalid.")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        private const val INSTALLED_MARKER = ".installed"
    }
}

data class InstalledVoicePack(
    val info: VoicePackInfo,
    val directory: File,
) {
    val modelPath: File get() = File(directory, info.modelFile)
    val tokensPath: File get() = File(directory, info.tokensFile)
    val dataDirPath: File get() = File(directory, info.dataDirName)
}
