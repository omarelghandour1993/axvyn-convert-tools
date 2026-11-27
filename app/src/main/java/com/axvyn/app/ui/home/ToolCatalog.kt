package com.axvyn.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.vector.ImageVector
import com.axvyn.app.navigation.Routes
import com.axvyn.app.ui.components.ToolCardModel
import com.axvyn.app.ui.theme.AccentAudio
import com.axvyn.app.ui.theme.AccentCompress
import com.axvyn.app.ui.theme.AccentConvert
import com.axvyn.app.ui.theme.AccentDocument
import com.axvyn.app.ui.theme.AccentExcel
import com.axvyn.app.ui.theme.AccentImage
import com.axvyn.app.ui.theme.AccentImagePdf
import com.axvyn.app.ui.theme.AccentMerge
import com.axvyn.app.ui.theme.AccentPdf
import com.axvyn.app.ui.theme.AccentPowerPoint

val ToolCatalog: List<ToolCardModel> = listOf(
    ToolCardModel(
        title = "Word to PDF",
        description = "DOCX up to 10 MB",
        icon = Icons.Default.Description,
        accent = AccentDocument,
        route = Routes.WORD_TO_PDF,
    ),
    ToolCardModel(
        title = "PowerPoint to PDF",
        description = "PPTX up to 10 MB",
        icon = Icons.Default.Slideshow,
        accent = AccentPowerPoint,
        route = Routes.POWERPOINT_TO_PDF,
    ),
    ToolCardModel(
        title = "Excel to PDF",
        description = "XLSX up to 10 MB",
        icon = Icons.Default.TableChart,
        accent = AccentExcel,
        route = Routes.EXCEL_TO_PDF,
    ),
    ToolCardModel(
        title = "Image to PDF",
        description = "JPG, PNG, WebP up to 10 MB each",
        icon = Icons.Default.PictureAsPdf,
        accent = AccentImagePdf,
        route = Routes.IMAGE_TO_PDF,
    ),
    ToolCardModel(
        title = "Compress PDF",
        description = "PDF up to 10 MB",
        icon = Icons.Default.PictureAsPdf,
        accent = AccentCompress,
        route = Routes.COMPRESS_PDF,
    ),
    ToolCardModel(
        title = "Merge PDF",
        description = "Combine PDFs up to 10 MB each",
        icon = Icons.Default.PictureAsPdf,
        accent = AccentMerge,
        route = Routes.MERGE_PDF,
    ),
    ToolCardModel(
        title = "Image Compressor",
        description = "Shrink JPEG, PNG & WebP",
        icon = Icons.Default.Image,
        accent = AccentImage,
        route = Routes.IMAGE_COMPRESSOR,
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
