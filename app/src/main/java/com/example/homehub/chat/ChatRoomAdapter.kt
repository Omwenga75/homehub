package com.example.homehub.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.firebase.auth.FirebaseAuth
import java.util.*
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.utils.UsernameFormatter
import java.text.SimpleDateFormat
import android.widget.ImageView
import com.example.homehub.other.Extensions.loadProfileImage

class ChatRoomAdapter(
    private var chatRooms: List<ChatRoom>,
    private val onChatRoomClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatRoomAdapter.ChatRoomViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val participantName: TextView = itemView.findViewById(R.id.participantName)
        val propertyTitle: TextView = itemView.findViewById(R.id.propertyTitle)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val lastMessageTime: TextView = itemView.findViewById(R.id.lastMessageTime)
        val unreadBadge: TextView = itemView.findViewById(R.id.unreadBadge)
        val participantAvatar: ImageView = itemView.findViewById(R.id.participantAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val chatRoom = chatRooms[position]
        val currentUserId = auth.currentUser?.uid ?: ""
        val otherParticipantId = chatRoom.getOtherParticipantId(currentUserId)
        var displayName = chatRoom.getOtherParticipantName(currentUserId)
        var avatarUrl = ""

        // PRO-TIP: Lookup 'real name' from GlobalDataCache for consistency
        otherParticipantId?.let { uid ->
            val cachedUser = GlobalDataCache.getUsers().find { it["uid"] == uid || it["id"] == uid }
            if (cachedUser != null) {
                val realName = (cachedUser["fullName"] as? String)
                    ?: (cachedUser["name"] as? String)
                    ?: (cachedUser["caretakerFullName"] as? String)
                    ?: (cachedUser["businessName"] as? String)
                    ?: (cachedUser["username"] as? String)
                
                if (!realName.isNullOrBlank()) {
                    displayName = realName
                }

                avatarUrl = (cachedUser["profileImageUrl"] as? String)
                    ?: (cachedUser["profilePictureUrl"] as? String)
                    ?: (cachedUser["image"] as? String)
                    ?: (cachedUser["profileImage"] as? String) ?: ""
            }
        }

        // Apply formatting if it still looks like a username
        displayName = UsernameFormatter.formatUsername(displayName)

        holder.participantName.text = displayName
        com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(holder.participantAvatar, displayName, avatarUrl, otherParticipantId)
        
        holder.lastMessage.text = chatRoom.lastMessage
        val timeText = formatMessageTime(chatRoom.lastMessageTime)
        holder.lastMessageTime.text = timeText

        val unreadCount = chatRoom.getUnreadCount(currentUserId)
        if (unreadCount > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = if (unreadCount > 9) "9+" else unreadCount.toString()
        } else {
            holder.unreadBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onChatRoomClick(chatRoom)
        }
    }

    override fun getItemCount() = chatRooms.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateChatRooms(newChatRooms: List<ChatRoom>) {
        this.chatRooms = newChatRooms.sortedByDescending { it.lastMessageTime }
        notifyDataSetChanged()
    }

    private fun formatMessageTime(messageTime: Date): String {
        val now = Date()
        return if (isSameDay(now, messageTime)) {
            timeFormat.format(messageTime)
        } else if (isYesterday(messageTime)) {
            "Yesterday"
        } else if (isThisYear(messageTime)) {
            dateFormat.format(messageTime)
        } else {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(messageTime)
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(date: Date): Boolean {
        val calendar = Calendar.getInstance().apply { time = date }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(date: Date): Boolean {
        val calendar = Calendar.getInstance().apply { time = date }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return calendar.get(Calendar.YEAR) == currentYear
    }
}
