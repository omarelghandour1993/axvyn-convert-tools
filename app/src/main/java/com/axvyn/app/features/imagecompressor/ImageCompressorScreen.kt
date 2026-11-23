package com.axvyn.app.features.imagecompressor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.ui.components.DisabledPrimaryButton
import com.axvyn.app.ui.components.DisabledSelectionButton
import com.axvyn.app.ui.components.PlaceholderNotice
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell

@Composable
fun ImageCompressorScreen(
    onBack: () -> Unit,
    viewModel: ImageCompressorViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ToolScreenShell(
        title = "Image Compressor",
        description = "Reduce image size while keeping it usable.",
        icon = Icons.Default.Image,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            DisabledSelectionButton(label = "Choose an image", icon = Icons.Default.UploadFile)
            Text(
                text = "Supported: JPEG, PNG and WebP · One image at a time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "COMPRESSION") {
            Text(
                text = "Quality: ${(state.quality * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.quality,
                onValueChange = viewModel::onQualityChange,
                valueRange = 0.1f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Resize before compressing", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.resizeEnabled,
                    onCheckedChange = viewModel::onResizeChange,
                )
            }
        }

        PlaceholderNotice(
            message = "The compression engine is not implemented yet. No image is selected, resized or saved here.",
        )
        DisabledPrimaryButton(label = "Compress image")
    }
}
