package com.example.homehub.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.homehub.R
import com.example.homehub.databinding.DialogCreateUserPremiumBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.util.UUID

class CreateUserBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogCreateUserPremiumBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var secondaryAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSecondaryAuth()
    }

    private fun setupSecondaryAuth() {
        val options = FirebaseOptions.Builder()
            .setApiKey("AIzaSyBzjSFVcyq614iME_LyvVrMPY32X9bu8TI")
            .setApplicationId("1:411917316187:android:346d2fcd75b72168a38f7e")
            .setProjectId("homehub-588b9")
            .build()

        val secondaryApp = try {
            FirebaseApp.getInstance("AdminUserCreation")
        } catch (e: Exception) {
            FirebaseApp.initializeApp(requireContext(), options, "AdminUserCreation")
        }
        secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
    }

    var onUserCreated: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateUserPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRoleSelection()
        setupListeners()
        
        // Default selection
        binding.chipStudent.isChecked = true
        updateUiForRole("student")
    }

    private fun setupRoleSelection() {
        binding.roleChipGroup.setOnCheckedChangeListener { _, checkedId ->
            val role = when (checkedId) {
                R.id.chipStudent -> "student"
                R.id.chipAdmin -> "admin"
                R.id.chipCaretaker -> "caretaker"
                R.id.chipSupplier -> "supplier"
                else -> "student"
            }
            updateUiForRole(role)
        }
    }

    private fun updateUiForRole(role: String) {
        binding.field1InputLayout.visibility = View.VISIBLE
        binding.field2InputLayout.visibility = View.VISIBLE
        
        when (role) {
            "student" -> {
                binding.nameInputLayout.hint = "Student Name (e.g. John Doe)"
                binding.field1InputLayout.hint = "Institution (University)"
                binding.field1InputLayout.setStartIconDrawable(R.drawable.ic_location)
                binding.field2InputLayout.hint = "Admission / ID"
                binding.field2InputLayout.setStartIconDrawable(R.drawable.ic_calendar)
            }
            "caretaker" -> {
                binding.nameInputLayout.hint = "Full Name"
                binding.field1InputLayout.hint = "Location / Region"
                binding.field1InputLayout.setStartIconDrawable(R.drawable.ic_location)
                binding.field2InputLayout.hint = "National ID Number"
                binding.field2InputLayout.setStartIconDrawable(R.drawable.baseline_account_circle_24)
            }
            "supplier" -> {
                binding.nameInputLayout.hint = "Business / Supplier Name"
                binding.field1InputLayout.hint = "License Number"
                binding.field1InputLayout.setStartIconDrawable(R.drawable.baseline_account_circle_24)
                binding.field2InputLayout.hint = "Service Area"
                binding.field2InputLayout.setStartIconDrawable(R.drawable.ic_location)
            }
            "admin" -> {
                binding.nameInputLayout.hint = "Full Name"
                binding.field1InputLayout.visibility = View.GONE
                binding.field2InputLayout.visibility = View.GONE
            }
            else -> {
                binding.nameInputLayout.hint = "Full Name"
                binding.field1InputLayout.visibility = View.GONE
                binding.field2InputLayout.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.btnCreate.setOnClickListener {
            validateAndCreate()
        }
    }

    private fun validateAndCreate() {
        val name = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val field1 = binding.etField1.text.toString().trim()
        val field2 = binding.etField2.text.toString().trim()
        
        val role = getSelectedRole()
        
        // Reset errors
        binding.nameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.phoneInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.field1InputLayout.error = null
        binding.field2InputLayout.error = null

        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Name is required"
            return
        }

        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            return
        }
        
        // Student Email Validation (.ac.ke)
        if (role == "student" && !email.lowercase().endsWith(".ac.ke")) {
            binding.emailInputLayout.error = "Student email must end with .ac.ke"
            return
        }

        if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters"
            return
        }

        // Phone Validation (+254 7... or +254 1...)
        val phoneRegex = Regex("""^\+254 [17][0-9]{8}$""")
        if (!phone.matches(phoneRegex)) {
            binding.phoneInputLayout.error = "Format: +254 7XXXXXXXX or +254 1XXXXXXXX"
            return
        }

        if (role != "admin" && (field1.isEmpty() || field2.isEmpty())) {
            Toast.makeText(requireContext(), "Role-specific fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        // Check uniqueness in Firestore FIRST, then create in Auth
        db.collection("users").whereEqualTo("email", email.lowercase()).get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    Toast.makeText(requireContext(), "Email already registered in database", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                } else {
                    createInAuth(name, email, password, role, phone, field1, field2)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Uniqueness check failed", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
    }

    private fun createInAuth(name: String, email: String, pass: String, role: String, phone: String, field1: String, field2: String) {
        secondaryAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: UUID.randomUUID().toString()
                createUserInFirestore(userId, name, email, pass, role, phone, field1, field2)
                
                // Sign out of the secondary app immediately so it doesn't persist a session
                secondaryAuth.signOut()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Auth Creation Failed: ${e.message}", Toast.LENGTH_LONG).show()
                setLoading(false)
            }
    }
    private fun getSelectedRole(): String {
        return when (binding.roleChipGroup.checkedChipId) {
            R.id.chipStudent -> "student"
            R.id.chipAdmin -> "admin"
            R.id.chipCaretaker -> "caretaker"
            R.id.chipSupplier -> "supplier"
            else -> "student"
        }
    }



    private fun createUserInFirestore(userId: String, name: String, email: String, pass: String, role: String, phone: String, field1: String, field2: String) {
        val userData = hashMapOf<String, Any>(
            "userId" to userId,
            "fullName" to name,
            "username" to name.replace(" ", ""),
            "email" to email.lowercase(),
            "phone" to phone,
            "userType" to role,
            "role" to role.uppercase(),
            "status" to "Active",
            "createdAt" to Timestamp.now(),
            "profileSetupCompleted" to (role != "student"), // Students need onboarding
            "basicVerificationCompleted" to true,
            "isVerified" to true, // Admin-created accounts are pre-verified
            "verificationStatus" to "VERIFIED"
        )
        
        when (role) {
            "student" -> {
                userData["university"] = field1
                userData["studentId"] = field2
                userData["course"] = "Not specified"
            }
            "caretaker" -> {
                userData["location"] = field1
                userData["residenceAddress"] = field1
                userData["idNumber"] = field2
            }
            "supplier" -> {
                userData["businessName"] = name
                userData["licenseNumber"] = field1
                userData["serviceArea"] = field2
            }
        }

        // Create document using UID as the key (Standard)
        db.collection("users").document(userId).set(userData)
            .addOnSuccessListener {
                if (role == "caretaker") {
                    createCaretakerEntry(userId, name, email, phone, field1, field2)
                } else {
                    handleSuccess("Account provisioned successfully")
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to create user", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
    }

    private fun createCaretakerEntry(uid: String, name: String, email: String, phone: String, location: String, idNumber: String) {
        val caretakerData = hashMapOf(
            "userId" to uid,
            "fullName" to name,
            "email" to email.lowercase(),
            "phone" to phone,
            "location" to location,
            "idNumber" to idNumber,
            "isVerified" to true,
            "status" to "active",
            "joinDate" to Timestamp.now()
        )
        db.collection("verifiedCaretakers").document(uid).set(caretakerData)
            .addOnSuccessListener {
                handleSuccess("Caretaker account provisioned")
            }
    }

    private fun handleSuccess(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        onUserCreated?.invoke()
        dismiss()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnCreate.isEnabled = !isLoading
        binding.btnCreate.text = if (isLoading) "Provisioning..." else "Execute Provisioning"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateUserBottomSheet"
    }
}
