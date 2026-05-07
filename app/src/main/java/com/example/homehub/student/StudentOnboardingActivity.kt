package com.example.homehub.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.databinding.ActivityStudentOnboardingBinding
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.utils.KenyanUniversities
import com.example.homehub.utils.UniversityPickerBottomSheet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentOnboardingBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        setupUniversityPicker()

        binding.btnCompleteSetup.setOnClickListener { handleCompleteSetup() }
    }

    private fun setupUniversityPicker() {
        // Make the field non-editable but clickable
        binding.autoCompleteUniversity.inputType = android.text.InputType.TYPE_NULL
        binding.autoCompleteUniversity.isFocusable = false
        binding.autoCompleteUniversity.setOnClickListener {
            showUniversityPicker()
        }
        // Also open on parent TextInputLayout end-icon tap
        binding.autoCompleteUniversity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showUniversityPicker()
        }
    }

    private fun showUniversityPicker() {
        val picker = UniversityPickerBottomSheet { selectedUniversity ->
            binding.autoCompleteUniversity.setText(selectedUniversity)
        }
        picker.show(supportFragmentManager, UniversityPickerBottomSheet.TAG)
    }

    private fun handleCompleteSetup() {
        val name = binding.etFullName.text.toString().trim()
        val regNo = binding.etRegNo.text.toString().trim()
        val university = binding.autoCompleteUniversity.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        if (name.isEmpty()) {
            binding.etFullName.error = "Name is required"
            return
        }
        if (regNo.isEmpty()) {
            binding.etRegNo.error = "Registration number is required"
            return
        }
        if (university.isEmpty() || university == "Select University") {
            showToast("Please select your university")
            return
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Phone is required"
            return
        }
        if (!phone.matches("^[17][0-9]{8}$".toRegex())) {
            binding.etPhone.error = "Phone must be 9 digits starting with 1 or 7"
            return
        }

        checkRegistrationNumberUniqueness(name, regNo, university, phone)
    }

    private fun checkRegistrationNumberUniqueness(name: String, regNo: String, university: String, phone: String) {
        binding.progressOverlay.visibility = View.VISIBLE
        db.collection("users")
            .whereEqualTo("registrationNumber", regNo)
            .get()
            .addOnSuccessListener { documents ->
                val currentUserId = auth.currentUser?.uid
                val isUnique = documents.isEmpty || documents.all { it.id == currentUserId }
                
                if (isUnique) {
                    saveToFirestore(name, regNo, university, phone)
                } else {
                    binding.progressOverlay.visibility = View.GONE
                    binding.etRegNo.error = "This registration number is already in use."
                    showToast("Registration number already exists!")
                }
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                // If query fails, fall back to saving
                saveToFirestore(name, regNo, university, phone)
            }
    }

    private fun saveToFirestore(name: String, regNo: String, university: String, phone: String) {
        val userId = auth.currentUser?.uid ?: return
        
        binding.progressOverlay.visibility = View.VISIBLE

        val formattedPhone = "+254$phone"
        val initials = ProfilePictureUtils.getInitials(name)
        val profileColor = ProfilePictureUtils.getColorHexForName(name)

        val updates = hashMapOf(
            "fullName" to name,
            "username" to name,
            "registrationNumber" to regNo,
            "studentId" to regNo, // Backwards compatibility
            "university" to university,
            "phone" to formattedPhone,
            "role" to "student",
            "roleSelected" to true,
            "profileSetupCompleted" to true,
            "isVerified" to true,
            "verificationStatus" to "approved",
            "profileInitials" to initials,
            "profileColor" to profileColor,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(userId).update(updates as Map<String, Any>)
            .addOnSuccessListener {
                binding.progressOverlay.visibility = View.GONE
                
                // Update local session
                sessionManager.saveUserId(userId)
                sessionManager.saveUserRole("student")
                sessionManager.setRoleSelected(true)
                sessionManager.setLoggedIn(true)
                
                showToast("Welcome to HomeHub, $name!")
                startActivity(Intent(this, StudentDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                showToast("Setup failed: ${e.message}")
            }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
