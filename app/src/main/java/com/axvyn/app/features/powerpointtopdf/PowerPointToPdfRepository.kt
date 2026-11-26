package com.axvyn.app.features.powerpointtopdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PowerPointToPdfRepository(context: Context) {
    private val app = context.applicationContext
    private val engine = PowerPointToPdfEngine()
    private val storage = LocalStorageManager(app)

    suspend fun inspect(uri: Uri): PowerPointSourceInfo = withContext(Dispatchers.IO) {
        val info = engine.inspect(app, uri)
        PowerPointSourceInfo(displayName = info.name, sizeBytes = info.sizeBytes, extension = info.extension)
    }

    suspend fun convert(
        uri: Uri,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): PowerPointToPdfResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-ppt-${System.currentTimeMillis()}.pdf"
        val outFile = storage.workingFile(fileName)
        try {
            val result = engine.convert(app, uri, outFile, onProgress, isCancelled)
            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            val original = engine.inspect(app, uri).sizeBytes
            PowerPointToPdfResult(
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