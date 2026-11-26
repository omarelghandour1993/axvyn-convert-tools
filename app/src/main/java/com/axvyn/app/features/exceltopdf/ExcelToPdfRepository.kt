package com.axvyn.app.features.exceltopdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExcelToPdfRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = ExcelToPdfEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): ExcelSourceInfo = withContext(Dispatchers.IO) {
        val info = engine.inspect(app, uri)
        ExcelSourceInfo(displayName = info.name, sizeBytes = info.sizeBytes, extension = info.extension)
    }

    suspend fun convert(
        uri: Uri,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ExcelToPdfResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-xlsx-${System.currentTimeMillis()}.pdf"
        val outFile = storage.workingFile(fileName)
        try {
            val result = engine.convert(app, uri, outFile, onProgress, isCancelled)
            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            val original = engine.inspect(app, uri).sizeBytes
            ExcelToPdfResult(
                fileName = fileName,
                originalBytes = original,
                outputBytes = result.outputBytes,
                pageCount = result.pageCount,
                sheetCount = result.sheetCount,
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
