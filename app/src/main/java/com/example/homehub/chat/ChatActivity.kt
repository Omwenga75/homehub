package com.example.homehub.chat

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import android.widget.Toast
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.utils.UsernameFormatter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.example.homehub.utils.GlobalDataCache
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.util.*
import com.example.homehub.auth.SessionManager

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var chatTitle: TextView
    private lateinit var replyLayout: View
    private lateinit var replySenderName: TextView
    private lateinit var replyMessageText: TextView
    private lateinit var cancelReplyButton: ImageButton
    private lateinit var chatProfileImage: ImageView
    private lateinit var onlineStatus: TextView
    private lateinit var btnCallParticipant: ImageButton
    
    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var messageListener: ListenerRegistration? = null
    private var chatRoomListener: ListenerRegistration? = null
    private lateinit var sessionManager: SessionManager
    
    private var editingMessage: ChatMessage? = null
    private val messagesList = mutableListOf<ChatMessage>()
    private var chatRoom: ChatRoom? = null
    private var replyingToMessage: ChatMessage? = null
    private var participantPhoneNumber: String = ""
    private var isInitialLoad = true



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background)

        sessionManager = SessionManager(this)
        @Suppress("DEPRECATION")
        chatRoom = intent.getParcelableExtra("CHAT_ROOM")

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        loadChatRoom()
        loadMessages()
    }



    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        chatTitle = findViewById(R.id.chatTitle)
        replyLayout = findViewById(R.id.replyLayout)
        replySenderName = findViewById(R.id.replySenderName)
        replyMessageText = findViewById(R.id.replyMessageText)
        cancelReplyButton = findViewById(R.id.cancelReplyButton)
        chatProfileImage = findViewById(R.id.chatProfileImage)
        onlineStatus = findViewById(R.id.onlineStatus)
        btnCallParticipant = findViewById(R.id.btnCallParticipant)
        
        hideReplyLayout()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messagesList.toList(), auth.currentUser?.uid ?: "") { message ->
            showChatOptions(message)
        }
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            if (editingMessage != null) {
                updateMessage()
            } else {
                sendMessage()
            }
        }

        cancelReplyButton.setOnClickListener {
            hideReplyLayout()
            cancelEditMode()
        }

        btnCallParticipant.setOnClickListener {
            callParticipant()
        }

        messageInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                if (editingMessage != null) updateMessage() else sendMessage()
                true
            } else {
                false
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (editingMessage != null) {
            cancelEditMode()
            return true
        }
        finish()
        return true
    }

    private fun callParticipant() {
        if (participantPhoneNumber.isEmpty()) {
            Toast.makeText(this, "Participant phone number not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$participantPhoneNumber")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadChatRoom() {
        chatRoom?.let { room ->
            val currentUserId = auth.currentUser?.uid ?: ""
            val otherParticipantId = room.getOtherParticipantId(currentUserId)
            var displayName = room.getOtherParticipantName(currentUserId)
            var avatarUrl = ""

            // Resolve real data from GlobalDataCache for consistency
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
                    
                    participantPhoneNumber = (cachedUser["phone"] as? String)
                        ?: (cachedUser["phoneNumber"] as? String) ?: ""
                }
            }

            chatTitle.text = com.example.homehub.utils.UsernameFormatter.formatUsername(displayName)
            com.example.homehub.utils.LetterAvatarHelper.setLetterAvatar(chatProfileImage, displayName, avatarUrl, otherParticipantId)

            // Listen for Online Status
            otherParticipantId?.let { uid ->
                db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                    val isOnline = snapshot?.getBoolean("isOnline") ?: false
                    
                    // Also refresh phone if not in cache
                    if (participantPhoneNumber.isEmpty()) {
                        participantPhoneNumber = snapshot?.getString("phone") 
                            ?: snapshot?.getString("phoneNumber") ?: ""
                    }
                    
                    onlineStatus.text = if (isOnline) "Online" else "Offline"
                    onlineStatus.setTextColor(if (isOnline) 
                        ContextCompat.getColor(this, R.color.green) 
                        else ContextCompat.getColor(this, R.color.grey_400))
                }
            }


            
        } ?: run {
            Toast.makeText(this, "Error loading chat", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadMessages() {
        chatRoom?.let { room ->
            messageListener = ChatManager.listenForMessages(room.id) { messages ->
                if (messages.isEmpty() && messagesList.isNotEmpty()) {
                    // Safety check: if server returns empty but we have data, don't clear immediately
                    // This often happens during momentary connection drops
                    Log.d("ChatActivity", "Ignoring empty snapshot to prevent disappearing flicker")
                    return@listenForMessages
                }

                val oldSize = messagesList.size
                messagesList.clear()
                messagesList.addAll(messages)
                
                messageAdapter.updateMessages(messagesList.toList())
                
                // Only scroll to bottom if it's the first load or a NEW message arrived
                if (isInitialLoad || messages.size > oldSize) {
                    scrollToBottom()
                    isInitialLoad = false
                }
                
                markMessagesAsRead()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sendOptimisticMessage(messageText: String, messageId: String) {
        val currentUser = auth.currentUser ?: return
        val userName = sessionManager.getCachedUserName(currentUser.uid)
        
        val optimisticMessage = ChatMessage(
            id = messageId,
            chatRoomId = chatRoom?.id ?: "",
            senderId = currentUser.uid,
            senderName = userName,
            message = messageText,
            timestamp = Date(),
            messageStatus = ChatMessage.STATUS_SENDING,
            replyToMessageId = replyingToMessage?.id ?: "",
            replyToMessage = replyingToMessage?.message ?: "",
            replyToSender = replyingToMessage?.senderName ?: "",
            isTemp = true
        )

        // Add to local list immediately
        messagesList.add(optimisticMessage)
        messageAdapter.updateMessages(messagesList.toList())
        scrollToBottom()
        
        messageInput.text.clear()
        hideReplyLayout()
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) return

        val currentUser = auth.currentUser ?: return

        chatRoom?.let { room ->
            // Use current message captured for closure
            val currentReplyingTo = replyingToMessage 
            
            // 1. Send optimistic UI update
            val messageId = ChatManager.sendMessage(room.id, messageText, currentReplyingTo) { success, id ->
                if (!success) {
                    // Update local message to failed status
                    messagesList.find { it.id == id }?.let {
                        val index = messagesList.indexOf(it)
                        messagesList[index] = it.copy(messageStatus = ChatMessage.STATUS_FAILED, isTemp = false)
                        messageAdapter.updateMessages(messagesList.toList())
                    }
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
            
            // 2. Reflect in UI instantly
            sendOptimisticMessage(messageText, messageId)
        }
    }

    private fun updateMessage() {
        val newText = messageInput.text.toString().trim()
        val messageToEdit = editingMessage ?: return
        
        if (newText.isEmpty() || newText == messageToEdit.message) {
            cancelEditMode()
            return
        }

        ChatManager.editMessage(chatRoom?.id ?: "", messageToEdit.id, newText) { success ->
            if (success) {
                cancelEditMode()
            } else {
                Toast.makeText(this, "Failed to update message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChatOptions(message: ChatMessage) {
        if (message.deleted || message.messageType == ChatMessage.TYPE_SYSTEM) {
           // Basic options for deleted messages if any, but usually we just skip
           return
        }

        val options = mutableListOf("Reply", "Copy")
        if (message.senderId == auth.currentUser?.uid) {
            options.add("Edit")
            options.add("Delete")
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Reply" -> showReplyLayout(message)
                    "Copy" -> copyToClipboard(message.message)
                    "Edit" -> enterEditMode(message)
                    "Delete" -> confirmDelete(message)
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("message", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }

    private fun enterEditMode(message: ChatMessage) {
        editingMessage = message
        messageInput.setText(message.message)
        messageInput.requestFocus()
        messageInput.setSelection(message.message.length)
        
        replyLayout.visibility = View.VISIBLE
        replySenderName.text = "Editing message"
        replyMessageText.text = message.message
        
        sendButton.setIconResource(R.drawable.ic_check) // Assuming ic_check exists or similar
    }

    private fun cancelEditMode() {
        editingMessage = null
        messageInput.text.clear()
        replyLayout.visibility = View.GONE
        sendButton.setIconResource(R.drawable.baseline_send_24)
    }

    private fun confirmDelete(message: ChatMessage) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete message?")
            .setMessage("This message will be deleted for everyone in this chat.")
            .setPositiveButton("Delete") { _, _ ->
                ChatManager.deleteMessage(chatRoom?.id ?: "", message.id) { success ->
                    if (!success) {
                        Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markMessagesAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return
        chatRoom?.let { room ->
            ChatManager.markMessagesAsRead(room.id, currentUserId)
        }
    }

    private fun showReplyLayout(message: ChatMessage) {
        if (message.messageType == ChatMessage.TYPE_SYSTEM) return

        replyingToMessage = message
        replyLayout.visibility = View.VISIBLE
        val formattedName = com.example.homehub.utils.UsernameFormatter.formatUsername(message.senderName)
        replySenderName.text = "Replying to $formattedName"
        replyMessageText.text = message.message
        messageInput.requestFocus()

        chatRecyclerView.post {
            chatRecyclerView.smoothScrollToPosition(messagesList.size - 1)
        }
    }

    private fun hideReplyLayout() {
        replyingToMessage = null
        replyLayout.visibility = View.GONE
    }

    private fun scrollToBottom() {
        chatRecyclerView.post {
            if (messagesList.isNotEmpty()) {
                chatRecyclerView.smoothScrollToPosition(messagesList.size - 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
        chatRoomListener?.remove()
        chatRoom?.let { room ->
            ChatManager.removeMessageListener(room.id)
        }
    }

    override fun onPause() {
        super.onPause()
        markMessagesAsRead()
    }

    override fun onResume() {
        super.onResume()
        markMessagesAsRead()
    }
}
