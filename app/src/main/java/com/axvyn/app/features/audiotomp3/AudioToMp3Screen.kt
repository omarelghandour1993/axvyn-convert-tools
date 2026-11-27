package com.axvyn.app.features.audiotomp3

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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.core.processing.ProcessingState
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell
import kotlin.math.roundToInt

@Composable
fun AudioToMp3Screen(
    onBack: () -> Unit,
    viewModel: AudioToMp3ViewModel = viewModel(
        factory = AudioToMp3ViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    val isRunning = state.processing is ProcessingState.Running
    val progress = (state.processing as? ProcessingState.Running)?.progress ?: 0f

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUri = uri
            viewModel.onSourceSelected(uri)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg"),
    ) { destination ->
        if (destination != null) viewModel.save(destination)
    }

    ToolScreenShell(
        title = "Audio to MP3",
        description = "Convert local audio files into MP3 on the phone.",
        icon = Icons.Default.Audiotrack,
        onBack = onBack,
    ) {
        SectionCard(title = "INPUT") {
            OutlinedButton(
                onClick = {
                    pickLauncher.launch(
                        arrayOf(
                            "audio/*",
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/m4a",
                            "audio/x-m4a",
                            "audio/wav",
                            "audio/x-wav",
                            "audio/vnd.wave",
                            "audio/flac",
                            "audio/x-flac",
                        ),
                    )
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (sourceUri == null) "Choose an audio file" else "Choose a different audio file")
            }
            Text(
                text = "Supported: M4A, WAV, FLAC · One file at a time · Everything stays on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.source?.let { source ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                    val duration = source.durationMs?.let { " · ${formatDuration(it)}" }.orEmpty()
                    Text(
                        text = "${formatAudioBytes(source.sizeBytes)}$duration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.source != null && sourceUri == null) {
                Text(
                    text = "Choose the audio file again before converting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = "OUTPUT") {
            Text(
                text = "MP3 bitrate",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mp3Bitrate.entries.forEach { bitrate ->
                    FilterChip(
                        selected = state.bitrate == bitrate,
                        onClick = { viewModel.onBitrateChange(bitrate) },
                        label = { Text("${bitrate.kbps}") },
                        enabled = !isRunning,
                    )
                }
            }
            Text(
                text = "Selected: ${state.bitrate.label}. A higher bitrate cannot restore quality lost in a low-quality source recording.",
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
            onClick = { sourceUri?.let(viewModel::convert) },
            enabled = sourceUri != null && !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(
                text = if (isRunning) "Converting…" else "Convert to MP3",
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
                    text = "Original: ${formatAudioBytes(result.originalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "MP3: ${formatAudioBytes(result.convertedBytes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val duration = result.durationMs?.let { " · ${formatDuration(it)}" }.orEmpty()
                Text(
                    text = "${result.bitrateKbps} kbps$duration · audio/mpeg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                                context.startActivity(Intent.createChooser(preview, "Preview audio"))
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
