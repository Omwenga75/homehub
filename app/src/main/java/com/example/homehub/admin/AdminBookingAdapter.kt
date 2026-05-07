package com.example.homehub.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.billing.Booking
import com.example.homehub.utils.ProfilePictureUtils
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.other.Extensions.loadPropertyImage

class AdminBookingAdapter(
    private var allBookings: List<Booking>,
    private val onItemClick: (Booking, isDelete: Boolean) -> Unit
) : RecyclerView.Adapter<AdminBookingAdapter.AdminBookingViewHolder>() {

    private var filteredBookings: List<Booking> = allBookings
    private val nameCache = mutableMapOf<String, String>()

    inner class AdminBookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStudentName: TextView = itemView.findViewById(R.id.tvStudentName)
        val tvStudentPhone: TextView = itemView.findViewById(R.id.tvStudentPhone)
        val tvPropertyName: TextView = itemView.findViewById(R.id.tvPropertyName)
        val tvRoomInfo: TextView = itemView.findViewById(R.id.tvRoomInfo)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val cardStatus: MaterialCardView = itemView.findViewById(R.id.cardStatus)
        val tvReceipt: TextView = itemView.findViewById(R.id.tvReceipt)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val ivPropertyImage: ImageView = itemView.findViewById(R.id.ivPropertyImage)
        val btnDelete: View = itemView.findViewById(R.id.btnDeleteBooking)

        fun bind(booking: Booking) {
            // First clear any XML placeholders to prevent "ghost" data
            // Set initial name (either from booking or cache)
            val currentName = booking.studentName.ifBlank { "Unknown Student" }
            if (currentName == "Unknown Student" || currentName == "Student") {
                val cached = nameCache[booking.studentId]
                if (cached != null) {
                    tvStudentName.text = cached
                } else {
                    tvStudentName.text = currentName
                    // Fetch real name from Firestore if it's a generic placeholder
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(booking.studentId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val realName = doc.getString("fullName") ?: doc.getString("username") ?: "Student"
                                nameCache[booking.studentId] = realName
                                // Only update if this holder is still showing the same booking
                                if (adapterPosition != RecyclerView.NO_POSITION) {
                                    tvStudentName.text = realName
                                }
                            }
                        }
                }
            } else {
                tvStudentName.text = currentName
            }

            tvStudentPhone.text = booking.studentPhone.ifBlank { "No Contact" }
            tvPropertyName.text = booking.propertyName.ifBlank { "Unknown Property" }
            
            var roomInfo = if (booking.roomTypeName.isNotEmpty()) booking.roomTypeName else ""
            if (booking.roomNumber.isNotEmpty()) {
                roomInfo += if (roomInfo.isNotEmpty()) " (Room ${booking.roomNumber})" else "Room ${booking.roomNumber}"
            }
            tvRoomInfo.text = roomInfo.ifEmpty { "Whole Property" }
            
            tvAmount.text = booking.getFormattedAmount()
            tvReceipt.text = booking.mpesaReceiptNumber.ifEmpty { booking.mpesaTransactionId.ifEmpty { "N/A" } }
            
            // Status Display Logic
            tvStatus.text = booking.status.uppercase()
            val statusColorRes = when (booking.status.lowercase()) {
                "confirmed" -> R.color.green
                "active" -> R.color.blue
                "pending" -> R.color.amber_500
                "cancelled", "failed" -> R.color.error
                "completed" -> R.color.primary_dark
                else -> R.color.gray_500
            }
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(itemView.context, statusColorRes))

            // Date formatting
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            tvDate.text = if (booking.createdAt != null) sdf.format(booking.createdAt) else "Unknown Date"

            // 1. Load Student Profile Image
            itemView.findViewById<ImageView>(R.id.studentIcon).loadProfileImage(booking.studentId, booking.studentImage)

            // 2. Load Property Image
            ivPropertyImage.loadPropertyImage(booking.propertyImage)

            itemView.setOnClickListener { onItemClick(booking, false) }
            btnDelete.setOnClickListener { onItemClick(booking, true) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminBookingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking_admin, parent, false)
        return AdminBookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminBookingViewHolder, position: Int) {
        holder.bind(filteredBookings[position])
    }

    override fun getItemCount(): Int = filteredBookings.size

    fun updateList(newBookings: List<Booking>) {
        this.allBookings = newBookings
        this.filteredBookings = newBookings
        notifyDataSetChanged()
    }

    fun filter(query: String, status: String) {
        filteredBookings = allBookings.filter { booking ->
            val matchesQuery = booking.studentName.contains(query, ignoreCase = true) ||
                    booking.propertyName.contains(query, ignoreCase = true) ||
                    booking.mpesaReceiptNumber.contains(query, ignoreCase = true) ||
                    booking.mpesaTransactionId.contains(query, ignoreCase = true)
            
            val matchesStatus = status == "All" || booking.status.equals(status, ignoreCase = true)
            
            matchesQuery && matchesStatus
        }
        notifyDataSetChanged()
    }
}
