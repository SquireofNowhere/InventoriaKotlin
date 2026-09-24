package com.inventoria.web

import com.inventoria.shared.remote.SessionStore
import kotlin.js.Promise

// The few browser APIs the app needs, declared directly rather than pulling in a wrapper library.
// The inventoria* functions live in resources/inventoria-auth.js.

external fun inventoriaGoogleSignIn(clientId: String): Promise<JsString>

external fun inventoriaHideBoot()

external fun inventoriaPrefersDark(): Boolean

external interface Storage : JsAny {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}

external val localStorage: Storage

/** Keeps the signed-in session across reloads, the way the Firebase JS SDK does by default. */
object LocalStorageSessionStore : SessionStore {
    private const val KEY = "inventoria.session"

    override fun load(): String? = runCatching { localStorage.getItem(KEY) }.getOrNull()

    override fun save(value: String?) {
        runCatching {
            if (value == null) localStorage.removeItem(KEY) else localStorage.setItem(KEY, value)
        }
    }
}
