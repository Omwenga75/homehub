package com.example.homehub.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class Message(
    val id: String = "",
    val guestName: String = "",
    val guestProfileImage: String = "",
    val guestId: String = "",
    val propertyName: String = "",
    val lastMessage: String = "",
    val timestamp: Date = Date(),
    val unreadCount: Int = 0,
    val caretakerId: String = "",
    val propertyId: String = "",
    val messageThreadId: String = "" // Unique ID for this conversation
) : Parcelable {

    fun getFormattedTime(): String {
        val now = Date()
        val diff = now.time - timestamp.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }

    fun hasUnreadMessages(): Boolean {
        return unreadCount > 0
    }
}
