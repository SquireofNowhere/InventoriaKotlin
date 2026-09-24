package com.inventoria.shared.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a signed-in session survives a reload: localStorage on the web. */
interface SessionStore {
    fun load(): String?
    fun save(value: String?)
}

/**
 * Owns the one current [AuthSession]: restores it from [store], keeps its ID token fresh, and
 * forgets it on sign-out or when the refresh token is rejected (account deleted or disabled).
 */
class AuthManager(
    private val auth: FirebaseAuthRest,
    private val store: SessionStore
) {
    private val _session = MutableStateFlow(restore())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    /** One refresh at a time, so parallel node reads share a single round-trip. */
    private val refreshLock = Mutex()

    suspend fun signInWithGoogleAccessToken(accessToken: String) {
        publish(auth.signInWithGoogleAccessToken(accessToken))
    }

    fun signOut() = publish(null)

    /** A usable ID token, refreshed first if it is about to expire or [forceRefresh] is set. */
    suspend fun idToken(forceRefresh: Boolean = false): String = refreshLock.withLock {
        val current = _session.value ?: throw FirebaseAuthException("Not signed in")
        if (!forceRefresh && !current.needsRefresh()) return current.idToken
        val refreshed = try {
            auth.refresh(current)
        } catch (e: FirebaseAuthException) {
            publish(null)
            throw e
        }
        publish(refreshed)
        refreshed.idToken
    }

    private fun publish(session: AuthSession?) {
        _session.value = session
        store.save(session?.let { InventoriaJson.encodeToString(AuthSession.serializer(), it) })
    }

    private fun restore(): AuthSession? = store.load()?.let {
        runCatching { InventoriaJson.decodeFromString(AuthSession.serializer(), it) }.getOrNull()
    }
}
