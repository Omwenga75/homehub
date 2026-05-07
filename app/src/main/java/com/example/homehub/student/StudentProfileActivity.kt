package com.example.homehub.student

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

import com.example.homehub.R
import com.example.homehub.databinding.ActivityStudentProfileBinding
import com.example.homehub.other.SettingsActivity
import com.example.homehub.auth.SessionManager
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.other.Extensions.loadCircularImage
import com.example.homehub.utils.ProfileImageManager
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.utils.UserVerificationBottomSheet
import com.example.homehub.other.ImageViewerActivity
import android.widget.Toast
import android.widget.PopupMenu

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    private lateinit var profileImageManager: ProfileImageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setupUI()
        profileImageManager = ProfileImageManager.create(this) { downloadUrl ->
            binding.ivProfile.loadProfileImage(auth.currentUser?.uid, downloadUrl, signature = System.currentTimeMillis())
            // Sync with session for lateral navigation updates
            val name = binding.tvFullName.text.toString()
            SessionManager(this).saveCachedUserProfile(name, ProfilePictureUtils.getInitials(name), downloadUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        loadUserStats()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tvWaterOrdersCount.setOnClickListener {
            startActivity(Intent(this, WaterOrdersActivity::class.java))
        }

        binding.ivProfile.setOnClickListener {
            showImageOptions()
        }

        // Verify button removed as everyone is now auto-verified
        binding.btnVerify.visibility = View.GONE

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun showImageOptions() {
        val options = arrayOf("View Profile Picture", "Change Profile Picture")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Profile Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val userId = auth.currentUser?.uid ?: ""
                        val localFile = java.io.File(filesDir, "profile_images/$userId.jpg")
                        
                        val intent = Intent(this, ImageViewerActivity::class.java)
                        intent.putExtra("image_title", "My Profile")
                        
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
                                    Toast.makeText(this, "No profile picture set", Toast.LENGTH_SHORT).show()
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

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("fullName") ?: document.getString("username") ?: SessionManager(this@StudentProfileActivity).getCachedUserName(user.uid)
                    val role = document.getString("role") ?: "Student"
                    val email = document.getString("email") ?: user.email ?: "Not Set"
                    val phone = document.getString("phone") ?: "Unassigned"
                    val joined = document.getTimestamp("createdAt")?.toDate()?.toString() ?: "System Epoch"

                    val profileUrl = document.getString("profileImageUrl") ?: document.getString("profilePictureUrl") ?: ""
                    val verificationStatus = document.getString("verificationStatus") ?: "none"
                    val university = document.getString("university") ?: "Not Set"
                    val studentId = document.getString("studentId") ?: "Not Set"
                    val course = document.getString("course") ?: document.getString("graduationYear") ?: "Not Set"
                    val location = document.getString("location") ?: ""
                    val idNumber = document.getString("idNumber") ?: ""
                    
                    // PROPERLY ASSIGN NAME AND EMAIL
                    binding.tvFullName.text = name
                    binding.tvEmail.text = email

                    // Use unified loader for instant persistent image support
                    binding.ivProfile.loadProfileImage(user.uid, profileUrl)
                    binding.tvInitials.visibility = View.GONE
                    
                    // Students are auto-verified, always show as verified
                    binding.tvVerifyStatus.text = "Verified"
                    binding.tvVerifySubtext.text = "Account secured"
                    binding.tvVerifyStatus.setTextColor(getColor(R.color.verified_active_orange))
                    binding.ivVerifyBadge.setImageResource(R.drawable.ic_verified)
                    binding.ivVerifyBadge.setColorFilter(getColor(R.color.verified_active_orange))
                    binding.btnVerify.visibility = View.GONE
                    binding.ivNameBadge.visibility = View.VISIBLE
                    binding.verifiedBadge.visibility = View.VISIBLE

                    // Load joined date
                    val createdAt = document.getTimestamp("createdAt")?.toDate()
                    if (createdAt != null) {
                        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        binding.tvJoinedSince.text = "Joined since ${sdf.format(createdAt)}"
                    } else {
                        binding.tvJoinedSince.text = "Joined since —"
                    }

                    binding.itemPhone.tvLabel.text = "Primary Contact"
                    binding.itemPhone.tvValue.text = phone
                    binding.itemPhone.ivIcon.setImageResource(R.drawable.ic_phone)


                    binding.itemUniversity.tvLabel.text = "Institution / Area"
                    binding.itemUniversity.tvValue.text = if (location.isNotEmpty()) "$university ($location)" else university
                    binding.itemUniversity.ivIcon.setImageResource(R.drawable.ic_location)

                    // Hide Course & Year for standard student profile
                    binding.itemCourse.root.visibility = View.GONE

                    binding.itemJoined.tvLabel.text = "ID Number/ Index"
                    binding.itemJoined.tvValue.text = idNumber.ifEmpty { studentId }
                    binding.itemJoined.ivIcon.setImageResource(R.drawable.ic_verified)

                    // Dynamic layout adaptation for Water Suppliers
                    if (role.equals("Water Supplier", ignoreCase = true)) {
                        binding.statsLayout.visibility = View.GONE
                        
                        val businessName = document.getString("businessName") ?: "Not Set"
                        val licenseNumber = document.getString("licenseNumber") ?: "Pending"
                        val serviceArea = document.getString("serviceArea") ?: "Local"
                        
                        binding.itemUniversity.tvLabel.text = "Registered Business"
                        binding.itemUniversity.tvValue.text = businessName
                        binding.itemUniversity.ivIcon.setImageResource(R.drawable.ic_check_circle)
                        
                        binding.itemCourse.root.visibility = View.VISIBLE
                        binding.itemCourse.tvLabel.text = "License & Area"
                        binding.itemCourse.tvValue.text = "$licenseNumber ($serviceArea)"
                        binding.itemCourse.ivIcon.setImageResource(R.drawable.ic_application)
                        
                        binding.tvVerifyStatus.text = "Verified"
                        binding.tvVerifySubtext.text = "Service credentials verified"
                        binding.tvVerifyStatus.setTextColor(getColor(R.color.verified_active_orange))
                        binding.ivVerifyBadge.setImageResource(R.drawable.ic_verified)
                        binding.ivVerifyBadge.setColorFilter(getColor(R.color.verified_active_orange))
                        binding.btnVerify.visibility = View.GONE
                        binding.verifiedBadge.visibility = View.VISIBLE
                    }

                    // Global override for Admin role
                    val sessionManager = SessionManager(this@StudentProfileActivity)
                    if (sessionManager.getUserRole()?.lowercase() == "admin") {
                        binding.tvVerifyStatus.text = "Verified"
                        binding.tvVerifySubtext.text = "Full system privileges"
                        binding.tvVerifyStatus.setTextColor(getColor(R.color.verified_active_orange))
                        binding.ivVerifyBadge.setImageResource(R.drawable.ic_verified)
                        binding.ivVerifyBadge.setColorFilter(getColor(R.color.verified_active_orange))
                        binding.verifiedBadge.visibility = View.VISIBLE
                    }
                }
            }
    }

    private fun loadUserStats() {
        val userId = auth.currentUser?.uid ?: return

        // Load confirmed payments count
        db.collection("bookings")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("paymentStatus", "completed")
            .get()
            .addOnSuccessListener { documents ->
                binding.tvBookingsCount.text = documents.size().toString()
            }

        // Load water orders count
        db.collection("waterOrders")
            .whereEqualTo("studentId", userId)
            .get()
            .addOnSuccessListener { documents ->
                binding.tvWaterOrdersCount.text = documents.size().toString()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun logout() {
        auth.signOut()
        getSharedPreferences("login_prefs", MODE_PRIVATE).edit().clear().apply()
        SessionManager(this).clearSession()
        com.example.homehub.utils.GlobalDataCache.clearAllCaches()
        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
