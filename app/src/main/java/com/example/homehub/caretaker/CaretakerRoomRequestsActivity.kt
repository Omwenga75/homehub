package com.example.homehub.caretaker

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.student.LeaveRequest
import com.example.homehub.utils.toastError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CaretakerRoomRequestsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: RoomRequestsAdapter
    private val requestsList = mutableListOf<LeaveRequest>()
    private var requestsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caretaker_room_requests)
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        setupRecyclerView()
        loadRequests()
    }

    private fun setupRecyclerView() {
        adapter = RoomRequestsAdapter(requestsList) { request, action ->
            when (action) {
                "ACCEPT" -> showAcceptConfirmation(request)
                "REJECT" -> showRejectConfirmation(request)
            }
        }
        val rvRequests = findViewById<RecyclerView>(R.id.rvRequests)
        rvRequests.layoutManager = LinearLayoutManager(this)
        rvRequests.adapter = adapter
    }

    private fun loadRequests() {
        val caretakerId = auth.currentUser?.uid ?: return
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val emptyState = findViewById<View>(R.id.emptyState)

        progressBar.visibility = View.VISIBLE

        requestsListener = db.collection("leave_requests")
            .whereEqualTo("caretakerId", caretakerId)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    progressBar.visibility = View.GONE
                    toastError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    requestsList.clear()
                    adapter.updateData(requestsList)
                    emptyState.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val newRequests = mutableListOf<LeaveRequest>()
                var processedCount = 0
                val totalRequests = snapshot.size()

                snapshot.forEach { doc ->
                    val request = LeaveRequest.fromMap(doc.id, doc.data)
                    
                    // Fetch profile picture and room info in parallel
                    db.collection("users").document(request.studentId).get().addOnSuccessListener { userDoc ->
                        val profilePic = userDoc.getString("profileImageUrl") ?: userDoc.getString("profilePictureUrl") ?: ""
                        request.studentProfilePicture = profilePic
                        
                        val realName = userDoc.getString("fullName") ?: userDoc.getString("name") ?: ""
                        if (realName.isNotEmpty()) {
                            request.studentName = realName
                        }
                        
                        db.collection("bookings").document(request.bookingId).get().addOnSuccessListener { bookingDoc ->
                            if (bookingDoc.exists()) {
                                request.roomNumber = bookingDoc.getString("roomNumber") ?: ""
                                val leaseStart = bookingDoc.getTimestamp("leaseStart")?.toDate() ?: bookingDoc.getTimestamp("createdAt")?.toDate()
                                if (leaseStart != null) {
                                    request.daysUntilRent = calculateDaysToNextRent(leaseStart)
                                }
                            }
                            
                            newRequests.add(request)
                            processedCount++
                            
                            if (processedCount == totalRequests) {
                                progressBar.visibility = View.GONE
                                requestsList.clear()
                                requestsList.addAll(newRequests.sortedByDescending { it.createdAt })
                                adapter.updateData(requestsList)
                                emptyState.visibility = if (requestsList.isEmpty()) View.VISIBLE else View.GONE
                            }
                        }.addOnFailureListener {
                            processedCount++
                            if (processedCount == totalRequests) finishLoading(progressBar, emptyState, newRequests)
                        }
                    }.addOnFailureListener {
                        processedCount++
                        if (processedCount == totalRequests) finishLoading(progressBar, emptyState, newRequests)
                    }
                }
            }
    }

    private fun finishLoading(progressBar: ProgressBar, emptyState: View, list: List<LeaveRequest>) {
        progressBar.visibility = View.GONE
        requestsList.clear()
        requestsList.addAll(list.sortedByDescending { it.createdAt })
        adapter.updateData(requestsList)
        emptyState.visibility = if (requestsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun calculateDaysToNextRent(leaseStartDate: java.util.Date): Int {
        val today = java.util.Calendar.getInstance()
        val nextRent = java.util.Calendar.getInstance()
        nextRent.time = leaseStartDate
        
        // Match day of month
        val dayOfMonth = nextRent.get(java.util.Calendar.DAY_OF_MONTH)
        
        nextRent.set(java.util.Calendar.YEAR, today.get(java.util.Calendar.YEAR))
        nextRent.set(java.util.Calendar.MONTH, today.get(java.util.Calendar.MONTH))
        
        // If the date has passed this month, move to next month
        if (nextRent.get(java.util.Calendar.DAY_OF_MONTH) < today.get(java.util.Calendar.DAY_OF_MONTH)) {
            nextRent.add(java.util.Calendar.MONTH, 1)
        }
        
        val diff = nextRent.timeInMillis - today.timeInMillis
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun showAcceptConfirmation(request: LeaveRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Accept Vacation Request")
            .setMessage("Are you sure you want to allow ${request.studentName} to vacate the room? This will permanently delete their booking record and free up the unit.")
            .setPositiveButton("Accept & Finalize") { _, _ ->
                handleAcceptRequest(request)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleAcceptRequest(request: LeaveRequest) {
        db.collection("bookings").document(request.bookingId).get()
            .addOnSuccessListener { doc ->
                val roomNumber = doc.getString("roomNumber") ?: ""
                val propertyId = doc.getString("propertyId") ?: request.propertyId
                
                db.runTransaction { transaction ->
                    val requestRef = db.collection("leave_requests").document(request.id)
                    val bookingRef = db.collection("bookings").document(request.bookingId)
                    
                    // ALL READS FIRST
                    val propSnapshot = if (propertyId.isNotEmpty()) {
                        transaction.get(db.collection("properties").document(propertyId))
                    } else null

                    // ALL WRITES AFTER
                    transaction.update(requestRef, "status", "ACCEPTED")
                    
                    if (propSnapshot != null && propSnapshot.exists()) {
                        val propertyRef = propSnapshot.reference
                        if (roomNumber.isNotEmpty()) {
                            val statuses = (propSnapshot.get("roomStatuses") as? Map<String, String> ?: emptyMap()).toMutableMap()
                            statuses[roomNumber] = "Available"
                            transaction.update(propertyRef, "roomStatuses", statuses)
                            val currentAvailable = (propSnapshot.getLong("availableRooms") ?: 0L).toInt()
                            transaction.update(propertyRef, "availableRooms", currentAvailable + 1)
                            transaction.update(propertyRef, "status", "Active")
                            transaction.update(propertyRef, "available", true)
                        } else {
                            transaction.update(propertyRef, "status", "Active")
                            transaction.update(propertyRef, "available", true)
                        }
                    }
                    
                    transaction.update(bookingRef, "status", "completed")
                    transaction.update(bookingRef, "checkoutDate", com.google.firebase.Timestamp.now())
                    null
                }.addOnSuccessListener {
                    Toast.makeText(this, "Request approved. Booking for ${request.studentName} has been cleared.", Toast.LENGTH_LONG).show()
                    sendNotification(request.studentId, "Vacation Request Approved", "Your request to vacate ${request.propertyName} has been approved. You are now free to book another house.")
                }.addOnFailureListener { toastError(it) }
                
            }.addOnFailureListener { toastError(it) }
    }

    private fun showRejectConfirmation(request: LeaveRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reject Vacation Request")
            .setMessage("Are you sure you want to reject this request? The student will remain linked to the room.")
            .setPositiveButton("Reject") { _, _ ->
                handleRejectRequest(request)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleRejectRequest(request: LeaveRequest) {
        db.collection("leave_requests").document(request.id)
            .update("status", "REJECTED")
            .addOnSuccessListener {
                Toast.makeText(this, "Request rejected.", Toast.LENGTH_SHORT).show()
                sendNotification(request.studentId, "Vacation Request Update", "Your request to vacate ${request.propertyName} was reviewed and rejected. Please contact your caretaker for more details.")
            }
            .addOnFailureListener { toastError(it) }
    }

    private fun sendNotification(userId: String, title: String, message: String) {
        val notification = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "read" to false,
            "type" to "vacation_update"
        )
        db.collection("notifications").add(notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        requestsListener?.remove()
    }
}
