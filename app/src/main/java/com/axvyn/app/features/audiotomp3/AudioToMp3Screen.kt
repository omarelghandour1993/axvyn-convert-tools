package com.axvyn.app.features.audiotomp3

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.axvyn.app.ui.components.DisabledPrimaryButton
import com.axvyn.app.ui.components.DisabledSelectionButton
import com.axvyn.app.ui.components.PlaceholderNotice
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell

@Composable
fun AudioToMp3Screen(onBack: () -> Unit) {
    ToolScreenShell(
        title = "Audio to MP3",
        description = "Convert local audio files into MP3 on the phone.",
        icon = Icons.Default.Audiotrack,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            DisabledSelectionButton(label = "Choose an audio file", icon = Icons.Default.UploadFile)
            Text(
                text = "Supported: M4A, WAV and FLAC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "OUTPUT") {
            Text(
                text = "MP3 · 320 kbps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A 320 kbps output cannot restore quality lost in a low-quality source recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PlaceholderNotice(
            message = "The audio conversion engine is not implemented yet. Progress, cancellation and file export arrive with it.",
        )
        DisabledPrimaryButton(label = "Convert to MP3")
    }
}
