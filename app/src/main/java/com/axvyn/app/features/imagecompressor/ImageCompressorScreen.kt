package com.axvyn.app.features.imagecompressor

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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.core.processing.ProcessingState
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell
import kotlin.math.roundToInt

@Composable
fun ImageCompressorScreen(
    onBack: () -> Unit,
    viewModel: ImageCompressorViewModel = viewModel(
        factory = ImageCompressorViewModelFactory(LocalContext.current.applicationContext),
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
            viewModel.onSourceSelected(context, uri)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(state.result?.mimeType ?: state.outputFormat.mimeType),
    ) { destination ->
        if (destination != null) viewModel.save(destination)
    }

    ToolScreenShell(
        title = "Image Compressor",
        description = "Reduce image size while keeping it usable.",
        icon = Icons.Default.Image,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            OutlinedButton(
                onClick = {
                    pickLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (sourceUri == null) "Choose an image" else "Choose a different image")
            }
            Text(
                text = "JPEG, PNG or WebP · One image at a time · Everything stays on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.source?.let { source ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${formatBytes(source.sizeBytes)} · ${source.width} × ${source.height} · ${source.format.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.source != null && sourceUri == null) {
                Text(
                    text = "Choose the image again before compressing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = "COMPRESSION") {
            Text("Output format", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.outputFormat == format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.label) },
                        enabled = !isRunning,
                    )
                }
            }

            Text(
                text = "Quality: ${state.quality}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.quality.toFloat(),
                onValueChange = { viewModel.onQualityChange(it.roundToInt()) },
                valueRange = 1f..100f,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.targetSizeKbInput,
                onValueChange = viewModel::onTargetSizeChange,
                label = { Text("Target size in KB (optional)") },
                singleLine = true,
                enabled = !isRunning,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.outputFormat == ImageFormat.PNG) {
                Text(
                    text = "PNG is lossless, so a target size is not available. Use JPEG or WebP instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Resize before compressing",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.resizeEnabled,
                    onCheckedChange = viewModel::onResizeChange,
                    enabled = !isRunning,
                )
            }
            if (state.resizeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.widthInput,
                        onValueChange = viewModel::onWidthChange,
                        label = { Text("Width (px)") },
                        singleLine = true,
                        enabled = !isRunning,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.heightInput,
                        onValueChange = viewModel::onHeightChange,
                        label = { Text("Height (px)") },
                        singleLine = true,
                        enabled = !isRunning,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "Leave one blank to keep the original aspect ratio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.message?.let { message ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
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
            onClick = { sourceUri?.let(viewModel::compress) },
            enabled = sourceUri != null && !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(
                text = if (isRunning) "Compressing…" else "Compress image",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (isRunning) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedButton(
                onClick = viewModel::cancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }

        state.result?.let { result ->
            SectionCard(title = "RESULT") {
                Text(
                    text = "Original: ${formatBytes(result.originalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Compressed: ${formatBytes(result.compressedBytes)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                val change = if (result.originalBytes > 0) {
                    (1 - result.compressedBytes.toDouble() / result.originalBytes) * 100
                } else {
                    0.0
                }
                Text(
                    text = if (change >= 0) "${change.roundToInt()}% smaller" else "${(-change).roundToInt()}% larger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${result.width} × ${result.height} · ${result.mimeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { saveLauncher.launch(result.fileName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save a copy")
                }
                result.shareUri?.let { uri ->
                    OutlinedButton(
                        onClick = {
                            val preview = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, result.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching {
                                context.startActivity(Intent.createChooser(preview, "Preview image"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview")
                    }
                }
                TextButton(onClick = viewModel::discardResult) {
                    Text("Discard result")
                }
            }
        }
    }
}
