package com.example.homehub.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object NotificationManager {
    private const val TAG = "NotificationManager"
    private var isInitializedInternal = false
    val isInitialized: Boolean get() = isInitializedInternal
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var activeListener: ListenerRegistration? = null

    fun initialize(context: Context) {
        isInitializedInternal = true
    }

    // --- Overloads with callback(Boolean) for existing callers ---

    fun markAsRead(notificationId: String, callback: (Boolean) -> Unit) {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnCompleteListener { callback(it.isSuccessful) }
    }

    fun deleteNotification(notificationId: String, callback: (Boolean) -> Unit) {
        db.collection("notifications").document(notificationId)
            .delete()
            .addOnCompleteListener { callback(it.isSuccessful) }
    }

    fun markAllAsRead(userId: String, callback: (Boolean) -> Unit) {
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit().addOnCompleteListener { callback(it.isSuccessful) }
            }
            .addOnFailureListener { callback(false) }
    }

    // --- Suspend-style overloads (no callback) for NotificationsActivity ---

    fun markAsRead(notificationId: String) {
        markAsRead(notificationId) { /* fire-and-forget */ }
    }

    fun deleteNotification(notificationId: String) {
        deleteNotification(notificationId) { /* fire-and-forget */ }
    }

    fun markAllAsRead(userId: String) {
        markAllAsRead(userId) { /* fire-and-forget */ }
    }

    // --- getUserNotifications overloads ---

    fun getUserNotifications(userId: String, forceRefresh: Boolean, callback: (List<Notification>) -> Unit) {
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        Notification(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            title = doc.getString("title") ?: "",
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                            isRead = doc.getBoolean("isRead") ?: false,
                            type = doc.getString("type") ?: "USER",
                            notificationType = doc.getString("notificationType") ?: "INFO",
                            priority = doc.getString("priority") ?: "MEDIUM",
                            actionType = doc.getString("actionType") ?: "NONE",
                            relatedId = doc.getString("relatedId") ?: "",
                            applicantId = doc.getString("applicantId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing notification \${doc.id}: \${e.message}")
                        null
                    }
                }
                // Client-side sort by timestamp descending
                val sortedList = list.sortedByDescending { it.timestamp }
                callback(sortedList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user notifications: \${e.message}")
                callback(emptyList())
            }
    }

    /** Overload without forceRefresh for NotificationsActivity */
    fun getUserNotifications(userId: String, callback: (List<Notification>) -> Unit) {
        getUserNotifications(userId, false, callback)
    }

    // --- Admin notifications ---

    fun getAdminNotifications(forceRefresh: Boolean, callback: (List<Notification>) -> Unit) {
        db.collection("notifications")
            .whereEqualTo("type", "ADMIN")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        Notification(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            title = doc.getString("title") ?: "",
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                            isRead = doc.getBoolean("isRead") ?: false,
                            type = doc.getString("type") ?: "USER",
                            notificationType = doc.getString("notificationType") ?: "INFO",
                            priority = doc.getString("priority") ?: "MEDIUM",
                            actionType = doc.getString("actionType") ?: "NONE",
                            relatedId = doc.getString("relatedId") ?: "",
                            applicantId = doc.getString("applicantId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing admin notification \${doc.id}: \${e.message}")
                        null
                    }
                }
                // Client-side sort
                val sortedList = list.sortedByDescending { it.timestamp }
                callback(sortedList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get admin notifications: ${e.message}")
                callback(emptyList())
            }
    }

    // --- Real-time listener with role gating ---
    private var currentGatedRole: String? = null

    /**
     * Sets the current active role to filter notifications.
     */
    fun setActiveRole(role: String) {
        currentGatedRole = role.uppercase()
        Log.d(TAG, "Active role set to: $currentGatedRole")
    }

    fun startListeningForNotifications(userId: String, onNewNotification: (Notification) -> Unit): () -> Unit {
        // Remove orderBy and limit to avoid index requirements and catch all new additions
        val registration = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Real-time listener error: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        try {
                            val doc = change.document
                            val type = doc.getString("type") ?: "USER"
                            
                            // GATING LOGIC: Only notify if the type matches our current role
                            // This prevent "leaks" between dashboards (e.g. Student popup on Admin screen)
                            if (currentGatedRole != null && type != currentGatedRole) {
                                Log.d(TAG, "Gating notification: Incoming $type doesn't match active $currentGatedRole")
                                return@forEach
                            }

                            val notification = Notification(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                isRead = doc.getBoolean("isRead") ?: false,
                                type = doc.getString("type") ?: "USER",
                                notificationType = doc.getString("notificationType") ?: "INFO",
                                priority = doc.getString("priority") ?: "MEDIUM",
                                actionType = doc.getString("actionType") ?: "NONE",
                                relatedId = doc.getString("relatedId") ?: "",
                                applicantId = doc.getString("applicantId") ?: ""
                            )
                            onNewNotification(notification)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing notification: \${e.message}")
                        }
                    }
                }
            }
        activeListener = registration
        return { registration.remove() }
    }

    fun stopListening() {
        activeListener?.remove()
        activeListener = null
    }

    // --- Unread count ---

    fun getUnreadCount(userId: String, callback: (Int) -> Unit) {
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot -> callback(snapshot.size()) }
            .addOnFailureListener { callback(0) }
    }

    /**
     * Listens for unread notifications for a specific user in real-time.
     */
    fun listenToUnreadCount(userId: String, callback: (Int) -> Unit): ListenerRegistration {
        return db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Unread count listener error: ${error.message}")
                    return@addSnapshotListener
                }
                callback(snapshot?.size() ?: 0)
            }
    }

    /**
     * Marks all unread notifications of a specific type as read for a user.
     */
    fun clearUnread(userId: String, type: String) {
        // Standardize type to uppercase for consistency
        val standardizedType = type.uppercase()
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("notificationType", standardizedType)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val batch = db.batch()
                    snapshot.documents.forEach { doc ->
                        batch.update(doc.reference, "isRead", true)
                    }
                    batch.commit()
                        .addOnSuccessListener { Log.d(TAG, "Cleared unread $standardizedType notifications for $userId") }
                }
            }
    }

    /**
     * Listens for unread admin notifications in real-time.
     */
    fun listenToAdminUnreadCount(callback: (Int) -> Unit): ListenerRegistration {
        return db.collection("notifications")
            .whereEqualTo("type", "ADMIN")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Admin unread count listener error: ${error.message}")
                    return@addSnapshotListener
                }
                callback(snapshot?.size() ?: 0)
            }
    }

    // --- Send notifications ---

    fun sendApplicationApprovedNotification(userId: String, applicantName: String) {
        val title = "Application Approved"
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("title", title)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    val notification = hashMapOf<String, Any>(
                        "userId" to userId,
                        "title" to title,
                        "message" to "Congratulations $applicantName! You can now start managing properties.",
                        "type" to "USER",
                        "notificationType" to "VERIFICATION",
                        "priority" to "HIGH",
                        "isRead" to false,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("notifications").add(notification)
                }
            }
    }

    fun sendApplicationRejectedNotification(userId: String, applicantName: String, reason: String) {
        val title = "Application Rejected"
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("title", title)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    val notification = hashMapOf<String, Any>(
                        "userId" to userId,
                        "title" to title,
                        "message" to "Dear $applicantName, we couldn't verify your details. Reason: $reason",
                        "type" to "USER",
                        "notificationType" to "VERIFICATION",
                        "priority" to "HIGH",
                        "isRead" to false,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("notifications").add(notification)
                }
            }
    }

    fun sendVerificationRequestNotification(userId: String, applicantName: String) {
        val notification = hashMapOf<String, Any>(
            "userId" to "super_admin_001", // Target all admins or specific admin
            "applicantId" to userId,
            "title" to "Verification Request",
            "message" to "$applicantName has submitted documents for review.",
            "type" to "ADMIN",
            "notificationType" to "VERIFICATION_REQUEST",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Verification request notification sent to admin")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send verification notification: ${e.message}")
            }
    }

    fun removeVerificationRequestNotification(applicantId: String) {
        db.collection("notifications")
            .whereEqualTo("type", "ADMIN")
            .whereEqualTo("applicantId", applicantId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val batch = db.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                }
            }
    }

    fun showNotification() {}

    /**
     * Profile notifications are now handled via status indicators on the profile screen.
     */
    fun sendProfileIncompleteNotification(userId: String) {
        // Method removed as per request to stop "Complete Profile" notifications.
    }

    /**
     * Sends a welcome notification to a new user.
     */
    fun sendWelcomeNotification(userId: String, fullName: String) {
        val title = "Welcome to HomeHub!"
        val notification = hashMapOf<String, Any>(
            "userId" to userId,
            "title" to title,
            "message" to "Hi $fullName, welcome to HomeHub! We're glad to have you on board. Start exploring properties now.",
            "type" to "USER",
            "notificationType" to "WELCOME",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Welcome notification sent for: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send welcome notification: ${e.message}")
            }
    }

    /**
     * Notifies a caretaker that their property has been booked.
     */
    fun sendBookingConfirmedNotification(caretakerId: String, studentName: String, propertyName: String, amount: Double) {
        val title = "New Booking Confirmed"
        val formattedAmount = "KSh ${String.format("%,.0f", amount)}"
        val notification = hashMapOf<String, Any>(
            "userId" to caretakerId,
            "title" to title,
            "message" to "Great news! $studentName has just booked $propertyName for $formattedAmount. Check your booking logs for details.",
            "type" to "USER",
            "notificationType" to "BOOKING",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Booking confirmed notification sent to caretaker: $caretakerId")
            }
    }

    /**
     * Notifies a caretaker that a student has requested to vacate their room.
     * This is an URGENT priority notification.
     */
    fun sendVacateRequestNotification(caretakerId: String, studentName: String, propertyName: String, roomNumber: String, bookingId: String) {
        val title = "URGENT: Vacate Request"
        val notification = hashMapOf<String, Any>(
            "userId" to caretakerId,
            "title" to title,
            "message" to "$studentName has requested to vacate room $roomNumber in $propertyName. Please review and approve/reject.",
            "type" to "HOST",
            "notificationType" to "LEAVE_REQUEST",
            "priority" to "URGENT",
            "relatedId" to bookingId,
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Vacate request notification sent to caretaker: $caretakerId")
            }
    }

    /**
     * Sends a digital receipt notification to the student after a successful booking.
     */
    fun sendBookingReceiptNotification(studentId: String, propertyName: String, receipt: String) {
        val title = "Booking Confirmed ✓"
        val notification = hashMapOf<String, Any>(
            "userId" to studentId,
            "title" to title,
            "message" to "Your booking for $propertyName was successful! Receipt: $receipt. You can find your rental details in 'My Bookings'.",
            "type" to "USER",
            "notificationType" to "BOOKING",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Booking receipt notification sent to student: $studentId")
            }
    }

    /**
     * Sends a generic notification when a new chat message is received.
     * Content is hidden to maintain context privacy.
     */
    fun sendNewMessageNotification(receiverId: String, senderName: String, receiverRole: String) {
        val title = "New Message from $senderName"
        val notification = hashMapOf<String, Any>(
            "userId" to receiverId,
            "title" to title,
            "message" to "You have a new message. Switch to the appropriate dashboard to view.",
            "type" to receiverRole.uppercase(),
            "notificationType" to "CHAT",
            "priority" to "MEDIUM",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Message notification sent to $receiverId (Role: $receiverRole)")
            }
    }

    /**
     * Notifies a water supplier of a new incoming order.
     */
    fun sendWaterOrderNotification(supplierId: String, studentName: String, amount: Double, quantity: String) {
        val title = "New Water Order 💧"
        val formattedAmount = "KSh ${String.format("%,.0f", amount)}"
        val notification = hashMapOf<String, Any>(
            "userId" to supplierId,
            "title" to title,
            "message" to "New order received! $studentName has ordered $quantity for $formattedAmount. Check your dashboard to process delivery.",
            "type" to "SUPPLIER",
            "notificationType" to "WATER_ORDER",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Water order notification sent to supplier: $supplierId")
            }
    }

    /**
     * Sends a confirmation receipt to the student for a water order.
     */
    fun sendWaterOrderReceiptNotification(studentId: String, supplierName: String, amount: Double, receipt: String) {
        val title = "Water Order Confirmed ✓"
        val formattedAmount = "KSh ${String.format("%,.0f", amount)}"
        val notification = hashMapOf<String, Any>(
            "userId" to studentId,
            "title" to title,
            "message" to "Your order from $supplierName ($formattedAmount) has been confirmed. Receipt: $receipt. Your water will be delivered shortly.",
            "type" to "USER",
            "notificationType" to "WATER_ORDER",
            "priority" to "HIGH",
            "isRead" to false,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Water order receipt sent to student: $studentId")
            }
    }
}
