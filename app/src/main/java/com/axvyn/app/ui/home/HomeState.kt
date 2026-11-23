package com.axvyn.app.ui.home

import com.axvyn.app.ui.components.ToolCardModel

data class HomeUiState(
    val query: String = "",
    val tools: List<ToolCardModel> = ToolCatalog,
)
