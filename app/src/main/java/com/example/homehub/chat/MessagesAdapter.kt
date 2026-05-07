package com.example.homehub.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.homehub.R

class MessagesAdapter(
    private val messages: List<Message>,
    private val onItemClick: (Message) -> Unit
) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val guestName: TextView = itemView.findViewById(R.id.guestName)
        val propertyName: TextView = itemView.findViewById(R.id.propertyName)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        val unreadBadge: TextView = itemView.findViewById(R.id.unreadBadge)
        val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        holder.guestName.text = message.guestName
        holder.propertyName.text = message.propertyName
        holder.lastMessage.text = message.lastMessage
        holder.timestamp.text = message.getFormattedTime()

        if (message.guestProfileImage.isNotEmpty()) {
            holder.profileImage.load(message.guestProfileImage) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.baseline_account_circle_24)
                error(R.drawable.baseline_account_circle_24)
                crossfade(true)
            }
        } else {
            holder.profileImage.setImageResource(R.drawable.baseline_account_circle_24)
        }

        if (message.hasUnreadMessages()) {
            holder.unreadIndicator.visibility = View.VISIBLE
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = message.unreadCount.toString()
            holder.lastMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
            holder.guestName.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
        } else {
            holder.unreadIndicator.visibility = View.INVISIBLE
            holder.unreadBadge.visibility = View.GONE
            holder.lastMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
            holder.guestName.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
        }

        holder.itemView.setOnClickListener {
            onItemClick(message)
        }
    }

    override fun getItemCount(): Int = messages.size
}
