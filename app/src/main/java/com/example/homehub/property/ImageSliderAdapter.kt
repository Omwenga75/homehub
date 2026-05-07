package com.example.homehub.property

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.R

class ImageSliderAdapter(private val images: List<String>) : RecyclerView.Adapter<ImageSliderAdapter.SliderViewHolder>() {

    inner class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.sliderImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_slider, parent, false)
        return SliderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        // Check if the activity is still valid before loading images
        val context = holder.itemView.context
        if (context is AppCompatActivity && context.isDestroyed) {
            return  // Activity is destroyed, skip image loading
        }
        
        try {
            Glide.with(holder.itemView)
                .load(images[position])
                .centerCrop()
                .placeholder(R.drawable.ic_house_placeholder)
                .error(R.drawable.ic_house_placeholder)
                .into(holder.imageView)
        } catch (e: Exception) {
            // Silently fail if activity is destroyed or context is invalid
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = images.size
}
