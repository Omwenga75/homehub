package com.example.homehub.chat

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.utils.UsernameFormatter
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageView
import com.example.homehub.other.Extensions.loadProfileImage

class MessageAdapter(
    private var messages: List<ChatMessage>,
    private val currentUserId: String,
    private val onMessageLongPress: (ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    companion object {
        private const val TAG = "MessageAdapter"
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_SYSTEM = 2
    }

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val messageStatus: ImageView = itemView.findViewById(R.id.messageStatus)
        val senderAvatar: ImageView = itemView.findViewById(R.id.senderAvatar)
        val editStatus: TextView = itemView.findViewById(R.id.editStatus)
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val senderAvatar: ImageView = itemView.findViewById(R.id.senderAvatar)
        val editStatus: TextView = itemView.findViewById(R.id.editStatus)
    }

    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.systemMessageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ms_sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ms_received, parent, false))
            VIEW_TYPE_SYSTEM -> SystemMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ms_system, parent, false))
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> bindSentMessage(holder, message)
            is ReceivedMessageViewHolder -> bindReceivedMessage(holder, message)
            is SystemMessageViewHolder -> bindSystemMessage(holder, message)
        }

        if (message.messageType != ChatMessage.TYPE_SYSTEM) {
            holder.itemView.setOnLongClickListener {
                onMessageLongPress(message)
                true
            }
        }
    }

    private fun bindSentMessage(holder: SentMessageViewHolder, message: ChatMessage) {
        holder.messageText.text = message.getDisplayMessage()
        holder.timeText.text = message.getFormattedTime()
        holder.editStatus.isVisible = message.isEdited && !message.deleted

        if (message.deleted) {
            holder.messageText.setTypeface(null, android.graphics.Typeface.ITALIC)
            holder.messageStatus.isVisible = false
        } else {
            holder.messageText.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.messageStatus.isVisible = true
        }

        val context = holder.itemView.context
        when {
            message.isTemp || message.messageStatus == "sending" -> {
                holder.messageStatus.setImageResource(R.drawable.baseline_refresh_24)
                holder.messageStatus.imageTintList = android.content.res.ColorStateList.valueOf(0x80FFFFFF.toInt())
            }
            message.messageStatus == ChatMessage.STATUS_FAILED -> {
                holder.messageStatus.setImageResource(R.drawable.baseline_warning_24)
                holder.messageStatus.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.red_400))
            }
            message.messageStatus == ChatMessage.STATUS_READ || message.isRead -> {
                holder.messageStatus.setImageResource(R.drawable.ic_check_all)
                holder.messageStatus.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.blue_400))
            }
            message.messageStatus == ChatMessage.STATUS_DELIVERED -> {
                holder.messageStatus.setImageResource(R.drawable.ic_check_all)
                holder.messageStatus.imageTintList = android.content.res.ColorStateList.valueOf(0xCCFFFFFF.toInt())
            }
            else -> {
                holder.messageStatus.setImageResource(R.drawable.ic_tick_single)
                holder.messageStatus.imageTintList = android.content.res.ColorStateList.valueOf(0xCCFFFFFF.toInt())
            }
        }
    }

    private fun bindReceivedMessage(holder: ReceivedMessageViewHolder, message: ChatMessage) {
        holder.messageText.text = message.getDisplayMessage()
        holder.timeText.text = message.getFormattedTime()
        holder.editStatus.isVisible = message.isEdited && !message.deleted

        if (message.deleted) {
            holder.messageText.setTypeface(null, android.graphics.Typeface.ITALIC)
        } else {
            holder.messageText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        val cachedUser = GlobalDataCache.getUsers().find { it["uid"] == message.senderId || it["id"] == message.senderId }
        val avatarUrl = cachedUser?.let {
            (it["profileImageUrl"] as? String)
                ?: (it["profilePictureUrl"] as? String)
                ?: (it["image"] as? String)
                ?: (it["profileImage"] as? String)
        } ?: ""

        val displayName = cachedUser?.let {
            (it["fullName"] as? String)
                ?: (it["name"] as? String)
                ?: (it["caretakerFullName"] as? String)
                ?: (it["businessName"] as? String)
                ?: (it["username"] as? String)
                ?: message.senderName
        } ?: message.senderName

        com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(holder.senderAvatar, displayName, avatarUrl, message.senderId)
    }

    private fun bindSystemMessage(holder: SystemMessageViewHolder, message: ChatMessage) {
        holder.messageText.text = message.message
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.messageType == ChatMessage.TYPE_SYSTEM -> VIEW_TYPE_SYSTEM
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages(newMessages: List<ChatMessage>) {
        val sortedNew = newMessages.sortedBy { it.timestamp }
        val diffCallback = MessageDiffCallback(this.messages, sortedNew)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        
        this.messages = sortedNew
        diffResult.dispatchUpdatesTo(this)
    }

    private class MessageDiffCallback(
        private val oldList: List<ChatMessage>,
        private val newList: List<ChatMessage>
    ) : androidx.recyclerview.widget.DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.message == newItem.message &&
                   oldItem.messageStatus == newItem.messageStatus &&
                   oldItem.isRead == newItem.isRead &&
                   oldItem.timestamp == newItem.timestamp
        }
    }
}
