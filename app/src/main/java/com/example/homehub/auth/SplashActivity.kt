package com.example.homehub.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.homehub.R
import com.example.homehub.admin.AdminSessionManager
import com.example.homehub.admin.AdminDashboardActivity
import com.example.homehub.auth.SessionManager
import com.example.homehub.caretaker.CaretakerDashboardActivity
import com.example.homehub.supplier.WaterSupplierDashboardActivity
import com.example.homehub.student.StudentDashboardActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val TAG = "SplashActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full immersive edge-to-edge experience (Modern approach)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Remove system-enforced scrims on API 29+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setContentView(R.layout.activity_splash)

        val splashContainer = findViewById<View>(R.id.splashContainer)

        splashContainer.scaleX = 0.8f
        splashContainer.scaleY = 0.8f
        splashContainer.alpha = 0f

        splashContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(1200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser

        window.decorView.postDelayed({
            val adminSessionManager = AdminSessionManager(this)
            if (adminSessionManager.isAdminLoggedIn()) {
                navigateToDashboard(AdminDashboardActivity::class.java)
                return@postDelayed
            }

            if (currentUser == null) {
                navigateToOnboarding()
            } else {
                checkUserOnboardingStatus(currentUser.uid)
            }
        }, 3000)
    }


    private fun checkUserOnboardingStatus(userId: String) {
        val sessionManager = SessionManager(this)
        val role = sessionManager.getUserRole() ?: "unassigned"

        // For providers/students: check Firestore for profile status
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    var firestoreRole = document.getString("role")?.lowercase()
                    if (firestoreRole.isNullOrEmpty()) {
                        firestoreRole = "unassigned"
                    }
                    val verificationStatus = document.getString("verificationStatus") ?: ""
                    val profileSetupCompleted = document.getBoolean("profileSetupCompleted") ?: false

                    // Check if account is disabled (REJECTED strictly by Admin)
                    if (verificationStatus.uppercase() == "REJECTED") {
                        Log.d(TAG, "→ Account REJECTED - signing out")
                        auth.signOut()
                        sessionManager.clearSession()
                        Toast.makeText(this, "Your account has been disabled.", Toast.LENGTH_LONG).show()
                        navigateToOnboarding()
                        return@addOnSuccessListener
                    }

                    // Students may need onboarding
                    if (firestoreRole == "student" && !profileSetupCompleted) {
                        startActivity(Intent(this, com.example.homehub.student.StudentOnboardingActivity::class.java))
                        finish()
                        return@addOnSuccessListener
                    }

                    Log.d(TAG, "→ Navigating to $firestoreRole dashboard")
                    when (firestoreRole) {
                        "caretaker" -> navigateToDashboard(CaretakerDashboardActivity::class.java)
                        "water_supplier", "supplier" -> navigateToDashboard(WaterSupplierDashboardActivity::class.java)
                        "unassigned" -> {
                            startActivity(Intent(this@SplashActivity, AccountVerificationActivity::class.java))
                            finish()
                        }
                        else -> navigateToDashboard(StudentDashboardActivity::class.java)
                    }
                } else {
                    // Force verification if record missing or no role. Verifications cannot be bypassed.
                    startActivity(Intent(this@SplashActivity, AccountVerificationActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user role", e)
                auth.signOut()
                navigateToOnboarding()
            }
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, UserLoginActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun navigateToDashboard(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
