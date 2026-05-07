package com.example.homehub.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Tasks
import java.io.File

class KYCUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = withContext(Dispatchers.IO) {
        Tasks.await(this@awaitTask)
    }

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()
        
        val fullName = inputData.getString(KEY_FULL_NAME) ?: ""
        val email = inputData.getString(KEY_EMAIL) ?: ""
        val idNumber = inputData.getString(KEY_ID_NUMBER) ?: ""
        val phone = inputData.getString("phone") ?: ""
        val address = inputData.getString(KEY_ADDRESS) ?: "No Location Provided"
        
        val idFrontPath = inputData.getString(KEY_ID_FRONT_PATH)
        val idBackPath = inputData.getString(KEY_ID_BACK_PATH)

        return try {
            val storage = FirebaseStorage.getInstance()
            val db = FirebaseFirestore.getInstance()

            val idFrontUrl = idFrontPath?.let { uploadImage(storage, userId, it, "id_front") }
            val idBackUrl = idBackPath?.let { uploadImage(storage, userId, it, "id_back") }

            // Fetch user role
            val userDoc = db.collection("users").document(userId).get().awaitTask()
            val userRole = userDoc.getString("role") ?: "Student"

            // Determine document type based on role
            val documentType = when (userRole.lowercase()) {
                "student", "user" -> "Registration Card"
                "caretaker" -> "National ID"
                "water_supplier", "supplier" -> "National ID"
                else -> "Identity Document"
            }

            val applicationData = hashMapOf(
                "userId" to userId,
                "fullName" to fullName,
                "email" to email,
                "idNumber" to idNumber,
                "phone" to phone,
                "location" to address,
                "idFrontUrl" to idFrontUrl,
                "idBackUrl" to idBackUrl,
                "role" to userRole,
                "documentType" to documentType,
                "status" to "PENDING",
                "submittedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Sync with user profile immediately
            db.collection("users").document(userId).update(
                mapOf(
                    "phone" to phone,
                    "fullName" to fullName,
                    "location" to address
                )
            ).awaitTask()

            // Save to verificationRequests so Admin Verifications panel detects it
            db.collection("verificationRequests").document(userId).set(applicationData).awaitTask()
            
            // Also update user document verification status
            db.collection("users").document(userId).update("verificationStatus", "PENDING").awaitTask()
            
            // Log for Admin Recent Activity
            val activity = hashMapOf(
                "title" to "$fullName Needs Approval",
                "description" to "New identity verification submitted for review",
                "activityType" to "NEW_USER_SIGNUP",
                "type" to "VERIFICATION",
                "user" to fullName,
                "userName" to fullName,
                "userId" to userId,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("activityLog").add(activity).awaitTask()
            
            // Send Notification to Admin
            NotificationManager.sendVerificationRequestNotification(userId, fullName)

            listOfNotNull(idFrontPath, idBackPath).forEach { path ->
                File(path).delete()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("KYCUploadWorker", "Error uploading KYC data", e)
            Result.retry()
        }
    }

    private suspend fun uploadImage(storage: FirebaseStorage, userId: String, path: String, type: String): String {
        val file = File(path)
        val ref = storage.reference.child("kyc_verifications/$userId/${type}_${System.currentTimeMillis()}.jpg")
        
        // Manual wrap because of persistent 'await' resolution issues in this file
        ref.putFile(Uri.fromFile(file)).awaitTask()
        
        return ref.downloadUrl.awaitTask().toString()
    }

    companion object {
        const val KEY_FULL_NAME = "full_name"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
        const val KEY_ID_NUMBER = "id_number"
        const val KEY_ADDRESS = "address"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_SELFIE_PATH = "selfie_path"
        const val KEY_ID_FRONT_PATH = "id_front_path"
        const val KEY_ID_BACK_PATH = "id_back_path"
    }
}
