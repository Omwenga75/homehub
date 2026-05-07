package com.example.homehub.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.homehub.utils.NotificationManager
import com.example.homehub.admin.AdminCredentials
import com.example.homehub.admin.AdminSessionManager
import com.example.homehub.utils.UsernameFormatter
import com.example.homehub.admin.AdminDashboardActivity
import com.example.homehub.caretaker.CaretakerDashboardActivity
import com.example.homehub.supplier.WaterSupplierDashboardActivity
import com.example.homehub.student.StudentDashboardActivity
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.utils.BackButtonHandler
import android.util.Log
import com.example.homehub.R

class UserLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reverted to transparent status bar for immersion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        sessionManager = SessionManager(this)

        NotificationManager.initialize(this)

        checkLoginState()

        binding.loginButton.setOnClickListener { handleEmailLogin() }
        binding.signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        binding.forgotPassword.setOnClickListener {
            val email = binding.loginEmail.text.toString().trim()
            showResetPasswordDialog(email)
        }

        // Auto-fill from SignUp if available
        intent.getStringExtra("AUTO_FILL_EMAIL")?.let { email ->
            binding.loginEmail.setText(email)
        }
        intent.getStringExtra("AUTO_FILL_PASSWORD")?.let { password ->
            binding.loginPassword.setText(password)
        }
    }

    private fun showResetPasswordDialog(prefilledEmail: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etResetEmail)
        if (prefilledEmail.isNotEmpty()) etEmail.setText(prefilledEmail)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your email address and we'll send you a link to reset your password.")
            .setView(dialogView)
            .setPositiveButton("Send Link") { dialog: android.content.DialogInterface, _: Int ->
                val email = etEmail.text.toString().trim()
                if (email.isEmpty()) {
                    showToast("Please enter your email")
                    return@setPositiveButton
                }
                
                binding.progressOverlay.visibility = View.VISIBLE
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        binding.progressOverlay.visibility = View.GONE
                        if (task.isSuccessful) {
                            showToast("Check your email for the reset link!")
                            dialog.dismiss()
                        } else {
                            showToast("Error: ${task.exception?.message}")
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleEmailLogin() {
        val email = binding.loginEmail.text.toString().trim()
        val password = binding.loginPassword.text.toString()

        when {
            email.isEmpty() -> {
                binding.loginEmail.error = "Please enter email"
                binding.emailInputLayout.error = " "
                binding.loginEmail.requestFocus()
            }
            !isValidEmail(email) -> {
                binding.loginEmail.error = "Please enter a valid email"
                binding.emailInputLayout.error = " "
                binding.loginEmail.requestFocus()
            }
            !isAllowedEmailFormat(email) -> {
                binding.loginEmail.error = "Wrong email format."
                binding.emailInputLayout.error = " "
                binding.loginEmail.requestFocus()
            }
            password.isEmpty() -> {
                binding.loginPassword.error = "Please enter password"
                binding.passwordInputLayout.error = " "
                binding.loginPassword.requestFocus()
            }
            else -> {
                // Check if this is the hardcoded admin email
                if (email.equals(com.example.homehub.admin.AdminCredentials.ADMIN_EMAIL, ignoreCase = true)) {
                    validateAdminLogin(email, password)
                } else {
                    loginUser(email, password)
                }
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun validateAdminLogin(email: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.progressOverlay.visibility = View.VISIBLE
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "default_device"
            val result = AdminCredentials.isValidAdmin(this, email, password, deviceId)

            if (result.isValid) {
                // Also sign in to Firebase Auth to satisfy Firestore security rules
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) {
                                // Instead of going directly to dashboard, route through normal verification flow
                                routeUserAfterLogin(user.uid, email)
                            } else {
                                binding.progressOverlay.visibility = View.GONE
                                showToast("Please verify your email address. Check your inbox for the link.")
                                auth.signOut()
                            }
                        } else {
                            binding.progressOverlay.visibility = View.GONE
                            val errorMessage = task.exception?.message ?: "Firebase Admin Sync failed"
                            showToast("Admin Auth Sync Failed: $errorMessage")
                        }
                    }
            } else {
                showToast(result.errorMessage ?: "Admin authentication failed")
                binding.loginPassword.text?.clear()
                binding.progressOverlay.visibility = View.GONE
            }
        } else {
            loginUser(email, password)
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()
        return email.matches(emailRegex)
    }

    private fun isAllowedEmailFormat(email: String): Boolean {
        // Now allowing all email formats for standard users
        return true
    }


    private fun loginUser(email: String, password: String) {
        binding.progressOverlay.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        routeUserAfterLogin(user.uid, email)
                    } else {
                        binding.progressOverlay.visibility = View.GONE
                        showToast("Please verify your email address. Check your inbox for the link.")
                        auth.signOut()
                    }
                } else {
                    binding.progressOverlay.visibility = View.GONE
                    showToast("Wrong email or password")
                    binding.loginPassword.text?.clear()
                }
            }
    }

    private fun routeUserAfterLogin(userId: String, email: String) {
        // For all roles, check Firestore for existing profile data
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                binding.progressOverlay.visibility = View.GONE

                if (document.exists()) {
                    // Check Password Expiration (30 days)
                    val lastChanged = document.getTimestamp("passwordLastChangedAt")
                    val isLegacyUser = lastChanged == null
                    val now = System.currentTimeMillis()
                    val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
                    
                    if (isLegacyUser || (now - (lastChanged?.toDate()?.time ?: 0) > thirtyDaysMillis)) {
                        startActivity(Intent(this, ChangePasswordActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    var firestoreRole = document.getString("role")?.lowercase()
                    if (firestoreRole.isNullOrEmpty()) {
                        firestoreRole = "unassigned"
                    }
                    val username = document.getString("fullName") ?: document.getString("username") ?: "User${(100..9999).random()}"
                    val profileImageUrl = document.getString("profileImageUrl") ?: document.getString("profilePictureUrl")
                    val profileSetupCompleted = document.getBoolean("profileSetupCompleted") ?: false
                    val verificationStatus = document.getString("verificationStatus") ?: "none"

                    // Admin Special Case: Ensure AdminSession is created if role is admin
                    if (firestoreRole == "admin") {
                        val adminSession = AdminSessionManager(this)
                        adminSession.createAdminSession(
                            name = username,
                            adminId = "super_admin_001",
                            role = "Super Admin",
                            permissions = "full_access"
                        )
                    }

                    // Check if account is disabled
                    if (verificationStatus.uppercase() == "REJECTED") {
                        auth.signOut()
                        sessionManager.clearSession()
                        Toast.makeText(this, "Your account has been disabled by administration.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    saveLoginState(email, username, "email", userId, firestoreRole, true, profileImageUrl)
                    sessionManager.setRoleSelected(true)
                    com.example.homehub.utils.GlobalDataCache.clearAllCaches()
                    
                    // Students skip verification, others require it
                    if (firestoreRole == "student") {
                        sessionManager.setBasicVerificationCompleted(true)
                    } else {
                        val isVerified = verificationStatus.uppercase() == "APPROVED"
                        if (isVerified) {
                            sessionManager.setBasicVerificationCompleted(true)
                        }
                    }
                    
                    sessionManager.saveUserRole(firestoreRole)

                    // Students may need onboarding
                    if (firestoreRole == "student" && !profileSetupCompleted) {
                        startActivity(Intent(this, com.example.homehub.student.StudentOnboardingActivity::class.java))
                    } else {
                        navigateToDashboard(firestoreRole)
                    }
                    finish()
                } else {
                    // No Firestore profile yet — create baseline and navigate to role selection/verification
                    val username = "User${(100..9999).random()}"
                    
                    // Special handling for hardcoded admin
                    val isHardcodedAdmin = email.equals(AdminCredentials.ADMIN_EMAIL, ignoreCase = true)
                    if (isHardcodedAdmin) {
                        // Create admin profile in Firestore
                        val adminData = hashMapOf(
                            "email" to email,
                            "username" to "System Admin",
                            "fullName" to "System Admin",
                            "role" to "admin",
                            "verificationStatus" to "APPROVED",
                            "profileSetupCompleted" to true,
                            "passwordLastChangedAt" to com.google.firebase.Timestamp.now(),
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("users").document(userId).set(adminData)
                            .addOnSuccessListener {
                    val profileImageUrl = adminData["profileImageUrl"] as? String ?: adminData["profilePictureUrl"] as? String
                    saveLoginState(email, "System Admin", "admin_credentials", userId, "admin", true, profileImageUrl)
                                sessionManager.setRoleSelected(true)
                                com.example.homehub.utils.GlobalDataCache.clearAllCaches()
                                sessionManager.saveUserRole("admin")
                                sessionManager.setBasicVerificationCompleted(true)
                                
                                // Create admin session
                                val adminSession = AdminSessionManager(this)
                                adminSession.createAdminSession(
                                    name = "System Admin",
                                    adminId = "super_admin_001",
                                    role = "Super Admin",
                                    permissions = "full_access"
                                )
                                
                                navigateToDashboard("admin")
                                finish()
                            }
                            .addOnFailureListener { e ->
                                binding.progressOverlay.visibility = View.GONE
                                showToast("Error creating admin profile: ${e.message}")
                            }
                    } else {
                        val role = if (email.lowercase().endsWith(".ac.ke")) "student" else "unassigned"
                        val isVerified = role == "student"
                        
                        val userData = hashMapOf(
                            "userId" to userId,
                            "email" to email,
                            "role" to role,
                            "verificationStatus" to if (isVerified) "APPROVED" else "none",
                            "profileSetupCompleted" to false,
                            "basicVerificationCompleted" to isVerified,
                            "isVerified" to isVerified,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("users").document(userId).set(userData).addOnCompleteListener {
                            saveLoginState(email, username, "email", userId, role, true, null)
                            sessionManager.setRoleSelected(true)
                            com.example.homehub.utils.GlobalDataCache.clearAllCaches()
                            sessionManager.saveUserRole(role)
                            if (isVerified) {
                                sessionManager.setBasicVerificationCompleted(true)
                            }
                            navigateToDashboard(role)
                            finish()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                showToast("Error retrieving user data: ${e.message}")
            }
    }

    private fun navigateToDashboard(role: String) {
        val intent = when (role.lowercase()) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "caretaker" -> Intent(this, CaretakerDashboardActivity::class.java)
            "water_supplier", "supplier" -> Intent(this, com.example.homehub.supplier.WaterSupplierDashboardActivity::class.java)
            "unassigned" -> Intent(this, AccountVerificationActivity::class.java)
            else -> Intent(this, com.example.homehub.student.StudentDashboardActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun saveLoginState(email: String, username: String, method: String, userId: String, role: String, roleSelected: Boolean = false, profileUrl: String? = null) {
        val initials = ProfilePictureUtils.getInitials(username)
        val profileColor = ProfilePictureUtils.getColorHexForName(username)

        Log.d("UserLogin", "Saving login state for $role: $email")

        // 1. Primary Session through SessionManager
        sessionManager.setLoggedIn(true)
        sessionManager.setRoleSelected(roleSelected)
        sessionManager.saveUserId(userId)
        sessionManager.saveUserRole(role)
        sessionManager.saveCachedUserName(username)
        sessionManager.saveCachedUserProfile(username, initials, profileUrl)

        val isAdmin = role.lowercase() == "admin"
        val isCaretaker = role.lowercase() == "caretaker"
        val isWaterSupplier = role.lowercase() == "water_supplier"

        sessionManager.saveUserMode(isCaretaker || isAdmin)
        
        val lastScreen = when {
            isAdmin -> SessionManager.SCREEN_ADMIN_DASHBOARD
            isCaretaker -> SessionManager.SCREEN_CARETAKER_DASHBOARD
            isWaterSupplier -> SessionManager.SCREEN_WATER_SUPPLIER_DASHBOARD
            else -> SessionManager.SCREEN_STUDENT_DASHBOARD
        }
        sessionManager.saveLastScreen(lastScreen)

        // 2. Legacy/Extra bits in LoginPrefs (shared with SessionManager file)
        getSharedPreferences("LoginPrefs", MODE_PRIVATE).edit().apply {
            putString("email", email)
            putString("username", username)
            putString("loginMethod", method)
            putString("role", role)
            putString("userId", userId)
            putString("profileInitials", initials)
            putString("profileColor", profileColor)
            apply()
        }
    }

    private fun checkLoginState() {
        if (sessionManager.isLoggedIn() && auth.currentUser != null) {
            val role = sessionManager.getUserRole() ?: "unassigned"
            Log.d("UserLogin", "Existing session found for role: $role. Redirecting...")
            navigateToDashboard(role)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (sessionManager.isLoggedIn() && auth.currentUser != null) {
            val role = sessionManager.getUserRole() ?: "unassigned"
            navigateToDashboard(role)
            finish()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (BackButtonHandler.handleBackPress(this)) {
            return
        }
        super.onBackPressed()
    }
}
