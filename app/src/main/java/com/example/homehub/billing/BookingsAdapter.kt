package com.example.homehub.billing


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.utils.ProfilePictureUtils // Assuming it might be used, or just for imports

class BookingsAdapter(
    private var bookings: List<Booking>,
    private val onDownloadClick: (Booking) -> Unit,
    private val onItemClick: (Booking) -> Unit
) : RecyclerView.Adapter<BookingsAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val propertyImage: ImageView = itemView.findViewById(R.id.bookingPropertyImage)
        val propertyName: TextView = itemView.findViewById(R.id.bookingPropertyName)
        val location: TextView = itemView.findViewById(R.id.bookingLocation)
        val amount: TextView = itemView.findViewById(R.id.bookingAmount)
        val status: TextView = itemView.findViewById(R.id.bookingStatus)
        val date: TextView = itemView.findViewById(R.id.bookingDate)
        val bookedBy: TextView = itemView.findViewById(R.id.bookedByText)

        fun bind(booking: Booking) {
            if (booking.paymentType == "water") {
                propertyName.text = "Water Order: ${booking.propertyName}"
                location.text = "💧 Water Delivery"
            } else {
                propertyName.text = booking.propertyName
                location.text = "📍 ${booking.propertyLocation}"
            }
            amount.text = booking.getFormattedAmount()
            status.text = booking.getStatusDisplay()
            
            // Format date correctly
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            date.text = sdf.format(booking.bookingDate)
            
            bookedBy.text = "Booked by: ${booking.studentName.ifEmpty { "Registered User" }}"

            // Load image if available
            if (booking.propertyImage.isNotEmpty()) {
                val bitmap = com.example.homehub.utils.ProfilePictureUtils.decodeBase64ToBitmap(booking.propertyImage)
                if (bitmap != null) {
                    propertyImage.setImageBitmap(bitmap)
                } else {
                    propertyImage.setImageResource(R.drawable.ic_house_placeholder)
                }
            } else {
                propertyImage.setImageResource(R.drawable.ic_house_placeholder)
            }

            itemView.findViewById<View>(R.id.btnDownloadReceipt).setOnClickListener {
                onDownloadClick(booking)
            }
            itemView.setOnClickListener { onItemClick(booking) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(bookings[position])
    }

    override fun getItemCount(): Int = bookings.size

    fun updateBookings(newBookings: List<Booking>) {
        this.bookings = newBookings
        notifyDataSetChanged()
    }
}
