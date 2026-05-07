package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.R
import com.google.android.material.switchmaterial.SwitchMaterial

class RoomAdapter(
    private var rooms: List<Room>,
    private val onEdit: (Room) -> Unit,
    private val onDelete: (Room) -> Unit,
    private val onToggleAvailability: (Room, Boolean) -> Unit,
    private val onItemClick: ((Room) -> Unit)? = null
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomImage: ImageView = view.findViewById(R.id.roomImage)
        val roomNumber: TextView = view.findViewById(R.id.roomNumber)
        val roomType: TextView = view.findViewById(R.id.roomType)
        val roomPrice: TextView = view.findViewById(R.id.roomPrice)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val availableSwitch: SwitchMaterial = view.findViewById(R.id.availableSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_management, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        holder.roomNumber.text = "Unit ${room.roomNumber}"
        holder.roomType.text = room.roomType
        holder.roomPrice.text = "Ksh ${String.format("%,.0f", room.price)}"
        
        holder.availableSwitch.setOnCheckedChangeListener(null)
        holder.availableSwitch.isChecked = room.isAvailable
        holder.availableSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleAvailability(room, isChecked)
        }

        holder.btnEdit.setOnClickListener { onEdit(room) }
        holder.btnDelete.setOnClickListener { onDelete(room) }
        holder.itemView.setOnClickListener { onItemClick?.invoke(room) }

        if (room.imageUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(room.imageUrls[0])
                .placeholder(R.drawable.hs)
                .into(holder.roomImage)
        } else {
            holder.roomImage.setImageResource(R.drawable.hs)
        }
    }

    override fun getItemCount() = rooms.size

    fun updateRooms(newRooms: List<Room>) {
        this.rooms = newRooms
        notifyDataSetChanged()
    }
}
