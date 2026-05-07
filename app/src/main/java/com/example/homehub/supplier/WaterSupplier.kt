package com.example.homehub.supplier

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class WaterSupplier(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val rating: Float = 0f,
    val pricePerLitre: Double = 0.0,
    val drinkingPrice: Double = 0.0,
    val unitSize: String = "20L",
    val deliveryFee: Double = 0.0,
    val reviewsCount: Int = 0,
    val imageUrl: String = "",
    val businessName: String = "",
    val phone: String = "",
    val serviceArea: String = "",
    val status: String = "active",
    val verificationStatus: String = "none",
    val stockLiters: Int = 1000,
    val cookingPrice: Double = 0.0,
    val cleaningPrice: Double = 0.0,
    val deliveredCount: Int = 0,
    val createdAt: Date = Date()
) : Parcelable {
    companion object {
        fun fromDocument(docId: String, data: Map<String, Any>): WaterSupplier {
            val pricePerLitre = when (val p = data["pricePerLitre"]) {
                is Number -> p.toDouble()
                is String -> p.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val drinkingPrice = when (val p = data["drinkingPrice"]) {
                is Number -> p.toDouble()
                is String -> p.toDoubleOrNull() ?: 0.0
                else -> (data["pricePerUnit"] as? Number)?.toDouble() ?: 0.0
            }

            val deliveryFee = when (val f = data["deliveryFee"]) {
                is Number -> f.toDouble()
                is String -> f.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            return WaterSupplier(
                id = docId,
                name = data["fullName"] as? String ?: data["username"] as? String ?: "",
                email = data["email"] as? String ?: "",
                rating = (data["rating"] as? Number)?.toFloat() ?: 0f,
                pricePerLitre = if (pricePerLitre > 0) pricePerLitre else drinkingPrice,
                drinkingPrice = drinkingPrice,
                unitSize = data["unitSize"] as? String ?: "20L",
                deliveryFee = deliveryFee,
                reviewsCount = (data["reviewsCount"] as? Number)?.toInt() ?: 0,
                imageUrl = data["profileImageUrl"] as? String ?: "",
                businessName = data["businessName"] as? String ?: "",
                phone = data["phone"] as? String ?: "",
                serviceArea = data["serviceArea"] as? String ?: "",
                status = data["status"] as? String ?: "active",
                verificationStatus = data["verificationStatus"] as? String ?: "none",
                stockLiters = (data["stockLiters"] as? Number)?.toInt() ?: 1000,
                cookingPrice = (data["cookingPrice"] as? Number)?.toDouble() ?: 0.0,
                cleaningPrice = (data["cleaningPrice"] as? Number)?.toDouble() ?: 0.0,
                deliveredCount = (data["deliveredCount"] as? Number)?.toInt() 
                    ?: (data["totalDeliveries"] as? Number)?.toInt() ?: 0,
                createdAt = when (val ts = data["createdAt"]) {
                    is com.google.firebase.Timestamp -> ts.toDate()
                    is Long -> Date(ts)
                    else -> Date()
                }
            )
        }
    }
}
