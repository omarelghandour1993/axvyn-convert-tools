package com.axvyn.app.core.processing

/** Common UI state shared by every conversion tool once its engine exists. */
sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(val progress: Float) : ProcessingState
    data class Success(val outputName: String, val sizeBytes: Long) : ProcessingState
    data class Failed(val message: String) : ProcessingState
    data object Cancelled : ProcessingState
}
