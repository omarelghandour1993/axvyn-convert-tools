package com.axvyn.app.features.pdftoimage

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

class PdfToImageViewModel(private val repository: PdfToImageRepository) : ViewModel() {
    private val _state = MutableStateFlow(PdfToImageUiState())
    val state: StateFlow<PdfToImageUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onSourceSelected(context: Context, uri: Uri?) {
        if (uri == null) return
        discardResult()
        viewModelScope.launch {
            runCatching { repository.inspect(uri) }
                .onSuccess { source ->
                    _state.update {
                        it.copy(
                            source = source,
                            pageInput = "1",
                            message = null,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            source = null,
                            message = "Could not read that PDF. Please choose a valid PDF file.",
                        )
                    }
                }
        }
    }

    fun onFormatChange(format: RasterFormat) {
        _state.update { it.copy(format = format) }
    }

    fun onResolutionChange(dpi: Int) {
        _state.update { it.copy(resolutionDpi = dpi) }
    }

    fun onPageChange(value: String) {
        _state.update { it.copy(pageInput = value.filter { it.isDigit() }.take(5)) }
    }

    fun render(uri: Uri) {
        if (job?.isActive == true) return
        val current = _state.value
        val source = current.source ?: run {
            _state.update { it.copy(message = "Choose a PDF first.") }
            return
        }
        val pageNumber = current.pageInput.toIntOrNull()
        if (pageNumber == null || pageNumber !in 1..source.pageCount) {
            _state.update { it.copy(message = "Choose a page between 1 and ${source.pageCount}.") }
            return
        }

        discardResult()
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.render(
                    uri = uri,
                    settings = RenderSettings(
                        format = current.format,
                        dpi = current.resolutionDpi,
                        pageNumber = pageNumber,
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
                        processing = ProcessingState.Failed(error.message ?: "Rendering failed."),
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
                _state.update { it.copy(message = error.message ?: "Could not save the rendered image.") }
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

class PdfToImageViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = PdfToImageRepository(context.applicationContext)
        return PdfToImageViewModel(repository) as T
    }
}
