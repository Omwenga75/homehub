package com.example.homehub.admin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.homehub.caretaker.CaretakerApplication
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.homehub.utils.EncryptionUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.example.homehub.admin.AdminSessionManager
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import com.example.homehub.R
import com.example.homehub.admin.CaretakerApplicationViewModel
import com.google.firebase.firestore.FirebaseFirestore

class ApplicationDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var application: CaretakerApplication
    private lateinit var viewModel: CaretakerApplicationViewModel
    private var documentId: String = ""
    private var firestoreListener: ListenerRegistration? = null

    // Views (v8 Namespace)
    private lateinit var progressBar: ProgressBar
    private lateinit var approveButton: MaterialButton
    private lateinit var rejectButton: MaterialButton
    private lateinit var notesEditText: EditText
    private lateinit var rejectionReasonEditText: EditText
    private lateinit var rejectionReasonLayout: LinearLayout
    private lateinit var rejectionReasonText: TextView
    private lateinit var statusText: TextView
    private lateinit var amenitiesChipGroup: ChipGroup
    private lateinit var noAmenitiesText: TextView
    private lateinit var submitRejectionButton: Button
    private lateinit var cancelRejectionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_details_v8)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[CaretakerApplicationViewModel::class.java]

        // Get application from intent
        application = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("application", CaretakerApplication::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("application")
        } ?: run {
            Toast.makeText(this, "Error loading application", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        documentId = intent.getStringExtra("documentId") ?: application.documentId ?: ""

        if (documentId.isEmpty()) {
            showErrorDialog("Document ID not found. Cannot proceed.")
            return
        }

        initializeViews()
        setupObservers()
        setupClickListeners()
        setupRealTimeListener()
        
        // Handle Back Button
        findViewById<View>(R.id.id_v8_backCard)?.setOnClickListener { finish() }
        findViewById<View>(R.id.id_v8_backButton)?.setOnClickListener { finish() }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                approveButton.isEnabled = false
                rejectButton.isEnabled = false
                submitRejectionButton.isEnabled = false
            } else {
                if (application.status == CaretakerApplication.STATUS_PENDING) {
                    approveButton.isEnabled = true
                    rejectButton.isEnabled = true
                }
            }
        }

        viewModel.statusUpdateSuccess.observe(this) { success ->
            if (success) {
                showToast("Status updated successfully!")
                Handler().postDelayed({
                    setResult(RESULT_OK)
                    finish()
                }, 1500)
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                showToast(it)
                if (application.status == CaretakerApplication.STATUS_PENDING) {
                    approveButton.isEnabled = true
                    rejectButton.isEnabled = true
                }
                submitRejectionButton.isEnabled = true
            }
        }
    }

    private fun setupRealTimeListener() {
        firestoreListener?.remove()
        firestoreListener = db.collection("caretakerApplications")
            .document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null) {
                    Log.e("ApplicationDetails", "Firestore error: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = android.content.Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val updatedApplication = CaretakerApplication.fromDocument(documentId, snapshot.data!!)
                        application = updatedApplication
                        populateApplicationData()
                    } catch (e: Exception) {
                        Log.e("ApplicationDetails", "Error parsing document: ${e.message}")
                    }
                }
            }
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.id_v8_progressBar)
        approveButton = findViewById(R.id.id_v8_approveButton)
        rejectButton = findViewById(R.id.id_v8_rejectButton)
        notesEditText = findViewById(R.id.id_v8_notesEditText)
        rejectionReasonEditText = findViewById(R.id.id_v8_rejectionReasonEditText)
        rejectionReasonLayout = findViewById(R.id.id_v8_rejectionReasonLayout)
        rejectionReasonText = findViewById(R.id.id_v8_rejectionReasonText)
        statusText = findViewById(R.id.id_v8_statusText)
        amenitiesChipGroup = findViewById(R.id.id_v8_amenitiesChipGroup)
        noAmenitiesText = findViewById(R.id.id_v8_noAmenitiesText)
        submitRejectionButton = findViewById(R.id.id_v8_submitRejectionButton)
        cancelRejectionButton = findViewById(R.id.id_v8_cancelRejectionButton)

        rejectionReasonLayout.visibility = View.GONE
        notesEditText.setText(application.notes)
        populateApplicationData()
    }

    private fun populateApplicationData() {
        // Header
        findViewById<TextView>(R.id.id_v8_headerName).text = application.fullName
        findViewById<TextView>(R.id.id_v8_headerEmail).text = application.email

        // Personal Section
        setInfoRow(R.id.id_v8_rowName, "Full Name", application.fullName, R.drawable.person)
        setInfoRow(R.id.id_v8_rowPhone, "Phone Number", application.phone, R.drawable.call)
        
        val decryptedId = if (application.idNumberEncrypted.isNotEmpty()) {
            try { EncryptionUtils.decrypt(application.idNumberEncrypted) } catch (e: Exception) { "Encryption Error" }
        } else { "Not Available" }
        setInfoRow(R.id.id_v8_rowIdNumber, "ID Number", decryptedId, R.drawable.id)
        setInfoRow(R.id.id_v8_rowNationality, "Nationality", application.nationality, R.drawable.baseline_flag_24)

        // KYC Previews
        val selfiePreview = findViewById<ImageView>(R.id.id_v8_selfieImagePreview)
        val idFrontPreview = findViewById<ImageView>(R.id.id_v8_idCardImagePreview)
        val idBackPreview = findViewById<ImageView>(R.id.id_v8_idCardBackPreview)

        if (!isFinishing && !isDestroyed) {
            Glide.with(this).load(application.selfieUrl).placeholder(R.drawable.placeholder_image).into(selfiePreview)
            Glide.with(this).load(application.idCardUrl).placeholder(R.drawable.placeholder_image).into(idFrontPreview)
            Glide.with(this).load(application.idCardBackUrl).placeholder(R.drawable.placeholder_image).into(idBackPreview)
        }

        // GPS & Identity
        val gpsText = findViewById<TextView>(R.id.id_v8_gpsCoordinatesText)
        gpsText.text = if (application.latitude != null) "GPS: ${application.latitude}, ${application.longitude} (Verified)" else "GPS: Not Captuted"
        gpsText.setTextColor(ContextCompat.getColor(this, if (application.latitude != null) R.color.primary else R.color.red))

        val securityText = findViewById<TextView>(R.id.id_v8_securityStatusText)
        securityText.text = if (application.isSecure) "Identity: Verified Sequence Complete" else "Identity: Standard Verification"
        securityText.setTextColor(ContextCompat.getColor(this, if (application.isSecure) R.color.green else R.color.orange))

        // Property Section
        setInfoRow(R.id.id_v8_rowPropertyType, "Property Type", application.propertyType, R.drawable.ic_properties)
        setInfoRow(R.id.id_v8_rowLocation, "Location", application.propertyLocation, R.drawable.ic_location)
        setInfoRow(R.id.id_v8_rowRooms, "Rooms", application.getRoomText(), R.drawable.bd)
        setInfoRow(R.id.id_v8_rowPrice, "Rent / Mo", application.getFormattedPrice(), R.drawable.ic_trending_up)
        findViewById<TextView>(R.id.id_v8_propertyDescriptionText).text = application.propertyDescription

        // Amenities
        populateAmenities()

        // Timeline
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        setInfoRow(R.id.id_v8_rowApplicationDate, "Applied Date", dateFormat.format(Date(application.applicationDate)), R.drawable.ic_calendar)

        if (application.reviewedAt > 0) {
            setInfoRow(R.id.id_v8_rowReviewedBy, "Reviewed By", application.reviewedBy, R.drawable.person)
            setInfoRow(R.id.id_v8_rowReviewedAt, "Reviewed On", dateFormat.format(Date(application.reviewedAt)), R.drawable.ic_calendar)
            findViewById<View>(R.id.id_v8_rowReviewedBy).visibility = View.VISIBLE
            findViewById<View>(R.id.id_v8_rowReviewedAt).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.id_v8_rowReviewedBy).visibility = View.GONE
            findViewById<View>(R.id.id_v8_rowReviewedAt).visibility = View.GONE
        }

        // Rejection Insight
        if (!application.rejectionReason.isNullOrEmpty()) {
            rejectionReasonText.text = application.rejectionReason
            findViewById<View>(R.id.id_v8_rejectionReasonSection).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.id_v8_rejectionReasonSection).visibility = View.GONE
        }

        updateStatusUI()
    }

    private fun populateAmenities() {
        amenitiesChipGroup.removeAllViews()
        if (application.amenities.isEmpty()) {
            noAmenitiesText.visibility = View.VISIBLE
            amenitiesChipGroup.visibility = View.GONE
            return
        }
        noAmenitiesText.visibility = View.GONE
        amenitiesChipGroup.visibility = View.VISIBLE
        for (amenity in application.amenities) {
            amenitiesChipGroup.addView(createAmenityChip(amenity))
        }
    }

    private fun createAmenityChip(amenity: String): Chip {
        return Chip(this).apply {
            text = amenity
            isCheckable = false
            chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.amenity_chip_background)
            setTextColor(ContextCompat.getColor(context, R.color.primary_dark))
            chipStrokeWidth = 0f
            chipCornerRadius = 14f
            setChipIconResource(R.drawable.ic_check_circle)
            chipIconTint = ContextCompat.getColorStateList(context, R.color.green)
            chipIconSize = 16f
            setEnsureMinTouchTargetSize(false)
        }
    }

    private fun updateStatusUI() {
        val statusBadge = findViewById<View>(R.id.id_v8_statusBadgeLayout)
        val statusIndicator = findViewById<View>(R.id.id_v8_statusIndicator)

        when (application.status) {
            CaretakerApplication.STATUS_PENDING -> {
                statusText.text = "PENDING"
                statusBadge.setBackgroundResource(R.drawable.bg_review_status_pending)
                statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
                approveButton.visibility = View.VISIBLE
                rejectButton.visibility = View.VISIBLE
            }
            CaretakerApplication.STATUS_APPROVED -> {
                statusText.text = "APPROVED"
                statusBadge.setBackgroundResource(R.drawable.bg_review_status_approved)
                statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
                approveButton.visibility = View.GONE
                rejectButton.visibility = View.GONE
            }
            CaretakerApplication.STATUS_REJECTED -> {
                statusText.text = "REJECTED"
                statusBadge.setBackgroundResource(R.drawable.bg_review_status_rejected)
                statusIndicator.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
                approveButton.visibility = View.GONE
                rejectButton.visibility = View.GONE
            }
        }
    }

    private fun setInfoRow(viewId: Int, label: String, value: String?, iconRes: Int) {
        val row = findViewById<View>(viewId) ?: return
        row.findViewById<TextView>(R.id.rowLabel).text = label
        row.findViewById<TextView>(R.id.rowValue).text = value ?: "N/A"
        row.findViewById<ImageView>(R.id.rowIcon).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@ApplicationDetailsActivity, R.color.primary_light))
        }
    }

    private fun setupClickListeners() {
        approveButton.setOnClickListener { showApproveConfirmation() }
        rejectButton.setOnClickListener { toggleRejectionReason() }
        submitRejectionButton.setOnClickListener {
            val reason = rejectionReasonEditText.text.toString().trim()
            if (reason.isEmpty()) {
                rejectionReasonEditText.error = "Reason required"
                return@setOnClickListener
            }
            showRejectConfirmation(reason)
        }
        cancelRejectionButton.setOnClickListener { toggleRejectionReason() }
        notesEditText.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveNotesIfChanged() }
    }

    private fun showApproveConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Approve Application")
            .setMessage("Confirm approval for ${application.fullName}?")
            .setPositiveButton("Approve") { _, _ -> approveApplication() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRejectConfirmation(reason: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reject Application")
            .setMessage("Confirm rejection for ${application.fullName}?")
            .setPositiveButton("Reject") { _, _ -> rejectApplication(reason) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleRejectionReason() {
        if (rejectionReasonLayout.visibility == View.VISIBLE) {
            rejectionReasonLayout.visibility = View.GONE
            rejectButton.visibility = View.VISIBLE
            approveButton.visibility = View.VISIBLE
        } else {
            rejectionReasonLayout.visibility = View.VISIBLE
            rejectButton.visibility = View.GONE
            approveButton.visibility = View.GONE
            rejectionReasonEditText.requestFocus()
        }
    }

    private fun approveApplication() {
        val reviewedBy = AdminSessionManager(this).getAdminName()
        viewModel.approveApplication(documentId, reviewedBy)
        createVerifiedCaretakerRecord(notesEditText.text.toString().trim())
        
        db.collection("users").document(application.userId)
            .update("verificationStatus", "APPROVED", "isVerified", true)
    }

    private fun createVerifiedCaretakerRecord(notes: String) {
        val hostData = hashMapOf(
            "userId" to application.userId,
            "fullName" to application.fullName,
            "phoneNumber" to application.phone,
            "isVerified" to true,
            "verificationDate" to System.currentTimeMillis(),
            "status" to "active",
            "notes" to notes
        )
        db.collection("verifiedCaretakers").document(application.userId).set(hostData)
    }

    private fun rejectApplication(reason: String) {
        val reviewedBy = AdminSessionManager(this).getAdminName()
        viewModel.rejectApplication(documentId, reviewedBy, reason)
        db.collection("users").document(application.userId).update("verificationStatus", "REJECTED")
        rejectionReasonLayout.visibility = View.GONE
    }

    private fun saveNotesIfChanged() {
        val notes = notesEditText.text.toString().trim()
        if (notes != application.notes) {
            db.collection("caretakerApplications").document(documentId).update("notes", notes)
                .addOnSuccessListener { application.notes = notes }
        }
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this).setTitle("Error").setMessage(message).setPositiveButton("OK") { _, _ -> finish() }.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
    }
}
