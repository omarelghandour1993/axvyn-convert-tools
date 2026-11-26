package com.axvyn.app.features.wordtopdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordToPdfRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = WordToPdfEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): WordSourceInfo = withContext(Dispatchers.IO) {
        val info = engine.inspect(app, uri)
        WordSourceInfo(displayName = info.name, sizeBytes = info.sizeBytes)
    }

    suspend fun convert(
        uri: Uri,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): WordToPdfResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-word-${System.currentTimeMillis()}.pdf"
        val outFile = storage.workingFile(fileName)
        try {
            val result = engine.convert(app, uri, outFile, onProgress, isCancelled)
            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            val original = engine.inspect(app, uri).sizeBytes
            WordToPdfResult(
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