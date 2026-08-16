package com.inventoria.app.data.repository

import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    companion object {
        /** Codes are typed by hand off a screen, so they stay short. */
        const val INVITE_CODE_LENGTH = 6
        private const val INVITE_CODE_CLAIM_ATTEMPTS = 5

        /** The only characters a code can contain -- and the only ones safe as a Realtime Database key. */
        private val INVITE_CODE_CHARS = (('A'..'Z') + ('0'..'9')).toSet()

        /**
         * Uppercases and strips anything that isn't part of a code. Codes are pasted as often as
         * typed, so stray whitespace and punctuation arrive routinely -- and `.`, `#`, `$`, `[`,
         * `]` and `/` are illegal in a Realtime Database key, which made `child(code)` throw
         * outright rather than simply not matching.
         */
        fun normalizeInviteCode(raw: String): String =
            raw.uppercase().filter { it in INVITE_CODE_CHARS }
    }

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    /**
     * Which database this device is using -- the external one if an invite code is active, ours
     * otherwise. The right question for sync, and the wrong one for anything that has to be
     * attributable to *us*; see [getOrCreateOwnUserId].
     */
    suspend fun getOrCreateUserId(): String {
        val manualId = settingsRepository.manualSyncId.first()
        if (manualId != null) return manualId
        return getOrCreateOwnUserId()
    }

    /**
     * This device's own account id, ignoring any external sync connection.
     *
     * Storage needs this rather than [getOrCreateUserId]: the folder path is the only thing Storage
     * rules can key off, since they can query Firestore but not the Realtime Database and so cannot
     * see `sharedWith` at all.
     */
    suspend fun getOrCreateOwnUserId(): String {
        firebaseAuth.currentUser?.let { return it.uid }

        val result = firebaseAuth.signInAnonymously().await()
        val uid = result.user?.uid ?: throw IllegalStateException("Failed to sign in anonymously")

        // A freshly-created anonymous session's ID token can briefly lag behind Firebase's
        // backend recognizing it as valid (see generateInviteCode's fix for the diagnosed
        // case). This is the single point every other operation gets its UID from after a
        // brand-new sign-in, so force the refresh here once instead of at every call site.
        try {
            result.user?.getIdToken(true)?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh after fresh anonymous sign-in failed, proceeding anyway", e)
        }

        return uid
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

        // A freshly-created anonymous session's ID token can briefly lag behind Firebase's
        // backend token validation (observed directly: writes right after a fresh sign-in got
        // denied with a token that "looked" valid locally, and a forced refresh fixed it).
        // Force a refresh before this call so the token is definitely current.
        try {
            firebaseAuth.currentUser?.getIdToken(true)?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh before generateInviteCode failed, proceeding anyway", e)
        }

        val previousCode = getExistingInviteCode()

        val code = claimUnusedInviteCode(userId)

        // Store the code in the user's own record for reference
        firebaseDatabase.getReference("users").child(userId).child("my_invite_code").setValue(code).await()

        // The old code stayed live in `invites` forever otherwise, so a user who regenerated ended
        // up with two working codes and no way to retire the first. Best-effort: failing to clean
        // up an old code must not fail the new one, which is already claimed and recorded.
        if (previousCode != null && previousCode != code) {
            try {
                firebaseDatabase.getReference("invites").child(previousCode).removeValue().await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not retire previous invite code $previousCode", e)
            }
        }

        return code
    }

    /**
     * Finds a code nobody else holds and claims it.
     *
     * This used to be a bare `setValue` on a random code. A collision is unlikely at
     * [INVITE_CODE_LENGTH] characters, but it was silent and the wrong way round: the new owner
     * simply overwrote the existing mapping, so everyone still holding the older user's code would
     * have been handed a stranger's database, while that user's own screen went on showing a code
     * that no longer pointed at them.
     */
    private suspend fun claimUnusedInviteCode(userId: String): String {
        repeat(INVITE_CODE_CLAIM_ATTEMPTS) {
            val code = (1..INVITE_CODE_LENGTH).map { INVITE_CODE_CHARS.random() }.joinToString("")
            if (tryClaimInviteCode(code, userId)) return code
            Log.w(TAG, "Invite code $code was already taken, retrying")
        }
        throw IllegalStateException("Couldn't find a free invite code. Please try again.")
    }

    /** Returns true if [code] was empty (or already ours) and now maps to [userId]. */
    private suspend fun tryClaimInviteCode(code: String, userId: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            firebaseDatabase.getReference("invites").child(code).runTransaction(
                object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val existing = currentData.getValue(String::class.java)
                        // Re-claiming our own code is fine; anyone else's is the collision.
                        if (existing != null && existing != userId) return Transaction.abort()
                        currentData.value = userId
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        snapshot: DataSnapshot?
                    ) {
                        if (!continuation.isActive) return
                        if (error != null) {
                            continuation.resumeWithException(error.toException())
                        } else {
                            continuation.resume(committed)
                        }
                    }
                }
            )
        }

    /**
     * Retires the current invite code so it stops working for anyone still holding it.
     *
     * [revokeSharedAccess] alone does not do this: it removes the joiner from `sharedWith`, but the
     * code that let them add themselves is still live, so the same person can paste it again and
     * re-link. Cutting someone off for good means rotating the code as well.
     */
    suspend fun revokeInviteCode(code: String): Result<Unit> {
        val userId = getCurrentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // The `invites` entry is what actually grants access; my_invite_code is only the
            // owner's own note of it. Once the first is gone the code is dead, so failing to tidy
            // up the second must not report back that the code is still live.
            firebaseDatabase.getReference("invites").child(code).removeValue().await()
            try {
                firebaseDatabase.getReference("users").child(userId).child("my_invite_code").removeValue().await()
            } catch (e: Exception) {
                Log.w(TAG, "Code $code retired, but clearing my_invite_code failed", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke invite code", e)
            Result.failure(e)
        }
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

    /** [code] must already be through [normalizeInviteCode]; anything else is not a valid database key. */
    suspend fun getUserIdFromInviteCode(code: String): String? {
        val snapshot = firebaseDatabase.getReference("invites").child(code).get().await()
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
                .setValue(normalizeInviteCode(inviteCode))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sharedWith. Check Firebase rules.", e)
            Result.failure(e)
        }
    }

    /**
     * Live map of {joinerUid -> inviteCode} for accounts currently synced to [ownerUid]'s
     * database via [linkToUser]. Never propagates onCancelled as an exception -- a permission
     * error here must not crash the whole app (see FirebaseSyncRepository's setupNodeSync for
     * why that matters), so it just logs and reports an empty map instead.
     */
    fun getSharedWithFlow(ownerUid: String): Flow<Map<String, String>> = callbackFlow {
        val ref = firebaseDatabase.getReference("users").child(ownerUid).child("sharedWith")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shared = snapshot.children.associate { child ->
                    child.key.orEmpty() to (child.getValue(String::class.java) ?: "")
                }
                trySend(shared)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read sharedWith for $ownerUid: ${error.message}")
                trySend(emptyMap())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun revokeSharedAccess(joinerUid: String): Result<Unit> {
        val ownerUid = getCurrentUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            firebaseDatabase.getReference("users").child(ownerUid).child("sharedWith").child(joinerUid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke access for $joinerUid", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(): Result<Unit> {
        val user = firebaseAuth.currentUser ?: return Result.failure(Exception("No user logged in"))
        val uid = user.uid

        try {
            // 1a. Retire the invite code first. It lives at the top level, so deleting users/$uid
            // does not touch it -- the mapping outlived the account it pointed at, and the rules
            // let anyone holding that code recreate users/$uid/sharedWith and so resurrect the
            // deleted node with themselves attached to it.
            val inviteCode = getExistingInviteCode()
            if (inviteCode != null) {
                try {
                    firebaseDatabase.getReference("invites").child(inviteCode).removeValue().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not retire invite code $inviteCode during account deletion", e)
                }
            }

            // 1b. Delete user data from Realtime Database
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
