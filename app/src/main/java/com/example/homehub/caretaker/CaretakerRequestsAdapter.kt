package com.example.homehub.caretaker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import java.text.SimpleDateFormat
import java.util.*

class CaretakerRequestsAdapter(
    private var requestsList: MutableList<CaretakerRequest>,
    private val onActionClick: (CaretakerRequest, String) -> Unit
) : RecyclerView.Adapter<CaretakerRequestsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val caretakerName: TextView = itemView.findViewById(R.id.caretakerName)
        val caretakerEmail: TextView = itemView.findViewById(R.id.caretakerEmail)
        val requestTime: TextView = itemView.findViewById(R.id.requestTime)
        val approveBtn: Button = itemView.findViewById(R.id.approveButton)
        val rejectBtn: Button = itemView.findViewById(R.id.rejectButton)
        val viewBtn: Button = itemView.findViewById(R.id.viewButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_caretaker_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requestsList[position]

        holder.caretakerName.text = request.userName
        holder.caretakerEmail.text = request.userEmail
        holder.requestTime.text = formatTimeAgo(request.requestedAt)

        holder.approveBtn.setOnClickListener { onActionClick(request, "approve") }
        holder.rejectBtn.setOnClickListener { onActionClick(request, "reject") }
        holder.viewBtn.setOnClickListener { onActionClick(request, "view") }
        
        // Tooltips for accessibility
        holder.approveBtn.contentDescription = "Approve this caretaker request"
        holder.rejectBtn.contentDescription = "Reject this caretaker request"
        
        setupButtonTooltips(holder)
    }

    override fun getItemCount(): Int = requestsList.size

    fun updateList(newList: MutableList<CaretakerRequest>) {
        requestsList.clear()
        requestsList.addAll(newList)
        notifyDataSetChanged()
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} min ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            diff < 604800000 -> "${diff / 86400000} days ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun setupButtonTooltips(holder: ViewHolder) {
        val longClickListener = View.OnLongClickListener { view ->
            when (view.id) {
                R.id.approveButton -> {
                    android.widget.Toast.makeText(view.context, "Approve this caretaker request", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.rejectButton -> {
                    android.widget.Toast.makeText(view.context, "Reject this caretaker request", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        holder.approveBtn.setOnLongClickListener(longClickListener)
        holder.rejectBtn.setOnLongClickListener(longClickListener)
    }
}
