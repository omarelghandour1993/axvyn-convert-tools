package com.axvyn.app.features.compresspdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompressPdfRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = CompressPdfEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): CompressPdfSourceInfo = withContext(Dispatchers.IO) {
        val info = engine.inspect(app, uri)
        CompressPdfSourceInfo(displayName = info.name, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
    }

    suspend fun convert(
        uri: Uri,
        level: PdfCompressionLevel,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): CompressPdfResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-compressed-${System.currentTimeMillis()}.pdf"
        val outFile = storage.workingFile(fileName)
        try {
            val result = engine.convert(app, uri, outFile, level, onProgress, isCancelled)
            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            val original = runCatching { engine.inspect(app, uri).sizeBytes }.getOrDefault(0L)
            CompressPdfResult(
                fileName = fileName,
                originalBytes = original,
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
