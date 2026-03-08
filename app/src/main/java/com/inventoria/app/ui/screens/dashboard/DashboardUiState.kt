package com.inventoria.app.ui.screens.dashboard

import com.inventoria.app.data.model.InventoryItem

data class DashboardUiState(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val outOfStockCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showTotalValue: Boolean = true
)
