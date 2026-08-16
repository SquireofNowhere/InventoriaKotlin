package com.inventoria.app.ui.screens.settings

import com.google.firebase.auth.FirebaseUser

/**
 * Who is actually signed in, as reported by Firebase itself.
 *
 * Only these two cases exist. A transient Loading and Error used to live here as well, which meant
 * an in-flight sign-in or an undismissed error *shadowed* the real account state: for as long as
 * either was showing, every `authState is Authenticated` check in the UI read false, and a signed-in
 * Google user was told they were on a local account. Those two now live in [AuthOperation],
 * alongside this rather than on top of it.
 */
sealed class AuthState {
    /** No Google account -- either the anonymous local account, or nothing signed in at all. */
    object Idle : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
}

/** An auth action the user started: shown while it runs, and until its failure is dismissed. */
sealed class AuthOperation {
    object InProgress : AuthOperation()
    data class Failed(val message: String) : AuthOperation()
}
