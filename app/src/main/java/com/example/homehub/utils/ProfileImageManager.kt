package com.example.homehub.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.homehub.admin.AdminSessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Centralized manager for profile image updates across the HomeHub ecosystem.
 * Handles selection, local caching, compression, upload to Storage, and database synchronization.
 * 
 * FLOW:
 * 1. User picks image → Instantly saved locally (UI updates)
 * 2. Firebase upload starts in background (silent, no blocking)
 * 3. On upload success → Firebase URL synced to Firestore
 */
class ProfileImageManager private constructor(
    private val activity: AppCompatActivity?,
    private val fragment: Fragment?,
    private val roleScope: String,
    private val onComplete: (String) -> Unit
) {
    private val context: Context = activity ?: fragment!!.requireContext()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private val uploadScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        fun create(activity: AppCompatActivity, roleKey: String = "default", onComplete: (String) -> Unit): ProfileImageManager {
            val manager = ProfileImageManager(activity, null, roleKey, onComplete)
            manager.initLauncher()
            return manager
        }

        fun create(fragment: Fragment, roleKey: String = "default", onComplete: (String) -> Unit): ProfileImageManager {
            val manager = ProfileImageManager(null, fragment, roleKey, onComplete)
            manager.initLauncher()
            return manager
        }

        private fun getLocalImagePath(context: Context, contextId: String): File {
            val dir = File(context.filesDir, "profile_images")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "$contextId.jpg")
        }
    }

    private fun initLauncher() {
        val contract = ActivityResultContracts.GetContent()
        if (activity != null) {
            pickImageLauncher = activity.registerForActivityResult(contract) { uri ->
                uri?.let { handleImageSelection(it) }
            }
        } else if (fragment != null) {
            pickImageLauncher = fragment.registerForActivityResult(contract) { uri ->
                uri?.let { handleImageSelection(it) }
            }
        }
    }

    fun launchPicker() {
        // Direct to gallery - no camera mode
        pickImageLauncher.launch("image/*")
    }

    private fun handleImageSelection(filePath: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val contextId = if (roleScope == "admin") "${userId}_admin" else userId

        // STEP 1: Process and cache locally (instant)
        try {
            val inputStream = context.contentResolver.openInputStream(filePath)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            if (originalBitmap == null) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                return
            }

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val data = outputStream.toByteArray()

            // Save locally immediately
            val localFile = getLocalImagePath(context, contextId)
            localFile.writeBytes(data)

            // Load locally and callback immediately - NO DIALOGS
            val localUri = Uri.fromFile(localFile)
            onComplete(localUri.toString())

            (activity?.runOnUiThread { 
                Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
            } ?: fragment?.activity?.runOnUiThread {
                Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
            })

            // STEP 2: Upload to Firebase in background (silent, non-blocking)
            uploadScope.launch {
                uploadToFirebaseInBackground(userId, contextId, data, localFile)
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun uploadToFirebaseInBackground(userId: String, contextId: String, imageData: ByteArray, localFile: File) {
        return withContext(Dispatchers.IO) {
            try {
                val ref = storage.child("profile_pictures/$contextId/${System.currentTimeMillis()}.jpg")
                
                ref.putBytes(imageData).continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    ref.downloadUrl
                }.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUri = task.result.toString()
                        
                        val lastUpdate = System.currentTimeMillis()
                        val updateMap = if (roleScope == "admin") {
                            mapOf("adminProfileImageUrl" to downloadUri, "lastAdminProfileUpdate" to lastUpdate)
                        } else {
                            mapOf("profileImageUrl" to downloadUri, "profilePictureUrl" to downloadUri, "lastProfileUpdate" to lastUpdate)
                        }
                        
                        // Update Firestore with Firebase Storage URL
                        db.collection("users").document(userId)
                            .update(updateMap)
                            .addOnSuccessListener {
                                // Sync session cache with permanent URL AND timestamp for instant invalidation
                                if (roleScope == "admin") {
                                    AdminSessionManager(context).saveLastAdminImageUpdate(lastUpdate)
                                } else {
                                    com.example.homehub.auth.SessionManager(context).updateCachedUserImageUrl(downloadUri, lastUpdate)
                                }
                                android.util.Log.d("ProfileImageManager", "Firebase sync complete: $downloadUri at $lastUpdate")
                            }
                            .addOnFailureListener { e ->
                                // Firestore update failed, but local image still works
                                android.util.Log.w("ProfileImageManager", "Firestore sync failed: ${e.message}")
                            }
                    } else {
                        // Firebase upload failed, but local image still works
                        android.util.Log.w("ProfileImageManager", "Firebase upload failed: ${task.exception?.message}")
                        // Keep local image as fallback
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileImageManager", "Background upload error: ${e.message}")
                // Silently fail - local image already cached and shown
            }
        }
    }
}
