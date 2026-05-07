package com.example.homehub.billing

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.homehub.R

class BookingDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BookingDetails"
    }

    private lateinit var booking: Booking
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_details)

        window.statusBarColor = resources.getColor(R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.navigationBarColor = resources.getColor(R.color.background)

        val bookingFromIntent = intent.getParcelableExtra<Booking>("BOOKING")
        if (bookingFromIntent == null) {
            Toast.makeText(this, "No booking data received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        booking = bookingFromIntent
        initializeViews()
        populateBookingInfo()
    }

    private fun initializeViews() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }

    private fun populateBookingInfo() {
        // Property image
        val propertyImage = findViewById<ImageView>(R.id.propertyImage)
        if (booking.propertyImage.isNotEmpty()) {
            propertyImage.load(booking.propertyImage) {
                crossfade(true)
                placeholder(R.drawable.ic_house_placeholder)
                error(R.drawable.ic_house_placeholder)
            }
        }

        // Status badge
        val statusBadge = findViewById<TextView>(R.id.statusBadge)
        statusBadge.text = booking.getStatusDisplay()

        // Property info
        findViewById<TextView>(R.id.propertyName).text = booking.propertyName
        findViewById<TextView>(R.id.propertyLocationText).text = "📍 ${booking.propertyLocation}"
        
        val caretakerText = findViewById<TextView>(R.id.caretakerNameText)
        if (booking.paymentType == "water") {
            caretakerText.text = "💧 Supplier: ${booking.caretakerName}"
            findViewById<View>(R.id.timelineCard).visibility = View.GONE
        } else {
            caretakerText.text = "🏠 Caretaker: ${booking.caretakerName}"
        }

        // Payment details
        findViewById<TextView>(R.id.amountText).text = booking.getFormattedAmount()
        val paymentStatusText = findViewById<TextView>(R.id.paymentStatusText)
        paymentStatusText.text = booking.getPaymentStatusDisplay()
        paymentStatusText.setTextColor(
            if (booking.isPaymentComplete()) getColor(R.color.success) else getColor(R.color.orange)
        )

        // Receipt
        if (booking.mpesaReceiptNumber.isNotEmpty()) {
            findViewById<View>(R.id.receiptRow).visibility = View.VISIBLE
            findViewById<TextView>(R.id.receiptText).text = booking.mpesaReceiptNumber
        }

        // Lease dates
        findViewById<TextView>(R.id.leaseStartText).text = dateFormat.format(booking.leaseStart)
        findViewById<TextView>(R.id.leaseEndText).text = dateFormat.format(booking.leaseEnd)
        findViewById<TextView>(R.id.bookingDateText).text = dateFormat.format(booking.bookingDate)

        // Cancel button (only for confirmed/active bookings, hide for water)
        val cancelButton = findViewById<MaterialButton>(R.id.cancelBookingButton)
        if (booking.paymentType != "water" && (booking.status == "confirmed" || booking.status == "active")) {
            cancelButton.visibility = View.VISIBLE
            cancelButton.setOnClickListener {
                showCancelConfirmation()
            }
        } else {
            cancelButton.visibility = View.GONE
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Booking")
            .setMessage("Are you sure you want to cancel your booking for ${booking.propertyName}?\n\nThis action cannot be undone.")
            .setPositiveButton("Cancel Booking") { _, _ ->
                cancelBooking()
            }
            .setNegativeButton("Keep Booking", null)
            .show()
    }

    private fun cancelBooking() {
        // Update booking status
        db.collection("bookings").document(booking.id)
            .update(
                mapOf(
                    "status" to "cancelled",
                    "updatedAt" to Date()
                )
            )
            .addOnSuccessListener {
                // Re-activate the property
                db.collection("properties").document(booking.propertyId)
                    .update(
                        mapOf(
                            "status" to "Active",
                            "available" to true,
                            "bookedBy" to "",
                            "updatedAt" to Date()
                        )
                    )

                Toast.makeText(this, "Booking cancelled successfully", Toast.LENGTH_SHORT).show()

                // Send notification to caretaker
                val notification = hashMapOf(
                    "userId" to booking.caretakerId,
                    "title" to "📢 Booking Cancelled",
                    "message" to "${booking.studentName} has cancelled their booking for ${booking.propertyName}",
                    "type" to "booking_cancelled",
                    "referenceId" to booking.id,
                    "isRead" to false,
                    "createdAt" to Date()
                )
                db.collection("notifications").add(notification)

                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to cancel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
