package com.axvyn.app.core.storage

import android.content.Context
import java.io.File

/** App-private scratch space. Nothing here is uploaded or shared. */
class LocalStorageManager(context: Context) {
    private val cacheDir = context.applicationContext.cacheDir

    fun workingFile(name: String): File = File(cacheDir, name)

    fun clearWorkingFiles() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }
}
