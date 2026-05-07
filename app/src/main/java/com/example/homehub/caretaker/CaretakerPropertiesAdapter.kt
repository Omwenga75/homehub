package com.example.homehub.caretaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.property.Property

class CaretakerPropertiesAdapter(
    private var properties: List<Property>,
    private val onItemClick: (Property) -> Unit
) : RecyclerView.Adapter<CaretakerPropertiesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: com.google.android.material.card.MaterialCardView =
            itemView.findViewById(R.id.propertyCard)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val locationText: TextView = itemView.findViewById(R.id.locationText)
        val priceText: TextView = itemView.findViewById(R.id.priceText)
        val typeText: TextView = itemView.findViewById(R.id.typeText)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        val bookingsText: TextView = itemView.findViewById(R.id.bookingsText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_caretaker_properties, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val property = properties[position]

        holder.titleText.text = property.displayTitle
        holder.locationText.text = property.getFormattedLocation()
        holder.priceText.text = property.getFormattedPriceForKenya()
        holder.typeText.text = property.propertyType ?: property.houseType ?: "Apartment"
        
        // Show current occupancy for room-based properties, otherwise show historical bookings
        holder.bookingsText.text = if (property.totalRooms > 0) {
            property.getBookedRoomsDisplay().replace(" Booked", "") // Layout has "Bookings" label below
        } else {
            "${property.totalBookings}"
        }

        holder.statusBadge.text = property.getStatusBadgeText()
        holder.statusBadge.setBackgroundResource(property.getStatusBadgeColorRes())

        holder.cardView.setOnClickListener {
            onItemClick(property)
        }
    }

    override fun getItemCount(): Int = properties.size

    fun updateProperties(newProperties: List<Property>) {
        properties = newProperties
        notifyDataSetChanged()
    }
}
