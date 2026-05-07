package com.example.homehub.caretaker

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityCaretakerNotificationsBinding
import com.example.homehub.R
import com.example.homehub.utils.NotificationManager
import com.example.homehub.other.NotificationsAdapter
import com.example.homehub.utils.Notification
import com.google.firebase.auth.FirebaseAuth

class CaretakerNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerNotificationsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationsAdapter: NotificationsAdapter
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaretakerNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Initialize NotificationManager if not already initialized
        if (!NotificationManager.isInitialized) {
            NotificationManager.initialize(this)
        }

        setupUI()
        loadNotifications()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        notificationsAdapter = NotificationsAdapter(
            onNotificationClick = { notification ->
                if (!notification.isRead) {
                    NotificationManager.markAsRead(notification.id) { success ->
                        if (success) {
                            notificationsAdapter.markAsRead(notification.id)
                        }
                    }
                }
                if (notification.notificationType == "LEAVE_REQUEST" || notification.title.contains("Vacation", ignoreCase = true)) {
                    val intent = android.content.Intent(this, CaretakerRoomRequestsActivity::class.java)
                    startActivity(intent)
                    return@NotificationsAdapter
                }

                // Show detail dialog
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(notification.title)
                    .setMessage(notification.message)
                    .setPositiveButton("OK", null)
                    .show()
            },
            onNotificationLongClick = { notification ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Alert")
                    .setMessage("Remove this notification from your records?")
                    .setPositiveButton("Delete") { _, _ ->
                        NotificationManager.deleteNotification(notification.id) { success ->
                            if (success) {
                                notificationsAdapter.removeNotification(notification.id)
                                if (notificationsAdapter.itemCount == 0) {
                                    binding.emptyState.visibility = View.VISIBLE
                                    binding.recyclerView.visibility = View.GONE
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CaretakerNotificationsActivity)
            adapter = notificationsAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadNotifications(true)
        }

        // Setup menu
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_refresh -> {
                    loadNotifications(true)
                    true
                }
                else -> false
            }
        }

    }

    private fun loadNotifications(forceRefresh: Boolean = false) {
        if (isLoading) return
        
        val uid = auth.currentUser?.uid ?: return
        
        isLoading = true
        binding.progressBar.visibility = if (forceRefresh) View.GONE else View.VISIBLE
        binding.emptyState.visibility = View.GONE

        NotificationManager.getUserNotifications(uid, forceRefresh) { notifications ->
            runOnUiThread {
                isLoading = false
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false

                if (notifications.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    notificationsAdapter.updateNotificationsImmediately(notifications)
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
