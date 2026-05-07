package com.example.homehub.student

import java.util.Date

data class LeaveRequest(
    var id: String = "",
    val studentId: String = "",
    var studentName: String = "",
    val studentEmail: String = "",
    val bookingId: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val caretakerId: String = "",
    val reason: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val createdAt: Date = Date(),
    var studentProfilePicture: String = "",
    var roomNumber: String = "",
    var daysUntilRent: Int = -1
) {
    companion object {
        fun fromMap(id: String, map: Map<String, Any>): LeaveRequest {
            return LeaveRequest(
                id = id,
                studentId = map["studentId"] as? String ?: "",
                studentName = map["studentName"] as? String ?: "",
                studentEmail = map["studentEmail"] as? String ?: "",
                bookingId = map["bookingId"] as? String ?: "",
                propertyId = map["propertyId"] as? String ?: "",
                propertyName = map["propertyName"] as? String ?: "",
                caretakerId = map["caretakerId"] as? String ?: "",
                reason = map["reason"] as? String ?: "",
                status = map["status"] as? String ?: "PENDING",
                createdAt = (map["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                studentProfilePicture = map["studentProfilePicture"] as? String ?: "",
                roomNumber = map["roomNumber"] as? String ?: "",
                daysUntilRent = (map["daysUntilRent"] as? Number)?.toInt() ?: -1
            )
        }
    }
}
