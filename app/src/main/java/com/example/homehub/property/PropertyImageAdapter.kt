package com.example.homehub.property

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.example.homehub.other.Extensions.loadPropertyImage

class PropertyImageAdapter(
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PropertyImageAdapter.ImageViewHolder>() {

    private val images = mutableListOf<Any>()

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.propertyImage)
        val removeBtn: View = view.findViewById(R.id.removeImageButton)
        val label: TextView = view.findViewById(R.id.imageLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_property_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = images[position]
        val (imgSrc, labelText) = when (item) {
            is Pair<*, *> -> (item.first to item.second.toString())
            else -> (item to "")
        }

        holder.image.loadPropertyImage(imgSrc as? String)

        holder.label.text = labelText
        holder.label.visibility = if (labelText.isEmpty()) View.GONE else View.VISIBLE

        holder.removeBtn.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onRemove(pos)
            }
        }
    }

    override fun getItemCount() = images.size

    fun addImage(image: Any) {
        images.add(image)
        notifyItemInserted(images.size - 1)
    }

    fun addImages(newImages: List<Any>) {
        val start = images.size
        images.addAll(newImages)
        notifyItemRangeInserted(start, newImages.size)
    }

    fun removeAt(position: Int) {
        if (position in images.indices) {
            images.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    fun getImages(): List<Any> = images.toList()

    fun updateImages(newImages: List<Any>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun clear() {
        val size = images.size
        images.clear()
        notifyItemRangeRemoved(0, size)
    }
}
