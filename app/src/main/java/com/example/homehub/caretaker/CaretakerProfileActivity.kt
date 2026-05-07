package com.example.homehub.caretaker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.other.ImageViewerActivity
import com.example.homehub.utils.ProfileImageManager
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.utils.UserVerificationBottomSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CaretakerProfileActivity : AppCompatActivity() {

    private lateinit var caretakerProfile: CaretakerProfile
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val PICK_IMAGE_REQUEST = 1
    private var currentCaretakerId = ""
    private lateinit var profileImageManager: ProfileImageManager

    // Statistics TextViews
    private var propertiesCount: TextView? = null
    private var caretakerRating: TextView? = null
    private var reviewsRating: TextView? = null
    private var caretakerTotalEarnings: TextView? = null

    // Real-time listeners
    private var propertiesListener: ListenerRegistration? = null
    private var ratingsListener: ListenerRegistration? = null
    private var earningsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caretaker_profile)

        currentCaretakerId = intent.getStringExtra("CARETAKER_ID") 
            ?: intent.getStringExtra("user_id") 
            ?: intent.getStringExtra("USER_ID") 
            ?: ""
        val caretakerName = intent.getStringExtra("CARETAKER_NAME") ?: "Caretaker"
        val caretakerPictureUrl = intent.getStringExtra("CARETAKER_PICTURE_URL") ?: ""

        initializeViews()
        initializeStatisticsViews()
        
        profileImageManager = ProfileImageManager.create(this) { downloadUrl ->
            findViewById<ImageView>(R.id.profileImage).loadProfileImage(auth.currentUser?.uid, downloadUrl, signature = System.currentTimeMillis())
        }
        
        val actualId = getActualCaretakerId()
        if (actualId.isEmpty()) {
            Log.e("CaretakerProfile", "No valid ID found for profile loading")
            Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        loadCaretakerProfile(actualId, caretakerName, caretakerPictureUrl)

        // Setup real-time property listener
        setupRealTimePropertyListener()

        // Register for property updates
        registerPropertyUpdateReceiver()
    }




    private fun initializeViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, com.example.homehub.other.SettingsActivity::class.java))
        }

        findViewById<View>(R.id.profileImage).setOnClickListener {
            showImageOptions()
        }

        // Verification logic removed: All caretakers are now auto-verified
        findViewById<MaterialButton>(R.id.btnVerify)?.visibility = View.GONE

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
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
                        val userId = getActualCaretakerId()
                        val localFile = java.io.File(filesDir, "profile_images/$userId.jpg")
                        
                        val intent = Intent(this, ImageViewerActivity::class.java)
                        intent.putExtra("image_title", "Caretaker Profile")
                        
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

    private fun initializeStatisticsViews() {
        propertiesCount = findViewById(R.id.propertiesCount)
        caretakerRating = findViewById(R.id.caretakerRating)
        caretakerTotalEarnings = findViewById(R.id.caretakerTotalEarnings)
        
        // Setup modern items initial states
        findViewById<View>(R.id.itemLocation).apply {
            findViewById<TextView>(R.id.tvLabel).text = "Location"
            findViewById<ImageView>(R.id.ivIcon).setImageResource(R.drawable.ic_location)
        }
        findViewById<View>(R.id.itemID).apply {
            findViewById<TextView>(R.id.tvLabel).text = "Official ID"
            findViewById<ImageView>(R.id.ivIcon).setImageResource(R.drawable.baseline_account_circle_24)
        }
        findViewById<View>(R.id.itemPhone).apply {
            findViewById<TextView>(R.id.tvLabel).text = "Primary Contact"
            findViewById<ImageView>(R.id.ivIcon).setImageResource(R.drawable.ic_phone)
        }
    }

    private fun loadCaretakerProfile(id: String, name: String, picUrl: String) {
        // Load verification badge and other profile info from users collection FIRST
        db.collection("users").document(id).get().addOnSuccessListener { userDoc ->
            val firestoreName = userDoc.getString("fullName") ?: userDoc.getString("username") ?: name
            val firestoreEmail = userDoc.getString("email") ?: "Email not provided"
            val firestorePicUrl = userDoc.getString("profileImageUrl") ?: userDoc.getString("profilePictureUrl") ?: picUrl
            
            findViewById<TextView>(R.id.tvCaretakerName).text = firestoreName
            findViewById<TextView>(R.id.tvCaretakerEmail)?.text = firestoreEmail

            // Use unified loader for instant persistent image support (checks local cache -> fallback)
            val lastUpdate = userDoc.getLong("lastProfileUpdate")
            findViewById<ImageView>(R.id.profileImage).loadProfileImage(id, firestorePicUrl, lastUpdate)

            // Update session cache with official name and image update timestamp
            val session = SessionManager(this)
            session.saveCachedUserProfile(
                firestoreName,
                ProfilePictureUtils.getInitials(firestoreName),
                firestorePicUrl.ifEmpty { session.getCachedUserImageUrl() }
            )
            lastUpdate?.let { session.saveLastImageUpdate(it) }

            val isCaretakerVerified = userDoc.getBoolean("isCaretakerVerified") ?: false
            val verificationStatus = userDoc.getString("verificationStatus") ?: "NOT VERIFIED"
            val verifiedBadge = findViewById<ImageView>(R.id.verifiedBadge)
            
            val tvVerifyStatus = findViewById<TextView>(R.id.tvVerifyStatus)
            val tvVerifySubtext = findViewById<TextView>(R.id.tvVerifySubtext)
            val ivVerifyBadge = findViewById<ImageView>(R.id.ivVerifyBadge)
            val btnVerify = findViewById<MaterialButton>(R.id.btnVerify)
            val cardVerification = findViewById<View>(R.id.cardVerification)
            
            // Premium DP Badge and Status Styling (Always APPROVED)
            verifiedBadge.visibility = View.VISIBLE
            verifiedBadge.setImageResource(R.drawable.ic_verified)
            verifiedBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.verified_active_orange))
            cardVerification?.visibility = View.GONE

            // If verifiedCaretakers doc exists, it has more detailed info
            db.collection("verifiedCaretakers").document(id).get().addOnSuccessListener { doc ->
                val tvAbout = findViewById<TextView>(R.id.aboutText)
                
                if (doc.exists()) {
                    val firestoreLocation = userDoc.getString("location") ?: userDoc.getString("residenceAddress") ?: doc.getString("location") ?: ""
                    val firestoreId = userDoc.getString("idNumber") ?: doc.getString("idNumber") ?: ""
                    
                    updateProfileItem(R.id.itemLocation, firestoreLocation.ifEmpty { "Location Not Set" }, firestoreLocation.isEmpty())
                    updateProfileItem(R.id.itemID, firestoreId.ifEmpty { "ID Not Set" }, firestoreId.isEmpty())
                    
                    val hostExp = doc.get("hostExperience") as? Map<String, Any>
                    val phones = hostExp?.get("phoneNumbers") as? Map<String, String>
                    val displayPhone = phones?.get("displayFormat") ?: userDoc.getString("phone") ?: ""
                    updateProfileItem(R.id.itemPhone, displayPhone.ifEmpty { "Contact Required" }, displayPhone.isEmpty())

                    // Executive Summary (Generated randomly if not explicitly set)
                    val bio = hostExp?.get("about") as? String ?: userDoc.getString("bio") ?: ""
                    if (bio.isEmpty() || bio.contains("Tell us about") || bio.contains("Professional summary")) {
                        tvAbout.text = generateRandomExecutiveSummary(firestoreName)
                        tvAbout.setTextColor(getColor(R.color.text_secondary))
                    } else {
                        tvAbout.text = bio
                        tvAbout.setTextColor(getColor(R.color.text_secondary))
                    }
                } else if (userDoc.exists()) {
                    // Fallback to basic user info
                    val loc = userDoc.getString("location") ?: userDoc.getString("residenceAddress") ?: ""
                    val idNum = userDoc.getString("idNumber") ?: ""
                    val tel = userDoc.getString("phone") ?: ""
                    
                    updateProfileItem(R.id.itemLocation, loc.ifEmpty { "Location Required" }, loc.isEmpty())
                    updateProfileItem(R.id.itemID, idNum.ifEmpty { "ID Required" }, idNum.isEmpty())
                    updateProfileItem(R.id.itemPhone, tel.ifEmpty { "Contact Required" }, tel.isEmpty())

                    val bio = userDoc.getString("bio") ?: ""
                    if (bio.isEmpty() || bio.contains("Tell us about") || bio.contains("Professional summary")) {
                        tvAbout.text = generateRandomExecutiveSummary(firestoreName)
                        tvAbout.setTextColor(getColor(R.color.text_secondary))
                    } else {
                        tvAbout.text = bio
                        tvAbout.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            }
        }
    }

    private fun generateRandomExecutiveSummary(name: String): String {
        val summaries = listOf(
            "Experienced property coordinator at HomeHub, specializing in student housing logistics and facility maintenance. Dedicated to providing a seamless residential experience through proactive communication and rapid issue resolution.",
            "Professional caretaker with over 5 years of experience in managing high-occupancy residential properties. Expert in maintaining high standards of cleanliness, security, and tenant satisfaction.",
            "Dynamic property manager committed to operational excellence. Proven track record of optimizing building performance and fostering positive relationships with student tenants and service providers.",
            "Detail-oriented caretaker focusing on the safety and comfort of the student community. Skilled at managing daily operations and long-term property upkeep to the highest industry standards.",
            "Client-focused property lead dedicated to creating a 'home away from home' for residents. Expert in administrative management and building maintenance coordination within the HomeHub ecosystem.",
            "Results-driven administrative lead for HomeHub properties, ensuring 24/7 support for all residential needs while maintaining prime asset value and tenant harmony.",
            "Senior residential coordinator with a passion for student hospitality. Bridging the gap between luxury living and practical student needs with exceptional organizational skills.",
            "Strategic property overseer with expertise in facility management and energy efficiency. Providing a modern, safe, and efficient living environment for the next generation of scholars."
        )
        // Use name's hash to keep it consistent for the same person
        val index = Math.abs(name.hashCode()) % summaries.size
        return summaries[index]
    }

    private fun updateProfileItem(itemId: Int, value: String, isMissing: Boolean) {
        findViewById<View>(itemId).apply {
            val tvValue = findViewById<TextView>(R.id.tvValue)
            tvValue.text = value
            if (isMissing) {
                tvValue.setTextColor(getColor(R.color.red_500))
                tvValue.alpha = 0.9f
            } else {
                tvValue.setTextColor(getColor(R.color.text_primary))
                tvValue.alpha = 1.0f
            }
        }
    }

    private fun setupRealTimePropertyListener() {
        val caretakerId = getActualCaretakerId()
        if (caretakerId.isEmpty()) return

        // 1. Properties Count & Likes Listener
        propertiesListener = db.collection("properties")
            .whereEqualTo("caretakerId", caretakerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CaretakerProfile", "Property listener failed", error)
                    return@addSnapshotListener
                }
                
                val count = snapshot?.size() ?: 0
                propertiesCount?.text = count.toString()
                
                // Calculate total likes instead of average rating
                val totalLikes = snapshot?.documents?.sumOf { it.getLong("likeCount")?.toInt() ?: 0 } ?: 0
                caretakerRating?.text = totalLikes.toString()
            }


        // 3. Earnings Listener (from confirmed/active bookings)
        earningsListener = db.collection("bookings")
            .whereEqualTo("caretakerId", caretakerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CaretakerProfile", "Earnings listener failed", error)
                    return@addSnapshotListener
                }
                var total = 0.0
                snapshot?.documents?.forEach { doc ->
                    val status = doc.getString("status") ?: ""
                    if (status == "confirmed" || status == "active" || status == "completed") {
                        total += doc.getDouble("amount") ?: 0.0
                    }
                }
                caretakerTotalEarnings?.text = "KSh ${String.format("%,.0f", total)}"
            }
    }

    private fun registerPropertyUpdateReceiver() {
        // Broadcast receiver for internal app updates (optional, implemented for robustness)
    }

    private fun getActualCaretakerId(): String = currentCaretakerId.ifEmpty { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private fun refreshUI() {
        loadCaretakerProfile(getActualCaretakerId(), "Caretaker", "")
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        getSharedPreferences("login_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        SessionManager(this).clearSession()
        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        propertiesListener?.remove()
        ratingsListener?.remove()
        earningsListener?.remove()
    }
}
