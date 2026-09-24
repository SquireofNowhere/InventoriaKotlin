package com.inventoria.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.inventoria.web.ui.InventoriaWebApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("composeApp") {
        InventoriaWebApp()
    }
}
