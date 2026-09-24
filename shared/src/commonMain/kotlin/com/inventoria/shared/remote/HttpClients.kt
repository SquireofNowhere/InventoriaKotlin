package com.inventoria.shared.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/** What every client talking to Firebase needs: JSON bodies read and written with [InventoriaJson]. */
fun HttpClientConfig<*>.installInventoriaJson() {
    install(ContentNegotiation) { json(InventoriaJson) }
}
