package com.inventoria.shared.remote

import kotlinx.serialization.json.Json

/**
 * The public identifiers of the Firebase project. None of these are secrets -- the database
 * security rules are what protect the data (see SECURITY.md).
 */
data class FirebaseConfig(
    /** Web API key from Firebase Console -> Project settings -> General. */
    val apiKey: String,
    /** e.g. https://your-project-default-rtdb.europe-west1.firebasedatabase.app */
    val databaseUrl: String,
    /** The same OAuth web client id the Android app signs in with. */
    val googleWebClientId: String
) {
    val isComplete: Boolean
        get() = apiKey.isNotBlank() && databaseUrl.isNotBlank() && googleWebClientId.isNotBlank()
}

/**
 * Lenient on purpose: rows are written by every client version there has ever been, so a field
 * this build does not know is ignored, and a null or unknown enum falls back to the default.
 */
val InventoriaJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
