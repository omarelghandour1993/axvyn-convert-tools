package com.axvyn.app.data.voicepacks

/**
 * Metadata contract for future downloadable offline voices. No packs ship in the
 * foundation, so the catalog is intentionally empty.
 */
data class VoicePackInfo(
    val id: String,
    val language: String,
    val voiceName: String,
    val version: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    val minimumSdk: Int,
)

object VoicePackCatalog {
    const val MAX_CHARACTERS = 3_000

    // Candidate languages. Actual availability must be verified against real
    // downloadable voices before release; none is installed in this phase.
    val candidateLanguages: List<String> = listOf(
        "Arabic",
        "Chinese",
        "Dutch",
        "English",
        "French",
        "German",
        "Italian",
        "Japanese",
        "Korean",
        "Polish",
        "Portuguese",
        "Russian",
        "Spanish",
        "Turkish",
    )

    val installedPacks: List<VoicePackInfo> = emptyList()
}
