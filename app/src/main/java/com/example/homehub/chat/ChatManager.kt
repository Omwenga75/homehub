package com.example.homehub.chat

import android.util.Log
import com.example.homehub.property.Property
import com.example.homehub.R
import com.example.homehub.caretaker.CaretakerProfile
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.utils.UsernameFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.Date
import java.util.UUID

object ChatManager {
    private const val TAG = "ChatManager"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private fun getChatRoomsRef() = db.collection("chatRooms")
    private fun getMessagesRef() = db.collection("messages")
    private val activeListeners = mutableMapOf<String, ListenerRegistration>()
    private val messageCache = mutableMapOf<String, MutableList<ChatMessage>>()

    fun testDatabaseConnection(onResult: (Boolean, String?) -> Unit) {
        val testData = hashMapOf(
            "message" to "Test connection from HomeHub App",
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "testing",
            "app" to "HomeHub Kenya"
        )

        db.collection("test_connection").document("connection_test")
            .set(testData)
            .addOnSuccessListener {
                onResult(true, "Connection successful")
            }
            .addOnFailureListener { e ->
                onResult(false, e.message)
            }
    }

    fun createChatRoom(property: Property, onResult: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(null)
            return
        }

        val userId = currentUser.uid
        val rawName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Student"
        val userName = resolveUserDisplayName(currentUser.uid, rawName)
        
        val caretakerId = if (property.caretakerId.isNotEmpty() && property.caretakerId != "default_caretaker") {
            property.caretakerId
        } else {
            "caretaker_${property.id.hashCode()}"
        }
        val caretakerName = UsernameFormatter.formatUsername(
            property.caretakerFullName.ifEmpty {
                property.ownerName.ifEmpty { "Property Caretaker" }
            }
        )

