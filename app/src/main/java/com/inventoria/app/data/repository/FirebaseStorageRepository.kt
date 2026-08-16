package com.inventoria.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val authRepository: FirebaseAuthRepository
) {
    private val TAG = "FirebaseStorage"

    /**
     * Uploads to *this device's own* folder, never the folder of an account it is merely synced to.
     *
     * It used to use getOrCreateUserId(), which returns the manualSyncId when one is set, so a
     * joiner wrote straight into the owner's folder. That layout cannot be secured: Storage rules
     * can query Firestore but not the Realtime Database, so they have no way to see `sharedWith`
     * and cannot tell a genuine joiner from anyone else with an account -- which meant read and
     * write had to be open to every authenticated user, and this app hands out anonymous accounts
     * to anyone who installs it. Uploading under our own uid lets the rules be a flat
     * `auth.uid == userId`.
     *
     * Sharing is unaffected: what gets synced is the tokenized download URL below, and fetching
     * that URL does not consult Storage rules at all.
     */
    suspend fun uploadItemImage(uri: Uri): Result<String> {
        val uploaderId = try {
            authRepository.getOrCreateOwnUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Generate a unique filename
        val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

        val storageRef = storage.reference
            .child("users")
            .child(uploaderId)
            .child("item_images")
            .child(fileName)

        return try {
            Log.d(TAG, "Starting upload to own folder ($uploaderId): ${storageRef.path}")

            // Upload the file
            storageRef.putFile(uri).await()
            
            // Get the download URL
            val downloadUrl = storageRef.downloadUrl.await()
            Log.d(TAG, "Upload successful. URL: $downloadUrl")
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Storage Upload Error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
