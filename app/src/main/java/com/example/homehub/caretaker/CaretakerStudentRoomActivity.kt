package com.example.homehub.caretaker

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class CaretakerStudentRoomActivity : AppCompatActivity() {

    private var daysLeft = 21
    private val db = FirebaseFirestore.getInstance()
    private var roomId: String = ""
    private var propertyId: String = ""
    private var roomNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caretaker_student_room)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        // Populate intent data
        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        propertyId = intent.getStringExtra("PROPERTY_ID") ?: ""
        roomNumber = intent.getStringExtra("ROOM_NO") ?: "Unit B4"
        findViewById<TextView>(R.id.tvOccupiedUnit).text = "Room: $roomNumber"

        // For demo purposes, we randomly simulate a leave request, or pass it via intent.
        val requestedLeave = intent.getBooleanExtra("REQUESTED_LEAVE", true)
        
        val cardLeaveRequest: MaterialCardView = findViewById(R.id.cardLeaveRequest)
        val dockLeaveDecision: MaterialCardView = findViewById(R.id.dockLeaveDecision)

        if (requestedLeave) {
            cardLeaveRequest.visibility = View.VISIBLE
            dockLeaveDecision.visibility = View.VISIBLE
        }

        findViewById<MaterialButton>(R.id.btnApprove).setOnClickListener {
            approveCheckout()
        }

        findViewById<MaterialButton>(R.id.btnReject).setOnClickListener {
            rejectCheckout()
        }

        findViewById<MaterialButton>(R.id.btnExtendRent).setOnClickListener {
            showExtendRentDialog()
        }
        
        findViewById<ImageButton>(R.id.btnCallStudent).setOnClickListener {
            Toast.makeText(this, "Initiating call...", Toast.LENGTH_SHORT).show()
        }

        loadStudentData()
    }

    private fun loadStudentData() {
        if (propertyId.isBlank() || roomNumber.isBlank()) return

        db.collection("bookings")
            .whereEqualTo("propertyId", propertyId)
            .whereEqualTo("roomNumber", roomNumber)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val booking = snapshot.documents.first()
                    val studentName = booking.getString("studentName") ?: "Student"
                    val studentId = booking.getString("studentId") ?: ""
                    
                    findViewById<TextView>(R.id.tvStudentName).text = studentName
                    
                    // Fetch user specific data for DP
                    if (studentId.isNotEmpty()) {
                        db.collection("users").document(studentId).get()
                            .addOnSuccessListener { userDoc ->
                                val avatarUrl = userDoc.getString("profileImageUrl") 
                                    ?: userDoc.getString("profilePictureUrl")
                                val dpName = userDoc.getString("fullName") ?: studentName
                                
                                val ivProfile: android.widget.ImageView = findViewById(R.id.ivStudentProfile)
                                com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(
                                    ivProfile, 
                                    dpName, 
                                    avatarUrl, 
                                    studentId
                                )
                            }
                    } else {
                        val ivProfile: android.widget.ImageView = findViewById(R.id.ivStudentProfile)
                        com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(
                            ivProfile, 
                            studentName
                        )
                    }
                }
            }
    }

    private fun approveCheckout() {
        val btnApprove = findViewById<MaterialButton>(R.id.btnApprove)
        btnApprove.isEnabled = false
        btnApprove.text = "Approving..."

        if (roomId.isBlank() || propertyId.isBlank()) {
            btnApprove.isEnabled = true
            btnApprove.text = "Approve"
            Toast.makeText(this, "Unable to complete approval. Missing room or property data.", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("bookings")
            .whereEqualTo("propertyId", propertyId)
            .whereEqualTo("roomNumber", roomNumber)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    btnApprove.isEnabled = true
                    btnApprove.text = "Approve"
                    Toast.makeText(this, "No active booking found for this room.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val bookingDoc = snapshot.documents.first()
                val bookingId = bookingDoc.id
                val studentName = bookingDoc.getString("studentName") ?: "Student"
                val bookingRef = bookingDoc.reference
                val roomRef = db.collection("rooms").document(roomId)
                val propertyRef = db.collection("properties").document(propertyId)

                db.runTransaction { transaction ->
                    // ALL READS FIRST
                    val propertySnapshot = transaction.get(propertyRef)
                    
                    // ALL WRITES AFTER
                    transaction.update(bookingRef, "status", "completed")
                    transaction.update(bookingRef, "checkoutDate", com.google.firebase.Timestamp.now())
                    transaction.update(roomRef, mapOf(
                        "isAvailable" to true,
                        "bookedBy" to "",
                        "bookedAt" to null
                    ))

                    if (propertySnapshot.exists()) {
                        val currentAvailable = (propertySnapshot.getLong("availableRooms") ?: 0L) + 1L
                        transaction.update(propertyRef, "availableRooms", currentAvailable)
                    }
                    null
                }.addOnSuccessListener {
                    acceptPendingLeaveRequests(bookingId)
                    Toast.makeText(
                        this,
                        "Checkout approved. $studentName is no longer assigned to room $roomNumber.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }.addOnFailureListener { e ->
                    btnApprove.isEnabled = true
                    btnApprove.text = "Approve"
                    Toast.makeText(this, "Approval failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                btnApprove.isEnabled = true
                btnApprove.text = "Approve"
                Toast.makeText(this, "Unable to find booking: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun rejectCheckout() {
        if (roomId.isBlank() || propertyId.isBlank()) {
            Toast.makeText(this, "Unable to process rejection. Missing data.", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<MaterialButton>(R.id.btnReject).isEnabled = false
        
        db.collection("leave_requests")
            .whereEqualTo("propertyId", propertyId)
            .whereEqualTo("roomNumber", roomNumber)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No pending request found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "status", "REJECTED")
                    
                    // Send notification to student
                    val studentId = doc.getString("studentId") ?: ""
                    if (studentId.isNotEmpty()) {
                        val notification = hashMapOf(
                            "userId" to studentId,
                            "title" to "Checkout Request Rejected",
                            "message" to "Your request to vacate room $roomNumber has been reviewed and rejected. You will remain assigned to this unit.",
                            "timestamp" to System.currentTimeMillis(),
                            "read" to false,
                            "type" to "checkout_rejected"
                        )
                        batch.set(db.collection("notifications").document(), notification)
                    }
                }

                batch.commit().addOnSuccessListener {
                    findViewById<MaterialCardView>(R.id.cardLeaveRequest).visibility = View.GONE
                    findViewById<MaterialCardView>(R.id.dockLeaveDecision).visibility = View.GONE
                    Toast.makeText(this, "Request rejected and student notified.", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    findViewById<MaterialButton>(R.id.btnReject).isEnabled = true
                    Toast.makeText(this, "Failed to update database: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                findViewById<MaterialButton>(R.id.btnReject).isEnabled = true
                Toast.makeText(this, "Error fetching requests: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun acceptPendingLeaveRequests(bookingId: String) {
        db.collection("leave_requests")
            .whereEqualTo("bookingId", bookingId)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.update("status", "ACCEPTED") }
            }
    }

    private fun showExtendRentDialog() {
        val input = TextInputEditText(this).apply {
            hint = "Days to extend (e.g. 5)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Extend Rent Period")
            .setMessage("Increase the student's rent deadline by allocating extra days.")
            .setView(container)
            .setPositiveButton("Extend") { _, _ ->
                val addedDays = input.text.toString().toIntOrNull() ?: 0
                if (addedDays > 0) {
                    daysLeft += addedDays
                    val pb = findViewById<ProgressBar>(R.id.pbDaysLeft)
                    pb.max = pb.max + addedDays
                    pb.progress = daysLeft
                    findViewById<TextView>(R.id.tvDaysLeftNum).text = daysLeft.toString()
                    Toast.makeText(this, "Deadline extended successfully.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
