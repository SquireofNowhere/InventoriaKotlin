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

    suspend fun uploadItemImage(uri: Uri): Result<String> {
        // Use the active database ID (could be the owner's ID if synced)
        // This ensures images are stored with the inventory they belong to
        val storageOwnerId = try {
            authRepository.getOrCreateUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
            
        // Generate a unique filename
        val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

        val storageRef = storage.reference
            .child("users")
            .child(storageOwnerId)
            .child("item_images")
            .child(fileName)

        return try {
            Log.d(TAG, "Starting upload to owner folder ($storageOwnerId): ${storageRef.path}")
            
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
