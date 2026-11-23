package com.axvyn.app.features.imagecompressor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates SAF access, the local engine and app-private scratch files. */
class ImageCompressorRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = ImageCompressionEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): SourceImageInfo = withContext(Dispatchers.IO) {
        engine.inspect(app, uri)
    }

    suspend fun compress(
        uri: Uri,
        settings: CompressionSettings,
        onProgress: (Float) -> Unit,
    ): CompressedImage = withContext(Dispatchers.IO) {
        val outcome = engine.compress(app, uri, settings, onProgress)
        val fileName = "axvyn-${System.currentTimeMillis()}.${settings.outputFormat.extension}"
        val file = storage.workingFile(fileName)
        try {
            file.writeBytes(outcome.bytes)
        } catch (error: Exception) {
            file.delete()
            throw error
        }

        CompressedImage(
            fileName = fileName,
            originalBytes = outcome.originalBytes,
            compressedBytes = outcome.bytes.size.toLong(),
            width = outcome.width,
            height = outcome.height,
            mimeType = settings.outputFormat.mimeType,
            shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file),
            note = outcome.note,
        )
    }

    fun save(fileName: String, destination: Uri) {
        FileSaver.writeBytes(app, destination, storage.workingFile(fileName).readBytes())
    }

    fun discard(fileName: String) {
        storage.workingFile(fileName).delete()
    }
}
