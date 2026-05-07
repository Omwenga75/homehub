package com.example.homehub.auth

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.homehub.R
import com.example.homehub.caretaker.CaretakerApplication
import com.example.homehub.caretaker.CaretakerDashboardActivity

class PrivateAccessActivity : AppCompatActivity() {

    private lateinit var fabApplyNow: ExtendedFloatingActionButton
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentApplication: CaretakerApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_access)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        checkUserAndShowStatusOnEnter()
    }

    private fun initializeViews() {
        fabApplyNow = findViewById(R.id.fabApplyNow)

        // Setup floating button click listener - NOW HANDLES NAVIGATION
        fabApplyNow.setOnClickListener {
            handleFloatingButtonClick()
        }
    }

    private fun checkUserAndShowStatusOnEnter() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User not logged in
            showLoginAlert("view application status")
            return
        }

        // Check if user has already applied
        db.collection("hostApplications")
            .whereEqualTo("userId", currentUser.uid)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    currentApplication = CaretakerApplication.fromDocument(
                        document.id,
                        document.data ?: emptyMap()
                    )

                    // Update FAB text based on status
                    updateFloatingButtonText(currentApplication!!.status.lowercase())

                    // Show application status dialog AUTOMATICALLY on entry
                    showApplicationStatusDialog(currentApplication!!)
                } else {
                    // No application found
                    fabApplyNow.text = "Apply Now"
                    showNoApplicationDialog()
                }
            }
            .addOnFailureListener {
                // On error, show generic message
                fabApplyNow.text = "Apply Now"
                Toast.makeText(this, "Failed to load application status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showApplicationStatusDialog(application: CaretakerApplication) {
        when (application.status.lowercase()) {
            "approved" -> {
                val title = "🎉 Host Application Approved!"
                val coloredTitle = SpannableString(title)
                coloredTitle.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.green)),
                    0,
                    title.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(coloredTitle)
                    .setMessage("Congratulations! Your host application has been approved.\n\nApplication Details:\n• Name: " +
                            "${application.fullName}\n• Property: ${application.propertyType} in ${application.propertyLocation}\n• Price: " +
                            "${application.getFormattedPrice()}\n• Application Date: ${application.getFormattedApplicationDate()}\n\nClick the floating button to go to your host dashboard.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }

            "rejected" -> {
                val title = "❌ Application Rejected"
                val coloredTitle = SpannableString(title)
                coloredTitle.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)),
                    0,
                    title.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(coloredTitle)
                    .setMessage("Your host application was reviewed and rejected.\n\n${if (application.rejectionReason.isNotEmpty()) "Reason: ${application.rejectionReason}\n\n" else ""}Application Details:\n• Name: ${application.fullName}\n• Property: ${application.propertyType} in ${application.propertyLocation}\n• Application Date: ${application.getFormattedApplicationDate()}\n\nClick the floating button to apply again.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }

            "pending" -> {
                val title = "⏳ Application Pending Review"
                val coloredTitle = SpannableString(title)
                coloredTitle.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.orange)),
                    0,
                    title.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(coloredTitle)
                    .setMessage("Your host application is currently under review.\n\nExpected review time: 24-48 hours.\n\nApplication Details:\n• Name: ${application.fullName}\n• Property: ${application.propertyType} in ${application.propertyLocation}\n• Price: ${application.getFormattedPrice()}\n• Application Date: ${application.getFormattedApplicationDate()}\n\nYou'll receive a notification once a decision is made.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }

            else -> {
                val title = "Application Status"
                val coloredTitle = SpannableString(title)
                coloredTitle.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)),
                    0,
                    title.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                MaterialAlertDialogBuilder(this)
                    .setTitle(coloredTitle)
                    .setMessage("Status: ${application.getStatusDisplayText()}\n\nApplication Details:\n• Name: ${application.fullName}\n• Property: ${application.propertyType} in ${application.propertyLocation}\n• Price: ${application.getFormattedPrice()}\n• Application Date: ${application.getFormattedApplicationDate()}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun showNoApplicationDialog() {
        val title = "No Application Found"
        val coloredTitle = SpannableString(title)
        coloredTitle.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)),
            0,
            title.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle)
            .setMessage("You haven't applied to become a host yet.\n\nClick the floating button to start your application.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleFloatingButtonClick() {
        when (currentApplication?.status?.lowercase()) {
            "approved" -> {
                // Navigate to host dashboard
                navigateToHostDashboard()
            }
            "rejected" -> {
                // Navigate to application (re-apply)
                navigateToCaretakerApplication()
            }
            "pending" -> {
                // Show status again if user wants to see it
                currentApplication?.let {
                    showApplicationStatusDialog(it)
                }
            }
            else -> {
                // No application or other status - go to application
                navigateToCaretakerApplication()
            }
        }
    }

    private fun showLoginAlert(action: String) {
        val title = "Login Required"
        val coloredTitle = SpannableString(title)
        coloredTitle.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)),
            0,
            title.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(coloredTitle)
            .setMessage("Please login to $action.")
            .setPositiveButton("Login") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, UserLoginActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToCaretakerApplication() {
        // Application flow now integrated into dashboard via verification bottom sheet
        val intent = Intent(this, CaretakerDashboardActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToHostDashboard() {
        val intent = Intent(this, com.example.homehub.caretaker.CaretakerDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateFloatingButtonText(status: String) {
        when (status) {
            "approved" -> fabApplyNow.text = "Go to Dashboard"
            "rejected" -> fabApplyNow.text = "Apply Again"
            "pending" -> fabApplyNow.text = "Check Status"
            else -> fabApplyNow.text = "Apply Now"
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when activity resumes
        updateFloatingButtonText(currentApplication?.status?.lowercase() ?: "")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    companion object {
        private const val TAG = "PrivateAccessActivity"
    }
}
