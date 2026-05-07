package com.example.homehub.admin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.homehub.R
import com.example.homehub.student.Student

class StudentAdapter(
    private var students: List<Student>,
    private val currentUserId: String = "",
    private val db: FirebaseFirestore,
    private var isReservedView: Boolean = false,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    private lateinit var context: Context

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.studentCard)
        val tvRoleLabel: TextView = itemView.findViewById(R.id.tvRoleLabel)
        val emailText: TextView = itemView.findViewById(R.id.emailText)
        val phoneText: TextView = itemView.findViewById(R.id.phoneText)
        val locationText: TextView = itemView.findViewById(R.id.locationText)
        val bookingsText: TextView = itemView.findViewById(R.id.bookingsText)
        val paymentsText: TextView = itemView.findViewById(R.id.paymentsText)
        val labelBookings: TextView = itemView.findViewById(R.id.labelBookings)
        val labelPayments: TextView = itemView.findViewById(R.id.labelPayments)
        val joinDateText: TextView = itemView.findViewById(R.id.joinDateText)
        val moreButton: ImageView = itemView.findViewById(R.id.moreButton)
        val studentIcon: ImageView = itemView.findViewById(R.id.studentIcon)
        val verifiedBadge: ImageView = itemView.findViewById(R.id.verifiedBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        val isCurrentUser = student.userId == currentUserId
        val isCaretaker = student.userType.equals("Caretaker", true)

        // Standard Fields - Name now goes to the header label
        holder.tvRoleLabel.text = student.getDisplayName()
        com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(holder.studentIcon, student.getDisplayName(), student.profileImageUrl, student.userId)
        
        holder.emailText.text = student.email
        holder.phoneText.text = student.getFormattedPhone()
        
        val regNum = student.registrationNumber
        if (regNum.isNotEmpty()) {
            holder.locationText.text = regNum
            holder.locationText.visibility = View.VISIBLE
        } else {
            // If it's a student with no reg, or a non-student, hide it or use location
            val location = student.getFormattedLocation()
            if (location != "Location not set") {
                holder.locationText.text = location
                holder.locationText.visibility = View.VISIBLE
            } else {
                holder.locationText.visibility = View.GONE
            }
        }
        holder.joinDateText.text = student.getFormattedJoinDate()
        holder.bookingsText.text = "..."
        holder.paymentsText.text = "..."

        // Update Labels based on view mode
        if (isReservedView) {
            holder.labelBookings.text = "RESERVED"
            holder.labelPayments.text = "PENDING PAYMENT"
            holder.paymentsText.setTextColor(ContextCompat.getColor(context, R.color.amber_500))
        } else {
            holder.labelBookings.text = "BOOKINGS"
            holder.labelPayments.text = "TOTAL PAYMENTS MADE"
            holder.paymentsText.setTextColor(ContextCompat.getColor(context, R.color.green))
        }
        
        // Calculate Total Payments and Bookings (Asynchronous, context-aware)
        calculateTotalPaymentsAndBookings(student.userId, holder)

        // Verified Badge
        holder.verifiedBadge.visibility = if (student.isVerified) View.VISIBLE else View.GONE

        // Set click listeners
        setupClickListeners(holder, student, isCurrentUser, isCaretaker)
    }

    private fun calculateTotalPaymentsAndBookings(studentId: String, holder: StudentViewHolder) {
        val sessionManager = com.example.homehub.auth.SessionManager(context)
        val isCaretakerViewer = sessionManager.getUserRole()?.equals("caretaker", true) == true
        
        var query = db.collection("bookings")
            .whereEqualTo("studentId", studentId)
            .whereIn("status", listOf("confirmed", "active", "completed", "paid", "pending_deferred"))
            
        if (isCaretakerViewer && currentUserId.isNotEmpty()) {
            query = query.whereEqualTo("caretakerId", currentUserId)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                var total = 0.0
                for (doc in snapshot.documents) {
                    val amount = when (val amt = doc.get("amount")) {
                        is Number -> amt.toDouble()
                        is String -> amt.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    total += amount
                }

                if (isReservedView) {
                    holder.bookingsText.text = snapshot.size().toString()
                    holder.paymentsText.text = "KSh ${String.format("%,d", total.toInt())}"
                } else {
                    holder.bookingsText.text = snapshot.size().toString()
                    holder.paymentsText.text = "Sh ${String.format("%,d", total.toInt())}"
                }
            }
            .addOnFailureListener {
                holder.paymentsText.text = if (isReservedView) "KSh 0" else "Sh 0"
                holder.bookingsText.text = "0"
            }
    }

    override fun getItemCount(): Int = students.size

    fun updateStudents(newStudents: List<Student>) {
        students = newStudents
        notifyDataSetChanged()
    }

    fun updateFilterState(isReserved: Boolean) {
        this.isReservedView = isReserved
        notifyDataSetChanged()
    }



    private fun setupClickListeners(holder: StudentViewHolder, student: Student, isCurrentUser: Boolean, isCaretaker: Boolean) {
        // More button click
        holder.moreButton.setOnClickListener { view ->
            showOptionsMenu(view, student, isCurrentUser, isCaretaker)
        }

        // Enable card click for actions
        holder.cardView.setOnClickListener { view ->
            showOptionsMenu(holder.moreButton, student, isCurrentUser, isCaretaker)
        }
        holder.cardView.isClickable = true
        holder.cardView.isFocusable = true
    }

    private fun showOptionsMenu(view: View, student: Student, isCurrentUser: Boolean, isCaretaker: Boolean) {
        val popup = PopupMenu(context, view)

        // For all users: Show all options
        popup.menu.add(0, 1, 0, if (student.isActive()) "Suspend Account" else "Activate Account")
        popup.menu.add(0, 2, 1, "Delete Account")
        popup.menu.add(0, 3, 2, "Reset Password")
        popup.menu.add(0, 4, 3, "Send Message")

        // Disable management options for the currently logged-in admin (self)
        if (isCurrentUser) {
            popup.menu.findItem(1).isEnabled = false
            popup.menu.findItem(2).isEnabled = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (student.isActive()) {
                        suspendStudent(student)
                    } else {
                        activateStudent(student)
                    }
                    true
                }
                2 -> {
                    deleteStudent(student)
                    true
                }
                3 -> {
                    resetStudentPassword(student)
                    true
                }
                4 -> {
                    sendMessageToStudent(student)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun suspendStudent(student: Student) {
        if (!student.isActive()) {
            Toast.makeText(context, "Student is already suspended", Toast.LENGTH_SHORT).show()
            return
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Suspend Student")
            .setMessage("Suspend ${student.getDisplayName()}?")
            .setPositiveButton("Suspend") { dialog, _ ->
                updateStudentStatus(student, "Suspended")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun activateStudent(student: Student) {
        if (student.isActive()) {
            Toast.makeText(context, "Student is already active", Toast.LENGTH_SHORT).show()
            return
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Activate ${if (student.userType == "Caretaker") "Caretaker" else "Student"}")
            .setMessage("Activate ${student.getDisplayName()}?")
            .setPositiveButton("Activate") { dialog, _ ->
                updateStudentStatus(student, "Active")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStudentStatus(student: Student, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updates = mapOf(
                    "status" to status,
                    "accountStatus" to status
                )

                // Update in users collection
                val studentRef = db.collection("users").document(student.userId)
                val studentSnapshot = studentRef.get().await()

                if (studentSnapshot.exists()) {
                    studentRef.update(updates).await()
                }

                // If student is a caretaker, also update in verifiedCaretakers
                if (student.userType == "Caretaker") {
                    val caretakerRef = db.collection("verifiedCaretakers").document(student.userId)
                    val caretakerSnapshot = caretakerRef.get().await()

                    if (caretakerSnapshot.exists()) {
                        caretakerRef.update(updates).await()
                    }
                }

                CoroutineScope(Dispatchers.Main).launch {
                    val action = if (status == "Suspended") "suspended" else "activated"
                    Toast.makeText(context,
                        "${student.getDisplayName()} has been $action", Toast.LENGTH_SHORT).show()
                    onDataChanged()
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteStudent(student: Student) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Delete Account")
            .setMessage("Permanently delete data for ${student.getDisplayName()}? This will cancel their bookings and wipe their profile. This cannot be undone!")
            .setPositiveButton("Delete") { dialog, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val userId = student.userId

                        // 1. Release property rooms & delete bookings
                        val bookingsSnap = db.collection("bookings").whereEqualTo("studentId", userId).get().await()
                        for (doc in bookingsSnap.documents) {
                            val bookingId = doc.id
                            val propertyId = doc.getString("propertyId") ?: ""
                            val roomNumber = doc.getString("roomNumber") ?: ""
                            val roomTypeId = doc.getString("roomTypeId") ?: ""

                            if (propertyId.isNotEmpty()) {
                                db.runTransaction { transaction ->
                                    val propertyRef = db.collection("properties").document(propertyId)
                                    val snap = transaction.get(propertyRef)
                                    if (snap.exists()) {
                                        if (roomNumber.isNotEmpty()) {
                                            val statuses = (snap.get("roomStatuses") as? Map<String, String> ?: emptyMap()).toMutableMap()
                                            if (statuses[roomNumber]?.equals("Available", ignoreCase = true) != true) {
                                                statuses[roomNumber] = "Available"
                                                transaction.update(propertyRef, "roomStatuses", statuses)
                                                val availRooms = (snap.getLong("availableRooms") ?: 0L).toInt()
                                                transaction.update(propertyRef, "availableRooms", availRooms + 1)
                                            }
                                        } else if (roomTypeId.isNotEmpty()) {
                                            val roomTypesList = snap.get("roomTypes") as? List<Map<String, Any>> ?: emptyList()
                                            val updatedList = roomTypesList.map { type ->
                                                val mutableType = type.toMutableMap()
                                                if (type["id"] == roomTypeId) {
                                                    val qty = (type["availableQuantity"] as? Long) ?: 0L
                                                    mutableType["availableQuantity"] = qty + 1
                                                }
                                                mutableType
                                            }
                                            transaction.update(propertyRef, "roomTypes", updatedList)
                                            val wasAvailable = snap.getBoolean("available") ?: true
                                            if (!wasAvailable) {
                                                transaction.update(propertyRef, "status", "Active")
                                                transaction.update(propertyRef, "available", true)
                                            }
                                        } else {
                                            transaction.update(propertyRef, "status", "Active")
                                            transaction.update(propertyRef, "available", true)
                                        }
                                    }
                                    transaction.delete(db.collection("bookings").document(bookingId))
                                }.await()
                            } else {
                                db.collection("bookings").document(bookingId).delete().await()
                            }
                        }

                        // 2. Clear leave requests
                        val leavesSnap = db.collection("leave_requests").whereEqualTo("studentId", userId).get().await()
                        for (doc in leavesSnap.documents) {
                            db.collection("leave_requests").document(doc.id).delete().await()
                        }

                        // 3. Clear user document & specific roles
                        db.collection("users").document(userId).delete().await()
                        if (student.userType == "Caretaker") {
                            db.collection("verifiedCaretakers").document(userId).delete().await()
                        }
                        if (student.userType == "Supplier") {
                            db.collection("waterSuppliers").document(userId).delete().await()
                        }

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "${student.getDisplayName()}'s data has been wiped.", Toast.LENGTH_SHORT).show()
                            onDataChanged()
                        }
                    } catch (e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetStudentPassword(student: Student) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Reset Password")
            .setMessage("Send password reset email to ${student.email}?")
            .setPositiveButton("Send") { dialog, _ ->
                Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendMessageToStudent(student: Student) {
        Toast.makeText(context, "Send message to ${student.getDisplayName()}", Toast.LENGTH_SHORT).show()
    }
}
