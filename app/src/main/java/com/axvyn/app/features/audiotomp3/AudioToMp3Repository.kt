package com.axvyn.app.features.audiotomp3

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates SAF access, on-device audio decoding and LAME MP3 encoding. */
class AudioToMp3Repository(context: Context) {
    private val app = context.applicationContext
    private val engine = AudioConversionEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): SourceAudioInfo = withContext(Dispatchers.IO) {
        engine.inspect(app, uri)
    }

    suspend fun convert(
        uri: Uri,
        settings: AudioConversionSettings,
        onProgress: (Float) -> Unit,
    ): ConvertedAudio = withContext(Dispatchers.IO) {
        val fileName = "axvyn-audio-${System.currentTimeMillis()}.mp3"
        val file = storage.workingFile(fileName)
        if (file.exists()) file.delete()
        try {
            val outcome = engine.convert(app, uri, settings, file, onProgress)
            ConvertedAudio(
                fileName = fileName,
                originalBytes = outcome.originalBytes,
                convertedBytes = outcome.convertedBytes,
                bitrateKbps = settings.bitrateKbps,
                durationMs = outcome.durationMs,
                mimeType = "audio/mpeg",
                shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file),
            )
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun save(fileName: String, destination: Uri) {
        FileSaver.writeBytes(app, destination, storage.workingFile(fileName).readBytes())
    }

    fun discard(fileName: String) {
        storage.workingFile(fileName).delete()
    }
}
