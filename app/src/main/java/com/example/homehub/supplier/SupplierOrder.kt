package com.example.homehub.supplier

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class SupplierOrder(
    val orderId: String = "",
    val supplierId: String = "",
    val studentId: String = "",
    val amount: Double = 0.0,
    val status: String = "pending",
    val waterType: String = "Water",
    val paymentMethod: String = "M-PESA",
    val deliveryAddress: String = "",
    val contactPhone: String = "",
    val isCheckedIn: Boolean = false,
    val timestamp: Date = Date()
) : Parcelable {
    companion object {
        fun fromDocument(docId: String, data: Map<String, Any>): SupplierOrder {
            return SupplierOrder(
                orderId = docId,
                supplierId = data["supplierId"] as? String ?: "",
                studentId = data["studentId"] as? String ?: "",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                status = data["status"] as? String ?: "pending",
                waterType = data["waterType"] as? String ?: "Water",
                paymentMethod = data["paymentMethod"] as? String ?: "M-PESA",
                deliveryAddress = data["deliveryAddress"] as? String ?: "",
                contactPhone = data["contactPhone"] as? String ?: "",
                isCheckedIn = data["isCheckedIn"] as? Boolean ?: false,
                timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
            )
        }
    }
}
