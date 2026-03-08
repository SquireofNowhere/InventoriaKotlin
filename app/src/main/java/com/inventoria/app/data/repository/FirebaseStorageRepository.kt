package com.inventoria.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val authRepository: FirebaseAuthRepository
) {
    private val TAG = "FirebaseStorage"

    suspend fun uploadItemImage(uri: Uri): Result<String> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not authenticated"))
            
        // Create reference
        val fileName = uri.lastPathSegment ?: "image_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference
            .child("users")
            .child(userId)
            .child("item_images")
            .child(fileName)

        return try {
            Log.d(TAG, "Starting upload to: ${storageRef.path}")
            
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
