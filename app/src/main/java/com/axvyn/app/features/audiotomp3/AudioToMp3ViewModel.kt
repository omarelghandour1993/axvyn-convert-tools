package com.axvyn.app.features.audiotomp3

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.axvyn.app.core.processing.ProcessingState
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

class AudioToMp3ViewModel(private val repository: AudioToMp3Repository) : ViewModel() {
    private val _state = MutableStateFlow(AudioToMp3UiState())
    val state: StateFlow<AudioToMp3UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onSourceSelected(uri: Uri?) {
        if (uri == null) return
        discardResult()
        viewModelScope.launch {
            runCatching { repository.inspect(uri) }
                .onSuccess { source -> _state.update { it.copy(source = source, message = null) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            source = null,
                            message = error.message ?: "Could not read that audio file.",
                        )
                    }
                }
        }
    }

    fun onBitrateChange(bitrate: Mp3Bitrate) {
        _state.update { it.copy(bitrate = bitrate) }
    }

    fun convert(uri: Uri) {
        if (job?.isActive == true) return
        if (_state.value.source == null) {
            _state.update { it.copy(message = "Choose an audio file first.") }
            return
        }
        val bitrate = _state.value.bitrate
        discardResult()
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.convert(
                    uri = uri,
                    settings = AudioConversionSettings(bitrateKbps = bitrate.kbps),
                    onProgress = { progress ->
                        _state.update { state ->
                            if (state.processing is ProcessingState.Running) {
                                state.copy(processing = ProcessingState.Running(progress.coerceIn(0f, 1f)))
                            } else state
                        }
                    },
                )
                val remaining = 350 - (System.currentTimeMillis() - started)
                if (remaining > 0) delay(remaining)
                if (isActive) {
                    _state.update { it.copy(processing = ProcessingState.Success, result = result) }
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        processing = ProcessingState.Failed(error.message ?: "Conversion failed."),
                        result = null,
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun save(destination: Uri) {
        val fileName = _state.value.result?.fileName ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.save(fileName, destination) }
            }.onSuccess {
                discardResult()
            }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Could not save the MP3 file.") }
            }
        }
    }

    fun discardResult() {
        _state.value.result?.fileName?.let(repository::discard)
        _state.update { it.copy(result = null, processing = ProcessingState.Idle) }
    }

    override fun onCleared() {
        job?.cancel()
        _state.value.result?.fileName?.let(repository::discard)
        super.onCleared()
    }
}

class AudioToMp3ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = AudioToMp3Repository(context.applicationContext)
        return AudioToMp3ViewModel(repository) as T
    }
}
