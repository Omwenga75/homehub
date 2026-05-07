package com.example.homehub.admin

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.example.homehub.databinding.ActivityVerificationDetailsBinding
import com.example.homehub.utils.NotificationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

class VerificationDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationDetailsBinding
    private lateinit var db: FirebaseFirestore
    private var applicantId: String? = null
    private var applicantName: String = "User"
    private var applicantPhone: String = ""
    
    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var isScrollingForward = true
    private var currentAnimator: android.animation.ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        applicantId = intent.getStringExtra("APPLICANT_ID")

        setupToolbar()
        loadVerificationDetails()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadVerificationDetails() {
        val uid = applicantId ?: return
        binding.progressOverlay.visibility = View.VISIBLE

        db.collection("verificationRequests").document(uid).get()
            .addOnSuccessListener { doc ->
                binding.progressOverlay.visibility = View.GONE
                if (doc.exists()) {
                    applicantName = doc.getString("fullName") ?: "User"
                    val role = doc.getString("role") ?: "User"
                    val status = doc.getString("status") ?: "PENDING"
                    
                    // Set personalized submission header
                    binding.tvSummaryHeader.text = "$applicantName has submitted details for review"

                    // Hide interaction buttons if already processed
                    if (status == "APPROVED" || status == "REJECTED") {
                        binding.interactionBar.visibility = View.GONE
                        
                        // Show a status note instead
                        binding.tvSummaryHeader.text = "$applicantName verification was ${status.lowercase()}"
                    }

                    setDetail(R.id.detailName, "FULL NAME", applicantName, R.drawable.baseline_person_outline_24)
                    setDetail(R.id.detailId, "ID NUMBER", doc.getString("idNumber") ?: "N/A", R.drawable.id)
                    
                    applicantPhone = doc.getString("phone") ?: ""
                    setDetail(R.id.detailPhone, "PHONE", if (applicantPhone.isEmpty()) "N/A" else applicantPhone, R.drawable.ic_phone)
                    setDetail(R.id.detailLocation, "RESIDENCE", doc.getString("location") ?: "N/A", R.drawable.ic_location)
                    
                    setDetail(R.id.detailBusiness, "BUSINESS", doc.getString("businessName") ?: "N/A", R.drawable.ic_admin)
                    setDetail(R.id.detailRole, "SYSTEM RANK", role.uppercase(), R.drawable.ic_verified)

                    val idFrontUrl = doc.getString("idFrontUrl")
                    val idBackUrl = doc.getString("idBackUrl")
                    val documentUrl = doc.getString("documentUrl")

                    val images = mutableListOf<String>()
                    if (!idFrontUrl.isNullOrEmpty()) images.add(idFrontUrl)
                    if (!idBackUrl.isNullOrEmpty()) images.add(idBackUrl)
                    if (!documentUrl.isNullOrEmpty()) images.add(documentUrl)
                    
                    if (images.isNotEmpty()) {
                        binding.cardDocuments.visibility = View.VISIBLE
                        val adapter = com.example.homehub.property.ImageSliderAdapter(images)
                        binding.imageViewPager.adapter = adapter
                        if (images.size > 1) startAutoScroll(images.size)
                    } else {
                        binding.cardDocuments.visibility = View.GONE
                    }
                } else {
                    showToast("Request not found")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                Log.e("VerificationDetails", "Error loading details: ${e.message}")
                if (e is com.google.firebase.firestore.FirebaseFirestoreException && 
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                    val intent = android.content.Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showToast("Error: ${e.message}")
                }
            }
    }

    private fun setDetail(layoutId: Int, label: String, value: String, iconRes: Int) {
        val root = findViewById<View>(layoutId)
        root.findViewById<TextView>(R.id.tvLabel).text = label
        root.findViewById<TextView>(R.id.tvValue).text = value
        root.findViewById<ImageView>(R.id.ivIcon).setImageResource(iconRes)
    }

    private fun setupListeners() {
        binding.btnApprove.setOnClickListener {
            showApprovalConfirmation()
        }

        binding.btnReject.setOnClickListener {
            showRejectionPrompt()
        }
    }

    private fun showApprovalConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Approve Account?")
            .setMessage("This will verify $applicantName and grant them full platform access.")
            .setPositiveButton("Approve") { _, _ -> approveVerification() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approveVerification() {
        val uid = applicantId ?: return
        binding.progressOverlay.visibility = View.VISIBLE

        val batch = db.batch()
        
        // Update user status
        val userRef = db.collection("users").document(uid)
        batch.update(userRef, "verificationStatus", "APPROVED")
        batch.update(userRef, "isVerified", true)
        batch.update(userRef, "fullName", applicantName)
        batch.update(userRef, "name", applicantName)
        batch.update(userRef, "username", applicantName)

        // Update request status
        val requestRef = db.collection("verificationRequests").document(uid)
        batch.update(requestRef, "status", "APPROVED")
        batch.update(requestRef, "reviewedAt", com.google.firebase.Timestamp.now())

        // Log result to activityLog
        val activityRef = db.collection("activityLog").document()
        val activity = hashMapOf(
            "title" to "$applicantName Approved",
            "description" to "Identity verification approved by admin",
            "activityType" to "CARETAKER_VERIFIED",
            "type" to "VERIFICATION_SUCCESS",
            "user" to applicantName,
            "userName" to applicantName,
            "userId" to uid,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        batch.set(activityRef, activity)

        // Propagate to verifiedCaretakers so student can see phone number
        val caretakerRef = db.collection("verifiedCaretakers").document(uid)
        val caretakerData = hashMapOf(
            "userId" to uid,
            "fullName" to applicantName,
            "phoneNumber" to applicantPhone,
            "isVerified" to true,
            "verificationDate" to System.currentTimeMillis(),
            "status" to "active"
        )
        batch.set(caretakerRef, caretakerData)

        batch.commit()
            .addOnSuccessListener {
                binding.progressOverlay.visibility = View.GONE
                NotificationManager.sendApplicationApprovedNotification(uid, applicantName)
                NotificationManager.removeVerificationRequestNotification(uid)
                showToast("$applicantName verified successfully")
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                showToast("Approval failed: ${e.message}")
            }
    }

    private fun showRejectionPrompt() {
        val input = android.widget.EditText(this)
        input.hint = "Enter reason for rejection"
        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, padding / 2, padding, 0)
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle("Reject Verification")
            .setView(container)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isNotEmpty()) {
                    rejectVerification(reason)
                } else {
                    showToast("Reason is required for rejection")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rejectVerification(reason: String) {
        val uid = applicantId ?: return
        binding.progressOverlay.visibility = View.VISIBLE

        val batch = db.batch()
        
        // Update user status
        val userRef = db.collection("users").document(uid)
        batch.update(userRef, "verificationStatus", "REJECTED")

        // Update request status
        val requestRef = db.collection("verificationRequests").document(uid)
        batch.update(requestRef, "status", "REJECTED")
        batch.update(requestRef, "rejectionReason", reason)
        batch.update(requestRef, "reviewedAt", com.google.firebase.Timestamp.now())

        // Log result to activityLog
        val activityRef = db.collection("activityLog").document()
        val activity = hashMapOf(
            "title" to "$applicantName Rejected",
            "description" to "Verification rejected: $reason",
            "activityType" to "CARETAKER_UNVERIFIED",
            "type" to "VERIFICATION_REJECTED",
            "user" to applicantName,
            "userName" to applicantName,
            "userId" to uid,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        batch.set(activityRef, activity)

        batch.commit()
            .addOnSuccessListener {
                binding.progressOverlay.visibility = View.GONE
                NotificationManager.sendApplicationRejectedNotification(uid, applicantName, reason)
                NotificationManager.removeVerificationRequestNotification(uid)
                showToast("Verification rejected")
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressOverlay.visibility = View.GONE
                showToast("Rejection failed: ${e.message}")
            }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun startAutoScroll(itemCount: Int) {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                val current = binding.imageViewPager.currentItem
                val next: Int
                
                if (isScrollingForward) {
                    if (current < itemCount - 1) {
                        next = current + 1
                    } else {
                        isScrollingForward = false
                        next = current - 1
                    }
                } else {
                    if (current > 0) {
                        next = current - 1
                    } else {
                        isScrollingForward = true
                        next = current + 1
                    }
                }
                
                binding.imageViewPager.setCurrentItemSlow(next, 1300)
                autoScrollHandler.postDelayed(this, 8000)
            }
        }
        autoScrollHandler.postDelayed(autoScrollRunnable!!, 8000)
    }

    private fun androidx.viewpager2.widget.ViewPager2.setCurrentItemSlow(
        item: Int,
        duration: Long,
        interpolator: android.view.animation.Interpolator = android.view.animation.AccelerateDecelerateInterpolator()
    ) {
        val pxToDrag = width * (item - currentItem)
        val animator = android.animation.ValueAnimator.ofInt(0, pxToDrag)
        currentAnimator = animator
        var previousValue = 0
        
        animator.addUpdateListener { valueAnimator ->
            val currentValue = valueAnimator.animatedValue as Int
            val currentPxToDrag = (currentValue - previousValue).toFloat()
            fakeDragBy(-currentPxToDrag)
            previousValue = currentValue
        }
        
        animator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) { beginFakeDrag() }
            override fun onAnimationEnd(animation: android.animation.Animator) { if (isFakeDragging) endFakeDrag() }
            override fun onAnimationCancel(animation: android.animation.Animator) { if (isFakeDragging) endFakeDrag() }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        animator.interpolator = interpolator
        animator.duration = duration
        animator.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentAnimator?.cancel()
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
    }
}
