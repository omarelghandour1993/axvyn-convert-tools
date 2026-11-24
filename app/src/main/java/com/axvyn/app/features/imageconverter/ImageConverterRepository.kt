package com.axvyn.app.features.imageconverter

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates SAF access, the local image converter and app-private scratch files. */
class ImageConverterRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = ImageConversionEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): SourceImageInfo = withContext(Dispatchers.IO) {
        engine.inspect(app, uri)
    }

    suspend fun convert(
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): ConvertedImage = withContext(Dispatchers.IO) {
        val outcome = engine.convert(app, uri, onProgress)
        val fileName = "axvyn-converted-${System.currentTimeMillis()}.png"
        val file = storage.workingFile(fileName)
        try {
            file.writeBytes(outcome.bytes)
        } catch (error: Exception) {
            file.delete()
            throw error
        }

        ConvertedImage(
            fileName = fileName,
            originalBytes = outcome.originalBytes,
            convertedBytes = outcome.bytes.size.toLong(),
            width = outcome.width,
            height = outcome.height,
            mimeType = "image/png",
            shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file),
        )
    }

    fun save(fileName: String, destination: Uri) {
        FileSaver.writeBytes(app, destination, storage.workingFile(fileName).readBytes())
    }

    fun discard(fileName: String) {
        storage.workingFile(fileName).delete()
    }
}
