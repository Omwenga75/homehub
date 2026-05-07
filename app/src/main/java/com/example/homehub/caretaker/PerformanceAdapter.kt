package com.example.homehub.caretaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.property.Property
import com.example.homehub.other.Extensions.loadPropertyImage

class PerformanceAdapter(private val properties: List<Property>) :
    RecyclerView.Adapter<PerformanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPropertyImage: ImageView = view.findViewById(R.id.ivPropertyImage)
        val tvPropertyTitle: TextView = view.findViewById(R.id.tvPropertyTitle)
        val tvPropertyLocation: TextView = view.findViewById(R.id.tvPropertyLocation)
        val tvLikes: TextView = view.findViewById(R.id.tvLikes)
        val tvViews: TextView = view.findViewById(R.id.tvViews)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_caretaker_analytics, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val property = properties[position]
        
        holder.tvPropertyTitle.text = property.displayTitle
        holder.tvPropertyLocation.text = property.location.ifBlank { "Location TBD" }
        holder.tvLikes.text = property.likeCount.toString()
        holder.tvViews.text = property.viewCount.toString()
        
        val imagePath = property.getFirstImagePath()
        if (imagePath != null) {
            holder.ivPropertyImage.loadPropertyImage(imagePath)
        } else {
            holder.ivPropertyImage.setImageResource(R.drawable.ic_house_placeholder)
        }
    }

    override fun getItemCount() = properties.size
}
