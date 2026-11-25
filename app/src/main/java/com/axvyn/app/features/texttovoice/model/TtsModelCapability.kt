package com.axvyn.app.features.texttovoice.model

/**
 * Describes a neural TTS model family/runtime capability. This is metadata only;
 * it does not load models or perform inference.
 */
data class TtsModelCapability(
    val modelFamily: String,
    val languageSupport: List<TtsLanguageCapability>,
    val speakerSupport: SpeakerSupport,
    val requiredModelFiles: List<ModelFileRequirement>,
    val sampleRateHz: Int?,
    val phonemization: PhonemizationCapability,
    val supportedInferenceConfig: InferenceConfigCapability,
    val modelLoadingRequirements: ModelLoadingRequirements,
)

data class TtsLanguageCapability(
    val languageCode: String,
    val displayName: String,
)

data class SpeakerSupport(
    val type: SpeakerSupportType,
    val speakerCount: Int? = null,
    val speakerIds: List<String> = emptyList(),
    val notes: String? = null,
)

enum class SpeakerSupportType {
    SINGLE,
    MULTI,
    VOICE_CLONING,
    UNKNOWN,
}

data class ModelFileRequirement(
    val id: String,
    val pathHint: String,
    val required: Boolean = true,
    val description: String? = null,
)

data class PhonemizationCapability(
    val requirement: PhonemizationRequirement,
    val mode: PhonemizationMode,
    val notes: String? = null,
)

enum class PhonemizationRequirement {
    NONE,
    OPTIONAL,
    REQUIRED,
    UNKNOWN,
}

enum class PhonemizationMode {
    INTERNAL,
    EXTERNAL_PHONEMIZER,
    PRE_PHONEMIZED_CODEPOINTS,
    LEXICON,
    MIXED,
    UNKNOWN,
}

data class InferenceConfigCapability(
    val supportsSpeed: Boolean = false,
    val supportsSpeakerId: Boolean = false,
    val supportsStreaming: Boolean = false,
    val supportsReferenceAudio: Boolean = false,
    val supportsPhonemeCodepoints: Boolean = false,
    val supportsTokens: Boolean = false,
    val notes: String? = null,
)

data class ModelLoadingRequirements(
    val runtime: String,
    val requiresFileSystemPaths: Boolean = true,
    val supportsAssetManager: Boolean = false,
    val minimumAbi: List<String> = emptyList(),
    val estimatedRamMb: Int? = null,
    val notes: String? = null,
)
