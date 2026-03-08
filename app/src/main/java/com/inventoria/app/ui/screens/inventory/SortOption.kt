package com.inventoria.app.ui.screens.inventory

enum class SortOption(val displayName: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Recently Updated"),
    QUANTITY_DESC("Highest Quantity"),
    PRICE_DESC("Highest Price")
}
