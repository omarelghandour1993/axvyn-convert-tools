package com.axvyn.app.features.texttovoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axvyn.app.data.voicepacks.VoicePackCatalog
import com.axvyn.app.ui.components.DisabledPrimaryButton
import com.axvyn.app.ui.components.PlaceholderNotice
import com.axvyn.app.ui.components.SectionCard
import com.axvyn.app.ui.components.ToolScreenShell

@Composable
fun TextToVoiceScreen(
    onBack: () -> Unit,
    viewModel: TextToVoiceViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var languageMenuOpen by remember { mutableStateOf(false) }

    ToolScreenShell(
        title = "Text to Voice",
        description = "Turn typed text into speech without a connection.",
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Language: ${state.language}", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = languageMenuOpen,
                    onDismissRequest = { languageMenuOpen = false },
                ) {
                    VoicePackCatalog.candidateLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language) },
                            onClick = {
                                viewModel.onLanguageChange(language)
                                languageMenuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                text = "No voice packs installed · Voice selection arrives with a downloaded pack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "PLAYBACK") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
                Text(
                    text = "Audio controls become active after a voice pack is installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PlaceholderNotice(
            message = "Speech generation and MP3 export are not implemented yet. No voice is downloaded and no audio is produced.",
        )
        DisabledPrimaryButton(label = "Generate & export MP3")
    }
}
