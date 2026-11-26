package com.axvyn.app.features.exceltopdf

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

class ExcelToPdfViewModel(private val repository: ExcelToPdfRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExcelToPdfUiState())
    val state: StateFlow<ExcelToPdfUiState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var cancelRequested = false

    fun onSourceSelected(uri: Uri?) {
        if (uri == null) return
        discardResult()
        viewModelScope.launch {
            runCatching { repository.inspect(uri) }
                .onSuccess { source ->
                    val ext = source.extension.lowercase()
                    when {
                        ext == "xls" -> _state.update {
                            it.copy(source = null, message = "Legacy .xls files are not supported yet. Please save the spreadsheet as .xlsx and try again.")
                        }
                        ext != "xlsx" -> _state.update {
                            it.copy(source = null, message = "Please choose a .xlsx Excel spreadsheet.")
                        }
                        source.sizeBytes > MAX_BYTES -> _state.update {
                            it.copy(
                                source = null,
                                message = "This file is too large (${formatBytes(source.sizeBytes)}). Maximum allowed size is 10 MB.",
                            )
                        }
                        else -> _state.update { it.copy(source = source, message = null) }
                    }
                }
                .onFailure {
                    _state.update { it.copy(source = null, message = "Could not read that spreadsheet.") }
                }
        }
    }

    fun convert(uri: Uri) {
        if (job?.isActive == true) return
        val source = _state.value.source ?: return
        if (source.sizeBytes > MAX_BYTES) {
            _state.update { it.copy(message = "This file is too large (${formatBytes(source.sizeBytes)}). Maximum allowed size is 10 MB.") }
            return
        }
        discardResult()
        cancelRequested = false
        job = viewModelScope.launch {
            _state.update { it.copy(processing = ProcessingState.Running(0f), message = null) }
            val started = System.currentTimeMillis()
            try {
                val result = repository.convert(
                    uri = uri,
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
            } catch (e: ExcelToPdfEngine.Cancellation) {
                _state.update { it.copy(processing = ProcessingState.Cancelled, result = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(processing = ProcessingState.Failed(e.message ?: "Conversion failed."), result = null)
                }
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
                .onSuccess { discardResult() }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Could not save the PDF.") } }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }

    fun discardResult() {
        _state.value.result?.fileName?.let(repository::discard)
        _state.update { it.copy(result = null, processing = ProcessingState.Idle) }
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

class ExcelToPdfViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExcelToPdfViewModel(ExcelToPdfRepository(context.applicationContext)) as T
    }
}
