package com.example.homehub.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.auth.SessionManager
import com.example.homehub.utils.UserVerificationBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object VerificationManager {
    private const val TAG = "VerificationManager"
    private const val VERIFICATION_DELAY_MS = 30000L // 30 seconds

    private val handler = Handler(Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    private var isBottomSheetVisible = false

    fun checkVerification(activity: AppCompatActivity) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val status = (doc.getString("verificationStatus") ?: "NONE").uppercase()
                when (status) {
                    "APPROVED" -> {
                        Log.d(TAG, "User verified, no prompt needed")
                        cancelTimer()
                    }
                    "REJECTED" -> {
                        cancelTimer()
                        handleRejectedUser(activity)
                    }
                    "PENDING", "NONE" -> {
                        Log.d(TAG, "User not verified, starting timer")
                        startVerificationTimer(activity)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check verification: ${e.message}")
            }
    }

    private fun startVerificationTimer(activity: AppCompatActivity) {
        cancelTimer() // Clear any existing timer
        
        Log.d(TAG, "Starting 1-minute verification timer...")
        verificationRunnable = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed) {
                showVerificationPrompt(activity)
            }
        }
        handler.postDelayed(verificationRunnable!!, VERIFICATION_DELAY_MS)
    }

    private fun cancelTimer() {
        verificationRunnable?.let {
            handler.removeCallbacks(it)
            verificationRunnable = null
        }
    }

    private fun showVerificationPrompt(activity: AppCompatActivity) {
        if (isBottomSheetVisible) return
        
        Log.d(TAG, "Showing mandatory verification bottom sheet")
        val bottomSheet = UserVerificationBottomSheet()
        bottomSheet.isCancelable = false
        bottomSheet.show(activity.supportFragmentManager, "VerificationBottomSheet")
        isBottomSheetVisible = true
    }

    private fun handleRejectedUser(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        
        FirebaseAuth.getInstance().signOut()
        
        MaterialAlertDialogBuilder(activity)
            .setTitle("Account Disabled")
            .setMessage("Your verification application was rejected by the administration. Your account has been disabled for security reasons.")
            .setPositiveButton("Logout") { _, _ ->
                activity.finishAffinity()
                // Redirect will be handled by Splash normally, but finishAffinity closes the app
            }
            .setCancelable(false)
            .show()
    }
    
    fun onVerificationSubmitted() {
        isBottomSheetVisible = false
        cancelTimer()
    }
}
