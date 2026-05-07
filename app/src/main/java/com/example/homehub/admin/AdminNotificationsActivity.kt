package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityAdminNotificationsBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.homehub.R
import com.example.homehub.utils.NotificationManager
import com.example.homehub.other.NotificationsAdapter
import com.example.homehub.utils.Notification

class AdminNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminNotificationsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationsAdapter: NotificationsAdapter
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminNotificationsBinding.inflate(layoutInflater)
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
                if (notification.notificationType == "VERIFICATION_REQUEST") {
                    val intent = Intent(this, VerificationDetailsActivity::class.java).apply {
                        putExtra("APPLICANT_ID", notification.applicantId)
                    }
                    startActivity(intent)
                } else {
                    // Show detail dialog
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(notification.title)
                        .setMessage(notification.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            },
            onNotificationLongClick = { notification ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Notification")
                    .setMessage("Are you sure you want to delete this notification?")
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
            layoutManager = LinearLayoutManager(this@AdminNotificationsActivity)
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
        
        isLoading = true
        binding.progressBar.visibility = if (forceRefresh) View.GONE else View.VISIBLE
        binding.emptyState.visibility = View.GONE

        // NotificationManager currently handles failures internally and returns empty list.
        // For 'Insufficient Permission' issues, we want to catch if the user is unauthenticated.
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        NotificationManager.getAdminNotifications(forceRefresh) { notifications ->
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

    private fun redirectToLogin() {
        android.widget.Toast.makeText(this, "Session expired or access denied.", android.widget.Toast.LENGTH_LONG).show()
        val intent = Intent(this, com.example.homehub.auth.UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
