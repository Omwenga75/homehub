package com.example.homehub.other

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.utils.ThemeHelper
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.databinding.ActivitySettingsBinding
import com.example.homehub.student.StudentProfileActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import java.net.UnknownHostException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUserData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }



        binding.btnEditProfile.setOnClickListener {
            val sessionManager = SessionManager(this)
            val role = sessionManager.getUserRole()
            startActivity(Intent(this, sessionManager.getProfileClass(role)))
        }



        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnPayment.setOnClickListener {
            showToast("M-Pesa settings coming soon")
        }

        binding.btnHelp.setOnClickListener {
            val message = "Phone: +254 111 307 585\nEmail: omwenganel75@gmail.com"
            MaterialAlertDialogBuilder(this)
                .setTitle("Help & Support")
                .setMessage(message)
                .setPositiveButton("Call") { _, _ ->
                    val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:+254111307585"))
                    startActivity(intent)
                }
                .setNegativeButton("Email") { _, _ ->
                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:omwenganel75@gmail.com"))
                    startActivity(intent)
                }
                .setNeutralButton("Cancel", null)
                .show()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }

    }

    // ─────────────────────────────────────────────
    // Change Password
    // ─────────────────────────────────────────────

    private fun showChangePasswordDialog() {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            showToast("You must be logged in with email to change password")
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val tilCurrent = dialogView.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
        val tilNew = dialogView.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = dialogView.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val etCurrent = dialogView.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelPassword)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSavePassword)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            tilCurrent.error = null
            tilNew.error = null
            tilConfirm.error = null

            val currentPassword = etCurrent.text.toString().trim()
            val newPassword = etNew.text.toString().trim()
            val confirmPassword = etConfirm.text.toString().trim()

            when {
                currentPassword.isEmpty() -> {
                    tilCurrent.error = "Enter current password"
                    etCurrent.requestFocus()
                }
                newPassword.isEmpty() -> {
                    tilNew.error = "Enter new password"
                    etNew.requestFocus()
                }
                newPassword.length < 6 -> {
                    tilNew.error = "Password must be at least 6 characters"
                    etNew.requestFocus()
                }
                confirmPassword.isEmpty() -> {
                    tilConfirm.error = "Confirm your new password"
                    etConfirm.requestFocus()
                }
                newPassword != confirmPassword -> {
                    tilConfirm.error = "Passwords do not match"
                    etConfirm.requestFocus()
                }
                currentPassword == newPassword -> {
                    tilNew.error = "New password must be different"
                    etNew.requestFocus()
                }
                else -> {
                    if (!isNetworkAvailable()) {
                        showToast("Make sure you're connected to the internet")
                        return@setOnClickListener
                    }

                    btnSave.isEnabled = false
                    btnSave.text = "Updating..."

                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    dialog.dismiss()
                                    showToast("Password updated successfully")
                                }
                                .addOnFailureListener { e ->
                                    btnSave.isEnabled = true
                                    btnSave.text = "Update Password"
                                    val msg = getFriendlyPasswordError(e)
                                    tilNew.error = msg
                                }
                        }
                        .addOnFailureListener { e ->
                            btnSave.isEnabled = true
                            btnSave.text = "Update Password"
                            if (isNetworkError(e)) {
                                showToast("Make sure you're connected to the internet")
                            } else {
                                tilCurrent.error = "Current password is incorrect"
                                etCurrent.requestFocus()
                            }
                        }
                }
            }
        }
    }


    // ─────────────────────────────────────────────
    // Existing helpers
    // ─────────────────────────────────────────────

    private fun loadUserData() {
        // Name and Email removed per dashboard privacy request
    }

    private fun logout() {
        auth.signOut()
        getSharedPreferences("LoginPrefs", MODE_PRIVATE).edit().clear().apply()
        SessionManager(this).clearSession()

        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }



    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isNetworkError(e: Exception): Boolean {
        return e is UnknownHostException ||
            e.cause is UnknownHostException ||
            e.message?.contains("network", ignoreCase = true) == true ||
            e.message?.contains("timeout", ignoreCase = true) == true ||
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true
    }

    private fun getFriendlyPasswordError(e: Exception): String {
        return when {
            isNetworkError(e) -> "Make sure you're connected to the internet"
            e is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters"
            e.message?.contains("recent", ignoreCase = true) == true -> "Please log out and log back in, then try again"
            else -> "Unable to update password. Please try again later"
        }
    }
}
