package com.example.homehub.supplier

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityWaterSupplierNotificationsBinding
import com.example.homehub.other.NotificationsAdapter
import com.example.homehub.utils.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class WaterSupplierNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterSupplierNotificationsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: NotificationsAdapter
    private var notificationsList = mutableListOf<Notification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaterSupplierNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
        loadNotifications()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationsAdapter(
            onNotificationClick = { notification ->
                if (!notification.isRead) {
                    markAsRead(notification)
                }
            },
            onNotificationLongClick = { notification ->
                // Maybe delete notification
            }
        )
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadNotifications()
        }

    }

    private fun loadNotifications() {
        val user = auth.currentUser ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        db.collection("notifications")
            .whereEqualTo("userId", user.uid) // Consistent with NotificationManager field name
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                
                if (error != null) {
                    Log.e("SupplierNotif", "Error loading notifications", error)
                    return@addSnapshotListener
                }

                notificationsList.clear()
                snapshots?.forEach { doc ->
                    val notification = doc.toObject(Notification::class.java)
                    notification.id = doc.id
                    notificationsList.add(notification)
                }
                
                adapter.updateNotificationsImmediately(notificationsList)
                if (notificationsList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun markAsRead(notification: Notification) {
        db.collection("notifications").document(notification.id)
            .update("isRead", true)
            .addOnSuccessListener {
                adapter.markAsRead(notification.id)
            }
    }

    private fun markAllAsRead() {
        val user = auth.currentUser ?: return
        db.collection("notifications")
            .whereEqualTo("relatedId", user.uid)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                snapshots.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit().addOnSuccessListener {
                    adapter.markAllAsRead()
                }
            }
    }
}
