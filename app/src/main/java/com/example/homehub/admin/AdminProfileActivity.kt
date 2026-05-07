package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityAdminProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.homehub.R
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.auth.SessionManager
import com.example.homehub.other.Extensions.loadCircularImage
import com.example.homehub.utils.ProfileImageManager
import android.widget.PopupMenu
import android.widget.Toast
import com.example.homehub.other.ImageViewerActivity

class AdminProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminProfileBinding
    private lateinit var sessionManager: AdminSessionManager
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var profileImageManager: ProfileImageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = AdminSessionManager(this)
        auth = FirebaseAuth.getInstance()

        profileImageManager = ProfileImageManager.create(this) { 
            // Refresh image immediately after upload success
            loadAdminData()
        }

        setupUI()
        loadAdminData()
        loadSystemStats()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, com.example.homehub.other.SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.ivProfile.setOnClickListener {
            showImageOptions()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadAdminData() {
        val userId = auth.currentUser?.uid ?: ""
        
        // 1. INSTANT HYDRATION FROM CACHE
        val cachedName = sessionManager.getAdminName()
        val cachedDept = sessionManager.getAdminDepartment()
        val cachedEmpId = sessionManager.getAdminEmployeeId()
        val cachedBio = sessionManager.getAdminBio()

        updateProfileUI(cachedName, cachedDept, cachedEmpId, cachedBio)

        // 2. CLOUD SYNC: Fetch latest from Firestore to ensure consistency
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val officialName = doc.getString("fullName") ?: doc.getString("username") ?: cachedName
                val officialDept = doc.getString("department") ?: cachedDept
                val officialId = doc.getString("employeeId") ?: cachedEmpId
                val officialBio = doc.getString("bio") ?: cachedBio
                
                // Update Session Cache for future instant loads
                sessionManager.setAdminName(officialName)
                sessionManager.setAdminDepartment(officialDept)
                sessionManager.setAdminEmployeeId(officialId)
                sessionManager.setAdminBio(officialBio)
                
                // Update UI with official data
                updateProfileUI(officialName, officialDept, officialId, officialBio)
                
                // Load profile image with signature for instant refresh
                val url = doc.getString("profileImageUrl") ?: doc.getString("profilePictureUrl") ?: ""
                val lastUpdate = doc.getLong("lastAdminProfileUpdate")
                
                binding.ivProfile.loadProfileImage(userId, url, lastUpdate)
                
                // Update local session timestamp cache for both session managers
                lastUpdate?.let { 
                    sessionManager.saveLastAdminImageUpdate(it)
                    com.example.homehub.auth.SessionManager(this@AdminProfileActivity).saveLastImageUpdate(it)
                    com.example.homehub.auth.SessionManager(this@AdminProfileActivity).updateCachedUserImageUrl(url, it)
                }
                
                binding.tvInitials.visibility = View.GONE
            }
        }.addOnFailureListener {
            binding.ivProfile.loadProfileImage(userId)
            binding.tvInitials.visibility = View.GONE
        }
    }

    private fun updateProfileUI(name: String, dept: String, empId: String, bio: String) {
        val email = auth.currentUser?.email ?: "admin@homehub.com"
        binding.tvAdminName.text = name
        binding.tvAdminBio.text = bio
        binding.tvAdminEmail.text = email

        binding.verifiedBadge.visibility = View.VISIBLE
        binding.verifiedBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.verified_active_orange))
        
        binding.itemDepartment.apply {
            tvLabel.text = "Operational Bureau"
            tvValue.text = dept
            ivIcon.setImageResource(R.drawable.ic_check_circle)
        }
        
        binding.itemEmployeeId.apply {
            tvLabel.text = "Administrative ID"
            tvValue.text = empId
            ivIcon.setImageResource(R.drawable.id)
        }

        binding.itemEmail.apply {
            tvLabel.text = "Official Email"
            tvValue.text = email
            ivIcon.setImageResource(R.drawable.ic_email)
        }
        
        val loginTime = sessionManager.getLoginTime()
        binding.itemLoginTime.apply {
            tvLabel.text = "Latest Secure Session"
            ivIcon.setImageResource(R.drawable.ic_calendar)
            if (loginTime > 0) {
                val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                tvValue.text = sdf.format(Date(loginTime))
            } else {
                tvValue.text = "Active Session"
            }
        }
    }

    private fun loadSystemStats() {
        // Load verified users count
        db.collection("users")
            .whereEqualTo("isStudentVerified", true)
            .get()
            .addOnSuccessListener { studentDocs ->
                db.collection("users")
                    .whereEqualTo("isCaretakerVerified", true)
                    .get()
                    .addOnSuccessListener { caretakerDocs ->
                        val totalVerified = studentDocs.size() + caretakerDocs.size()
                        binding.tvVerifiedCount.text = totalVerified.toString()
                    }
            }

        // Mock counts for alerts and tasks for now
        binding.tvAlertsCount.text = "0"
        binding.tvTasksCount.text = (10..50).random().toString()
    }

    private fun showImageOptions() {
        val options = arrayOf("View Profile Picture", "Change Profile Picture")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Profile Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val userId = auth.currentUser?.uid ?: ""
                        val localFile = java.io.File(filesDir, "profile_images/${userId}_admin.jpg")
                        
                        val intent = Intent(this, ImageViewerActivity::class.java)
                        intent.putExtra("image_title", "Administrative Profile")
                        
                        if (localFile.exists()) {
                            intent.putExtra("image_url", "file://" + localFile.absolutePath)
                            startActivity(intent)
                        } else {
                            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                                val url = doc.getString("adminProfileImageUrl") ?: doc.getString("profileImageUrl") ?: doc.getString("profilePictureUrl") ?: ""
                                if (url.isNotEmpty()) {
                                    intent.putExtra("image_url", url)
                                    startActivity(intent)
                                } else {
                                    android.widget.Toast.makeText(this, "No profile picture set", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    1 -> {
                        profileImageManager.launchPicker()
                    }
                }
            }
            .show()
    }

    private fun logout() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout from the Admin Panel?")
            .setPositiveButton("Logout") { _, _ ->
                sessionManager.clearAdminSession()
                getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                SessionManager(this).clearSession()
                auth.signOut()
                
                val intent = Intent(this, UserLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
