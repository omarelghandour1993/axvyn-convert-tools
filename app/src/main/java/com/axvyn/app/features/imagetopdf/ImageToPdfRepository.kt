package com.axvyn.app.features.imagetopdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageToPdfRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = ImageToPdfEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): ImageSourceInfo = withContext(Dispatchers.IO) {
        val info = engine.inspect(app, uri)
        ImageSourceInfo(
            uri = uri,
            displayName = info.name,
            sizeBytes = info.sizeBytes,
            mimeType = info.mimeType,
            width = info.width,
            height = info.height,
        )
    }

    suspend fun convert(
        uris: List<Uri>,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ImageToPdfResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-images-${System.currentTimeMillis()}.pdf"
        val outFile = storage.workingFile(fileName)
        try {
            val result = engine.convert(app, uris, outFile, onProgress, isCancelled)
            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            val originalBytes = uris.sumOf { runCatching { engine.inspect(app, it).sizeBytes }.getOrDefault(0L) }
            ImageToPdfResult(
                fileName = fileName,
                originalBytes = originalBytes,
                outputBytes = result.outputBytes,
                pageCount = result.pageCount,
                shareUri = shareUri,
            )
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }
    }

    fun save(fileName: String, destination: Uri) {
        FileSaver.writeBytes(app, destination, storage.workingFile(fileName).readBytes())
    }

    fun discard(fileName: String) {
        storage.workingFile(fileName).delete()
    }
}
