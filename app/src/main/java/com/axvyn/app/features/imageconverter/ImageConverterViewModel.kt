package com.axvyn.app.features.imageconverter

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

class ImageConverterViewModel(private val repository: ImageConverterRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImageConverterUiState())
    val state: StateFlow<ImageConverterUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onSourceSelected(context: Context, uri: Uri?) {
        if (uri == null) return
        discardResult()
        viewModelScope.launch {
            runCatching { repository.inspect(uri) }
                .onSuccess { source ->
                    if (source.sizeBytes > MAX_BYTES) {
                        _state.update {
                            it.copy(
                                source = null,
                                message = "This file is too large (${formatBytes(source.sizeBytes)}). Maximum allowed size is 10 MB.",
                            )
                        }
                    } else {
                        _state.update { it.copy(source = source, message = null) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            source = null,
                            message = error.message
                                ?: "Could not read that image. Please choose a WebP or AVIF file.",
                        )
                    }
                }
        }
    }

    fun convert(uri: Uri) {
        if (job?.isActive == true) return
        val source = _state.value.source ?: run {
            _state.update { it.copy(message = "Choose a WebP or AVIF image first.") }
            return
        }
        if (source.sizeBytes > MAX_BYTES) {
            _state.update { it.copy(message = "This file is too large (${formatBytes(source.sizeBytes)}). Maximum allowed size is 10 MB.") }
            return
        }
        if (source.format == SourceImageFormat.UNKNOWN) {
            _state.update { it.copy(message = "This file is not a supported WebP or AVIF image.") }
            return
        }

        discardResult()
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.convert(
                    uri = uri,
                    onProgress = { progress ->
                        _state.update { state ->
                            if (state.processing is ProcessingState.Running) {
                                state.copy(processing = ProcessingState.Running(progress.coerceIn(0f, 1f)))
                            } else {
                                state
                            }
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
            }.onSuccess { discardResult("Saved successfully.") }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Could not save the PNG image.") }
            }
        }
    }

    fun discardResult(message: String? = null) {
        _state.value.result?.fileName?.let(repository::discard)
        _state.update { it.copy(result = null, processing = ProcessingState.Idle, message = message) }
    }

    override fun onCleared() {
        job?.cancel()
        _state.value.result?.fileName?.let(repository::discard)
        super.onCleared()
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}

class ImageConverterViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = ImageConverterRepository(context.applicationContext)
        return ImageConverterViewModel(repository) as T
    }
}
