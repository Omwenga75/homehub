package com.example.homehub.supplier

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.other.Extensions.loadCircularImage
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.databinding.ActivityWaterSupplierProfileBinding
import com.example.homehub.other.ImageViewerActivity
import com.example.homehub.utils.ProfileImageManager
import com.example.homehub.utils.ProfilePictureUtils
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WaterSupplierProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterSupplierProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private lateinit var profileImageManager: ProfileImageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaterSupplierProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        setupUI()
        
        // INSTANT HYDRATION: Fill UI with cached profile data immediately to avoid flicker
        hydrateUIInstantly()
        
        profileImageManager = ProfileImageManager.create(this) { _ ->
            // FIX: Use loadProfileImage with System signature to force Glide to refresh 
            // the local file cache instantly and avoid showing the 'last picture'.
            val userId = auth.currentUser?.uid ?: ""
            binding.ivProfile.loadProfileImage(userId, signature = System.currentTimeMillis())
        }
        
        loadProfileData()
    }

    private fun hydrateUIInstantly() {
        val user = auth.currentUser ?: return
        val cachedName = sessionManager.getCachedUserName(user.uid)
        val cachedImage = sessionManager.getCachedUserImageUrl()
        val fallbackName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Water Supplier"

        binding.tvSupplierName.text = if (cachedName.isNotEmpty() && cachedName != "User") {
            cachedName
        } else {
            fallbackName
        }

        binding.ivProfile.loadProfileImage(user.uid, cachedImage)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.ivProfile.setOnClickListener {
            showImageOptions()
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, com.example.homehub.other.SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

        // Initialize labels for modern items
        binding.itemServiceArea.tvLabel.text = "Primary Service Area"
        binding.itemServiceArea.ivIcon.setImageResource(R.drawable.ic_location)
        
        binding.itemWaterSource.tvLabel.text = "Water Source"
        binding.itemWaterSource.ivIcon.setImageResource(R.drawable.ic_water_drop)
        
        binding.itemPhone.tvLabel.text = "Contact Phone"
        binding.itemPhone.ivIcon.setImageResource(R.drawable.ic_phone)

        binding.itemEmail.tvLabel.text = "Contact Email"
        binding.itemEmail.ivIcon.setImageResource(R.drawable.ic_email)

        // New ID row
        binding.itemID.tvLabel.text = "Business Owner ID"
        binding.itemID.ivIcon.setImageResource(R.drawable.ic_verified)
    }

    private fun loadProfileData() {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (doc.exists()) {
                    val name = doc.getString("fullName")?.takeIf { it.isNotBlank() } ?: doc.getString("username")?.takeIf { it.isNotBlank() }
                    val businessName = doc.getString("businessName") ?: ""
                    val displayName: String = name ?: (if (businessName.isNotBlank()) businessName else "Water Supplier")
                    val email = doc.getString("email") ?: "Not Provided"
                    val phone = doc.getString("phone") ?: "Not Set"
                    val serviceArea = doc.getString("serviceArea") ?: "Not Specified"
                    val waterSource = doc.getString("waterSource") ?: "Clean Tap Water"
                    val bio = doc.getString("bio") ?: "No bio provided"
                    val status = doc.getString("verificationStatus") ?: "PENDING"

                    // Identification removed for privacy
                    val displayBio = if (bio.isEmpty() || bio.contains("No bio provided") || bio.contains("Tell us about")) {
                        generateRandomOperationalSummary(displayName)
                    } else {
                        bio
                    }
                    binding.tvSupplierBio.text = displayBio
                    binding.tvSupplierName.text = displayName
                    binding.tvSupplierEmail.text = email

                    
                    binding.itemPhone.tvValue.text = phone
                    binding.itemServiceArea.tvValue.text = serviceArea.ifEmpty { "Not Specified" }
                    binding.itemWaterSource.tvValue.text = waterSource
                    binding.itemEmail.tvValue.text = email
                    
                    val firestoreId = doc.getString("idNumber") ?: "Not Set"
                    binding.itemID.tvValue.text = firestoreId

                    // Verification Badge logic
                    val verificationStatus = (doc.getString("verificationStatus") ?: "NONE").uppercase()
                    val cardVerification = binding.root.findViewById<View>(R.id.cardVerification)
                    val tvVerifyStatus = binding.root.findViewById<TextView>(R.id.tvVerifyStatus)
                    val tvVerifySubtext = binding.root.findViewById<TextView>(R.id.tvVerifySubtext)
                    val ivVerifyBadge = binding.root.findViewById<ImageView>(R.id.ivVerifyBadge)
                    val btnVerify = binding.root.findViewById<View>(R.id.btnVerify) as? com.google.android.material.button.MaterialButton
                    
                    // Display actual verification status
                    when (verificationStatus) {
                        "APPROVED" -> {
                            binding.verifiedBadge.visibility = View.VISIBLE
                            binding.verifiedBadge.setImageResource(R.drawable.ic_verified)
                            binding.verifiedBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.verified_active_orange))
                            cardVerification?.visibility = View.GONE
                        }
                        "PENDING" -> {
                            binding.verifiedBadge.visibility = View.VISIBLE
                            binding.verifiedBadge.setImageResource(R.drawable.ic_verified)
                            binding.verifiedBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.warning_color))
                            cardVerification?.visibility = View.GONE
                        }
                        "REJECTED" -> {
                            binding.verifiedBadge.visibility = View.VISIBLE
                            binding.verifiedBadge.setImageResource(R.drawable.ic_verified)
                            binding.verifiedBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error_color))
                            cardVerification?.visibility = View.VISIBLE
                        }
                        else -> {
                            binding.verifiedBadge.visibility = View.GONE
                            cardVerification?.visibility = View.VISIBLE
                        }
                    }

                    // Cache for next time
                    sessionManager.saveCachedUserProfile(
                        displayName,
                        ProfilePictureUtils.getInitials(displayName),
                        doc.getString("profileImageUrl") ?: sessionManager.getCachedUserImageUrl()
                    )

                    // Profile Image (unified loader handles local cache + fallback)
                    val profileUrl = doc.getString("profileImageUrl") ?: ""
                    val userId = auth.currentUser?.uid ?: ""
                    val lastUpdate = doc.getLong("lastProfileUpdate")
                    
                    binding.ivProfile.loadProfileImage(userId, profileUrl, lastUpdate)
                    
                    // Cache signature for next time and dashboards
                    lastUpdate?.let { sessionManager.saveLastImageUpdate(it) }
                    
                }
            }
            .addOnFailureListener { e ->
                Log.e("WaterProfile", "Error loading profile", e)
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }

        // Fetch Order Stats for financial metrics
        db.collection("waterOrders")
            .whereEqualTo("supplierId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                var receivedPay = 0.0
                var pendingPay = 0.0
                var deliveredCount = 0

                for (doc in documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    val amountStr = doc.get("amount")?.toString() ?: "0"
                    val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0

                    if (status in listOf("completed", "delivered", "paid")) {
                        receivedPay += amount
                        deliveredCount++
                    } else if (status in listOf("pending", "new", "pending_cod")) {
                        pendingPay += amount
                    }
                }

                binding.tvDeliveriesCount.text = deliveredCount.toString()
                
                binding.tvRating.text = if (receivedPay >= 100000) {
                    String.format("%.1fk", receivedPay / 1000)
                } else {
                    String.format("%,.0f", receivedPay)
                }
                
                binding.tvCapacity.text = if (pendingPay >= 100000) {
                    String.format("%.1fk", pendingPay / 1000)
                } else {
                    String.format("%,.0f", pendingPay)
                }
            }
    }

    private fun generateRandomOperationalSummary(name: String): String {
        val summaries = listOf(
            "Providing clean and safe water delivery services to the community with efficiency and reliability.",
            "Dedicated to sustainable water sourcing and rapid delivery. Serving residential and commercial clients with top-tier hydration solutions.",
            "Water supply specialist with a focus on purity and punctual service. Your trusted partner for high-capacity water needs in the region.",
            "Expert in logistics and water quality management. Committed to ensuring every drop meets the highest health standards for HomeHub residents.",
            "Premium water delivery service leveraging modern filtration and real-time tracking to provide a superior customer experience."
        )
        val index = Math.abs(name.hashCode()) % summaries.size
        return summaries[index]
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
                        intent.putExtra("image_title", "Business Profile")
                        
                        if (localFile.exists()) {
                            intent.putExtra("image_url", "file://" + localFile.absolutePath)
                            startActivity(intent)
                        } else {
                            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                                if (isFinishing || isDestroyed) return@addOnSuccessListener
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

    private fun logoutUser() {
        auth.signOut()
        sessionManager.clearSession()
        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