        val chatRoomId = generateChatRoomId(userId, caretakerId, property.id)
        checkAndCreateChatRoom(chatRoomId, property, userId, userName, caretakerId, caretakerName, onResult)
    }

    fun createChatRoomForCaretaker(caretakerProfile: CaretakerProfile, callback: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            callback(null)
            return
        }

        val userId = currentUser.uid
        val userName = resolveUserDisplayName(currentUser.uid, currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Student")
        val caretakerId = caretakerProfile.id
        val caretakerName = caretakerProfile.name

        val chatRoomId = generateDirectChatRoomId(userId, caretakerId)
        checkAndCreateCaretakerChatRoom(chatRoomId, userId, userName, caretakerId, caretakerName, callback)
    }

    private fun generateDirectChatRoomId(userId: String, caretakerId: String): String {
        val participants = listOf(userId, caretakerId).sorted()
        return "direct_chat_${participants.joinToString("_")}"
    }

    private fun checkAndCreateCaretakerChatRoom(
        chatRoomId: String,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        callback: (String?) -> Unit
    ) {
        getChatRoomsRef().document(chatRoomId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callback(chatRoomId)
                } else {
                    createNewCaretakerChatRoom(chatRoomId, userId, userName, caretakerId, caretakerName, callback)
                }
            }
            .addOnFailureListener {
                createNewCaretakerChatRoom(chatRoomId, userId, userName, caretakerId, caretakerName, callback)
            }
    }

    private fun createNewCaretakerChatRoom(
        chatRoomId: String,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        callback: (String?) -> Unit
    ) {
        val chatRoomData = hashMapOf(
            "id" to chatRoomId,
            "participantIds" to listOf(userId, caretakerId),
            "participantNames" to mapOf(userId to userName, caretakerId to caretakerName),
            "participantTypes" to mapOf(userId to "guest", caretakerId to "caretaker"),
            "propertyId" to "direct_chat",
            "propertyTitle" to "Direct Message",
            "propertyImage" to "",
            "lastMessage" to "Chat started",
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "lastMessageSender" to userName,
            "createdAt" to FieldValue.serverTimestamp(),
            "isActive" to true,
            "createdBy" to userId,
            "unreadCount" to mapOf(userId to 0, caretakerId to 1),
            "isDirectChat" to true,
            "caretakerProfileId" to caretakerId
        )

        getChatRoomsRef().document(chatRoomId).set(chatRoomData)
            .addOnSuccessListener {
                messageCache[chatRoomId] = mutableListOf()
                callback(chatRoomId)
            }
            .addOnFailureListener {
                createMinimalCaretakerChatRoom(chatRoomId, userId, userName, caretakerId, caretakerName, callback)
            }
    }

    private fun createMinimalCaretakerChatRoom(
        chatRoomId: String,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        callback: (String?) -> Unit
    ) {
        val minimalData = hashMapOf(
            "id" to chatRoomId,
            "participantIds" to listOf(userId, caretakerId),
            "participantNames" to mapOf(userId to userName, caretakerId to caretakerName),
            "participantTypes" to mapOf(userId to "guest", caretakerId to "caretaker"),
            "propertyId" to "direct_chat",
            "propertyTitle" to "Direct Message",
            "lastMessage" to "Chat started",
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "isDirectChat" to true
        )

        getChatRoomsRef().document(chatRoomId).set(minimalData)
            .addOnSuccessListener {
                messageCache[chatRoomId] = mutableListOf()
                callback(chatRoomId)
            }
            .addOnFailureListener {
                callback(null)
            }
    }



    private fun checkAndCreateChatRoom(
        chatRoomId: String,
        property: Property,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        onResult: (String?) -> Unit
    ) {
        getChatRoomsRef().document(chatRoomId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onResult(chatRoomId)
                } else {
                    createNewChatRoom(chatRoomId, property, userId, userName, caretakerId, caretakerName, onResult)
                }
            }
            .addOnFailureListener {
                createNewChatRoom(chatRoomId, property, userId, userName, caretakerId, caretakerName, onResult)
            }
    }

    private fun generateChatRoomId(userId: String, caretakerId: String, propertyId: String): String {
        val participants = listOf(userId, caretakerId).sorted()
        val propertyHash = propertyId.hashCode().toString().replace("-", "n")
        return "chat_${participants.joinToString("_")}_$propertyHash"
    }

    private fun createNewChatRoom(
        chatRoomId: String,
        property: Property,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        onResult: (String?) -> Unit
    ) {
        val propertyImage = getSafePropertyImage(property)

        val chatRoomData = hashMapOf(
            "id" to chatRoomId,
            "participantIds" to listOf(userId, caretakerId),
            "participantNames" to mapOf(userId to userName, caretakerId to caretakerName),
            "participantTypes" to mapOf(userId to "guest", caretakerId to "caretaker"),
            "propertyId" to property.id,
            "propertyTitle" to property.title,
            "propertyImage" to propertyImage,
            "lastMessage" to "Chat started",
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "lastMessageSender" to userName,
            "createdAt" to FieldValue.serverTimestamp(),
            "isActive" to true,
            "createdBy" to userId,
            "unreadCount" to mapOf(userId to 0, caretakerId to 1)
        )

        getChatRoomsRef().document(chatRoomId).set(chatRoomData)
            .addOnSuccessListener {
                messageCache[chatRoomId] = mutableListOf()
                onResult(chatRoomId)
            }
            .addOnFailureListener {
                createMinimalChatRoom(chatRoomId, userId, userName, caretakerId, caretakerName, property, onResult)
            }
    }

    private fun createMinimalChatRoom(
        chatRoomId: String,
        userId: String,
        userName: String,
        caretakerId: String,
        caretakerName: String,
        property: Property,
        onResult: (String?) -> Unit
    ) {
        val minimalData = hashMapOf(
            "id" to chatRoomId,
            "participantIds" to listOf(userId, caretakerId),
            "participantNames" to mapOf(userId to userName, caretakerId to caretakerName),
            "participantTypes" to mapOf(userId to "guest", caretakerId to "caretaker"),
            "propertyId" to property.id,
            "propertyTitle" to property.title,
            "lastMessage" to "Chat started",
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "unreadCount" to mapOf(userId to 0, caretakerId to 1)
        )

        getChatRoomsRef().document(chatRoomId).set(minimalData)
            .addOnSuccessListener {
                messageCache[chatRoomId] = mutableListOf()
                onResult(chatRoomId)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun getSafePropertyImage(property: Property): String {
        return try {
            when {
                property.firebaseImages.isNotEmpty() -> property.firebaseImages.first()
                property.localImagePaths.isNotEmpty() -> property.localImagePaths.first()
                property.images.isNotEmpty() -> "drawable://${property.images.first()}"
                else -> "drawable://${property.imageRes}"
            }
        } catch (e: Exception) {
            "drawable://${R.drawable.ic_house_placeholder}"
        }
    }

    private fun resolveUserDisplayName(userId: String, fallback: String): String {
        val cachedUser = GlobalDataCache.getUsers().find { it["uid"] == userId || it["id"] == userId }
        if (cachedUser != null) {
            val realName = (cachedUser["fullName"] as? String)
                ?: (cachedUser["name"] as? String)
                ?: (cachedUser["caretakerFullName"] as? String)
                ?: (cachedUser["businessName"] as? String)
            
            if (!realName.isNullOrBlank()) {
                return realName
            }
        }
        return UsernameFormatter.formatUsername(fallback)
    }


    fun sendMessage(
        chatRoomId: String,
        message: String,
        replyToMessage: ChatMessage? = null,
        onResult: (Boolean, String) -> Unit
    ): String {
        val currentUser = auth.currentUser ?: return "".also { onResult(false, "") }
        val messageId = UUID.randomUUID().toString()
        val userName = resolveUserDisplayName(currentUser.uid, currentUser.displayName?.takeIf { it.isNotBlank() } ?: "User")

        getChatRoomsRef().document(chatRoomId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Read the actual participant type from the chat room document
                    val participantTypes = when (val types = document.get("participantTypes")) {
                        is Map<*, *> -> types
                        else -> emptyMap<String, String>()
                    }
                    val userType = participantTypes[currentUser.uid] as? String ?: "guest"

                    val participantNames = when (val names = document.get("participantNames")) {
                        is Map<*, *> -> names
                        else -> emptyMap<String, Any>()
                    }
                    val resolvedSenderName = (participantNames[currentUser.uid] as? String)
                        ?: resolveUserDisplayName(currentUser.uid, userName)

                    val messageData = hashMapOf(
                        "id" to messageId,
                        "chatRoomId" to chatRoomId,
                        "senderId" to currentUser.uid,
                        "senderName" to resolvedSenderName,
                        "senderType" to userType,
                        "message" to message,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "messageType" to "text",
                        "isRead" to false,
                        "readBy" to listOf(currentUser.uid),
                        "replyToMessageId" to (replyToMessage?.id ?: ""),
                        "replyToMessage" to (replyToMessage?.message ?: ""),
                        "replyToSender" to (replyToMessage?.senderName ?: ""),
                        "messageStatus" to "sent",
                        "persisted" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    saveMessageWithRetry(messageId, messageData, chatRoomId, message, userName, currentUser.uid) { success ->
                        onResult(success, messageId)
                    }
                } else {
                    Log.e(TAG, "Chat room $chatRoomId does not exist")
                    onResult(false, messageId)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch chat room $chatRoomId: ${e.message}")
                onResult(false, messageId)
            }
        
        return messageId
    }

    private fun saveMessageWithRetry(
        messageId: String,
        messageData: HashMap<String, Any>,
        chatRoomId: String,
        message: String,
        userName: String,
        senderId: String,
        onResult: (Boolean) -> Unit
    ) {
        getMessagesRef().document(messageId).set(messageData)
            .addOnSuccessListener {
                updateLastMessage(chatRoomId, message, userName, senderId, messageId)
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun updateLastMessage(chatRoomId: String, lastMessage: String, senderName: String, senderId: String, messageId: String) {
        getChatRoomsRef().document(chatRoomId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val participantIds = snapshot.get("participantIds") as? List<*> ?: emptyList<String>()
                val otherUserId = (participantIds.firstOrNull { it != senderId } as? String) ?: return@addOnSuccessListener
                
                val updates = hashMapOf<String, Any>(
                    "lastMessage" to lastMessage,
                    "lastMessageId" to messageId,
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "lastMessageSender" to senderName
                )
                
                updates["unreadCount.$otherUserId"] = FieldValue.increment(1)
                
                getChatRoomsRef().document(chatRoomId).update(updates)

                // Trigger in-app notification logic - REMOVED per user request to avoid feed noise
                /*
                com.example.homehub.utils.NotificationManager.sendNewMessageNotification(
                    receiverId = otherUserId,
                    senderName = senderName,
                    receiverRole = receiverRole
                )
                */
            }
        }
    }

    fun listenForMessages(chatRoomId: String, onMessageReceived: (List<ChatMessage>) -> Unit): ListenerRegistration {
        // Prevent duplicate listeners for the same chat room
        activeListeners[chatRoomId]?.remove()
        
        val registration = getMessagesRef()
            .whereEqualTo("chatRoomId", chatRoomId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed for messages in room $chatRoomId", error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { 
                    ChatMessage.fromFirebaseData(it.id, it.data ?: emptyMap()) 
                } ?: emptyList()
                
                onMessageReceived(messages)
            }
        activeListeners[chatRoomId] = registration
        return registration
    }

    fun removeMessageListener(chatRoomId: String) {
        activeListeners[chatRoomId]?.remove()
        activeListeners.remove(chatRoomId)
    }

    fun getCaretakerChatRooms(userId: String, onChatRoomsReceived: (List<ChatRoom>) -> Unit): ListenerRegistration {
        return getChatRoomsRef()
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed.", error)
                    return@addSnapshotListener
                }
                val chatRooms = snapshot?.documents?.mapNotNull { 
                    ChatRoom.fromFirebaseData(it.id, it.data ?: return@mapNotNull null) 
                }?.sortedByDescending { it.lastMessageTime } ?: emptyList()
                onChatRoomsReceived(chatRooms)
            }
    }

    fun getChatRooms(onChatRoomsReceived: (List<ChatRoom>) -> Unit): ListenerRegistration {
        val userId = auth.currentUser?.uid ?: return object : ListenerRegistration { override fun remove() {} }
        return getChatRoomsRef()
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val chatRooms = snapshot?.documents?.mapNotNull { 
                    ChatRoom.fromFirebaseData(it.id, it.data!!) 
                }?.sortedByDescending { it.lastMessageTime } ?: emptyList()
                onChatRoomsReceived(chatRooms)
            }
    }

    fun markMessagesAsRead(chatRoomId: String, currentUserId: String) {
        // Reset the unreadCount in the chatRoom document
        getChatRoomsRef().document(chatRoomId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val counts = snapshot.get("unreadCount") as? Map<*, *>
                val currentUnread = (counts?.get(currentUserId) as? Number)?.toInt() ?: 0
                
                if (currentUnread > 0) {
                    val updates = hashMapOf<String, Any>(
                        "unreadCount.$currentUserId" to 0
                    )
                    getChatRoomsRef().document(chatRoomId).update(updates)
                }
            }
        }

        getMessagesRef()
            .whereEqualTo("chatRoomId", chatRoomId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) return@addOnSuccessListener
                
                val batch = db.batch()
                var hasUpdates = false
                
                snapshot.documents.forEach { doc ->
                    val senderId = doc.getString("senderId")
                    if (senderId != null && senderId != currentUserId) {
                        batch.update(doc.reference, "isRead", true, "readBy", FieldValue.arrayUnion(currentUserId))
                        hasUpdates = true
                    }
                }
                
                if (hasUpdates) {
                    batch.commit()
                        .addOnFailureListener { e -> Log.e(TAG, "Failed to commit markAsRead batch", e) }
                }
            }
    }

    fun editMessage(chatRoomId: String, messageId: String, newText: String, onResult: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "message" to newText,
            "isEdited" to true,
            "lastEditedAt" to FieldValue.serverTimestamp()
        )

        getMessagesRef().document(messageId).update(updates)
            .addOnSuccessListener {
                // Check if this was the last message in the room
                getChatRoomsRef().document(chatRoomId).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists() && snapshot.getString("lastMessageId") == messageId) {
                        getChatRoomsRef().document(chatRoomId).update("lastMessage", newText)
                    }
                }
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    fun deleteMessage(chatRoomId: String, messageId: String, onResult: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "message" to "🚫 This message was deleted",
            "deleted" to true,
            "messageStatus" to ChatMessage.STATUS_DELETED
        )

        getMessagesRef().document(messageId).update(updates)
            .addOnSuccessListener {
                // Update last message preview if needed
                getChatRoomsRef().document(chatRoomId).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists() && snapshot.getString("lastMessageId") == messageId) {
                        getChatRoomsRef().document(chatRoomId).update("lastMessage", "🚫 Message deleted")
                    }
                }
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }
    fun removeAllListeners() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
    }

    fun getChatRoom(roomId: String, onResult: (ChatRoom?) -> Unit) {
        getChatRoomsRef().document(roomId).get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.data != null) {
                    onResult(ChatRoom.fromFirebaseData(document.id, document.data!!))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
}
