package com.example.homehub.other

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.utils.NotificationManager
import com.example.homehub.utils.Notification
import com.example.homehub.R
import com.example.homehub.auth.PrivateAccessActivity
import com.example.homehub.caretaker.CaretakerDashboardActivity
import com.example.homehub.databinding.ActivityNotificationsBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationsAdapter: NotificationsAdapter

    private var isLoading = false
    private var isRealTimeListenerActive = false
    private var isInitialLoadComplete = false
    private val notificationScope = CoroutineScope(Dispatchers.IO + Job())
    private val seenNotificationIds = mutableSetOf<String>()
    private var realTimeListenerRegistration: (() -> Unit)? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = resources.getColor(R.color.themeColor)

        auth = FirebaseAuth.getInstance()

        if (!NotificationManager.isInitialized) {
            NotificationManager.initialize(this)
            Log.d("NotificationsActivity", "NotificationManager initialized")
        }

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        requestNotificationPermission()

        loadNotificationsImmediately()
        setupRealtimeListener()
    }

    override fun onResume() {
        super.onResume()
        if (isInitialLoadComplete) {
            loadNotificationsImmediately()
        }
        if (!isRealTimeListenerActive) {
            setupRealtimeListener()
        }
        updateUnreadCount()
    }

    override fun onPause() {
        super.onPause()
        isRealTimeListenerActive = false
        realTimeListenerRegistration?.invoke()
        realTimeListenerRegistration = null
    }

    override fun onDestroy() {
        super.onDestroy()
        realTimeListenerRegistration?.invoke()
        notificationScope.coroutineContext.cancelChildren()
        seenNotificationIds.clear()
        NotificationManager.stopListening()
    }

    private fun setupRealtimeListener() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            if (isRealTimeListenerActive) return@let

            realTimeListenerRegistration = NotificationManager.startListeningForNotifications(user.uid) { notification ->
                if (seenNotificationIds.contains(notification.id)) return@startListeningForNotifications
                seenNotificationIds.add(notification.id)

                runOnUiThread {
                    try {
                        val wasAdded = notificationsAdapter.addNotificationAtTop(notification)
                        if (wasAdded) {
                            if (binding.recyclerView.visibility != View.VISIBLE) {
                                binding.recyclerView.visibility = View.VISIBLE
                                binding.emptyState.visibility = View.GONE
                            }
                            binding.recyclerView.smoothScrollToPosition(0)
                            updateUnreadCount()
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationsActivity", "Error handling real-time notification: ${e.message}")
                    }
                }
            }
            isRealTimeListenerActive = true
        } ?: run {
            showEmptyState("Please login to view notifications")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS) -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("This app needs notification permission to show real-time updates.")
                        .setPositiveButton("OK") { _, _ ->
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadNotificationsImmediately()
            }
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener { finish() }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_delete_all -> { showDeleteConfirmation(); true }
                R.id.menu_refresh -> { loadNotificationsImmediately(); true }
                else -> false
            }
        }
    }

    private var allNotifications = listOf<Notification>()

    private fun applyFilter() {
        notificationsAdapter.updateNotificationsImmediately(allNotifications)
        if (allNotifications.isEmpty()) showEmptyState("No notifications yet") else showNotificationsList()
    }

    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsAdapter(
            onNotificationClick = { notification -> handleNotificationClick(notification) },
            onNotificationLongClick = { notification -> showNotificationOptions(notification) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter = notificationsAdapter
            setHasFixedSize(true)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    binding.swipeRefreshLayout.isEnabled = !recyclerView.canScrollVertically(-1)
                }
            })
        }

        notificationsAdapter.setOnScrollToTopListener {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener { loadNotificationsImmediately() }
        binding.swipeRefreshLayout.setColorSchemeColors(
            resources.getColor(R.color.blue),
            resources.getColor(R.color.themeColor),
            resources.getColor(R.color.primaryColor)
        )
        binding.swipeRefreshLayout.setDistanceToTriggerSync(250)
        binding.swipeRefreshLayout.isEnabled = false
    }

    private fun loadNotificationsImmediately() {
        if (isLoading) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = true
        binding.emptyState.visibility = View.GONE

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState("Please login to view notifications")
            isLoading = false
            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        seenNotificationIds.clear()

        NotificationManager.getUserNotifications(currentUser.uid) { notifications ->
            runOnUiThread {
                try {
                    allNotifications = notifications
                    notifications.forEach { seenNotificationIds.add(it.id) }
                    applyFilter()
                    updateUnreadCount()
                } catch (e: Exception) {
                    Log.e("NotificationsActivity", "Error updating UI: ${e.message}")
                    showEmptyState("Error loading notifications")
                } finally {
                    isLoading = false
                    isInitialLoadComplete = true
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun showEmptyState(message: String) {
        binding.recyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        val emptyStateText = binding.emptyState.findViewById<android.widget.TextView>(R.id.emptyStateText)
        emptyStateText?.text = message
    }

    private fun showNotificationsList() {
        binding.emptyState.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isEnabled = true
    }

    private fun handleNotificationClick(notification: Notification) {
        if (!notification.isRead) {
            notificationScope.launch {
                try {
                    NotificationManager.markAsRead(notification.id)
                    runOnUiThread {
                        notificationsAdapter.markAsRead(notification.id)
                        updateUnreadCount()
                    }
                } catch (e: Exception) {
                    Log.e("NotificationsActivity", "Error marking as read: ${e.message}")
                }
            }
        }
        handleNotificationAction(notification)
    }

    private fun handleNotificationAction(notification: Notification) {
        try {
            when (notification.notificationType) {
                "VERIFICATION" -> {
                    if (notification.title.contains("Approved", ignoreCase = true)) {
                        startActivity(Intent(this, CaretakerDashboardActivity::class.java))
                    } else if (notification.title.contains("Rejected", ignoreCase = true)) {
                        // Notification rejected - no action needed
                    }
                }
                else -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(notification.title)
                        .setMessage(notification.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationsActivity", "Error opening notification: ${e.message}")
        }
    }

    private fun showNotificationOptions(notification: Notification) {
        val options = arrayOf("📖 Mark as Read", "🗑️ Delete", "❌ Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Notification Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> markAsRead(notification.id)
                    1 -> deleteNotification(notification.id)
                }
            }
            .show()
    }

    private fun markAsRead(notificationId: String) {
        notificationScope.launch {
            try {
                NotificationManager.markAsRead(notificationId)
                runOnUiThread {
                    notificationsAdapter.markAsRead(notificationId)
                    updateUnreadCount()
                }
            } catch (e: Exception) {
                Log.e("NotificationsActivity", "Error marking as read: ${e.message}")
            }
        }
    }

    private fun markAllAsRead() {
        val currentUser = auth.currentUser ?: return
        if (notificationsAdapter.itemCount == 0) {
            return
        }
        notificationScope.launch {
            try {
                NotificationManager.markAllAsRead(currentUser.uid)
                runOnUiThread {
                    notificationsAdapter.markAllAsRead()
                    updateUnreadCount()
                }
            } catch (e: Exception) {
                Log.e("NotificationsActivity", "Error marking all as read: ${e.message}")
            }
        }
    }

    private fun deleteNotification(notificationId: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Notification")
            .setMessage("Are you sure you want to delete this notification?")
            .setPositiveButton("Delete") { _, _ ->
                notificationScope.launch {
                    try {
                        NotificationManager.deleteNotification(notificationId)
                        runOnUiThread {
                            notificationsAdapter.removeNotification(notificationId)
                            seenNotificationIds.remove(notificationId)
                            if (notificationsAdapter.itemCount == 0) showEmptyState("📭 No notifications yet")
                            updateUnreadCount()
                            Toast.makeText(this@NotificationsActivity, "🗑️ Deleted", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@NotificationsActivity, "❌ Error deleting", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        if (notificationsAdapter.itemCount == 0) {
            Toast.makeText(this, "📭 No notifications to delete", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete All Notifications")
            .setMessage("Are you sure you want to delete ALL notifications?")
            .setPositiveButton("Delete All") { _, _ -> deleteAllNotifications() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllNotifications() {
        val currentUser = auth.currentUser ?: return
        notificationScope.launch {
            try {
                NotificationManager.getUserNotifications(currentUser.uid) { notifications ->
                    notifications.forEach { NotificationManager.deleteNotification(it.id) }
                    runOnUiThread {
                        notificationsAdapter.clearAll()
                        seenNotificationIds.clear()
                        showEmptyState("📭 No notifications yet")
                        updateUnreadCount()
                        Toast.makeText(this@NotificationsActivity, "✅ All deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@NotificationsActivity, "❌ Error deleting", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUnreadCount() {
        val currentUser = auth.currentUser ?: return
        notificationScope.launch {
            try {
                NotificationManager.getUnreadCount(currentUser.uid) { count ->
                    runOnUiThread {
                        binding.toolbarTitle.text = if (count > 0) "Notifications ($count)" else "Notifications"
                        binding.toolbarTitle.alpha = 1.0f
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { binding.toolbarTitle.text = "Notifications" }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
