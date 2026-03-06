package com.inventoria.app.data.repository

import com.google.firebase.auth.AuthCredential
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
    /**
     * Flow that emits the current FirebaseUser whenever the auth state changes.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Ensures the user is signed in anonymously if not already signed in.
     * Returns the Firebase UID.
     */
    suspend fun getOrCreateUserId(): String {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            return currentUser.uid
        }
        
        return firebaseAuth.signInAnonymously().await().user?.uid 
            ?: throw IllegalStateException("Failed to sign in anonymously")
    }

    /**
     * Signs in with Google credentials.
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebaseAuth.signInWithCredential(credential).await().user
    }

    /**
     * Returns the current authenticated user.
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Returns current user ID if available, otherwise null.
     */
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
    
    /**
     * Signs out the current user.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }
}
