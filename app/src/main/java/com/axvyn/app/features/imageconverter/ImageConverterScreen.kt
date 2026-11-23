package com.axvyn.app.features.imageconverter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Transform
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
fun ImageConverterScreen(onBack: () -> Unit) {
    ToolScreenShell(
        title = "WEBP / AVIF to PNG",
        description = "Convert modern image formats into widely supported PNGs.",
        icon = Icons.Default.Transform,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            DisabledSelectionButton(label = "Choose a WebP or AVIF image", icon = Icons.Default.UploadFile)
            Text(
                text = "Supported: .webp and .avif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "OUTPUT") {
            Text(
                text = "PNG",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Image dimensions and transparency will be preserved where the decoder allows it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PlaceholderNotice(
            message = "The decoder and PNG encoder are not implemented yet. No image is converted here.",
        )
        DisabledPrimaryButton(label = "Convert to PNG")
    }
}
