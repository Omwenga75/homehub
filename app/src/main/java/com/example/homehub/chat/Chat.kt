package com.example.homehub.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.UUID
import java.util.Locale

@Parcelize
data class ChatRoom(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantTypes: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTime: Date = Date(),
    val lastMessageSender: String = "",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val propertyImage: String = "",
    val createdAt: Date = Date(),
    val isActive: Boolean = true,
    val unreadCount: Map<String, Int> = emptyMap(),
    val createdBy: String = ""
) : Parcelable {

    fun getOtherParticipantId(currentUserId: String): String? {
        return participantIds.firstOrNull { it != currentUserId }
    }

    fun getOtherParticipantName(currentUserId: String): String {
        val otherParticipantId = getOtherParticipantId(currentUserId)
        return if (otherParticipantId != null) {
            participantNames[otherParticipantId] ?: "Unknown Student"
        } else {
            "Unknown Student"
        }
    }

    fun isCaretaker(userId: String): Boolean {
        return participantTypes[userId] == "caretaker"
    }

    fun getDisplayTitle(currentUserId: String): String {
        return getOtherParticipantName(currentUserId)
    }

    fun getUnreadCount(userId: String): Int {
        return unreadCount[userId] ?: 0
    }

    fun hasUnreadMessages(userId: String): Boolean {
        return getUnreadCount(userId) > 0
    }

    fun getLastMessagePreview(): String {
        return if (lastMessage.length > 50) {
            lastMessage.substring(0, 50) + "..."
        } else {
            lastMessage
        }
    }

    fun isValidChatRoom(): Boolean {
        return id.isNotEmpty() &&
                participantIds.size == 2 &&
                propertyId.isNotEmpty() &&
                propertyTitle.isNotEmpty()
    }

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (id.isEmpty()) errors.add("Chat room ID is required")
        if (participantIds.size != 2) errors.add("Chat room must have exactly 2 participants")
        if (propertyId.isEmpty()) errors.add("Property ID is required")
        if (propertyTitle.isEmpty()) errors.add("Property title is required")
        return errors
    }

    companion object {
        fun fromFirebaseData(
            id: String,
            data: Map<String, Any>
        ): ChatRoom {
            val participantIds = when (val ids = data["participantIds"]) {
                is List<*> -> ids.filterIsInstance<String>()
                else -> emptyList()
            }

            val participantNames = mutableMapOf<String, String>()
            when (val names = data["participantNames"]) {
                is Map<*, *> -> {
                    names.forEach { (key, value) ->
                        if (key is String && value is String) {
                            participantNames[key] = value
                        }
                    }
                }
            }

            val participantTypes = mutableMapOf<String, String>()
            when (val types = data["participantTypes"]) {
                is Map<*, *> -> {
                    types.forEach { (key, value) ->
                        if (key is String && value is String) {
                            participantTypes[key] = value
                        }
                    }
                }
            }

            val unreadCount = mutableMapOf<String, Int>()
            when (val counts = data["unreadCount"]) {
                is Map<*, *> -> {
                    counts.forEach { (key, value) ->
                        if (key is String) {
                            when (value) {
                                is Int -> unreadCount[key] = value
                                is Long -> unreadCount[key] = value.toInt()
                                is Number -> unreadCount[key] = value.toInt()
                            }
                        }
                    }
                }
            }

            return ChatRoom(
                id = id,
                participantIds = participantIds,
                participantNames = participantNames,
                participantTypes = participantTypes,
                lastMessage = data["lastMessage"] as? String ?: "",
                lastMessageTime = (data["lastMessageTime"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                lastMessageSender = data["lastMessageSender"] as? String ?: "",
                propertyId = data["propertyId"] as? String ?: "",
                propertyTitle = data["propertyTitle"] as? String ?: "",
                propertyImage = data["propertyImage"] as? String ?: "",
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                isActive = data["isActive"] as? Boolean ?: true,
                unreadCount = unreadCount,
                createdBy = data["createdBy"] as? String ?: ""
            )
        }

        fun createChatRoom(
            id: String,
            guestId: String,
            guestName: String,
            caretakerId: String,
            caretakerName: String,
            propertyId: String,
            propertyTitle: String,
            propertyImage: String = "",
            createdBy: String = guestId
        ): ChatRoom {
            return ChatRoom(
                id = id,
                participantIds = listOf(guestId, caretakerId),
                participantNames = mapOf(guestId to guestName, caretakerId to caretakerName),
                participantTypes = mapOf(guestId to "guest", caretakerId to "caretaker"),
                lastMessage = "Chat started about $propertyTitle",
                lastMessageTime = Date(),
                lastMessageSender = guestName,
                propertyId = propertyId,
                propertyTitle = propertyTitle,
                propertyImage = propertyImage,
                createdAt = Date(),
                isActive = true,
                unreadCount = mapOf(guestId to 0, caretakerId to 1),
                createdBy = createdBy
            )
        }

        fun generateChatRoomId(userId: String, caretakerId: String, propertyId: String): String {
            val participants = listOf(userId, caretakerId).sorted()
            val propertyHash = propertyId.hashCode().toString().replace("-", "n")
            return "chat_${participants.joinToString("_")}_$propertyHash"
        }
    }
}

@Parcelize
data class ChatMessage(
    val id: String = "",
    val chatRoomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderType: String = "",
    val message: String = "",
    val timestamp: Date = Date(),
    val messageType: String = TYPE_TEXT,
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(),
    val replyToMessageId: String = "",
    val replyToMessage: String = "",
    val replyToSender: String = "",
    val messageStatus: String = STATUS_SENT,
    val isEdited: Boolean = false,
    val lastEditedAt: Date? = null,
    val deleted: Boolean = false,
    val isTemp: Boolean = false,
    val persisted: Boolean = false
) : Parcelable {

    fun isSentByUser(currentUserId: String): Boolean {
        return senderId == currentUserId
    }

    fun isReply(): Boolean {
        return replyToMessageId.isNotEmpty()
    }

    fun getFormattedTime(): String {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        return timeFormat.format(timestamp)
    }

    fun getFormattedDate(): String {
        val format = java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return format.format(timestamp)
    }

    fun getDisplayMessage(): String {
        return if (deleted) "🚫 This message was deleted" else message
    }

    fun markAsRead(userId: String): ChatMessage {
        val updatedReadBy = if (userId !in readBy) {
            readBy + userId
        } else {
            readBy
        }
        return this.copy(isRead = updatedReadBy.isNotEmpty(), readBy = updatedReadBy)
    }

    fun markAsDelivered(): ChatMessage {
        return this.copy(messageStatus = STATUS_DELIVERED)
    }

    fun markAsRead(): ChatMessage {
        return this.copy(messageStatus = STATUS_READ, isRead = true)
    }

    fun isValidMessage(): Boolean {
        return id.isNotEmpty() &&
                chatRoomId.isNotEmpty() &&
                senderId.isNotEmpty() &&
                senderName.isNotEmpty() &&
                (deleted || message.isNotEmpty())
    }

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (id.isEmpty()) errors.add("Message ID is required")
        if (chatRoomId.isEmpty()) errors.add("Chat room ID is required")
        if (senderId.isEmpty()) errors.add("Sender ID is required")
        if (senderName.isEmpty()) errors.add("Sender name is required")
        if (!deleted && message.isEmpty()) errors.add("Message content is required")
        return errors
    }

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_SYSTEM = "system"
        const val TYPE_LOCATION = "location"
        const val TYPE_FILE = "file"

        const val STATUS_SENT = "sent"
        const val STATUS_SENDING = "sending"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
        const val STATUS_DELETED = "deleted"

        fun fromFirebaseData(
            id: String,
            data: Map<String, Any>
        ): ChatMessage {
            val readBy = when (val readByData = data["readBy"]) {
                is List<*> -> readByData.filterIsInstance<String>()
                else -> emptyList()
            }

            return ChatMessage(
                id = id,
                chatRoomId = data["chatRoomId"] as? String ?: "",
                senderId = data["senderId"] as? String ?: "",
                senderName = data["senderName"] as? String ?: "",
                senderType = data["senderType"] as? String ?: "",
                message = data["message"] as? String ?: "",
                timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                messageType = data["messageType"] as? String ?: TYPE_TEXT,
                isRead = data["isRead"] as? Boolean ?: false,
                readBy = readBy,
                replyToMessageId = data["replyToMessageId"] as? String ?: "",
                replyToMessage = data["replyToMessage"] as? String ?: "",
                replyToSender = data["replyToSender"] as? String ?: "",
                messageStatus = data["messageStatus"] as? String ?: STATUS_SENT,
                isEdited = data["isEdited"] as? Boolean ?: false,
                lastEditedAt = (data["lastEditedAt"] as? com.google.firebase.Timestamp)?.toDate(),
                deleted = data["deleted"] as? Boolean ?: false,
                persisted = data["persisted"] as? Boolean ?: false
            )
        }

        fun createTextMessage(
            chatRoomId: String,
            senderId: String,
            senderName: String,
            senderType: String,
            message: String
        ): ChatMessage {
            return ChatMessage(
                id = UUID.randomUUID().toString(),
                chatRoomId = chatRoomId,
                senderId = senderId,
                senderName = senderName,
                senderType = senderType,
                message = message,
                timestamp = Date(),
                messageType = TYPE_TEXT,
                isRead = false,
                messageStatus = STATUS_SENT
            )
        }

        fun createReplyMessage(
            originalMessage: ChatMessage,
            replyText: String,
            currentUserId: String,
            currentUserName: String,
            currentUserType: String
        ): ChatMessage {
            return ChatMessage(
                id = UUID.randomUUID().toString(),
                chatRoomId = originalMessage.chatRoomId,
                senderId = currentUserId,
                senderName = currentUserName,
                senderType = currentUserType,
                message = replyText,
                timestamp = Date(),
                messageType = TYPE_TEXT,
                isRead = false,
                replyToMessageId = originalMessage.id,
                replyToMessage = originalMessage.message,
                replyToSender = originalMessage.senderName,
                messageStatus = STATUS_SENT
            )
        }

        fun createSystemMessage(chatRoomId: String, message: String): ChatMessage {
            return ChatMessage(
                id = "${System.currentTimeMillis()}_system",
                chatRoomId = chatRoomId,
                senderId = "system",
                senderName = "System",
                message = message,
                timestamp = Date(),
                messageType = TYPE_SYSTEM,
                isRead = true,
                messageStatus = STATUS_DELIVERED
            )
        }

        fun createWelcomeMessage(chatRoomId: String, propertyTitle: String, userName: String): ChatMessage {
            return createSystemMessage(
                chatRoomId,
                "Chat started. $userName is interested in your property."
            )
        }

        fun createTempMessage(
            chatRoomId: String,
            senderId: String,
            senderName: String,
            senderType: String,
            message: String,
            replyToMessage: ChatMessage? = null
        ): ChatMessage {
            return if (replyToMessage != null) {
                createReplyMessage(
                    originalMessage = replyToMessage,
                    replyText = message,
                    currentUserId = senderId,
                    currentUserName = senderName,
                    currentUserType = senderType
                ).copy(isTemp = true, messageStatus = "sending")
            } else {
                createTextMessage(
                    chatRoomId = chatRoomId,
                    senderId = senderId,
                    senderName = senderName,
                    senderType = senderType,
                    message = message
                ).copy(isTemp = true, messageStatus = "sending")
            }
        }
    }
}

