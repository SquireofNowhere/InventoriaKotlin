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

    suspend fun generateInviteCode(): String {
        val userId = getCurrentUserId() ?: throw IllegalStateException("User not logged in")
        // Generate a 6-character alphanumeric code
        val chars = ('A'..'Z') + ('0'..'9')
        val code = (1..6).map { chars.random() }.joinToString("")

        // 1. Map code to userId in a public invites node
        firebaseDatabase.getReference("invites").child(code).setValue(userId).await()
        
        // 2. Store the code in the user's own record for reference
        firebaseDatabase.getReference("users").child(userId).child("my_invite_code").setValue(code).await()
        
        return code
    }

    suspend fun getExistingInviteCode(): String? {
        val userId = getCurrentUserId() ?: return null
        return try {
            val snapshot = firebaseDatabase.getReference("users").child(userId).child("my_invite_code").get().await()
            snapshot.getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserIdFromInviteCode(code: String): String? {
        val snapshot = firebaseDatabase.getReference("invites").child(code.uppercase()).get().await()
        return snapshot.getValue(String::class.java)
    }

    /**
     * Links the current user to another user's inventory using an invite code.
     * We write the invite code as the value to allow Firebase rules to validate the link.
     */
    suspend fun linkToUser(targetUserId: String, inviteCode: String): Result<Unit> {
        val currentUserId = getCurrentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Update the target user's sharedWith list to include the current user.
            // We store the invite code used to allow the backend to verify this request.
            firebaseDatabase.getReference("users")
                .child(targetUserId)
                .child("sharedWith")
                .child(currentUserId)
                .setValue(inviteCode.uppercase())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sharedWith. Check Firebase rules.", e)
            Result.failure(e)
        }
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
