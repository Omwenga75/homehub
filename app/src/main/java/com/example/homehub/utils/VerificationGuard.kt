package com.example.homehub.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object VerificationGuard {

    private const val TAG = "VerificationGuard"

    fun checkAndExecute(context: Context, onApproved: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val status = (doc.getString("verificationStatus") ?: "NONE").uppercase()
                when (status) {
                    "APPROVED" -> onApproved()
                    "REJECTED" -> showBlockedActionDialog(context, "REJECTED")
                    "PENDING" -> showBlockedActionDialog(context, "PENDING")
                    else -> showBlockedActionDialog(context, "NONE")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check verification: ${e.message}")
                showBlockedActionDialog(context, "ERROR")
            }
    }

    private fun refreshVerificationCache(userId: String, sessionManager: com.example.homehub.auth.SessionManager) {
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val status = (doc.getString("verificationStatus") ?: "NONE").uppercase()
                sessionManager.saveVerificationStatus(status)
            }
    }

    private fun showBlockedActionDialog(context: Context, status: String) {
        val (title, message) = when (status.uppercase()) {
            "PENDING" -> "Identity Review in Progress" to "Your verification is currently being reviewed. You will be able to perform this action once your identity is confirmed (usually within 24 hours)."
            "REJECTED" -> "Account Restricted" to "Your verification was rejected. Please contact support or check your notifications for details."
            else -> "Verification Required" to "To maintain platform security, you must verify your identity before performing this action. Please wait for the mandatory verification prompt or visit your profile."
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show()
    }
}
