package com.example.homehub.property

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class Room(
    var id: String = "",
    var propertyId: String = "",
    var roomNumber: String = "",
    var roomType: String = "Bedsitter",
    var price: Double = 0.0,
    var isAvailable: Boolean = true,
    var imageUrls: List<String> = emptyList(),
    var features: List<String> = emptyList(),
    var description: String = "",
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var inactiveAt: Date? = null,
    var inactiveBy: String = "",
    var adminAction: Boolean = false,
    var bookedBy: String = "",
    var bookedAt: Date? = null
) : Parcelable {

    val formattedPrice: String
        get() = "KSh ${String.format("%,.0f", price)}"

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "propertyId" to propertyId,
            "roomNumber" to roomNumber,
            "roomType" to roomType,
            "price" to price,
            "isAvailable" to isAvailable,
            "imageUrls" to imageUrls,
            "features" to features,
            "description" to description,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "bookedBy" to bookedBy,
            "bookedAt" to bookedAt
        )
    }

    companion object {
        fun fromDocument(data: Map<String, Any>): Room {
            fun dateFrom(value: Any?): Date = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }
            fun dateOrNull(value: Any?): Date? = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> null
            }

            return Room(
                id = data["id"] as? String ?: "",
                propertyId = data["propertyId"] as? String ?: "",
                roomNumber = data["roomNumber"] as? String ?: "",
                roomType = data["roomType"] as? String ?: "Bedsitter",
                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                isAvailable = data["isAvailable"] as? Boolean ?: true,
                imageUrls = data["imageUrls"] as? List<String> ?: emptyList(),
                features = data["features"] as? List<String> ?: emptyList(),
                description = data["description"] as? String ?: "",
                createdAt = dateFrom(data["createdAt"]),
                updatedAt = dateFrom(data["updatedAt"]),
                bookedBy = data["bookedBy"] as? String ?: "",
                bookedAt = dateOrNull(data["bookedAt"])
            )
        }
    }
}
