package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.example.homehub.other.Extensions.loadPropertyImage

/**
 * A unified adapter for displaying [Property] items using ListAdapter for smooth updates.
 */
class UnifiedPropertyAdapter(
    private val layoutResId: Int,
    private val onItemClick: ((Property, View) -> Unit)? = null,
    private val onFavoriteClick: ((Property, View) -> Unit)? = null,
    private val onMoreClick: ((Property, View) -> Unit)? = null
) : ListAdapter<Property, UnifiedPropertyAdapter.PropertyViewHolder>(PropertyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return PropertyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class PropertyDiffCallback : DiffUtil.ItemCallback<Property>() {
        override fun areItemsTheSame(oldItem: Property, newItem: Property): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Property, newItem: Property): Boolean {
            return oldItem == newItem
        }
    }

    inner class PropertyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(property: Property, position: Int) {
            // Title: propertyName, propertyTitle, tvHouseTitle, or titleText
            val tvTitle = itemView.findViewById<TextView?>(R.id.propertyName)
                ?: itemView.findViewById<TextView?>(R.id.propertyTitle)
                ?: itemView.findViewById<TextView?>(R.id.tvHouseTitle)
                ?: itemView.findViewById<TextView?>(R.id.titleText)
            tvTitle?.text = property.displayTitle

            // Location: propertyLocation, tvLocation, or locationText
            val tvLocation = itemView.findViewById<TextView?>(R.id.propertyLocation)
                ?: itemView.findViewById<TextView?>(R.id.tvLocation)
                ?: itemView.findViewById<TextView?>(R.id.locationText)
            tvLocation?.text = property.location

            // Price with /mo suffix for the card overlay
            val tvPrice = itemView.findViewById<TextView?>(R.id.propertyPrice)
                ?: itemView.findViewById<TextView?>(R.id.tvPrice)
                ?: itemView.findViewById<TextView?>(R.id.priceTag)
                ?: itemView.findViewById<TextView?>(R.id.priceText)
            tvPrice?.text = "${property.getFormattedPrice()}/month"

            // Status badge (if present in layout)
            val tvStatus = itemView.findViewById<TextView?>(R.id.propertyStatus)
                ?: itemView.findViewById<TextView?>(R.id.statusBadge)
            if (tvStatus != null) {
                tvStatus.text = property.getStatusBadgeText()
                
                // Update Badge Appearance based on status
                when (property.status.lowercase()) {
                    "active" -> {
                        tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(itemView.context.resources.getColor(R.color.green_100))
                        tvStatus.setTextColor(itemView.context.resources.getColor(R.color.green_700))
                    }
                    "booked", "rented" -> {
                        tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(itemView.context.resources.getColor(R.color.orange_50))
                        tvStatus.setTextColor(itemView.context.resources.getColor(R.color.orange_700))
                    }
                    "inactive" -> {
                        tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(itemView.context.resources.getColor(R.color.grey_100))
                        tvStatus.setTextColor(itemView.context.resources.getColor(R.color.grey_700))
                    }
                }
            }

            // Rooms chip / status
            val tvRooms = itemView.findViewById<TextView?>(R.id.tvRooms)
                ?: itemView.findViewById<TextView?>(R.id.roomsText)
                ?: itemView.findViewById<TextView?>(R.id.tvArea)
            if (tvRooms != null) {
                tvRooms.text = property.getAvailableRoomsDisplay()
            }
            
            // Featured badge
            itemView.findViewById<TextView?>(R.id.tvFeatured)?.visibility = 
                if (property.isFeatured) View.VISIBLE else View.GONE

            // Likes count (replacing Rating)
            val tvLikes = itemView.findViewById<TextView?>(R.id.likesCount)
                ?: itemView.findViewById<TextView?>(R.id.propertyRating)
                ?: itemView.findViewById<TextView?>(R.id.tvRating)
                ?: itemView.findViewById<TextView?>(R.id.ratingText)
            tvLikes?.text = property.likeCount.toString()

            // View Count (replacing Reviews count)
            val tvViews = itemView.findViewById<TextView?>(R.id.viewsCount)
                ?: itemView.findViewById<TextView?>(R.id.tvReviews)
            tvViews?.text = property.viewCount.toString()

            // Image: propertyImage or ivHouseImage — loads REAL caretaker photos
            val imageView = itemView.findViewById<ImageView?>(R.id.propertyImage)
                ?: itemView.findViewById<ImageView?>(R.id.ivHouseImage)
            
            if (imageView != null) {
                val imagePath = property.getFirstImagePath()
                imageView.loadPropertyImage(imagePath)
            }

            // Like button: cardFavorite container or ivFavorite directly
            val likeBtn = itemView.findViewById<View?>(R.id.cardFavorite)
                ?: itemView.findViewById<View?>(R.id.ivFavorite)
                ?: itemView.findViewById<View?>(R.id.likeButton)
            
            likeBtn?.setOnClickListener { v ->
                onFavoriteClick?.invoke(property, v)
            }
            
            // Handle like icon state
            val ivLike = itemView.findViewById<ImageView?>(R.id.ivFavorite)
                ?: itemView.findViewById<ImageView?>(R.id.likeButton)
            
            ivLike?.setImageResource(R.drawable.baseline_favorite_24)
            ivLike?.imageTintList = android.content.res.ColorStateList.valueOf(
                if (property.isLiked || property.isFavorite) 
                    itemView.context.resources.getColor(R.color.pink_700)
                else 
                    itemView.context.resources.getColor(R.color.gray)
            )

            // Settings/More button wrapper (Crucial for Caretaker functions)
            val btnMore = itemView.findViewById<View?>(R.id.btnMore)
            btnMore?.setOnClickListener { v ->
                onMoreClick?.invoke(property, v)
            }

            // Whole-card click
            itemView.setOnClickListener { v ->
                onItemClick?.invoke(property, v)
            }
        }
    }
}
