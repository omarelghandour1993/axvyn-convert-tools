package com.axvyn.app.features.imagetopdf

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

class ImageToPdfViewModel(private val repository: ImageToPdfRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImageToPdfUiState())
    val state: StateFlow<ImageToPdfUiState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var cancelRequested = false

    fun onSourcesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        discardResult()
        viewModelScope.launch {
            val inspected = mutableListOf<ImageSourceInfo>()
            for (uri in uris) {
                val result = runCatching { repository.inspect(uri) }
                val info = result.getOrElse { e ->
                    _state.update { s -> s.copy(sources = emptyList(), message = e.message ?: "Could not read one of the selected images. Please choose supported image files.") }
                    return@launch
                }
                if (info.sizeBytes > MAX_BYTES) {
                    _state.update {
                        it.copy(
                            sources = emptyList(),
                            message = "\"${info.displayName}\" is too large (${formatBytes(info.sizeBytes)}). Maximum allowed size is 10 MB per image.",
                        )
                    }
                    return@launch
                }
                inspected += info
            }
            _state.update { it.copy(sources = inspected, message = null) }
        }
    }

    fun clearSources() {
        if (job?.isActive == true) return
        discardResult()
        _state.update { it.copy(sources = emptyList(), message = null) }
    }

    fun convert() {
        if (job?.isActive == true) return
        val sources = _state.value.sources
        if (sources.isEmpty()) {
            _state.update { it.copy(message = "Choose at least one image.") }
            return
        }
        val oversized = sources.firstOrNull { it.sizeBytes > MAX_BYTES }
        if (oversized != null) {
            _state.update { it.copy(message = "\"${oversized.displayName}\" is too large (${formatBytes(oversized.sizeBytes)}). Maximum allowed size is 10 MB per image.") }
            return
        }
        discardResult()
        cancelRequested = false
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.convert(
                    uris = sources.map { it.uri },
                    onProgress = { progress ->
                        _state.update { s ->
                            if (s.processing is ProcessingState.Running) s.copy(processing = ProcessingState.Running(progress.coerceIn(0f, 1f))) else s
                        }
                    },
                    isCancelled = { cancelRequested || !isActive },
                )
                val remaining = 350 - (System.currentTimeMillis() - started)
                if (remaining > 0) delay(remaining)
                if (isActive && !cancelRequested) {
                    _state.update { it.copy(processing = ProcessingState.Success, result = result) }
                } else {
                    repository.discard(result.fileName)
                    _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
                throw e
            } catch (e: ImageToPdfEngine.Cancellation) {
                _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
            } catch (e: Exception) {
                _state.update { it.copy(processing = ProcessingState.Failed(e.message ?: "Conversion failed."), result = null) }
            }
        }
    }

    fun cancel() {
        cancelRequested = true
        job?.cancel()
    }

    fun save(destination: Uri) {
        val fileName = _state.value.result?.fileName ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.save(fileName, destination) } }
                .onSuccess { discardResult("Saved successfully.") }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Could not save the PDF.") } }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }

    fun discardResult(message: String? = null) {
        _state.value.result?.fileName?.let(repository::discard)
        _state.update { it.copy(result = null, processing = ProcessingState.Idle, message = message) }
    }

    override fun onCleared() {
        cancelRequested = true
        job?.cancel()
        _state.value.result?.fileName?.let(repository::discard)
        super.onCleared()
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}

class ImageToPdfViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ImageToPdfViewModel(ImageToPdfRepository(context.applicationContext)) as T
    }
}
