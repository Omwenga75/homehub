package com.example.homehub.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.utils.ProfileImageManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminProfileFragment : Fragment() {

    private lateinit var sessionManager: AdminSessionManager
    private lateinit var profileImageManager: ProfileImageManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var ivAdminProfile: ImageView
    private lateinit var adminNameText: TextView
    private lateinit var totalStudentsText: TextView
    private lateinit var verifiedCaretakersText: TextView
    private lateinit var totalPropertiesStatsText: TextView
    private lateinit var pendingRequestsText: TextView
    private lateinit var adminEmailText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = AdminSessionManager(requireContext())
        
        initializeViews(view)
        setupProfileManager()
        loadAdminData()
        loadPlatformStats()
    }

    private fun initializeViews(view: View) {
        ivAdminProfile = view.findViewById(R.id.ivAdminProfile)
        adminNameText = view.findViewById(R.id.adminNameText)
        totalStudentsText = view.findViewById(R.id.totalStudentsText)
        verifiedCaretakersText = view.findViewById(R.id.verifiedCaretakersText)
        totalPropertiesStatsText = view.findViewById(R.id.totalPropertiesStatsText)
        pendingRequestsText = view.findViewById(R.id.pendingRequestsText)
        adminEmailText = view.findViewById(R.id.adminEmailText)

        ivAdminProfile.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Profile Picture")
                .setMessage("Would you like to select a new profile picture from your gallery?")
                .setPositiveButton("Open Gallery") { _, _ ->
                    profileImageManager.launchPicker()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<MaterialButton>(R.id.changePasswordButton).setOnClickListener {
            // Handle password change
        }
    }

    private fun setupProfileManager() {
        val userId = auth.currentUser?.uid
        profileImageManager = ProfileImageManager.create(this, "admin") { downloadUrl ->
            // Use signature for instant refresh after picking
            ivAdminProfile.loadProfileImage(userId, downloadUrl, System.currentTimeMillis())
        }
    }

    private fun loadAdminData() {
        adminNameText.text = sessionManager.getAdminName()
        
        val userId = auth.currentUser?.uid ?: return
        
        // Also fetch email from Auth, or fallback if available
        val email = auth.currentUser?.email ?: "Not Set"
        adminEmailText.text = email
        
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val url = doc.getString("adminProfileImageUrl") ?: doc.getString("profileImageUrl") ?: ""
                val lastUpdate = doc.getLong("lastAdminProfileUpdate")
                
                val docEmail = doc.getString("email")
                if (!docEmail.isNullOrEmpty()) {
                    adminEmailText.text = docEmail
                }
                
                // Load profile image with premium extension and signature
                ivAdminProfile.loadProfileImage(userId, url, lastUpdate)
                
                // Update local session timestamp
                lastUpdate?.let { sessionManager.saveLastAdminImageUpdate(it) }
            }
        }
    }

    private fun loadPlatformStats() {
        // Students Count
        db.collection("users").whereEqualTo("role", "STUDENT").get().addOnSuccessListener { 
            totalStudentsText.text = it.size().toString()
        }

        // Caretakers Count
        db.collection("users").whereEqualTo("role", "CARETAKER").get().addOnSuccessListener { 
            verifiedCaretakersText.text = it.size().toString()
        }

        // Properties Count
        db.collection("properties").get().addOnSuccessListener { 
            totalPropertiesStatsText.text = it.size().toString()
        }

        // Pending Bookings (Simplified Pending count)
        db.collection("bookings").whereEqualTo("status", "pending").get().addOnSuccessListener { 
            pendingRequestsText.text = it.size().toString()
        }
    }
}

