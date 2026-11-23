package com.axvyn.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = query
        .map { value ->
            HomeUiState(
                query = value,
                tools = ToolCatalog.filter { tool ->
                    val term = value.trim()
                    term.isEmpty() ||
                        tool.title.contains(term, ignoreCase = true) ||
                        tool.description.contains(term, ignoreCase = true)
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun onQueryChange(value: String) {
        viewModelScope.launch { query.emit(value) }
    }
}
