package com.inventoria.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.remote.AuthManager
import com.inventoria.shared.remote.AuthSession
import com.inventoria.shared.remote.FirebaseAuthRest
import com.inventoria.shared.remote.FirebaseConfig
import com.inventoria.shared.remote.InventoriaRemote
import com.inventoria.shared.remote.InventoriaSnapshot
import com.inventoria.shared.remote.RealtimeDatabaseRest
import com.inventoria.shared.remote.installInventoriaJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The web app's one state holder. There is no local database: every screen renders the last
 * [snapshot] read from the cloud, reads are refreshed on an interval while signed in, and writes
 * go straight to the database and are then reflected locally.
 */
class WebAppState(private val scope: CoroutineScope) {
    val config = FirebaseConfig(
        apiKey = WebConfig.FIREBASE_WEB_API_KEY,
        databaseUrl = WebConfig.FIREBASE_DATABASE_URL,
        googleWebClientId = WebConfig.DEFAULT_WEB_CLIENT_ID
    )

    private val http = HttpClient(Js) { installInventoriaJson() }
    private val auth = AuthManager(FirebaseAuthRest(http, config), LocalStorageSessionStore)
    private val remote = InventoriaRemote(RealtimeDatabaseRest(http, config) { force -> auth.idToken(force) })

    val session: StateFlow<AuthSession?> = auth.session

    var snapshot by mutableStateOf<InventoriaSnapshot?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSigningIn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var pollJob: Job? = null

    fun signIn() {
        if (isSigningIn) return
        scope.launch {
            isSigningIn = true
            error = null
            try {
                val accessToken = inventoriaGoogleSignIn(config.googleWebClientId).await<JsString>().toString()
                auth.signInWithGoogleAccessToken(accessToken)
            } catch (e: Throwable) {
                error = e.message ?: "Sign-in failed."
            } finally {
                isSigningIn = false
            }
        }
    }

    fun signOut() {
        pollJob?.cancel()
        pollJob = null
        snapshot = null
        error = null
        auth.signOut()
    }

    /** Called whenever the signed-in account changes: load it now, then keep it fresh. */
    fun onSessionChanged(session: AuthSession?) {
        pollJob?.cancel()
        pollJob = null
        if (session == null) return
        pollJob = scope.launch {
            while (true) {
                load(session.uid)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        val uid = session.value?.uid ?: return
        scope.launch { load(uid) }
    }

    fun setTodoDone(todo: Todo, done: Boolean) {
        val uid = session.value?.uid ?: return
        val target = if (done) TodoState.COMPLETE else TodoState.INCOMPLETE
        scope.launch {
            try {
                val written = remote.setTodoState(uid, todo, target)
                snapshot = snapshot?.let { s -> s.copy(todos = s.todos.map { if (it.id == written.id) written else it }) }
            } catch (e: Throwable) {
                error = "Couldn't update \"${todo.title}\": ${e.message}"
            }
        }
    }

    fun dismissError() {
        error = null
    }

    private suspend fun load(uid: String) {
        if (isLoading) return
        isLoading = true
        try {
            snapshot = remote.loadSnapshot(uid)
            error = null
        } catch (e: Throwable) {
            error = "Couldn't reach your data: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
    }
}
