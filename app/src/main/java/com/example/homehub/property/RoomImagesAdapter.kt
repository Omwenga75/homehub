package com.example.homehub.property

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.utils.LocalImageManager

class RoomImagesAdapter(
    private val imagePaths: List<String>,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<RoomImagesAdapter.RoomImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_image, parent, false)
        return RoomImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomImagesAdapter.RoomImageViewHolder, position: Int) {
        val imagePath = imagePaths[position]
        holder.bind(imagePath)
    }

    override fun getItemCount(): Int = imagePaths.size

    inner class RoomImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.roomImageView)

        fun bind(imagePath: String) {
            // Load image from local storage
            val bitmap = LocalImageManager.loadImageFromInternalStorage(itemView.context, imagePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                // Fallback to default image
                imageView.setImageResource(R.drawable.ic_house_placeholder)
            }

            itemView.setOnClickListener {
                onImageClick(imagePath)
            }
        }
    }
}
