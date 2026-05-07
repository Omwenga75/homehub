package com.example.homehub.property

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoomType(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val totalQuantity: Int = 0,
    var availableQuantity: Int = 0,
    var imageUrl: String = "",
    val description: String = ""
) : Parcelable {
    val formattedPrice: String get() = "Ksh " + String.format("%,.0f", price)
    val isAvailable: Boolean get() = availableQuantity > 0

    companion object {
        fun fromDocument(map: Map<String, Any>): RoomType {
            fun doubleFrom(value: Any?): Double = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            fun intFrom(value: Any?): Int = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }

            return RoomType(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                price = doubleFrom(map["price"]),
                totalQuantity = intFrom(map["totalQuantity"]),
                availableQuantity = intFrom(map["availableQuantity"]),
                imageUrl = map["imageUrl"] as? String ?: "",
                description = map["description"] as? String ?: ""
            )
        }
    }
}
