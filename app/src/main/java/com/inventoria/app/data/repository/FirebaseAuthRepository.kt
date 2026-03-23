package com.inventoria.app.data.repository

import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
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
    private val settingsRepository: SettingsRepository,
    private val firebaseDatabase: FirebaseDatabase,
    private val firebaseStorage: FirebaseStorage
) {
    private val TAG = "FirebaseAuthRepository"

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

    suspend fun deleteUserAccount(): Result<Unit> {
        val user = firebaseAuth.currentUser ?: return Result.failure(Exception("No user logged in"))
        val uid = user.uid

        try {
            // 1. Delete user data from Realtime Database
            Log.d(TAG, "Deleting Realtime Database node for user: $uid")
            firebaseDatabase.getReference("users").child(uid).removeValue().await()

            // 2. Delete user data from Firebase Storage
            Log.d(TAG, "Deleting Storage files for user: $uid")
            val storageRef = firebaseStorage.reference.child("users").child(uid).child("item_images")
            try {
                val listResult = storageRef.listAll().await()
                listResult.items.forEach { itemRef ->
                    itemRef.delete().await()
                }
                Log.d(TAG, "Storage files deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting storage files or directory empty: ${e.message}")
            }

            // 3. Delete the Auth record
            Log.d(TAG, "Deleting Firebase Auth record")
            user.delete().await()

            // 4. Log out locally
            signOut()
            
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user account: ${e.message}", e)
            return Result.failure(e)
        }
    }
}
