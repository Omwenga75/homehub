package com.example.homehub.auth

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.databinding.ActivityPhoneAuthBinding
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.admin.AdminDashboardActivity
import com.example.homehub.caretaker.CaretakerDashboardActivity

import com.example.homehub.supplier.WaterSupplierDashboardActivity
import com.example.homehub.student.StudentDashboardActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import com.example.homehub.utils.NotificationManager
import com.example.homehub.utils.toastError

class PhoneAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences

    private var storedVerificationId: String? = null
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Transparent status bar for the gradient header
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = resources.getColor(R.color.bg_light)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        NotificationManager.initialize(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Setup Toolbar
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Initially show phone input, hide OTP section
        binding.phoneInputLayout.visibility = View.VISIBLE
        binding.otpInputLayout.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.sendOtpButton.setOnClickListener {
            val phoneInput = binding.phoneNumber.text.toString().trim()
            if (phoneInput.isEmpty()) {
                showToast("Please enter a phone number")
            } else {
                // Prepend +254 since it's a prefix in the UI
                val fullPhone = if (phoneInput.startsWith("+")) phoneInput else "+254$phoneInput"
                
                if (!isValidPhoneNumber(fullPhone)) {
                    showToast("Please enter a valid phone number")
                } else {
                    phoneNumber = fullPhone
                    sendVerificationCode(fullPhone)
                }
            }
        }

        binding.verifyOtpButton.setOnClickListener {
            val code = binding.otpCode.text.toString().trim()
            if (code.isEmpty()) {
                showToast("Enter the OTP sent to your phone")
            } else if (storedVerificationId != null) {
                verifyCode(code)
            } else {
                showToast("Please request OTP first")
            }
        }

        binding.backToLogin.setOnClickListener {
            startActivity(Intent(this, UserLoginActivity::class.java))
            finish()
        }

        binding.resendOtpButton.setOnClickListener {
            if (phoneNumber.isNotEmpty()) {
                resendVerificationCode(phoneNumber)
            } else {
                showToast("Please enter phone number again")
            }
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Basic validation - should start with + and have at least 10 digits
        return phone.startsWith("+") && phone.length >= 10
    }

    private fun sendVerificationCode(phoneNumber: String) {
        showLoading(true)
        showToast("Sending OTP...")

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resendVerificationCode(phoneNumber: String) {
        showLoading(true)
        showToast("Resending OTP...")

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-retrieval or instant verification
            showToast("Verification completed automatically")
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            showLoading(false)
            toastError(e)
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            showLoading(false)
            storedVerificationId = verificationId
            resendToken = token

            // Animated transition to OTP input UI
            binding.phoneInputLayout.animate().alpha(0f).setDuration(300).withEndAction {
                binding.phoneInputLayout.visibility = View.GONE
                binding.otpInputLayout.visibility = View.VISIBLE
                binding.otpInputLayout.alpha = 0f
                binding.otpInputLayout.animate().alpha(1f).setDuration(300).start()
            }.start()

            showToast("OTP sent successfully!")
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
            showToast("OTP auto-retrieval timeout. Please enter manually.")
        }
    }

    private fun verifyCode(code: String) {
        showLoading(true)
        val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        handleSuccessfulLogin(user)
                    } else {
                        showLoading(false)
                        showToast("Login failed: User is null")
                    }
                } else {
                    showLoading(false)
                    val error = task.exception
                    if (error is FirebaseAuthInvalidCredentialsException) {
                        showToast("Invalid OTP. Please try again.")
                    } else {
                        toastError(error)
                    }
                }
            }
    }


    private fun handleSuccessfulLogin(user: FirebaseUser) {
        val phone = user.phoneNumber ?: phoneNumber
        val uid = user.uid

        binding.progressOverlay.visibility = View.VISIBLE
        
        // Fetch user data from Firestore to check onboarding status
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("fullName") ?: document.getString("username") ?: generateUsernameFromPhone(phone)
                    val role = document.getString("role") ?: "student"
                    val roleSelected = document.getBoolean("roleSelected") ?: false
                    val basicVerificationCompleted = document.getBoolean("basicVerificationCompleted") ?: false
                    
                    saveLoginState(phone, username, "phone", uid, role, roleSelected)
                    
                    navigateUser(role, roleSelected, basicVerificationCompleted)
                } else {
                    // New user via phone
                    val username = generateUsernameFromPhone(phone)
                    registerNewUserSilently(username, phone, uid)
                }
            }
            .addOnFailureListener {
                binding.progressOverlay.visibility = View.GONE
                showToast("Error retrieving user data")
            }
    }

    private fun registerNewUserSilently(username: String, phone: String, uid: String) {
        val initials = ProfilePictureUtils.getInitials(username)
        val profileColor = ProfilePictureUtils.getColorHexForName(username)

        val userData = hashMapOf(
            "userId" to uid,
            "fullName" to username,
            "username" to username,
            "phone" to phone,
            "signupMethod" to "phone",
            "lastLogin" to com.google.firebase.Timestamp.now(),
            "createdAt" to com.google.firebase.Timestamp.now(),
            "role" to "student",
            "roleSelected" to true,
            "isVerified" to true,
            "profileInitials" to initials,
            "profileColor" to profileColor,
            "hasCustomProfile" to false,
            "basicVerificationCompleted" to true
        )

        db.collection("users").document(uid).set(userData)
            .addOnSuccessListener {
                saveLoginState(phone, username, "phone", uid, "student", true)
                // Send welcome notification
                NotificationManager.sendWelcomeNotification(uid, username)
                startActivity(Intent(this, com.example.homehub.student.StudentDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                toastError(it)
            }
    }

    private fun saveLoginState(phone: String, username: String, method: String, uid: String, role: String, roleSelected: Boolean) {
        val sessionManager = SessionManager(this)
        val initials = ProfilePictureUtils.getInitials(username)
        val profileColor = ProfilePictureUtils.getColorHexForName(username)
        
        Log.d("PhoneAuth", "Saving login state for $role: $phone")

        sharedPref.edit()
            .putString("phone", phone)
            .putString("username", username)
            .putString("loginMethod", method)
            .putString("role", role)
            .putBoolean("isLoggedIn", true)
            .putString("userId", uid)
            .putString("profileInitials", initials)
            .putString("profileColor", profileColor)
            .apply()

        sessionManager.setLoggedIn(true)
        sessionManager.setRoleSelected(roleSelected)
        sessionManager.saveUserId(uid)
        sessionManager.saveUserRole(role)
        sessionManager.saveCachedUserName(username)
        sessionManager.saveCachedUserProfile(username, initials, null)
        
        if (role.isNotEmpty()) {
            val isCaretaker = role.lowercase() == "caretaker"
            val isAdmin = role.lowercase() == "admin"
            val isWaterSupplier = role.lowercase() == "water_supplier"
            
            sessionManager.saveLastScreen(when {
                isAdmin -> SessionManager.SCREEN_ADMIN_DASHBOARD
                isCaretaker -> SessionManager.SCREEN_CARETAKER_DASHBOARD
                isWaterSupplier -> SessionManager.SCREEN_WATER_SUPPLIER_DASHBOARD
                else -> SessionManager.SCREEN_STUDENT_DASHBOARD
            })
            sessionManager.saveUserMode(isCaretaker || isAdmin)
        }
    }

    private fun navigateUser(role: String, roleSelected: Boolean, basicVerificationCompleted: Boolean) {
        showLoading(false)
        val finalRole = if (role.isNullOrEmpty() || role == "user") "student" else role
        
        val intent = when (finalRole.lowercase()) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "caretaker" -> Intent(this, CaretakerDashboardActivity::class.java)
            "water_supplier" -> Intent(this, WaterSupplierDashboardActivity::class.java)
            else -> Intent(this, StudentDashboardActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun generateUsernameFromPhone(phone: String): String {
        // Generate a username from phone number (last 6 digits)
        return if (phone.length >= 6) {
            "Student${phone.takeLast(6)}"
        } else {
            "Student$phone"
        }
    }
    private fun showLoading(show: Boolean) {
        binding.progressOverlay.visibility = if (show) View.VISIBLE else View.GONE

        // Disable buttons during loading
        binding.sendOtpButton.isEnabled = !show
        binding.verifyOtpButton.isEnabled = !show
        binding.resendOtpButton.isEnabled = !show
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (binding.otpInputLayout.visibility == View.VISIBLE) {
            // If in OTP screen, go back to phone input with animation
            binding.otpInputLayout.animate().alpha(0f).setDuration(300).withEndAction {
                binding.otpInputLayout.visibility = View.GONE
                binding.phoneInputLayout.visibility = View.VISIBLE
                binding.phoneInputLayout.alpha = 0f
                binding.phoneInputLayout.animate().alpha(1f).setDuration(300).start()
                binding.otpCode.text?.clear()
            }.start()
        } else {
            super.onBackPressed()
        }
    }
}
