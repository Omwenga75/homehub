package com.example.homehub.caretaker

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.homehub.R

@Parcelize
data class Caretaker(
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val isVerified: Boolean,
    val verificationDate: Long,
    val verifiedBy: String,
    val propertyType: String,
    val propertyLocation: String,
    val status: String,
    val joinDate: Long,
    val totalProperties: Int,
    val totalBookings: Int,
    val totalEarnings: Double,
    val rating: Double,
    val profileImageUrl: String = ""
) : Parcelable {

    // Enhanced helper methods for better data handling
    fun getFormattedVerificationDate(): String {
        return if (verificationDate > 0) {
            val date = Date(verificationDate)
            val format = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            format.format(date)
        } else {
            "Not Verified"
        }
    }

    fun getFormattedJoinDate(): String {
        val date = Date(joinDate)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }

    fun getFormattedEarnings(): String {
        return "${String.format("%,.0f", totalEarnings)}"
    }

    fun getFormattedLikes(): String {
        return String.format("%.1f", rating)
    }

    fun getStatusBadgeResource(): Int {
        return when (status.lowercase()) {
            "active" -> R.drawable.badge_approved
            "suspended" -> R.drawable.badge_rejected
            "pending" -> R.drawable.badge_pending
            "rejected" -> R.drawable.badge_rejected
            else -> R.drawable.badge_pending
        }
    }

    fun getStatusDisplayText(): String {
        return when (status.lowercase()) {
            "active" -> "ACTIVE"
            "suspended" -> "SUSPENDED"
            "pending" -> "PENDING"
            "rejected" -> "REJECTED"
            else -> status.uppercase()
        }
    }

    fun isActive(): Boolean = status.equals("active", ignoreCase = true)
    fun isSuspended(): Boolean = status.equals("suspended", ignoreCase = true)
    fun isPending(): Boolean = status.equals("pending", ignoreCase = true)
    fun isRejected(): Boolean = status.equals("rejected", ignoreCase = true)

    fun getDisplayName(): String {
        return fullName.ifEmpty { "Unknown Caretaker" }
    }

    fun getDisplayLocation(): String {
        return propertyLocation.ifEmpty { "Location not specified" }
    }

    fun getDisplayPropertyType(): String {
        return propertyType.ifEmpty { "Not specified" }
    }

    companion object {
        fun empty(): Caretaker {
            return Caretaker(
                userId = "",
                fullName = "",
                email = "",
                phone = "",
                isVerified = false,
                verificationDate = 0L,
                verifiedBy = "",
                propertyType = "",
                propertyLocation = "",
                status = "inactive",
                joinDate = System.currentTimeMillis(),
                totalProperties = 0,
                totalBookings = 0,
                totalEarnings = 0.0,
                rating = 0.0,
                profileImageUrl = ""
            )
        }

        fun fromIntent(intent: Intent, key: String = "caretaker"): Caretaker? {
            return try {
                intent.getParcelableExtra(key)
            } catch (e: Exception) {
                null
            }
        }

        private fun toLong(value: Any?): Long {
            return when (value) {
                is Long -> value
                is com.google.firebase.Timestamp -> value.toDate().time
                is java.util.Date -> value.time
                is Double -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        fun fromDocument(document: Map<String, Any>): Caretaker {
            return try {
                val userId = document["userId"] as? String ?: document["id"] as? String ?: ""
                Caretaker(
                    userId = userId,
                    fullName = document["fullName"] as? String
                        ?: document["caretakerName"] as? String
                        ?: document["name"] as? String
                        ?: "",
                    email = document["email"] as? String ?: "",
                    phone = document["phone"] as? String ?: document["phoneNumber"] as? String ?: "",
                    isVerified = document["isVerified"] as? Boolean ?: false,
                    verificationDate = toLong(document["verificationDate"] ?: document["verifiedAt"]),
                    verifiedBy = document["verifiedBy"] as? String ?: "System",
                    propertyType = document["propertyType"] as? String ?: "",
                    propertyLocation = document["propertyLocation"] as? String
                        ?: document["location"] as? String
                        ?: "",
                    status = (document["status"] as? String)?.lowercase() ?: "active",
                    joinDate = toLong(document["joinDate"] ?: document["createdAt"] ?: document["timestamp"]).let {
                        if (it == 0L) System.currentTimeMillis() else it
                    },
                    totalProperties = (document["totalProperties"] as? Number)?.toInt()
                        ?: (document["propertiesCount"] as? Number)?.toInt()
                        ?: 0,
                    totalBookings = (document["totalBookings"] as? Number)?.toInt()
                        ?: (document["bookingsCount"] as? Number)?.toInt()
                        ?: 0,
                    totalEarnings = (document["totalEarnings"] as? Number)?.toDouble()
                        ?: (document["earnings"] as? Number)?.toDouble()
                        ?: 0.0,
                    rating = (document["rating"] as? Number)?.toDouble()
                        ?: (document["caretakerRating"] as? Number)?.toDouble()
                        ?: 0.0,
                    profileImageUrl = document["profileImageUrl"] as? String 
                        ?: document["profilePictureUrl"] as? String 
                        ?: document["image"] as? String 
                        ?: ""
                )
            } catch (e: Exception) {
                empty()
            }
        }

        fun fromFirestoreDocument(documentId: String, data: Map<String, Any>): Caretaker {
            val caretaker = fromDocument(data)
            return if (caretaker.userId.isEmpty()) {
                caretaker.copy(userId = documentId)
            } else {
                caretaker
            }
        }

        fun isValid(caretaker: Caretaker): Boolean {
            return caretaker.userId.isNotEmpty() && caretaker.fullName.isNotEmpty()
        }
    }
}

fun Caretaker?.orEmpty(): Caretaker {
    return this ?: Caretaker.empty()
}

fun Caretaker?.isValidCaretaker(): Boolean {
    return this != null && Caretaker.isValid(this)
}

fun List<Caretaker>.filterActive(): List<Caretaker> = this.filter { it.isActive() }
fun List<Caretaker>.filterVerified(): List<Caretaker> = this.filter { it.isVerified }
fun List<Caretaker>.filterSuspended(): List<Caretaker> = this.filter { it.isSuspended() }
fun List<Caretaker>.filterRejected(): List<Caretaker> = this.filter { it.isRejected() }
fun List<Caretaker>.sortByRatingDesc(): List<Caretaker> = this.sortedByDescending { it.rating }
fun List<Caretaker>.sortByEarningsDesc(): List<Caretaker> = this.sortedByDescending { it.totalEarnings }
fun List<Caretaker>.sortByPropertiesDesc(): List<Caretaker> = this.sortedByDescending { it.totalProperties }
