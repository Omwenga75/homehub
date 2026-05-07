package com.example.homehub.student

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.example.homehub.billing.Booking
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.widget.LinearLayout
import android.os.CountDownTimer
import android.content.Intent
import com.example.homehub.billing.PaymentDetailsActivity
import com.example.homehub.billing.BookingCleanupManager
import com.example.homehub.supplier.SupplierOrder

class StudentMyRoomActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentBooking: Booking? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_my_room)
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener { finish() }

        val btnPayRent = findViewById<MaterialButton>(R.id.btnPayRent)
        btnPayRent.setOnClickListener {
            val booking = currentBooking ?: return@setOnClickListener
            val intent = Intent(this, PaymentDetailsActivity::class.java)
            intent.putExtra("PROPERTY_ID", booking.propertyId)
            intent.putExtra("EXISTING_BOOKING_ID", booking.id)
            startActivity(intent)
        }

        val btnLeaveRoom = findViewById<MaterialButton>(R.id.btnLeaveRoom)
        btnLeaveRoom.setOnClickListener {
            showReasonDialog()
        }

        val btnCheckIn = findViewById<MaterialButton>(R.id.btnCheckIn)
        btnCheckIn.setOnClickListener {
            performCheckIn()
        }
        
        loadStudentRoomData()
    }

    private fun loadStudentRoomData() {
        val userId = auth.currentUser?.uid ?: return
        
        // INSTANT LOADING: Show cached data immediately to prevent empty state flicker
        if (com.example.homehub.utils.GlobalDataCache.isBookingsCacheFresh()) {
            val cachedBooking = com.example.homehub.utils.GlobalDataCache.getBookings()
                .mapNotNull { Booking.fromDocument(it) }
                .filter { it.studentId == userId && it.isValidForMyRoom() }
                .maxByOrNull { it.createdAt }

            if (cachedBooking != null) {
                currentBooking = cachedBooking
                findViewById<View>(R.id.contentScrollView).visibility = View.VISIBLE
                findViewById<View>(R.id.bottomActionBar).visibility = View.VISIBLE
                findViewById<View>(R.id.emptyStateLayout).visibility = View.GONE
                findViewById<View>(R.id.loadingProgressBar).visibility = View.GONE
                bindBookingData(cachedBooking)
            }
        }
        
        db.collection("bookings")
            .whereEqualTo("studentId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (!snapshot.isEmpty) {
                    val activeBooking = snapshot.documents.mapNotNull { 
                        val data = it.data?.toMutableMap() ?: return@mapNotNull null
                        data["id"] = it.id
                        val b = Booking.fromDocument(data)
                        if (b != null && b.isValidForMyRoom()) b else null
                    }.maxByOrNull { it.createdAt }

                    if (activeBooking != null) {
                        currentBooking = activeBooking
                        findViewById<View>(R.id.contentScrollView).visibility = View.VISIBLE
                        findViewById<View>(R.id.bottomActionBar).visibility = View.VISIBLE
                        findViewById<View>(R.id.emptyStateLayout).visibility = View.GONE
                        findViewById<View>(R.id.loadingProgressBar).visibility = View.GONE
                        bindBookingData(activeBooking)
                        checkPendingRequest(activeBooking.id)
                        loadPaymentHistory()
                    } else {
                        showEmptyState()
                    }
                } else {
                    showEmptyState()
                    loadPaymentHistory()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load room details.", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
    }

    private fun checkPendingRequest(bookingId: String) {
        val btnLeaveRoom = findViewById<MaterialButton>(R.id.btnLeaveRoom)
        db.collection("leave_requests")
            .whereEqualTo("bookingId", bookingId)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    btnLeaveRoom.text = "Request Pending"
                    btnLeaveRoom.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.textSecondary, null))
                    btnLeaveRoom.isEnabled = false
                    
                    // GATING: Also disable pay rent button if they are planning to leave
                    val btnPayRent = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPayRent)
                    btnPayRent.isEnabled = false
                    btnPayRent.alpha = 0.5f // Grayed out look
                }
            }
    }

    private fun showReasonDialog() {
        val booking = currentBooking ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_leave_room_reason, null)
        val etReason = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReason)
        val tilReason = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilReason)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Submit") { dialog, _ ->
                val reason = etReason.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Please provide a reason", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                submitLeaveRequest(booking, reason)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitLeaveRequest(booking: Booking, reason: String) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: "Student"
        val userEmail = auth.currentUser?.email ?: ""
        
        val btnLeaveRoom = findViewById<MaterialButton>(R.id.btnLeaveRoom)
        btnLeaveRoom.isEnabled = false
        btnLeaveRoom.text = "Submitting..."

        db.collection("properties").document(booking.propertyId).get()
            .addOnSuccessListener { propertyDoc ->
                val trueCaretakerId = propertyDoc.getString("caretakerId")?.takeIf { it.isNotEmpty() } ?: booking.caretakerId
                
                val request = hashMapOf(
                    "studentId" to userId,
                    "studentName" to userName,
                    "studentEmail" to userEmail,
                    "bookingId" to booking.id,
                    "propertyId" to booking.propertyId,
                    "propertyName" to booking.propertyName,
                    "caretakerId" to trueCaretakerId,
                    "reason" to reason,
                    "status" to "PENDING",
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                db.collection("leave_requests").add(request)
                    .addOnSuccessListener {
                        btnLeaveRoom.text = "Pending Approval"
                        btnLeaveRoom.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.textSecondary, null))

                        if (trueCaretakerId.isNotEmpty()) {
                            // Use centralized manager for consistent priority and naming
                            com.example.homehub.utils.NotificationManager.sendVacateRequestNotification(
                                trueCaretakerId,
                                userName,
                                booking.propertyName,
                                booking.roomNumber ?: "N/A",
                                booking.id
                            )
                        }

                        val studentNotification = hashMapOf(
                            "userId" to userId,
                            "title" to "Vacation Request Received",
                            "message" to "Your vacation request has been received. You'll be notified within 24 hours.",
                            "type" to "INFO",
                            "notificationType" to "LEAVE_REQUEST_STATUS",
                            "priority" to "NORMAL",
                            "relatedId" to booking.id,
                            "isRead" to false,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("notifications").add(studentNotification)

                        Toast.makeText(this, "Vacation request submitted successfully.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        btnLeaveRoom.isEnabled = true
                        btnLeaveRoom.text = "Leave Room"
                        Toast.makeText(this, "Failed to submit request. Please try again.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                btnLeaveRoom.isEnabled = true
                btnLeaveRoom.text = "Leave Room"
                Toast.makeText(this, "Failed to locate property details.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performCheckIn() {
        val booking = currentBooking ?: return
        
        // If payment is pending, redirect to payment
        if (booking.paymentStatus == "pending_deferred") {
            val intent = Intent(this, PaymentDetailsActivity::class.java)
            intent.putExtra("PROPERTY_ID", booking.propertyId)
            intent.putExtra("EXISTING_BOOKING_ID", booking.id)
            startActivity(intent)
            return
        }

        val btnCheckIn = findViewById<MaterialButton>(R.id.btnCheckIn)
        btnCheckIn.isEnabled = false
        btnCheckIn.text = "Checking In..."

        db.collection("bookings").document(booking.id)
            .update("isCheckedIn", true)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                Toast.makeText(this, "Check-in successful! Welcome to ${booking.propertyName}.", Toast.LENGTH_LONG).show()
                currentBooking?.let {
                    val updated = it.copy(isCheckedIn = true)
                    currentBooking = updated
                    bindBookingData(updated)
                }
            }
            .addOnFailureListener {
                btnCheckIn.isEnabled = true
                btnCheckIn.text = "Check In"
                Toast.makeText(this, "Check-in failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }



    private fun showEmptyState() {
        findViewById<View>(R.id.contentScrollView).visibility = View.GONE
        findViewById<View>(R.id.bottomActionBar).visibility = View.GONE
        findViewById<View>(R.id.loadingProgressBar).visibility = View.GONE
        findViewById<View>(R.id.emptyStateLayout).visibility = View.VISIBLE
    }

    private fun loadPaymentHistory() {
        val userId = auth.currentUser?.uid ?: return
        val container = findViewById<LinearLayout>(R.id.paymentHistoryContainer) ?: return
        val emptyText = findViewById<TextView>(R.id.tvNoPaymentHistory) ?: return

        db.collection("bookings")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("paymentStatus", "completed")
            .get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                container.removeAllViews()
                container.addView(emptyText)
                emptyText.text = "No payment history"
                
                if (snapshot.isEmpty) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    emptyText.visibility = View.GONE
                    // Sort in memory to avoid needing a Firestore index
                    val sortedDocs = snapshot.documents.sortedByDescending { 
                        it.getTimestamp("createdAt")?.toDate() ?: Date(0)
                    }.take(5)

                    sortedDocs.forEachIndexed { index, doc ->
                        val data = doc.data?.toMutableMap() ?: return@forEachIndexed
                        data["id"] = doc.id
                        val booking = Booking.fromDocument(data)
                        addPaymentHistoryItem(container, booking)
                        
                        // Add divider if not last item
                        if (index < sortedDocs.size - 1) {
                            val divider = View(this)
                            divider.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * resources.displayMetrics.density).toInt()
                            ).apply {
                                setMargins(0, (4 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
                            }
                            divider.setBackgroundColor(android.graphics.Color.parseColor("#F1F5F9"))
                            container.addView(divider)
                        }
                    }
                }
            }
            .addOnFailureListener {
                emptyText.visibility = View.VISIBLE
                emptyText.text = "No payment history"
            }
    }

    private fun addPaymentHistoryItem(container: LinearLayout, booking: Booking) {
        val itemView = layoutInflater.inflate(R.layout.item_payment_row_simple, null) ?: return
        
        val tvLabel = itemView.findViewById<TextView>(R.id.tvPaymentLabel)
        val tvSublabel = itemView.findViewById<TextView>(R.id.tvPaymentSublabel)
        val tvAmount = itemView.findViewById<TextView>(R.id.tvPaymentAmount)
        val ivIcon = itemView.findViewById<ImageView>(R.id.ivPaymentIcon)

        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = if (booking.createdAt != null) sdf.format(booking.createdAt) else "Unknown Date"

        tvLabel?.text = if (booking.paymentType == "water") {
            "Water Order: ${booking.propertyName}"
        } else {
            // Safe check for date formatting
            val month = if (booking.createdAt != null) SimpleDateFormat("MMM", Locale.getDefault()).format(booking.createdAt) else "???"
            "Rent Payment - $month"
        }
        
        tvSublabel?.text = "$dateStr • M-Pesa"
        
        if (ivIcon != null) {
            if (booking.paymentType == "water") {
                ivIcon.setImageResource(R.drawable.ic_water_drop)
                ivIcon.setColorFilter(getColor(R.color.themeColor))
            } else {
                ivIcon.setImageResource(R.drawable.ic_check_circle)
                ivIcon.setColorFilter(getColor(R.color.green_500))
            }
        }

        tvAmount?.text = booking.getFormattedAmount() ?: "KSh 0"
        container.addView(itemView)
    }

    private fun bindBookingData(booking: Booking) {
        if (isFinishing || isDestroyed) return

        try {
            findViewById<TextView>(R.id.tvPropertyName)?.text = booking.propertyName.takeIf { it.isNotEmpty() } ?: "Unknown Property"
            findViewById<TextView>(R.id.tvLocation)?.text = booking.propertyLocation.takeIf { it.isNotBlank() } ?: "Location Unavailable"
            findViewById<TextView>(R.id.tvRentAmount)?.text = "${booking.getFormattedAmount() ?: "KSh 0"} - Rent + Deposit"

            val btnPayRent = findViewById<MaterialButton>(R.id.btnPayRent)
            val btnCheckIn = findViewById<MaterialButton>(R.id.btnCheckIn)
            val btnLeaveRoom = findViewById<MaterialButton>(R.id.btnLeaveRoom)

            if (booking.isPendingCheckIn()) {
                btnCheckIn?.visibility = View.VISIBLE
                btnPayRent?.visibility = View.GONE
                btnLeaveRoom?.visibility = View.GONE
                btnCheckIn?.text = "Check In"
            } else if (booking.paymentStatus == "pending_deferred") {
                // Check expiry first
                if (checkAndHandleExpiry(booking)) return
                
                btnCheckIn?.visibility = View.VISIBLE
                btnCheckIn?.text = "Proceed to Payment"
                btnPayRent?.visibility = View.GONE
                btnLeaveRoom?.visibility = View.GONE
                
                startPaymentCountdown(booking)
            } else {
                btnCheckIn?.visibility = View.GONE
                btnPayRent?.visibility = View.VISIBLE
                btnLeaveRoom?.visibility = View.VISIBLE
            }

            val ivRoomImage = findViewById<ImageView>(R.id.ivRoomImage)
            if (ivRoomImage != null && !booking.propertyImage.isNullOrBlank()) {
                if (!isFinishing && !isDestroyed) {
                    Glide.with(this)
                        .load(booking.propertyImage)
                        .placeholder(R.drawable.hs2)
                        .error(R.drawable.hs2)
                        .into(ivRoomImage)
                }
            } else if (ivRoomImage != null) {
                ivRoomImage.setImageResource(R.drawable.hs2)
            }

            if (booking.createdAt != null) {
                calculateAndBindDates(booking.createdAt)
            }
            fetchPropertyForNextPaymentAmount(booking)
        } catch (e: Exception) {
            Log.e("StudentMyRoom", "❌ Error binding booking data: ${e.message}", e)
        }
    }

    private fun fetchPropertyForNextPaymentAmount(booking: Booking) {
        // Fetch property to get current rent amount for next payment calculation
        db.collection("properties").document(booking.propertyId).get()
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed || !document.exists()) return@addOnSuccessListener

                val propertyData = document.data ?: return@addOnSuccessListener
                val property = com.example.homehub.property.Property.fromDocument(propertyData.apply { this["id"] = document.id })

                // Calculate next payment amount (rent only, no deposit)
                val nextPaymentAmount = if (booking.roomTypeId.isNotEmpty()) {
                    // Find the matching room type
                    property.roomTypes.find { it.id == booking.roomTypeId }?.price ?: property.priceValue
                } else {
                    property.priceValue
                }

                // Update the next payment display
                val tvNextPaymentAmount = findViewById<TextView>(R.id.tvNextPaymentAmount)
                if (tvNextPaymentAmount != null) {
                    tvNextPaymentAmount.text = "KSh ${String.format("%,.0f", nextPaymentAmount)}"
                }
            }
            .addOnFailureListener {
                // Fallback: use booking amount minus estimated deposit (not ideal but better than nothing)
                val estimatedDeposit = booking.amount * 0.5 // Rough estimate
                val nextPaymentAmount = (booking.amount - estimatedDeposit).coerceAtLeast(booking.amount * 0.5)

                val tvNextPaymentAmount = findViewById<TextView>(R.id.tvNextPaymentAmount)
                if (tvNextPaymentAmount != null) {
                    tvNextPaymentAmount.text = "KSh ${String.format("%,.0f", nextPaymentAmount)}"
                }
            }
    }

    private fun calculateAndBindDates(createdAt: Date) {
        val dueDateCal = Calendar.getInstance()
        dueDateCal.time = createdAt
        dueDateCal.add(Calendar.DAY_OF_YEAR, 30) // Baseline rent cycle
        
        val todayCal = Calendar.getInstance() // Gets 2026 context contextually
        
        // Push dates into the future seamlessly if looking at old legacy database bookings
        while (dueDateCal.time.before(todayCal.time)) {
            dueDateCal.add(Calendar.DAY_OF_YEAR, 30)
        }
        
        val dueDate = dueDateCal.time
        
        // Render formatting
        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tvDueDate).text = format.format(dueDate)

        // Generate tracking math
        val diffInMillis = dueDate.time - todayCal.time.time
        val daysLeft = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS).coerceAtLeast(0).toInt()

        findViewById<TextView>(R.id.tvDaysLeftNum).text = daysLeft.toString()
        val pbDaysLeft = findViewById<ProgressBar>(R.id.pbDaysLeft)
        pbDaysLeft.max = 30
        pbDaysLeft.progress = daysLeft.coerceAtMost(30)
        
        // Disable "Pay Rent" button if rent is not due (Proposed: > 7 days left)
        val btnPayRent = findViewById<MaterialButton>(R.id.btnPayRent)
        if (daysLeft > 7) {
            btnPayRent.isEnabled = false
            btnPayRent.alpha = 0.5f // Grayed out look
        } else {
            // Only re-enable if there's no pending leave request (handled separately but for safety)
            btnPayRent.isEnabled = true
            btnPayRent.alpha = 1.0f
        }
    }

    private fun startPaymentCountdown(booking: Booking) {
        val deadline = booking.paymentDeadline ?: return
        val now = Date().time
        val diff = deadline.time - now

        if (diff <= 0) {
            checkAndHandleExpiry(booking)
            return
        }

        findViewById<View>(R.id.cardPaymentDeadline).visibility = View.VISIBLE
        val tvTimer = findViewById<TextView>(R.id.tvPaymentDeadlineTimer)
        val tvAmount = findViewById<TextView>(R.id.tvPaymentRequiredAmount)

        // Calculate the payment amount (rent only for deferred payments)
        val paymentAmount = if (booking.roomTypeId.isNotEmpty()) {
            // For deferred payments, we need to fetch the property to get current rent
            // For now, estimate based on booking amount (assuming first booking included deposit)
            val estimatedDeposit = booking.amount * 0.4 // Estimate deposit as 40% of first payment
            (booking.amount - estimatedDeposit).coerceAtLeast(booking.amount * 0.6)
        } else {
            booking.amount // Fallback
        }

        tvAmount?.text = "Amount: KSh ${String.format("%,.0f", paymentAmount)}"

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(diff, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvTimer.text = String.format("Expires in: %02dh %02dm %02ds", hours, minutes, seconds)
            }

            override fun onFinish() {
                checkAndHandleExpiry(booking)
            }
        }.start()
    }

    private fun checkAndHandleExpiry(booking: Booking): Boolean {
        val deadline = booking.paymentDeadline ?: return false
        if (deadline.before(Date())) {
            // Expired!
            BookingCleanupManager.checkAndCancelIfExpired(booking) { success ->
                if (success) {
                    runOnUiThread {
                        showEmptyState()
                        Toast.makeText(this, "Reservation expired and room is now available.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return true
        }
        return false
    }

    private fun cancelExpiredBooking(booking: Booking) {
        BookingCleanupManager.checkAndCancelIfExpired(booking) { success ->
            if (success) {
                runOnUiThread {
                    showEmptyState()
                    Toast.makeText(this, "Reservation cancelled and room is now available.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
