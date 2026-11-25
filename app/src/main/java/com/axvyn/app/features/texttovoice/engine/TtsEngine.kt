package com.axvyn.app.features.texttovoice.engine

import com.axvyn.app.features.texttovoice.model.TtsModelCapability
import java.io.File

/** Common voice descriptor used by UI/state across engine implementations. */
data class TtsVoiceOption(
    val id: String,
    val label: String,
    val localeTag: String,
    val languageDisplay: String,
    val engineId: String,
    val enginePackage: String? = null,
    val isOffline: Boolean,
    val quality: Int = 0,
)

data class TtsSynthesisRequest(
    val text: String,
    val voice: TtsVoiceOption,
)

data class TtsSynthesisOutcome(
    val file: File,
    val mp3Bytes: Long,
    val durationMs: Long?,
)

interface TtsEngine {
    /** Stable engine identifier (e.g. "android-system", "sherpa-onnx"). */
    val id: String

    /** User-facing engine name for diagnostics/future UI. */
    val displayName: String

    /** Model/runtime capabilities exposed by this engine. May be empty for system engines. */
    val capabilities: List<TtsModelCapability>

    suspend fun initialize(): Boolean

    fun availableVoices(): List<TtsVoiceOption>

    fun availableLanguages(voices: List<TtsVoiceOption>): List<String>

    suspend fun synthesizeToMp3(
        request: TtsSynthesisRequest,
        outputFile: File,
        cacheDir: File,
        onProgress: (Float) -> Unit,
    ): TtsSynthesisOutcome

    fun stop()

    fun shutdown()
}
