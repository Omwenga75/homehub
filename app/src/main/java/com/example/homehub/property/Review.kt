package com.example.homehub.property

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.example.homehub.R

@Parcelize
data class Review(
    val reviewerName: String = "",
    val reviewText: String = "",
    val date: String = "",
    val reviewerImage: Int = R.drawable.ic_profile,
    val rating: Double = 5.0,
    val isLeftAligned: Boolean = true,
    val reviewerId: String = "",
    val propertyId: String = "",
    val caretakerId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false
) : Parcelable {
    companion object {
        fun fromDocument(data: Map<String, Any>): Review {
            return Review(
                reviewerName = data["reviewerName"] as? String ?: "Anonymous Guest",
                reviewText = data["reviewText"] as? String ?: "",
                date = data["date"] as? String ?: "",
                reviewerImage = (data["reviewerImage"] as? Number)?.toInt() ?: R.drawable.ic_profile,
                rating = (data["rating"] as? Number)?.toDouble() ?: 5.0,
                isLeftAligned = data["isLeftAligned"] as? Boolean ?: true,
                reviewerId = data["reviewerId"] as? String ?: "",
                propertyId = data["propertyId"] as? String ?: "",
                caretakerId = data["caretakerId"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isVerified = data["isVerified"] as? Boolean ?: false
            )
        }
    }
}
