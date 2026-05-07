package com.example.homehub.auth

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.databinding.SignUpBinding
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.homehub.auth.SessionManager
import com.example.homehub.utils.NotificationManager
import com.example.homehub.utils.NotificationAccessManager
import com.example.homehub.utils.UsernameFormatter
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.utils.BackButtonHandler
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.chip.Chip

class SignUp : AppCompatActivity() {

    private lateinit var binding: SignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reverted to transparent status bar for immersion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        sessionManager = SessionManager(this)



        NotificationManager.initialize(this)
        NotificationAccessManager.initialize()

        binding.registerButton.setOnClickListener { handleEmailSignUp() }
        binding.loginRedirect.setOnClickListener {
            startActivity(Intent(this, UserLoginActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        setupPasswordWatcher()
    }

    private fun setupPasswordWatcher() {
        binding.signupPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                if (password.isEmpty()) {
                    binding.strengthIndicatorContainer.visibility = View.GONE
                    return
                }
                binding.strengthIndicatorContainer.visibility = View.VISIBLE
                updateStrengthUI(password)
            }
        })
    }

    private fun updateStrengthUI(password: String) {
        val strength = calculatePasswordStrength(password)
        val bars = arrayOf(binding.strengthBar1, binding.strengthBar2, binding.strengthBar3, binding.strengthBar4)
        
        // Reset bars
        bars.forEach { it.setBackgroundColor(resources.getColor(R.color.grey_200)) }

        val (color, text) = when (strength) {
            1 -> R.color.red_500 to "Weak"
            2 -> R.color.orange_500 to "Fair"
            3 -> R.color.blue_500 to "Good"
            4 -> R.color.success_green to "Strong"
            else -> R.color.grey_500 to "Very Weak"
        }

        binding.strengthText.text = text
        binding.strengthText.setTextColor(resources.getColor(color))

        for (i in 0 until strength) {
            bars[i].setBackgroundColor(resources.getColor(color))
        }
    }

    private fun calculatePasswordStrength(password: String): Int {
        var strength = 0
        if (password.length >= 8) strength++
        if (password.any { it.isUpperCase() }) strength++
        if (password.any { it.isLowerCase() }) strength++
        if (password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() }) {
            if (password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }) {
               strength++
            } else if (strength < 4 && (password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() })) {
                // If it has at least one of them, but we want both for max strength
                // This logic is a bit simple, let's refine:
            }
        }
        
        // Refined logic for 4 bars:
        var points = 0
        if (password.length >= 8) points++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) points++
        if (password.any { it.isDigit() }) points++
        if (password.any { !it.isLetterOrDigit() }) points++
        
        return points
    }


    private fun handleEmailSignUp() {
        val fullName = binding.signupFullName.text.toString().trim()
        val email = binding.signupEmail.text.toString().trim()
        val password = binding.signupPassword.text.toString()

        when {
            fullName.isEmpty() -> {
                binding.signupFullName.error = "Please enter your full name"
                binding.fullNameInputLayout.error = " "
                binding.signupFullName.requestFocus()
            }
            email.isEmpty() -> {
                binding.signupEmail.error = "Please enter email"
                binding.emailInputLayout.error = " "
                binding.signupEmail.requestFocus()
            }
            !isValidEmail(email) -> {
                binding.signupEmail.error = "Please enter a valid email"
                binding.emailInputLayout.error = " "
                binding.signupEmail.requestFocus()
            }
            password.isEmpty() -> {
                binding.signupPassword.error = "Please enter password"
                binding.passwordInputLayout.error = " "
                binding.signupPassword.requestFocus()
            }
            calculatePasswordStrength(password) < 4 -> {
                binding.signupPassword.error = "Password is too weak. Must include Upper, Lower, Number and Special Char."
                binding.passwordInputLayout.error = " "
                binding.signupPassword.requestFocus()
            }
            else -> {
                registerUser(fullName, email, password)
            }
        }
    }

    private fun isValidUsername(username: String): Boolean {
        val usernameRegex = "^[a-zA-Z0-9 ]{2,30}$".toRegex()
        return username.matches(usernameRegex)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()
        return email.matches(emailRegex)
    }

    private fun registerUser(username: String, email: String, password: String) {
        binding.progressOverlay.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    sendVerificationEmail(username, email, password)
                } else {
                    binding.progressOverlay.visibility = View.GONE
                    val errorMessage = task.exception?.message ?: "Registration failed"
                    showToast("Registration failed: $errorMessage")
                    binding.signupPassword.text?.clear()
                }
            }
    }

    private fun sendVerificationEmail(username: String, email: String, password: String) {
        auth.currentUser?.let { user ->
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://homehub-588b9.firebaseapp.com/verify?email=${user.email}")
                .setHandleCodeInApp(true)
                .setAndroidPackageName(packageName, true, null)
                .build()

            user.sendEmailVerification(actionCodeSettings)
                .addOnCompleteListener { task ->
                    binding.progressOverlay.visibility = View.GONE
                    if (task.isSuccessful) {
                        val role = if (email.lowercase().endsWith(".ac.ke")) "student" else "unassigned"
                        
                        val formattedName = (username.substringBefore(" ") + " " + username.substringAfter(" ", "")).trim()
                        saveUserToFirestore(formattedName, email, "email", role)
                        showVerificationDialog(email, formattedName, password)
                    } else {
                        val errorMessage = task.exception?.message ?: "Failed to send verification email"
                        showToast("Verification Error: $errorMessage")
                    }
                }
        }
    }

    private fun showVerificationDialog(email: String, username: String, password: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Verify Your Email")
            .setMessage("A verification link has been sent to $email. Please check your inbox and verify your email before logging in.")
            .setCancelable(false)
            .setPositiveButton("OK") { dialogInterface, _ ->
                dialogInterface.dismiss()
                auth.signOut()
                val intent = Intent(this, UserLoginActivity::class.java).apply {
                    putExtra("AUTO_FILL_EMAIL", email)
                    putExtra("AUTO_FILL_PASSWORD", password)
                }
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
            .create()
        dialog.show()
    }

    private fun saveUserToFirestore(username: String, email: String, method: String, role: String = "") {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: ""

        val initials = ProfilePictureUtils.getInitials(username)
        val profileColor = ProfilePictureUtils.getColorHexForName(username)

        val userData = hashMapOf(
            "userId" to userId,
            "fullName" to username,
            "username" to username,
            "email" to email,
            "signupMethod" to method,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "lastLogin" to com.google.firebase.Timestamp.now(),
            "role" to role,
            "roleSelected" to true,
            "profileSetupCompleted" to false,
            "basicVerificationCompleted" to true,
            "isVerified" to true,
            "emailVerified" to false,
            "verificationStatus" to "APPROVED",
            "profileInitials" to initials,
            "profileColor" to profileColor,
            "hasCustomProfile" to false,
            "passwordLastChangedAt" to com.google.firebase.Timestamp.now()
        )

        val batch = db.batch()

        val userDocRef = db.collection("users").document(userId)
        batch.set(userDocRef, userData)

        if (email.isNotEmpty() && email != userId) {
            val emailDocRef = db.collection("users").document(email)
            batch.set(emailDocRef, userData)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("SignUp", "User data saved successfully for: $username (UID: $userId)")
                // Send welcome notification
                NotificationManager.sendWelcomeNotification(userId, username)
            }
            .addOnFailureListener { e ->
                Log.e("SignUp", "Failed to save user data: ${e.message}")
                db.collection("users").document(userId).set(userData)
                if (email.isNotEmpty() && email != userId) {
                    db.collection("users").document(email).set(userData)
                }
            }
    }

    override fun onBackPressed() {
        if (BackButtonHandler.handleBackPress(this)) {
            return
        }
        super.onBackPressed()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
