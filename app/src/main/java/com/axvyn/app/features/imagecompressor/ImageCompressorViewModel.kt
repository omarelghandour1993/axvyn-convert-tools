package com.axvyn.app.features.imagecompressor

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

class ImageCompressorViewModel(private val repository: ImageCompressorRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImageCompressorUiState())
    val state: StateFlow<ImageCompressorUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onSourceSelected(context: Context, uri: Uri?) {
        if (uri == null) return
        discardResult()
        viewModelScope.launch {
            runCatching { repository.inspect(uri) }
                .onSuccess { source -> _state.update { it.copy(source = source, message = null) } }
                .onFailure {
                    _state.update {
                        it.copy(
                            source = null,
                            message = "Could not read that image. Please choose a JPEG, PNG or WebP file.",
                        )
                    }
                }
        }
    }

    fun onQualityChange(quality: Int) {
        _state.update { it.copy(quality = quality.coerceIn(1, 100)) }
    }

    fun onFormatChange(format: ImageFormat) {
        _state.update { it.copy(outputFormat = format, message = null) }
    }

    fun onResizeChange(enabled: Boolean) {
        _state.update { it.copy(resizeEnabled = enabled) }
    }

    fun onWidthChange(value: String) {
        _state.update { it.copy(widthInput = value.filter { it.isDigit() }.take(5)) }
    }

    fun onHeightChange(value: String) {
        _state.update { it.copy(heightInput = value.filter { it.isDigit() }.take(5)) }
    }

    fun onTargetSizeChange(value: String) {
        _state.update { it.copy(targetSizeKbInput = value.filter { it.isDigit() }.take(6)) }
    }

    fun compress(uri: Uri) {
        if (job?.isActive == true) return
        val current = _state.value
        val width = current.widthInput.toIntOrNull()?.takeIf { it > 0 }
        val height = current.heightInput.toIntOrNull()?.takeIf { it > 0 }
        val targetSize = current.targetSizeKbInput.toIntOrNull()?.takeIf { it > 0 }

        if (current.resizeEnabled && width == null && height == null) {
            _state.update { it.copy(message = "Enter a width or height, or turn resize off.") }
            return
        }
        if (current.outputFormat == ImageFormat.PNG && targetSize != null) {
            _state.update { it.copy(message = "PNG is lossless — choose JPEG or WebP to set a target file size.") }
            return
        }

        discardResult()
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.compress(
                    uri = uri,
                    settings = CompressionSettings(
                        outputFormat = current.outputFormat,
                        quality = current.quality,
                        resizeEnabled = current.resizeEnabled,
                        targetWidth = width,
                        targetHeight = height,
                        targetSizeKb = targetSize,
                    ),
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
                        processing = ProcessingState.Failed(error.message ?: "Compression failed."),
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
                _state.update {
                    it.copy(message = error.message ?: "Could not save the compressed image.")
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
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
}

class ImageCompressorViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = ImageCompressorRepository(context.applicationContext)
        return ImageCompressorViewModel(repository) as T
    }
}
