package com.inventoria.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    /**
     * Ensures the user is signed in anonymously.
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
     * Returns current user ID if available, otherwise null.
     */
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
