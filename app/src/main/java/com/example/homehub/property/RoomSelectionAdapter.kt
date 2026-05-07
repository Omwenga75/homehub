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

class RoomSelectionAdapter(
    private var rooms: List<Room>,
    private val onBookClick: (Room) -> Unit
) : RecyclerView.Adapter<RoomSelectionAdapter.RoomSelectionViewHolder>() {

    fun updateRooms(newRooms: List<Room>) {
        rooms = newRooms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomSelectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_selection, parent, false)
        return RoomSelectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomSelectionViewHolder, position: Int) {
        val room = rooms[position]
        holder.bind(room)
    }

    override fun getItemCount(): Int = rooms.size

    inner class RoomSelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivRoom: ImageView = itemView.findViewById(R.id.ivRoom)
        private val tvUnitNumber: TextView = itemView.findViewById(R.id.tvUnitNumber)
        private val tvUnitType: TextView = itemView.findViewById(R.id.tvUnitType)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val btnBookUnit: MaterialButton = itemView.findViewById(R.id.btnBookUnit)

        fun bind(room: Room) {
            tvUnitNumber.text = "Unit ${room.roomNumber}"
            tvUnitType.text = room.roomType
            tvPrice.text = "${room.formattedPrice} / month"
            
            val firstImage = room.imageUrls.firstOrNull()
            if (firstImage != null) {
                Glide.with(itemView.context).load(firstImage).placeholder(R.drawable.hs).into(ivRoom)
            } else {
                ivRoom.setImageResource(R.drawable.hs)
            }

            btnBookUnit.setOnClickListener { onBookClick(room) }
            
            // If room is not available, disable booking
            if (!room.isAvailable) {
                btnBookUnit.isEnabled = false
                btnBookUnit.text = "Booked"
                btnBookUnit.alpha = 0.5f
            } else {
                btnBookUnit.isEnabled = true
                btnBookUnit.text = "Book"
                btnBookUnit.alpha = 1.0f
            }
        }
    }
}
