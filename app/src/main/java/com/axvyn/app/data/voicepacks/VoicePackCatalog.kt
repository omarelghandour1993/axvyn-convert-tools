package com.axvyn.app.data.voicepacks

/**
 * Verified, downloadable offline neural voice packs.
 *
 * Only models with clear redistribution licenses are exposed.
 * Arabic is intentionally omitted until a compatible Piper voice with a clear
 * commercial redistribution license is verified.
 */
data class VoicePackInfo(
    val id: String,
    val language: String,
    val localeTag: String,
    val voiceName: String,
    val modelFamily: String = "Piper VITS (int8)",
    val archiveFileName: String,
    val downloadUrl: String,
    val archiveSha256: String,
    val archiveSizeBytes: Long,
    val license: String,
    val sampleRateHz: Int = 22050,
    val numSpeakers: Int = 1,
    val rootDirName: String,
    val modelFile: String,
    val tokensFile: String = "tokens.txt",
    val dataDirName: String = "espeak-ng-data",
    val testSentence: String,
)

enum class VoicePackInstallState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    FAILED,
}

data class VoicePackStatus(
    val info: VoicePackInfo,
    val state: VoicePackInstallState,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val installedBytes: Long = 0L,
    val message: String? = null,
)

object VoicePackCatalog {
    const val MAX_CHARACTERS = 3_000

    // Candidate languages for the TTS screen. Neural packs are exposed only when
    // a verified, clear-license model is available in [packs].
    val candidateLanguages: List<String> = listOf(
        "English",
        "French",
        "German",
        "Arabic",
    )

    val packs: List<VoicePackInfo> = listOf(
        VoicePackInfo(
            id = "en-us-ljspeech-medium-int8",
            language = "English",
            localeTag = "en-US",
            voiceName = "LJSpeech (medium)",
            archiveFileName = "vits-piper-en_US-ljspeech-medium-int8.tar.bz2",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ljspeech-medium-int8.tar.bz2",
            archiveSha256 = "24dc3bd77dd48c291e52c297878d3437c9492f245d823d7f6a06c4bbb67f4b6b",
            archiveSizeBytes = 21_090_429L,
            license = "Public domain (LJ Speech dataset)",
            rootDirName = "vits-piper-en_US-ljspeech-medium-int8",
            modelFile = "en_US-ljspeech-medium.onnx",
            testSentence = "Hello from Axvyn. This is an offline neural English voice running on your device.",
        ),
        VoicePackInfo(
            id = "fr-fr-siwis-medium-int8",
            language = "French",
            localeTag = "fr-FR",
            voiceName = "Siwis (medium)",
            archiveFileName = "vits-piper-fr_FR-siwis-medium-int8.tar.bz2",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-siwis-medium-int8.tar.bz2",
            archiveSha256 = "3909cff9b3cfd4820c66aa13bf554315c82e34899c161f0b446ece372bc4b5ec",
            archiveSizeBytes = 20_914_888L,
            license = "CC-BY 4.0 (Siwis dataset)",
            rootDirName = "vits-piper-fr_FR-siwis-medium-int8",
            modelFile = "fr_FR-siwis-medium.onnx",
            testSentence = "Bonjour depuis Axvyn. Ceci est une voix neuronale française hors ligne sur votre appareil.",
        ),
        VoicePackInfo(
            id = "de-de-thorsten-medium-int8",
            language = "German",
            localeTag = "de-DE",
            voiceName = "Thorsten (medium)",
            archiveFileName = "vits-piper-de_DE-thorsten-medium-int8.tar.bz2",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium-int8.tar.bz2",
            archiveSha256 = "07e240b7b9c1fc9211d5a69512f8cbe11b3286c2ed79c15c076ac6ed427fdf13",
            archiveSizeBytes = 20_949_833L,
            license = "CC0 (Thorsten-Voice)",
            rootDirName = "vits-piper-de_DE-thorsten-medium-int8",
            modelFile = "de_DE-thorsten-medium.onnx",
            testSentence = "Hallo von Axvyn. Dies ist eine offline neuronale deutsche Stimme auf deinem Gerät.",
        ),
    )

    fun byId(id: String): VoicePackInfo? = packs.firstOrNull { it.id == id }

    fun byLanguage(language: String): List<VoicePackInfo> =
        packs.filter { it.language.equals(language, ignoreCase = true) }
}
