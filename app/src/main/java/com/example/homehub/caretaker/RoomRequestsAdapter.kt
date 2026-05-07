package com.example.homehub.caretaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.student.LeaveRequest
import com.google.android.material.button.MaterialButton

class RoomRequestsAdapter(
    private var requests: List<LeaveRequest>,
    private val onAction: (LeaveRequest, String) -> Unit
) : RecyclerView.Adapter<RoomRequestsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStudentPicture: android.widget.ImageView = view.findViewById(R.id.ivStudentPicture)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvPropertyName: TextView = view.findViewById(R.id.tvPropertyName)
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val tvRentStatus: TextView = view.findViewById(R.id.tvRentStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAccept)
        val btnReject: MaterialButton = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        
        // Student Info
        holder.tvStudentName.text = request.studentName
        
        // Use ks4 as requested for the profile picture
        com.bumptech.glide.Glide.with(holder.itemView.context)
            .load(R.drawable.ks4)
            .placeholder(R.drawable.person)
            .error(R.drawable.person)
            .circleCrop()
            .into(holder.ivStudentPicture)

        // Property & Room Info
        val propertyDisplay = if (request.roomNumber.isNotEmpty()) {
            "${request.propertyName} - RM ${request.roomNumber}"
        } else {
            request.propertyName
        }
        holder.tvPropertyName.text = propertyDisplay

        // Rent Status
        if (request.daysUntilRent >= 0) {
            holder.tvRentStatus.visibility = View.VISIBLE
            holder.tvRentStatus.text = "Rent Due: ${request.daysUntilRent} days remaining"
            
            // Color coding for urgency
            if (request.daysUntilRent <= 3) {
                holder.tvRentStatus.setTextColor(android.graphics.Color.RED)
                holder.tvRentStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFEBEE"))
            } else {
                holder.tvRentStatus.setTextColor(holder.itemView.context.getColor(R.color.primary_dark))
                holder.tvRentStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E8F5E9"))
            }
        } else {
            holder.tvRentStatus.visibility = View.GONE
        }

        holder.tvReason.text = request.reason
        holder.tvTimestamp.text = com.example.homehub.utils.UsernameFormatter.getRelativeTime(request.createdAt)

        holder.btnAccept.setOnClickListener { onAction(request, "ACCEPT") }
        holder.btnReject.setOnClickListener { onAction(request, "REJECT") }
    }

    override fun getItemCount() = requests.size

    fun updateData(newRequests: List<LeaveRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
