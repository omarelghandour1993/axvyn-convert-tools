package com.axvyn.app.features.compresspdf

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
fun CompressPdfScreen(
    onBack: () -> Unit,
    viewModel: CompressPdfViewModel = viewModel(
        factory = CompressPdfViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sourceUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val isRunning = state.processing is ProcessingState.Running
    val progress = (state.processing as? ProcessingState.Running)?.progress ?: 0f

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUri = uri
            viewModel.onSourceSelected(uri)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        if (destination != null) viewModel.save(destination)
    }

    ToolScreenShell(
        title = "Compress PDF",
        description = "Reduce PDF file size on your device.",
        icon = Icons.Default.PictureAsPdf,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (sourceUri == null) "Choose a PDF" else "Choose a different PDF")
            }
            Text(
                text = "PDF · Maximum file size: 10 MB · Everything stays on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.source?.let { source ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${formatBytes(source.sizeBytes)} · ${source.pageCount} page${if (source.pageCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.source != null && sourceUri == null) {
                Text(
                    text = "Choose the PDF again before compressing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = "COMPRESSION") {
            Text("Compression level", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfCompressionLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.level == level,
                        onClick = { viewModel.onLevelChange(level) },
                        label = { Text(level.label) },
                        enabled = !isRunning,
                    )
                }
            }
            Text(
                text = "Creates an image-based PDF by re-rendering pages. This can reduce file size, but text selection and vector sharpness may be reduced.",
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
                text = "Compression failed: ${processing.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            ProcessingState.Cancelled -> Text(
                text = "Compression cancelled. No file was saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }

        Button(
            onClick = { sourceUri?.let(viewModel::convert) },
            enabled = sourceUri != null && state.source != null && !isRunning,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(if (isRunning) "Compressing…" else "Compress PDF", style = MaterialTheme.typography.titleMedium)
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
                Text("Compressed: ${formatBytes(result.outputBytes)}", style = MaterialTheme.typography.titleMedium)
                val saved = result.originalBytes - result.outputBytes
                if (saved > 0) {
                    Text(
                        text = "Saved ${formatBytes(saved)} (${((saved.toDouble() / result.originalBytes.coerceAtLeast(1)) * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (result.originalBytes > 0) {
                    Text(
                        text = "Output is not smaller at this level. Try a smaller-file setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
