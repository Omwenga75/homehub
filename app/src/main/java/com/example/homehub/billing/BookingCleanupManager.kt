package com.example.homehub.billing

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * Utility to centralize logic for cleaning up expired reservations.
 */
object BookingCleanupManager {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Checks if a pending deferred booking has expired and cancels it if necessary.
     */
    fun checkAndCancelIfExpired(booking: Booking, onComplete: ((Boolean) -> Unit)? = null) {
        if (booking.paymentStatus != "pending_deferred") {
            onComplete?.invoke(false)
            return
        }

        val deadline = booking.paymentDeadline
        if (deadline != null && deadline.before(Date())) {
            Log.d("BookingCleanup", "Cancelling expired booking: ${booking.id}")
            cancelBooking(booking, onComplete)
        } else {
            onComplete?.invoke(false)
        }
    }

    /**
     * Transactionally cancels a booking and releases the associated room/property availability.
     */
    private fun cancelBooking(booking: Booking, onComplete: ((Boolean) -> Unit)?) {
        db.runTransaction { transaction ->
            val propertyRef = db.collection("properties").document(booking.propertyId)
            val propertySnapshot = transaction.get(propertyRef)
            
            if (propertySnapshot.exists()) {
                // 1. Release room number if specific room was booked
                if (booking.roomNumber.isNotEmpty()) {
                    val statuses = (propertySnapshot.get("roomStatuses") as? Map<String, String> ?: emptyMap()).toMutableMap()
                    if (statuses[booking.roomNumber] == "Booked") {
                        statuses[booking.roomNumber] = "Available"
                        transaction.update(propertyRef, "roomStatuses", statuses)
                        
                        val currentAvailable = (propertySnapshot.getLong("availableRooms") ?: 0L).toInt()
                        transaction.update(propertyRef, "availableRooms", (currentAvailable + 1).coerceAtLeast(1))
                    }
                } 
                // 2. Or release quantity if room type was booked
                else if (booking.roomTypeId.isNotEmpty()) {
                    val roomTypes = propertySnapshot.get("roomTypes") as? List<Map<String, Any>> ?: emptyList()
                    val updatedRoomTypes = roomTypes.map { type ->
                        val mutableType = type.toMutableMap()
                        if (type["id"] == booking.roomTypeId) {
                            val currentQty = (type["availableQuantity"] as? Number)?.toLong() ?: 0L
                            mutableType["availableQuantity"] = currentQty + 1
                        }
                        mutableType
                    }
                    transaction.update(propertyRef, "roomTypes", updatedRoomTypes)
                    
                    val currentAvailable = (propertySnapshot.getLong("availableRooms") ?: 0L).toInt()
                    transaction.update(propertyRef, "availableRooms", currentAvailable + 1)
                }
                
                // 3. Ensure property is Active and Available
                transaction.update(propertyRef, "status", "Active")
                transaction.update(propertyRef, "available", true)
            }

            // 4. Mark booking as cancelled instead of deleting to keep history
            val bookingRef = db.collection("bookings").document(booking.id)
            transaction.update(bookingRef, 
                "status", "cancelled",
                "paymentStatus", "cancelled",
                "notes", "Reservation expired (Auto-cancelled by system)",
                "updatedAt", Date()
            )
        }.addOnSuccessListener {
            Log.d("BookingCleanup", "Successfully cancelled booking ${booking.id}")
            onComplete?.invoke(true)
        }.addOnFailureListener { e ->
            Log.e("BookingCleanup", "Failed to cancel booking ${booking.id}", e)
            onComplete?.invoke(false)
        }
    }
}
