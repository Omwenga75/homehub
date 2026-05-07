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

class RoomTypeEditAdapter(
    private var roomTypes: MutableList<RoomType>,
    private val onEdit: (Int, RoomType) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RoomTypeEditAdapter.RoomTypeViewHolder>() {

    fun updateList(newList: List<RoomType>) {
        roomTypes.clear()
        roomTypes.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomTypeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_type_edit, parent, false)
        return RoomTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomTypeViewHolder, position: Int) {
        holder.bind(roomTypes[position], position)
    }

    override fun getItemCount(): Int = roomTypes.size

    inner class RoomTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivTypeImage: ImageView = itemView.findViewById(R.id.ivTypeImage)
        private val tvTypeName: TextView = itemView.findViewById(R.id.tvTypeName)
        private val tvTypePrice: TextView = itemView.findViewById(R.id.tvTypePrice)
        private val tvTypeQuantity: TextView = itemView.findViewById(R.id.tvTypeQuantity)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditType)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteType)

        fun bind(roomType: RoomType, position: Int) {
            tvTypeName.text = roomType.name
            tvTypePrice.text = roomType.formattedPrice
            tvTypeQuantity.text = "Quantity: ${roomType.totalQuantity}"

            if (roomType.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(roomType.imageUrl).placeholder(R.drawable.hs).into(ivTypeImage)
            } else {
                ivTypeImage.setImageResource(R.drawable.hs)
            }

            btnEdit.setOnClickListener { onEdit(position, roomType) }
            btnDelete.setOnClickListener { onDelete(position) }
        }
    }
}
