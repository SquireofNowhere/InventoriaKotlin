package com.inventoria.app.ui.screens.dashboard

import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.Task

data class DashboardUiState(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val outOfStockCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val showTotalValue: Boolean = true
)
