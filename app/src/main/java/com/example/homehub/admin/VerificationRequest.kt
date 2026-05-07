package com.example.homehub.admin

import java.io.Serializable

data class VerificationRequest(
    var documentId: String = "",
    var userId: String = "",
    var fullName: String = "",
    var idNumber: String = "",
    var location: String = "",
    var phone: String = "",
    var businessName: String = "",
    var documentType: String = "",
    var documentImageUrl: String = "",
    var role: String = "student",
    var status: String = "pending",
    var submittedAt: com.google.firebase.Timestamp? = null,
    var reviewedAt: com.google.firebase.Timestamp? = null,
    var rejectionReason: String = ""
) : Serializable {

    fun isPending(): Boolean = status.lowercase() == "pending"
    fun isApproved(): Boolean = status.lowercase() == "approved"
    fun isRejected(): Boolean = status.lowercase() == "rejected"

    companion object {
        fun fromDocument(id: String, map: Map<String, Any>): VerificationRequest {
            return VerificationRequest(
                documentId = id,
                userId = map["userId"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                idNumber = map["idNumber"] as? String ?: "",
                location = map["location"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                businessName = map["businessName"] as? String ?: "",
                documentType = map["documentType"] as? String ?: "",
                documentImageUrl = map["documentImageUrl"] as? String ?: "",
                role = map["role"] as? String ?: "student",
                status = map["status"] as? String ?: "pending",
                submittedAt = map["submittedAt"] as? com.google.firebase.Timestamp
                    ?: map["timestamp"] as? com.google.firebase.Timestamp,
                reviewedAt = map["reviewedAt"] as? com.google.firebase.Timestamp,
                rejectionReason = map["rejectionReason"] as? String ?: ""
            )
        }
    }
}
