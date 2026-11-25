package com.axvyn.app.features.texttovoice

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.axvyn.app.data.voicepacks.VoicePackCatalog
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell
import kotlin.math.roundToInt

@Composable
fun TextToVoiceScreen(
    onBack: () -> Unit,
    viewModel: TextToVoiceViewModel = viewModel(
        factory = TextToVoiceViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var languageMenuOpen by remember { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    val isRunning = state.processing is ProcessingState.Running
    val progress = (state.processing as? ProcessingState.Running)?.progress ?: 0f
    val voicesForLanguage = state.availableVoices.filter { it.languageDisplay.equals(state.selectedLanguage, ignoreCase = true) }
    val selectedVoice = state.availableVoices.firstOrNull { it.id == state.selectedVoiceId }
        ?: voicesForLanguage.firstOrNull()

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg"),
    ) { destination ->
        if (destination != null) viewModel.save(destination)
    }

    ToolScreenShell(
        title = "Text to Voice",
        description = "Turn typed text into offline speech and export MP3.",
        icon = Icons.Default.RecordVoiceOver,
        onBack = onBack,
    ) {
        SectionCard(title = "TEXT") {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("Text to speak") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 8,
                enabled = !isRunning,
            )
            Text(
                text = "${state.text.length} / ${state.maxCharacters} characters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }

        SectionCard(title = "VOICE") {
            Box {
                OutlinedButton(
                    onClick = { languageMenuOpen = true },
                    enabled = !isRunning && state.languages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Language: ${state.selectedLanguage}", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = languageMenuOpen,
                    onDismissRequest = { languageMenuOpen = false },
                ) {
                    state.languages.forEach { language ->
                        val available = state.availableVoices.any { it.languageDisplay.equals(language, ignoreCase = true) }
                        DropdownMenuItem(
                            text = {
                                Text(if (available) language else "$language (no offline voice)")
                            },
                            onClick = {
                                viewModel.onLanguageChange(language)
                                languageMenuOpen = false
                            },
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { voiceMenuOpen = true },
                    enabled = !isRunning && voicesForLanguage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Voice: ${selectedVoice?.label ?: "No offline voice available"}",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = voiceMenuOpen,
                    onDismissRequest = { voiceMenuOpen = false },
                ) {
                    voicesForLanguage.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.label) },
                            onClick = {
                                viewModel.onVoiceChange(voice.id)
                                voiceMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(
                text = if (state.isLoadingVoices) "Checking on-device voices…"
                else "Uses on-device TTS voices already installed on this phone. Conversion stays offline; no voice packs are downloaded by Axvyn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.message?.let { message ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
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
                text = "Generation failed: ${processing.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            ProcessingState.Cancelled -> Text(
                text = "Generation cancelled. No file was saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }

        Button(
            onClick = viewModel::generate,
            enabled = !isRunning && !state.isLoadingVoices && state.text.trim().isNotEmpty() && selectedVoice != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                text = if (isRunning) "Generating…" else "Generate & export MP3",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (isRunning) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }

        state.result?.let { result ->
            SectionCard(title = "RESULT") {
                Text(
                    text = "MP3: ${formatTtsBytes(result.mp3Bytes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val duration = result.durationMs?.let { " · ${formatTtsDuration(it)}" }.orEmpty()
                Text(
                    text = "${result.charCount} characters$duration · audio/mpeg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Voice: ${result.voiceLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { saveLauncher.launch(result.fileName) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save a copy")
                }
                result.shareUri?.let { uri ->
                    OutlinedButton(
                        onClick = {
                            val preview = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "audio/mpeg")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(preview, "Preview audio")) }
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

        Text(
            text = "Supported languages are verified against on-device voices: ${VoicePackCatalog.candidateLanguages.joinToString(", ")}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
