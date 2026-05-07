package com.example.homehub.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import androidx.activity.enableEdgeToEdge
import com.google.firebase.auth.FirebaseAuth

class ChatListActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatRoomAdapter
    private lateinit var noChatsText: android.view.View
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_list)

        com.example.homehub.utils.GlobalDataCache.initialize()
        initializeViews()
        setupRecyclerView()
        loadChatRooms()
    }

    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        noChatsText = findViewById(R.id.noChatsText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        findViewById<TextView>(R.id.titleText).text = "Messages"
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatRoomAdapter(emptyList()) { chatRoom ->
            openChat(chatRoom)
        }

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadChatRooms() {
        ChatManager.getChatRooms { chatRooms ->
            if (chatRooms.isEmpty()) {
                noChatsText.visibility = android.view.View.VISIBLE
                chatRecyclerView.visibility = android.view.View.GONE
            } else {
                noChatsText.visibility = android.view.View.GONE
                chatRecyclerView.visibility = android.view.View.VISIBLE
                chatAdapter.updateChatRooms(chatRooms)
                chatAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun openChat(chatRoom: ChatRoom) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("CHAT_ROOM", chatRoom)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
