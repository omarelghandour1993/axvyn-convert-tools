package com.axvyn.app.features.texttovoice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.axvyn.app.core.processing.ProcessingState
import com.axvyn.app.data.voicepacks.VoicePackCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextToVoiceViewModel(private val repository: TextToVoiceRepository) : ViewModel() {
    private val _state = MutableStateFlow(TextToVoiceUiState(maxCharacters = VoicePackCatalog.MAX_CHARACTERS))
    val state: StateFlow<TextToVoiceUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingVoices = true, message = null) }
            val ready = runCatching { repository.initialize() }.getOrDefault(false)
            if (!ready) {
                _state.update {
                    it.copy(
                        isTtsReady = false,
                        isLoadingVoices = false,
                        languages = VoicePackCatalog.candidateLanguages,
                        message = "Text-to-speech is not available on this device.",
                    )
                }
                return@launch
            }
            val voices = runCatching { repository.availableVoices() }.getOrDefault(emptyList())
            val languages = repository.availableLanguages(voices)
            val defaultLang = languages.firstOrNull { it.equals("English", ignoreCase = true) }
                ?: languages.firstOrNull()
                ?: "English"
            val defaultVoice = voices.firstOrNull { it.languageDisplay.equals(defaultLang, ignoreCase = true) }
            _state.update {
                it.copy(
                    isTtsReady = true,
                    isLoadingVoices = false,
                    availableVoices = voices,
                    languages = languages,
                    selectedLanguage = defaultLang,
                    selectedVoiceId = defaultVoice?.id,
                    message = if (voices.isEmpty()) {
                        "No offline voices found for the supported languages. Install a TTS voice on your device and reopen this tool."
                    } else null,
                )
            }
        }
    }

    fun onTextChange(value: String) {
        _state.update { it.copy(text = value.take(VoicePackCatalog.MAX_CHARACTERS)) }
    }

    fun onLanguageChange(language: String) {
        val voices = _state.value.availableVoices
        val voice = voices.firstOrNull { it.languageDisplay.equals(language, ignoreCase = true) }
        _state.update { it.copy(selectedLanguage = language, selectedVoiceId = voice?.id, message = null) }
    }

    fun onVoiceChange(voiceId: String) {
        _state.update { it.copy(selectedVoiceId = voiceId, message = null) }
    }

    fun generate() {
        if (job?.isActive == true) return
        val current = _state.value
        val text = current.text.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(message = "Enter some text first.") }
            return
        }
        if (text.length > VoicePackCatalog.MAX_CHARACTERS) {
            _state.update { it.copy(message = "Text exceeds ${VoicePackCatalog.MAX_CHARACTERS} characters.") }
            return
        }
        val voice = current.availableVoices.firstOrNull { it.id == current.selectedVoiceId }
            ?: current.availableVoices.firstOrNull { it.languageDisplay.equals(current.selectedLanguage, ignoreCase = true) }
        if (voice == null) {
            _state.update { it.copy(message = "No offline voice is available for the selected language.") }
            return
        }

        discardResult()
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.synthesize(
                    request = TtsSynthesisRequest(text = text, voice = voice),
                    onProgress = { progress ->
                        _state.update { state ->
                            if (state.processing is ProcessingState.Running) {
                                state.copy(processing = ProcessingState.Running(progress.coerceIn(0f, 1f)))
                            } else state
                        }
                    },
                )
                val remaining = 300 - (System.currentTimeMillis() - started)
                if (remaining > 0) delay(remaining)
                if (isActive) {
                    _state.update { it.copy(processing = ProcessingState.Success, result = result) }
                }
            } catch (e: CancellationException) {
                repository.stop()
                _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        processing = ProcessingState.Failed(e.message ?: "Speech generation failed."),
                        result = null,
                    )
                }
            }
        }
    }

    fun cancel() {
        repository.stop()
        job?.cancel()
    }

    fun save(destination: Uri) {
        val fileName = _state.value.result?.fileName ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.save(fileName, destination) }
            }.onSuccess {
                discardResult()
            }.onFailure { e ->
                _state.update { it.copy(message = e.message ?: "Could not save the MP3 file.") }
            }
        }
    }

    fun discardResult() {
        _state.value.result?.fileName?.let(repository::discard)
        _state.update { it.copy(result = null, processing = ProcessingState.Idle) }
    }

    override fun onCleared() {
        repository.stop()
        job?.cancel()
        _state.value.result?.fileName?.let(repository::discard)
        repository.shutdown()
        super.onCleared()
    }
}

class TextToVoiceViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = TextToVoiceRepository(context.applicationContext)
        return TextToVoiceViewModel(repository) as T
    }
}
