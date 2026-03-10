package com.inventoria.app.data.repository

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val googleSignInClient: GoogleSignInClient,
    private val settingsRepository: SettingsRepository
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
        val manualId = settingsRepository.manualSyncId.first()
        if (manualId != null) return manualId

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
        googleSignInClient.signOut()
    }
    
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
}