@Parcelize
data class Users(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Date = Date(),
    val phoneNumber: String = "",
    val userType: String = "user",
    val fcmToken: String = "",
    val createdAt: Date = Date(),
    val lastLogin: Date = Date(),
    val isEmailVerified: Boolean = false
) : Parcelable {

    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else email.substringBefore("@")
    }

    fun getInitials(): String {
        return if (name.isNotEmpty()) {
            name.split(" ")
                .take(2)
                .joinToString("") { it.firstOrNull()?.toString() ?: "" }
                .uppercase()
        } else {
            email.firstOrNull()?.uppercase() ?: "U"
        }
    }

    fun isCaretaker(): Boolean {
        return userType == "caretaker"
    }

    fun isAdmin(): Boolean {
        return userType == "admin"
    }

    fun isRegularUser(): Boolean {
        return userType == "user"
    }

    fun getFormattedLastSeen(): String {
        if (isOnline) return "Online"

        val now = Date()
        val diff = now.time - lastSeen.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            else -> java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(lastSeen)
        }
    }

    fun getFormattedJoinDate(): String {
        val format = java.text.SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return format.format(createdAt)
    }

    fun isValidUser(): Boolean {
        return uid.isNotEmpty() && email.isNotEmpty()
    }

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (uid.isEmpty()) errors.add("User ID is required")
        if (email.isEmpty()) errors.add("Email is required")
        return errors
    }

    companion object {
        const val TYPE_USER = "user"
        const val TYPE_CARETAKER = "caretaker"
        const val TYPE_ADMIN = "admin"

        fun fromFirebaseData(
            uid: String,
            data: Map<String, Any>
        ): Users {
            return Users(
                uid = uid,
                name = data["name"] as? String ?: "",
                email = data["email"] as? String ?: "",
                profileImage = data["profileImage"] as? String ?: "",
                isOnline = data["isOnline"] as? Boolean ?: false,
                lastSeen = (data["lastSeen"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                phoneNumber = data["phoneNumber"] as? String ?: "",
                userType = data["userType"] as? String ?: TYPE_USER,
                fcmToken = data["fcmToken"] as? String ?: "",
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                lastLogin = (data["lastLogin"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                isEmailVerified = data["isEmailVerified"] as? Boolean ?: false,
            )
        }

        fun createDefaultUser(uid: String, email: String): Users {
            return Users(
                uid = uid,
                name = email.substringBefore("@"),
                email = email,
                profileImage = "",
                isOnline = false,
                lastSeen = Date(),
                phoneNumber = "",
                userType = TYPE_USER,
                fcmToken = "",
                createdAt = Date(),
                lastLogin = Date(),
                isEmailVerified = false,
            )
        }

        fun createCaretakerUser(uid: String, email: String, name: String, phoneNumber: String = ""): Users {
            return Users(
                uid = uid,
                name = name,
                email = email,
                profileImage = "",
                isOnline = false,
                lastSeen = Date(),
                phoneNumber = phoneNumber,
                userType = TYPE_CARETAKER,
                fcmToken = "",
                createdAt = Date(),
                lastLogin = Date(),
                isEmailVerified = false,
            )
        }
    }
}
