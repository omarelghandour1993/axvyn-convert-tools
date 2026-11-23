package com.axvyn.app.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Reads display metadata from a content URI without copying the file. */
object FilePicker {

    fun metadata(context: Context, uri: Uri): FileMetadata? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        ) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return null
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            FileMetadata(
                displayName = if (nameIndex >= 0) it.getString(nameIndex) else "Selected file",
                sizeBytes = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L,
                mimeType = context.contentResolver.getType(uri),
            )
        }
    }
}
