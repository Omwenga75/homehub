package com.example.homehub.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Notification(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var message: String = "",
    var timestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    var type: String = "USER", // USER, HOST, ADMIN
    var notificationType: String = "INFO", // INFO, BOOKING, PAYMENT, VERIFICATION, SYSTEM
    var priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    var actionType: String = "NONE", // DEEP_LINK, OPEN_ACTIVITY, NONE
    var relatedId: String = "", // e.g., House ID, Booking ID
    var applicantId: String = "" // For verification requests
) {
    // No-arg constructor for Firestore
    constructor() : this("", "", "", "", Timestamp.now(), false, "USER", "INFO", "MEDIUM", "NONE", "", "")
}
