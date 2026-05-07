package com.example.homehub.caretaker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.chat.ChatRoom
import com.example.homehub.chat.ChatManager
import com.example.homehub.chat.ChatRoomAdapter
import com.example.homehub.chat.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class CaretakerMessagesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var chatRoomsRecyclerView: RecyclerView
    private lateinit var chatRoomAdapter: ChatRoomAdapter
    private lateinit var emptyState: android.view.View

    private var chatRoomsListener: ListenerRegistration? = null
    private val chatRoomsList = mutableListOf<ChatRoom>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caretaker_messages)

        window.statusBarColor = resources.getColor(R.color.themeColor)
        window.navigationBarColor = resources.getColor(R.color.white)

        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupRecyclerView()
        loadCaretakerChatRooms()
    }

    private fun initializeViews() {
        chatRoomsRecyclerView = findViewById(R.id.messagesRecyclerView)
        emptyState = findViewById(R.id.emptyState)

        findViewById<TextView>(R.id.titleText).text = "Messages"
    }

    private fun setupRecyclerView() {
        chatRoomAdapter = ChatRoomAdapter(chatRoomsList) { chatRoom ->
            openChat(chatRoom)
        }
        chatRoomsRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRoomsRecyclerView.adapter = chatRoomAdapter
    }

    private fun loadCaretakerChatRooms() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            chatRoomsListener = ChatManager.getCaretakerChatRooms(currentUser.uid) { chatRooms ->
                chatRoomsList.clear()
                chatRoomsList.addAll(chatRooms)
                chatRoomAdapter.updateChatRooms(chatRoomsList)
                updateEmptyState()
            }
        } else {
            updateEmptyState()
        }
    }
    private fun openChat(chatRoom: ChatRoom) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("CHAT_ROOM", chatRoom)
        startActivity(intent)
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (chatRoomsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        chatRoomsListener?.remove()
    }
}
