package com.axvyn.app.features.imagetopdf

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.core.processing.ProcessingState
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell
import kotlin.math.roundToInt

@Composable
fun ImageToPdfScreen(
    onBack: () -> Unit,
    viewModel: ImageToPdfViewModel = viewModel(
        factory = ImageToPdfViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRunning = state.processing is ProcessingState.Running
    val progress = (state.processing as? ProcessingState.Running)?.progress ?: 0f

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onSourcesSelected(uris)
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        if (destination != null) viewModel.save(destination)
    }

    ToolScreenShell(
        title = "Image to PDF",
        description = "Combine images into a single PDF on your device.",
        icon = Icons.Default.PictureAsPdf,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.sources.isEmpty()) "Choose images" else "Choose different images")
            }
            Text(
                text = "Supported: JPG, PNG, WebP · Multiple images · Maximum 10 MB per image · Everything stays on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.sources.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${state.sources.size} image${if (state.sources.size == 1) "" else "s"} selected", style = MaterialTheme.typography.titleSmall)
                    state.sources.take(5).forEach { source ->
                        Text(
                            text = "${source.displayName} · ${formatBytes(source.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.sources.size > 5) {
                        Text(
                            text = "+${state.sources.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = viewModel::clearSources, enabled = !isRunning) { Text("Clear selection") }
                }
            }
        }

        SectionCard(title = "OUTPUT") {
            Text("PDF, written by the app to a location you choose.", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Each image is placed on its own PDF page, fitted to the page while preserving aspect ratio and image quality where possible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.message?.let { message ->
            val isSuccess = message == "Saved successfully."
            val color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                    tint = color,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }

        when (val processing = state.processing) {
            is ProcessingState.Failed -> Text(
                text = "Conversion failed: ${processing.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            ProcessingState.Cancelled -> Text(
                text = "Conversion cancelled. No file was saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }

        Button(
            onClick = viewModel::convert,
            enabled = state.sources.isNotEmpty() && !isRunning,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(if (isRunning) "Converting…" else "Convert to PDF", style = MaterialTheme.typography.titleMedium)
        }

        if (isRunning) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }

        state.result?.let { result ->
            SectionCard(title = "RESULT") {
                Text("Original: ${formatBytes(result.originalBytes)}", style = MaterialTheme.typography.bodyMedium)
                Text("PDF: ${formatBytes(result.outputBytes)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${result.pageCount} page${if (result.pageCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { saveLauncher.launch(result.fileName) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save PDF")
                }
                result.shareUri?.let { uri ->
                    OutlinedButton(
                        onClick = {
                            val preview = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(preview, "Preview PDF")) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview")
                    }
                }
                TextButton(onClick = viewModel::discardResult) { Text("Discard result") }
            }
        }
    }
}
