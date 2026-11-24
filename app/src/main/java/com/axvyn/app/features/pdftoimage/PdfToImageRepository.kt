package com.axvyn.app.features.pdftoimage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates SAF access, the local PDF renderer and app-private scratch files. */
class PdfToImageRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = PdfRenderingEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): SourcePdfInfo = withContext(Dispatchers.IO) {
        engine.inspect(app, uri)
    }

    suspend fun render(
        uri: Uri,
        settings: RenderSettings,
        onProgress: (Float) -> Unit,
    ): RenderedPage = withContext(Dispatchers.IO) {
        val outcome = engine.render(app, uri, settings, onProgress)
        val fileName = "axvyn-page-${outcome.pageNumber}-${System.currentTimeMillis()}.${settings.format.extension}"
        val file = storage.workingFile(fileName)
        try {
            file.writeBytes(outcome.bytes)
        } catch (error: Exception) {
            file.delete()
            throw error
        }

        RenderedPage(
            fileName = fileName,
            pageNumber = outcome.pageNumber,
            pageCount = outcome.pageCount,
            width = outcome.width,
            height = outcome.height,
            sizeBytes = outcome.bytes.size.toLong(),
            mimeType = settings.format.mimeType,
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
