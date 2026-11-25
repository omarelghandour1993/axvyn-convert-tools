package com.axvyn.app.features.texttovoice.engine

import android.content.Context
import com.axvyn.app.features.texttovoice.model.InferenceConfigCapability
import com.axvyn.app.features.texttovoice.model.ModelFileRequirement
import com.axvyn.app.features.texttovoice.model.ModelLoadingRequirements
import com.axvyn.app.features.texttovoice.model.PhonemizationCapability
import com.axvyn.app.features.texttovoice.model.PhonemizationMode
import com.axvyn.app.features.texttovoice.model.PhonemizationRequirement
import com.axvyn.app.features.texttovoice.model.SpeakerSupport
import com.axvyn.app.features.texttovoice.model.SpeakerSupportType
import com.axvyn.app.features.texttovoice.model.TtsLanguageCapability
import com.axvyn.app.features.texttovoice.model.TtsModelCapability
import java.io.File

/**
 * Sherpa-ONNX neural TTS adapter skeleton.
 *
 * Status: architectural skeleton only. No voice packs are bundled/downloaded,
 * no INTERNET permission is used, and no neural model is loaded by default.
 * Existing Android system TTS remains the active production engine.
 */
class SherpaTtsEngine(
    @Suppress("unused") private val context: Context,
) : TtsEngine {

    override val id: String = "sherpa-onnx"
    override val displayName: String = "Sherpa-ONNX (offline neural)"

    // Preliminary capability registry. Only models verified against the bundled
    // sherpa-onnx runtime should be marked verified in docs/reports.
    override val capabilities: List<TtsModelCapability> = listOf(
        supertonic3Int8Capability(),
    )

    override suspend fun initialize(): Boolean {
        // No installed/selected neural voice pack in this phase.
        return false
    }

    override fun availableVoices(): List<TtsVoiceOption> = emptyList()

    override fun availableLanguages(voices: List<TtsVoiceOption>): List<String> = emptyList()

    override suspend fun synthesizeToMp3(
        request: TtsSynthesisRequest,
        outputFile: File,
        cacheDir: File,
        onProgress: (Float) -> Unit,
    ): TtsSynthesisOutcome {
        outputFile.delete()
        throw UnsupportedOperationException(
            "Neural Sherpa-ONNX synthesis is not configured yet. Install and select a verified voice pack first.",
        )
    }

    override fun stop() {
        // No active native synthesis session in skeleton phase.
    }

    override fun shutdown() {
        // No native model resources owned in skeleton phase.
    }

    private fun supertonic3Int8Capability(): TtsModelCapability = TtsModelCapability(
        modelFamily = "Supertonic 3 (ONNX int8)",
        languageSupport = listOf(
            // Preliminary: Supertonic 3 is advertised as 31-language multilingual.
            // Exact language/code mapping must be confirmed against the final packaged model metadata.
            TtsLanguageCapability(languageCode = "multilingual", displayName = "31 languages (preliminary, see model card)"),
        ),
        speakerSupport = SpeakerSupport(
            type = SpeakerSupportType.MULTI,
            speakerCount = 10,
            speakerIds = (0..9).map { it.toString() },
            notes = "Speaker count observed from sherpa-onnx 2.0.3 on-device smoke (numSpeakers=10).",
        ),
        requiredModelFiles = listOf(
            ModelFileRequirement("duration_predictor", "duration_predictor.int8.onnx", description = "Duration predictor ONNX model"),
            ModelFileRequirement("text_encoder", "text_encoder.int8.onnx", description = "Text encoder ONNX model"),
            ModelFileRequirement("vector_estimator", "vector_estimator.int8.onnx", description = "Vector estimator ONNX model"),
            ModelFileRequirement("vocoder", "vocoder.int8.onnx", description = "Vocoder ONNX model"),
            ModelFileRequirement("tts_json", "tts.json", description = "Model configuration JSON"),
            ModelFileRequirement("unicode_indexer", "unicode_indexer.bin", description = "Unicode indexer for text processing"),
            ModelFileRequirement("voice_style", "voice.bin", description = "Multi-speaker voice style/embedding bundle"),
        ),
        sampleRateHz = 44100,
        phonemization = PhonemizationCapability(
            requirement = PhonemizationRequirement.NONE,
            mode = PhonemizationMode.INTERNAL,
            notes = "Supertonic uses bundled unicode indexer/text processing; no external espeak-ng phonemizer required.",
        ),
        supportedInferenceConfig = InferenceConfigCapability(
            supportsSpeed = true,
            supportsSpeakerId = true,
            supportsStreaming = false,
            supportsReferenceAudio = false,
            supportsPhonemeCodepoints = false,
            supportsTokens = false,
            notes = "Verified with sherpa-onnx OfflineTts.generate(text,sid,speed). Streaming/reference audio not assessed.",
        ),
        modelLoadingRequirements = ModelLoadingRequirements(
            runtime = "sherpa-onnx 2.0.3 (bundled ONNX Runtime CPU)",
            requiresFileSystemPaths = true,
            supportsAssetManager = false,
            minimumAbi = listOf("arm64-v8a"),
            estimatedRamMb = 500,
            notes = "A15 smoke: load ~1.6s, generated ~5.5s audio in ~2.9s at 44.1kHz (numThreads=2, CPU). RAM is preliminary estimate; measure before release. Model license: OpenRAIL-M (product/legal decision required before distribution).",
        ),
    )
}
