package com.axvyn.app.core.files

/** Display information for a file chosen through the Storage Access Framework. */
data class FileMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

val EMPTY_FILE_METADATA = FileMetadata(
    displayName = "",
    sizeBytes = 0L,
    mimeType = null,
)
