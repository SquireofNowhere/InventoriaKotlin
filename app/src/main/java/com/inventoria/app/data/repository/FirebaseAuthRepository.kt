package com.inventoria.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    suspend fun getOrCreateUserId(): String {
        firebaseAuth.currentUser?.let { return it.uid }
        
        val result = firebaseAuth.signInAnonymously().await()
        return result.user?.uid ?: throw IllegalStateException("Failed to sign in anonymously")
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        return result.user
    }

    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid

    fun signOut() {
        firebaseAuth.signOut()
    }
    
    // Helper for Splash screen Google Sign In
    fun getGoogleSignInIntent(): android.content.Intent {
        // This usually requires a GoogleSignInClient configured with options
        // For the sake of restoration, we assume the caller handles the client creation
        // but we could provide a helper if we port the full AuthModule
        throw UnsupportedOperationException("Intent should be requested via GoogleSignIn.getClient(...)")
    }
}
