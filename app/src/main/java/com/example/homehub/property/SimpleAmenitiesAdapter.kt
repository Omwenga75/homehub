package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R

class SimpleAmenitiesAdapter(private val amenities: List<String>) :
    RecyclerView.Adapter<SimpleAmenitiesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val amenityText: TextView = itemView.findViewById(R.id.amenityText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_amenity_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.amenityText.text = "• ${amenities[position]}"
    }

    override fun getItemCount(): Int = amenities.size
}
