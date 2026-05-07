package com.example.homehub.caretaker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CaretakerRequestsFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var requestsAdapter: CaretakerRequestsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private val requestsList = mutableListOf<CaretakerRequest>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_caretaker_requests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        initializeViews(view)
        loadCaretakerRequests()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.requestsRecyclerView)
        emptyState = view.findViewById(R.id.emptyStateText)

        requestsAdapter = CaretakerRequestsAdapter(requestsList) { request, action ->
            when (action) {
                "approve" -> approveCaretakerRequest(request)
                "reject" -> rejectCaretakerRequest(request)
                "view" -> viewUserProfile(request)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = requestsAdapter
    }

    private fun loadCaretakerRequests() {
        db.collection("caretakerRequests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Error loading requests", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                requestsList.clear()
                snapshots?.forEach { document ->
                    val request = document.toObject(CaretakerRequest::class.java)
                    request.id = document.id
                    requestsList.add(request)
                }

                requestsAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
    }

    private fun approveCaretakerRequest(request: CaretakerRequest) {
        val userUpdates = hashMapOf<String, Any>(
            "userType" to "caretaker",
            "isVerifiedCaretaker" to true,
            "isApproved" to true,
            "verifiedAt" to System.currentTimeMillis(),
            "verifiedBy" to "admin",
            "caretakerStatus" to "active"
        )

        db.collection("users").document(request.userId).update(userUpdates)
            .addOnSuccessListener {
                updateRequestStatus(request, "approved")
                createCaretakerProfile(request.userId, request.userName, request.userEmail)
                sendNotificationToUser(request.userId, true, request.userName)
                Toast.makeText(requireContext(), "Caretaker request approved! ${request.userName} is now a verified caretaker.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error approving request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createCaretakerProfile(userId: String, userName: String, userEmail: String) {
        val caretakerProfile = hashMapOf(
            "userId" to userId,
            "caretakerName" to userName,
            "email" to userEmail,
            "isVerified" to true,
            "verificationDate" to System.currentTimeMillis(),
            "badge" to "verified",
            "propertiesCount" to 0,
            "totalBookings" to 0,
            "totalEarnings" to 0.0,
            "rating" to 0.0,
            "joinedDate" to System.currentTimeMillis()
        )

        db.collection("caretakers").document(userId).set(caretakerProfile)
            .addOnSuccessListener {
                Log.d("CaretakerCreation", "✅ Caretaker profile created for: $userName")
            }
            .addOnFailureListener { e ->
                Log.e("CaretakerCreation", "❌ Error creating caretaker profile: ${e.message}")
            }
    }

    private fun rejectCaretakerRequest(request: CaretakerRequest) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject Caretaker Request")
            .setMessage("Are you sure you want to reject ${request.userName}'s caretaker request?")
            .setPositiveButton("Reject") { dialog, _ ->
                updateRequestStatus(request, "rejected")
                sendNotificationToUser(request.userId, false, request.userName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateRequestStatus(request: CaretakerRequest, status: String) {
        db.collection("caretakerRequests").document(request.id).update("status", status)
            .addOnSuccessListener {
                requestsList.remove(request)
                requestsAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
    }

    private fun sendNotificationToUser(userId: String, approved: Boolean, userName: String) {
        val notificationData = hashMapOf(
            "userId" to userId,
            "title" to if (approved) "🎉 Caretaker Request Approved!" else "Caretaker Request Update",
            "message" to if (approved)
                "Congratulations $userName! You are now a verified HomeHub caretaker. You can now manage properties and your dashboard."
            else "Your caretaker request requires additional verification. Please contact support for more information.",
            "type" to "caretaker_request_update",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        db.collection("notifications").add(notificationData)
    }

    private fun viewUserProfile(request: CaretakerRequest) {
        val intent = Intent(requireContext(), CaretakerProfileActivity::class.java).apply {
            putExtra("CARETAKER_ID", request.userId)
        }
        startActivity(intent)
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (requestsList.isEmpty()) View.VISIBLE else View.GONE
    }
}
