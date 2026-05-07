package com.example.homehub.billing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class Booking(
    var id: String = "",
    val propertyId: String = "",
    val propertyName: String = "",
    val propertyLocation: String = "",
    val propertyImage: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val studentPhone: String = "",
    val caretakerId: String = "",
    val caretakerName: String = "",
    val amount: Double = 0.0,
    val paymentStatus: String = "pending", // pending, completed, failed, cancelled
    val mpesaReceiptNumber: String = "",
    val mpesaTransactionId: String = "",
    val bookingDate: Date = Date(),
    val leaseStart: Date = Date(),
    val leaseEnd: Date = Date(),
    val status: String = "pending", // pending, confirmed, active, completed, cancelled
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val notes: String = "",
    val studentImage: String = "",
    val roomTypeId: String = "",
    val roomTypeName: String = "",
    val roomNumber: String = "",
    val isCheckedIn: Boolean = false,
    val paymentDeadline: Date? = null,
    val paymentOption: String = "instant", // instant, 24h, 3d
    val paymentType: String = "room" // room, water
) : Parcelable {

    fun isActive(): Boolean {
        return status == "confirmed" || status == "active"
    }

    fun isPending(): Boolean {
        return status == "pending"
    }

    fun isPaymentComplete(): Boolean {
        return paymentStatus == "completed"
    }

    fun isPendingCheckIn(): Boolean {
        return isPaymentComplete() && !isCheckedIn
    }

    fun isDeferredPaymentPending(): Boolean {
        if (paymentStatus != "pending_deferred") return false
        val deadline = paymentDeadline ?: return false
        return deadline.after(Date())
    }

    /**
     * Determines if this booking is "Real" and should be shown in the My Room view.
     * Excludes cancelled, explicitly failed, or expired deferred reservations.
     */
    fun isValidForMyRoom(): Boolean {
        // Exclude generic cancellations
        if (status == "cancelled" || paymentStatus == "cancelled" || paymentStatus == "failed") return false
        
        // Exclude past completions (optional, depends on if user wants history in My Room)
        // if (status == "completed") return false

        // Filter pending deferred bookings: only valid if not expired
        if (paymentStatus == "pending_deferred") {
            val deadline = paymentDeadline ?: return false
            return deadline.after(Date())
        }
        
        // Show if confirmed/active and not a water order
        return (status == "confirmed" || status == "active") && !paymentType.equals("water", ignoreCase = true)
    }

    fun getStatusDisplay(): String {
        return when (status) {
            "pending" -> "⏳ Pending Payment"
            "pending_deferred" -> "🕒 Reserved (Pay Later)"
            "confirmed" -> "✅ Confirmed"
            "active" -> "🏠 Active"
            "completed" -> "✔️ Completed"
            "cancelled" -> "❌ Cancelled"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    }

    fun getPaymentStatusDisplay(): String {
        return when (paymentStatus) {
            "pending" -> "Awaiting Payment"
            "pending_deferred" -> "Pay Later"
            "completed" -> "Paid"
            "failed" -> "Payment Failed"
            "cancelled" -> "Cancelled"
            else -> paymentStatus.replaceFirstChar { it.uppercase() }
        }
    }

    fun getFormattedAmount(): String {
        return "KSh ${String.format("%,.0f", amount)}"
    }

    companion object {
        fun fromDocument(document: Map<String, Any>): Booking {
            fun dateFrom(value: Any?): Date = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }
            fun doubleFrom(value: Any?, fallback: Double = 0.0): Double = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: fallback
                else -> fallback
            }
            fun dateOrNull(value: Any?): Date? = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> null
            }

            return try {
                val createdAt = dateFrom(document["createdAt"])
                val bookingDate = dateFrom(document["bookingDate"])
                val leaseStart = dateFrom(document["leaseStart"])
                val leaseEnd = dateFrom(document["leaseEnd"])
                val amount = doubleFrom(document["amount"])

                Booking(
                    id = document["id"] as? String ?: "",
                    propertyId = document["propertyId"] as? String ?: "",
                    propertyName = document["propertyName"] as? String ?: "",
                    propertyLocation = document["propertyLocation"] as? String ?: "",
                    propertyImage = document["propertyImage"] as? String ?: "",
                    studentId = document["studentId"] as? String ?: "",
                    studentName = document["studentName"] as? String ?: "",
                    studentEmail = document["studentEmail"] as? String ?: "",
                    studentPhone = document["studentPhone"] as? String ?: "",
                    caretakerId = document["caretakerId"] as? String ?: document["hostId"] as? String ?: "",
                    caretakerName = document["caretakerName"] as? String ?: document["hostName"] as? String ?: "",
                    amount = amount,
                    paymentStatus = document["paymentStatus"] as? String ?: "pending",
                    mpesaReceiptNumber = document["mpesaReceiptNumber"] as? String ?: document["mpesaReceipt"] as? String ?: "",
                    mpesaTransactionId = document["mpesaTransactionId"] as? String ?: "",
                    bookingDate = bookingDate,
                    leaseStart = leaseStart,
                    leaseEnd = leaseEnd,
                    status = document["status"] as? String ?: "pending",
                    createdAt = createdAt,
                    notes = document["notes"] as? String ?: "",
                    studentImage = document["studentImage"] as? String ?: "",
                    roomTypeId = document["roomTypeId"] as? String ?: "",
                    roomTypeName = document["roomTypeName"] as? String ?: "",
                    roomNumber = document["roomNumber"] as? String ?: "",
                    isCheckedIn = document["isCheckedIn"] as? Boolean ?: false,
                    paymentDeadline = dateOrNull(document["paymentDeadline"]),
                    paymentOption = document["paymentOption"] as? String ?: "instant",
                    paymentType = document["paymentType"] as? String ?: "room"
                )
            } catch (e: Exception) {
                Booking()
            }
        }
    }
}
