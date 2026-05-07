package com.example.homehub.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityAccountVerificationBinding
import com.example.homehub.utils.NotificationManager
import com.example.homehub.utils.ProfilePictureUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.SetOptions
import com.example.homehub.R

class AccountVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var sessionManager: SessionManager

    private var selectedDocUri: Uri? = null
    private var userRole: String = "student"

    // Image Picker removed as per new simplified workflow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        sessionManager = SessionManager(this)

        userRole = sessionManager.getUserRole() ?: "student"

        setupUI()
        setupListeners()
        setupSpinners()
        restoreProgress()
    }

    private fun setupUI() {
        // Single page UI setup
        binding.tvSubtitle.text = "Complete your profile details"

        if (userRole == "unassigned") {
            binding.roleSelectionContainer.visibility = View.VISIBLE
            binding.businessNameLayout.visibility = View.VISIBLE
        } else {
            binding.roleSelectionContainer.visibility = View.GONE
            binding.businessNameLayout.visibility = if (userRole == "student") View.GONE else View.VISIBLE
        }
    }

    // updateStepBars removed as it is now a single step

    private fun setupSpinners() {
        val docs = arrayOf("National ID Card", "International Passport")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, docs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spDocumentType.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnNextStep.setOnClickListener {
            if (validateStep1()) {
                if (userRole == "unassigned") {
                    userRole = when (binding.roleRadioGroup.checkedRadioButtonId) {
                        R.id.radioCaretaker -> "caretaker"
                        R.id.radioWaterSupplier -> "water_supplier"
                        else -> "unassigned"
                    }
                    if (userRole == "caretaker") {
                         binding.businessNameLayout.visibility = View.VISIBLE
                         binding.etBusinessName.hint = "Apartment/Agency Name"
                    } else if (userRole == "water_supplier") {
                         binding.businessNameLayout.visibility = View.VISIBLE
                         binding.etBusinessName.hint = "Company Name (As per M-Pesa)"
                    }
                }
                binding.verificationFlipper.showNext()
                binding.tvSubtitle.text = "Step 2 of 2"
            }
        }

        binding.btnBack.setOnClickListener {
            binding.verificationFlipper.showPrevious()
            binding.tvSubtitle.text = "Step 1 of 2"
        }

        binding.btnUploadDocs.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnSubmitVerification.setOnClickListener {
            if (validateStep2()) {
                submitVerificationHybrid()
            }
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedDocUri = uri
            binding.ivDocPreview.visibility = View.VISIBLE
            binding.ivDocPreview.setImageURI(uri)
            binding.uploadPrompt.visibility = View.GONE
        }
    }

    private fun validateStep1(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val idNumber = binding.etIdNumber.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Name is required"
            return false
        }
        if (idNumber.length != 8) {
            binding.etIdNumber.error = "ID Number must be exactly 8 digits"
            return false
        }
        if (userRole == "unassigned" && binding.roleRadioGroup.checkedRadioButtonId == -1) {
            showToast("Please select a profile type.")
            return false
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Phone is required"
            return false
        }
        if (!phone.matches("^[17][0-9]{8}$".toRegex())) {
            binding.etPhone.error = "Phone must be 9 digits starting with 1 or 7"
            return false
        }
        if (location.isEmpty()) {
            binding.etLocation.error = "Location is required"
            return false
        }

        return true
    }

    private fun validateStep2(): Boolean {
        val businessName = binding.etBusinessName.text.toString().trim()
        if (userRole != "student" && businessName.isEmpty()) {
            binding.etBusinessName.error = "Business Name is required"
            return false
        }
        if (selectedDocUri == null) {
            showToast("Please upload a document image.")
            return false
        }
        return true
    }

    /**
     * Hybrid Approach:
     * 1. Save profile to Firestore immediately to restore account access safely.
     * 2. Trigger Firebase Storage upload in the background (fire-and-forget).
     * 3. Route user to dashboard without waiting for the slow upload.
     */
    private fun submitVerificationHybrid() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        val fullName = binding.etFullName.text.toString().trim()
        val idNumber = binding.etIdNumber.text.toString().trim()
        val phone = "+254" + binding.etPhone.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val businessName = binding.etBusinessName.text.toString().trim()

        val verificationData = hashMapOf(
            "userId" to userId,
            "fullName" to fullName,
            "idNumber" to idNumber,
            "phone" to phone,
            "location" to location,
            "businessName" to businessName,
            "role" to userRole,
            "status" to "PENDING",
            "documentType" to binding.spDocumentType.selectedItem.toString(),
            "submittedAt" to com.google.firebase.Timestamp.now()
        )

        val updateMap = hashMapOf<String, Any>(
            "verificationStatus" to "PENDING",
            "isVerified" to false,
            "basicVerificationCompleted" to true,
            "idNumber" to idNumber,
            "location" to location,
            "residenceAddress" to location,
            "fullName" to fullName,
            "username" to (if (userRole == "water_supplier") businessName else fullName),
            "phone" to phone,
            "role" to userRole,
            "businessName" to businessName
        )

        binding.btnSubmitVerification.isEnabled = false
        binding.btnSubmitVerification.text = "Securing Profile..."

        // Save immediately as PENDING
        db.collection("users").document(userId).set(updateMap, SetOptions.merge()).addOnSuccessListener {
            // Log verification request for record
            db.collection("verificationRequests").document(userId).set(verificationData)
            
            // Upload document in background
            uploadDocumentAsync(userId)
            
            // Update Session
            sessionManager.saveUserRole(userRole)
            sessionManager.setBasicVerificationCompleted(true)
            val initials = ProfilePictureUtils.getInitials(fullName)
            sessionManager.saveCachedUserProfile(fullName, initials, sessionManager.getCachedUserImageUrl())
            
            clearProgress()
            showSuccessDialog()
        }.addOnFailureListener { e ->
            binding.btnSubmitVerification.isEnabled = true
            binding.btnSubmitVerification.text = "COMPLETE PROFILE"
            showToast("Error securing profile. Please try again.")
        }
    }

    private fun uploadDocumentAsync(userId: String) {
        val uri = selectedDocUri ?: return
        val ref = storage.reference.child("verification_docs/$userId.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                db.collection("verificationRequests").document(userId)
                    .update("documentUrl", downloadUrl.toString())
            }
        }
    }

    private fun showSuccessDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Details Submitted")
            .setMessage("Your profile details have been saved. Your document is now undergoing review by our administration. Some features may be restricted until approval.")
            .setPositiveButton("Go to Dashboard") { _, _ ->
                navigateToDashboard()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToDashboard() {
        val intent = when (userRole.lowercase()) {
            "caretaker" -> Intent(this, com.example.homehub.caretaker.CaretakerDashboardActivity::class.java)
            "water_supplier", "supplier" -> Intent(this, com.example.homehub.supplier.WaterSupplierDashboardActivity::class.java)
            else -> Intent(this, com.example.homehub.student.StudentDashboardActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun saveProgress() {
        val userId = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("VerificationProgress_$userId", MODE_PRIVATE).edit()
        prefs.putString("fullName", binding.etFullName.text.toString())
        prefs.putString("idNumber", binding.etIdNumber.text.toString())
        prefs.putString("phone", binding.etPhone.text.toString())
        prefs.putString("location", binding.etLocation.text.toString())
        prefs.putString("role", userRole)
        prefs.putBoolean("hasSavedData", true)
        prefs.apply()
    }

    private fun clearProgress() {
        val userId = auth.currentUser?.uid ?: return
        getSharedPreferences("VerificationProgress_$userId", MODE_PRIVATE).edit().clear().apply()
    }

    private fun restoreProgress() {
        val userId = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("VerificationProgress_$userId", MODE_PRIVATE)
        if (prefs.getBoolean("hasSavedData", false)) {
            binding.etFullName.setText(prefs.getString("fullName", ""))
            binding.etIdNumber.setText(prefs.getString("idNumber", ""))
            binding.etPhone.setText(prefs.getString("phone", ""))
            binding.etLocation.setText(prefs.getString("location", ""))
            val savedRole = prefs.getString("role", "")
            if (savedRole == "caretaker") {
                binding.radioCaretaker.isChecked = true
                userRole = "caretaker"
            } else if (savedRole == "water_supplier") {
                binding.radioWaterSupplier.isChecked = true
                userRole = "water_supplier"
            }
        }
    }
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
