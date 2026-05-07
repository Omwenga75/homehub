package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.google.android.material.button.MaterialButton

class RoomTypeSelectionAdapter(
    private var roomTypes: List<RoomType>,
    private val onSelected: (RoomType) -> Unit
) : RecyclerView.Adapter<RoomTypeSelectionAdapter.RoomTypeViewHolder>() {

    fun updateRooms(newList: List<RoomType>) {
        roomTypes = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomTypeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_selection, parent, false)
        return RoomTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomTypeViewHolder, position: Int) {
        holder.bind(roomTypes[position])
    }

    override fun getItemCount(): Int = roomTypes.size

    inner class RoomTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivRoom: ImageView = itemView.findViewById(R.id.ivRoom)
        private val tvRoomNumber: TextView = itemView.findViewById(R.id.tvUnitNumber)
        private val tvRoomType: TextView = itemView.findViewById(R.id.tvUnitType)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val btnSelect: MaterialButton = itemView.findViewById(R.id.btnBookUnit)

        fun bind(roomType: RoomType) {
            // Repurpose item_room_selection.xml
            tvRoomNumber.text = roomType.name
            tvRoomType.text = if (roomType.availableQuantity > 0) "${roomType.availableQuantity} available" else "Sold Out"
            tvPrice.text = roomType.formattedPrice

            if (roomType.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(roomType.imageUrl).placeholder(R.drawable.hs).into(ivRoom)
            } else {
                ivRoom.setImageResource(R.drawable.hs)
            }

            btnSelect.isEnabled = roomType.availableQuantity > 0
            btnSelect.text = if (roomType.availableQuantity > 0) "Select" else "Sold Out"
            
            btnSelect.setOnClickListener { onSelected(roomType) }
        }
    }
}
