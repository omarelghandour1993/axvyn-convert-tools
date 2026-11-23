package com.axvyn.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.vector.ImageVector
import com.axvyn.app.navigation.Routes
import com.axvyn.app.ui.components.ToolCardModel
import com.axvyn.app.ui.theme.AccentAudio
import com.axvyn.app.ui.theme.AccentConvert
import com.axvyn.app.ui.theme.AccentDocument
import com.axvyn.app.ui.theme.AccentImage
import com.axvyn.app.ui.theme.AccentPdf
import com.axvyn.app.ui.theme.AccentVoice

val ToolCatalog: List<ToolCardModel> = listOf(
    ToolCardModel(
        title = "Word to PDF",
        description = "DOCX up to 10 MB",
        icon = Icons.Default.Description,
        accent = AccentDocument,
        route = Routes.WORD_TO_PDF,
    ),
    ToolCardModel(
        title = "Image Compressor",
        description = "Shrink JPEG, PNG & WebP",
        icon = Icons.Default.Image,
        accent = AccentImage,
        route = Routes.IMAGE_COMPRESSOR,
    ),
    ToolCardModel(
        title = "Text to Voice",
        description = "Offline speech, 14 languages",
        icon = Icons.Default.RecordVoiceOver,
        accent = AccentVoice,
        route = Routes.TEXT_TO_VOICE,
    ),
    ToolCardModel(
        title = "WEBP / AVIF to PNG",
        description = "Convert with transparency",
        icon = Icons.Default.Transform,
        accent = AccentConvert,
        route = Routes.IMAGE_CONVERTER,
    ),
    ToolCardModel(
        title = "PDF to Images",
        description = "Export pages as JPG or PNG",
        icon = Icons.Default.PictureAsPdf,
        accent = AccentPdf,
        route = Routes.PDF_TO_IMAGE,
    ),
    ToolCardModel(
        title = "Audio to MP3",
        description = "M4A, WAV & FLAC at 320 kbps",
        icon = Icons.Default.Audiotrack,
        accent = AccentAudio,
        route = Routes.AUDIO_TO_MP3,
    ),
)
