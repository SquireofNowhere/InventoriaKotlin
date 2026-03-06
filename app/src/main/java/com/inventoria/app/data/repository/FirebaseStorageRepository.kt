package com.inventoria.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val authRepository: FirebaseAuthRepository
) {
    private val TAG = "FirebaseStorage"

    suspend fun uploadItemImage(localPath: String): Result<String> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not authenticated"))
            
        val file = File(localPath)
        if (!file.exists()) {
            return Result.failure(Exception("Local file not found at $localPath"))
        }

        // Ensure we are using a proper file Uri
        val fileUri = Uri.fromFile(file)

        // Create reference
        val storageRef = storage.reference
            .child("users")
            .child(userId)
            .child("item_images")
            .child(file.name)

        return try {
            Log.d(TAG, "Starting upload to: ${storageRef.path}")
            
            // Upload the file
            storageRef.putFile(fileUri).await()
            
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
