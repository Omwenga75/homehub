package com.example.homehub.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.android.material.button.MaterialButton

class RoomNumberAdapter(
    private val property: Property,
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<RoomNumberAdapter.RoomViewHolder>() {

    // Only show available rooms for booking, sorted numerically
    private val availableRooms = property.getAvailableSortedRoomNumbers()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_number, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(availableRooms[position])
    }

    override fun getItemCount(): Int = availableRooms.size

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomNumber: TextView = itemView.findViewById(R.id.tvRoomNumber)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnBook: MaterialButton = itemView.findViewById(R.id.btnBook)

        fun bind(roomNumber: String) {
            tvRoomNumber.text = roomNumber
            tvStatus.text = "Available"
            
            btnBook.setOnClickListener {
                onSelected(roomNumber)
            }
        }
    }
}
