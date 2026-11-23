package com.axvyn.app.features.pdftoimage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.ui.components.DisabledPrimaryButton
import com.axvyn.app.ui.components.DisabledSelectionButton
import com.axvyn.app.ui.components.PlaceholderNotice
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell

@Composable
fun PdfToImageScreen(
    onBack: () -> Unit,
    viewModel: PdfToImageViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ToolScreenShell(
        title = "PDF to Images",
        description = "Render PDF pages into image files, one page at a time.",
        icon = Icons.Default.PictureAsPdf,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            DisabledSelectionButton(label = "Choose a PDF", icon = Icons.Default.UploadFile)
            OutlinedTextField(
                value = "All pages",
                onValueChange = {},
                label = { Text("Pages") },
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Page selection becomes available when the renderer is added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "OUTPUT") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RasterFormat.entries.forEach { format ->
                    FilterChip(
                        selected = format == state.format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.label) },
                    )
                }
            }
            Text(
                text = "Resolution: ${state.resolutionDpi} DPI",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150, 300).forEach { dpi ->
                    FilterChip(
                        selected = dpi == state.resolutionDpi,
                        onClick = { viewModel.onResolutionChange(dpi) },
                        label = { Text("$dpi DPI") },
                    )
                }
            }
        }

        PlaceholderNotice(
            message = "The PDF renderer is not implemented yet. Pages are not drawn and no images are saved.",
        )
        DisabledPrimaryButton(label = "Export pages")
    }
}
