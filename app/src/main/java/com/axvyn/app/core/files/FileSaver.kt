package com.axvyn.app.core.files

import android.content.Context
import android.net.Uri

/**
 * Writes engine output into a URI granted by ACTION_CREATE_DOCUMENT.
 * Conversion engines are added in later phases; this helper keeps the write
 * path in one place so no tool needs broad storage permissions.
 */
object FileSaver {

    fun writeBytes(context: Context, destination: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            output.write(bytes)
        } ?: error("Cannot open the selected output location")
    }
}
