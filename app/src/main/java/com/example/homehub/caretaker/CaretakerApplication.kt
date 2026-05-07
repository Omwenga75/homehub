package com.example.homehub.caretaker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class CaretakerApplication(
    val documentId: String = "", // Firestore document ID
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val idNumberEncrypted: String = "",
    val nationality: String = "",
    val propertyType: String = "",
    val propertyLocation: String = "",
    val propertyDescription: String = "",
    val numberOfRooms: Int = 0,
    val pricePerMonth: Double = 0.0,
    val amenities: List<String> = emptyList(),
    val propertyImageUrls: List<String> = emptyList(),
    val selfieUrl: String? = null,
    val idCardUrl: String? = null,
    val idCardBackUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var status: String = "pending",
    val isSecure: Boolean = false,
    val applicationDate: Long = System.currentTimeMillis(),
    var reviewedBy: String = "",
    var reviewedAt: Long = 0,
    var rejectionReason: String = "",
    var notes: String = ""
) : Parcelable {

    fun isPending(): Boolean = status.equals("PENDING", ignoreCase = true)
    fun isApproved(): Boolean = status.equals("APPROVED", ignoreCase = true)
    fun isRejected(): Boolean = status.equals("REJECTED", ignoreCase = true)

    fun getFormattedApplicationDate(): String {
        val date = Date(applicationDate)
        val format = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return format.format(date)
    }

    fun getFormattedPrice(): String {
        return "KSh ${String.format("%,.0f", pricePerMonth)}/month"
    }

    fun getFormattedLocation(): String {
        return propertyLocation.ifEmpty { "Location not specified" }
    }

    fun getRoomText(): String {
        return when (numberOfRooms) {
            0 -> "Studio"
            1 -> "1 Room"
            else -> "$numberOfRooms Rooms"
        }
    }

    fun getAmenitiesText(): String {
        return if (amenities.isEmpty()) {
            "No amenities specified"
        } else {
            amenities.joinToString(", ")
        }
    }

    fun hasDocuments(): Boolean {
        return idCardUrl != null && idCardBackUrl != null && selfieUrl != null
    }

    fun getStatusColorRes(): Int {
        return when (status) {
            "APPROVED" -> android.R.color.holo_green_dark
            "REJECTED" -> android.R.color.holo_red_dark
            else -> android.R.color.holo_orange_dark
        }
    }

    fun getStatusDisplayText(): String {
        return status.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    companion object {
        fun fromDocument(documentId: String, document: Map<String, Any>): CaretakerApplication {
            return CaretakerApplication(
                documentId = documentId,
                userId = document["userId"] as? String ?: "",
                fullName = document["fullName"] as? String ?: "",
                email = document["email"] as? String ?: "",
                phone = document["phone"] as? String ?: "",
                idNumberEncrypted = document["idNumber"] as? String ?: document["idNumberEncrypted"] as? String ?: "",
                nationality = document["nationality"] as? String ?: "",
                propertyType = document["propertyType"] as? String ?: "",
                propertyLocation = document["propertyLocation"] as? String ?: "",
                propertyDescription = document["propertyDescription"] as? String ?: "",
                numberOfRooms = (document["numberOfRooms"] as? Long)?.toInt() ?: 0,
                pricePerMonth = document["pricePerMonth"] as? Double ?: 0.0,
                amenities = document["amenities"] as? List<String> ?: emptyList(),
                propertyImageUrls = document["propertyImageUrls"] as? List<String> ?: emptyList(),
                selfieUrl = document["selfieUrl"] as? String,
                idCardUrl = document["idCardUrl"] as? String,
                idCardBackUrl = document["idCardBackUrl"] as? String,
                latitude = document["latitude"] as? Double,
                longitude = document["longitude"] as? Double,
                status = (document["status"] as? String)?.uppercase() ?: STATUS_PENDING,
                isSecure = document["isSecure"] as? Boolean ?: false,
                applicationDate = (document["applicationDate"] as? com.google.firebase.Timestamp)?.toDate()?.time
                    ?: (document["applicationDate"] as? Long) ?: (document["submittedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
                reviewedBy = document["reviewedBy"] as? String ?: "",
                reviewedAt = (document["reviewedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                    ?: (document["reviewedAt"] as? Long) ?: 0,
                rejectionReason = document["rejectionReason"] as? String ?: "",
                notes = document["notes"] as? String ?: ""
            )
        }

        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"

        val PROPERTY_TYPES = listOf(
            "Bedsitter", "One Bedroom", "Two Bedroom", "Three Bedroom",
            "Four Bedroom", "Studio", "Apartment", "House", "Commercial Property"
        )

        val COMMON_AMENITIES = listOf(
            "WiFi", "Parking", "Security", "Water",
            "Furnished", "Garden", "Balcony", "Swimming Pool", "Gym"
        )
    }
}
