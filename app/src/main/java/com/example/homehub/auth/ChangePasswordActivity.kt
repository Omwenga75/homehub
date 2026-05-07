package com.example.homehub.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.databinding.ActivityChangePasswordBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupPasswordWatcher()

        binding.updatePasswordButton.setOnClickListener { handlePasswordUpdate() }
        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, UserLoginActivity::class.java))
            finish()
        }
    }

    private fun setupPasswordWatcher() {
        binding.newPassword.addTextChangedListener(object : TextWatcher {
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
        var points = 0
        if (password.length >= 8) points++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) points++
        if (password.any { it.isDigit() }) points++
        if (password.any { !it.isLetterOrDigit() }) points++
        return points
    }

    private fun handlePasswordUpdate() {
        val newPwd = binding.newPassword.text.toString()
        val confirmPwd = binding.confirmPassword.text.toString()

        if (calculatePasswordStrength(newPwd) < 4) {
            Toast.makeText(this, "Password must be strong (8+ chars, mixed case, number, symbol)", Toast.LENGTH_LONG).show()
            return
        }

        if (newPwd != confirmPwd) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser
        if (user != null) {
            user.updatePassword(newPwd).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateTimestampInFirestore(user.uid)
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}. You might need to re-login first.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateTimestampInFirestore(userId: String) {
        val now = com.google.firebase.Timestamp.now()
        val data = hashMapOf<String, Any>("passwordLastChangedAt" to now)
        
        db.collection("users").document(userId).update(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_LONG).show()
                // Redirect based on role or back to login
                auth.signOut()
                startActivity(Intent(this, UserLoginActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Local error updating record, but password was changed.", Toast.LENGTH_SHORT).show()
                auth.signOut()
                startActivity(Intent(this, UserLoginActivity::class.java))
                finish()
            }
    }
}
