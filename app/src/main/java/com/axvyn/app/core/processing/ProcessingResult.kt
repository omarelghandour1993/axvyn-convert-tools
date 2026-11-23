package com.axvyn.app.core.processing

/** Result contract returned by local conversion engines. */
sealed interface ProcessingResult {
    data class Success(val outputName: String, val sizeBytes: Long) : ProcessingResult
    data class Failure(val message: String) : ProcessingResult
}
