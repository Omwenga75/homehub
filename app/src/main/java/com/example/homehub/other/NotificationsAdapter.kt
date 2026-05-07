package com.example.homehub.other

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.databinding.ItemNotificationsBinding
import com.example.homehub.utils.Notification
import androidx.core.content.ContextCompat
import com.example.homehub.R
import java.text.SimpleDateFormat
import java.util.*

class NotificationsAdapter(
    private val onNotificationClick: (Notification) -> Unit,
    private val onNotificationLongClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    private var notifications = mutableListOf<Notification>()
    private var onScrollToTop: (() -> Unit)? = null

    fun setOnScrollToTopListener(listener: () -> Unit) {
        onScrollToTop = listener
    }

    fun updateNotificationsImmediately(newNotifications: List<Notification>) {
        val diffResult = DiffUtil.calculateDiff(NotificationDiffCallback(notifications, newNotifications))
        notifications.clear()
        notifications.addAll(newNotifications)
        diffResult.dispatchUpdatesTo(this)
    }

    fun addNotificationAtTop(notification: Notification): Boolean {
        // Check for duplicates
        if (notifications.any { it.id == notification.id }) return false
        notifications.add(0, notification)
        notifyItemInserted(0)
        onScrollToTop?.invoke()
        return true
    }

    fun markAsRead(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            notifications[index].isRead = true
            notifyItemChanged(index)
        }
    }

    fun removeNotification(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            notifications.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun markAllAsRead() {
        notifications.forEachIndexed { index, notification ->
            if (!notification.isRead) {
                notification.isRead = true
                notifyItemChanged(index)
            }
        }
    }

    fun clearAll() {
        val size = notifications.size
        notifications.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: ItemNotificationsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            binding.notificationTitle.text = notification.title
            binding.notificationMessage.text = notification.message
            binding.unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
            
            // Format time
            binding.notificationTime.text = formatTimestamp(notification.timestamp.toDate())

            // Priority UI (Stripe and Badge)
            val context = binding.root.context
            val (stripeColor, badgeColor, badgeText) = when (notification.priority.uppercase()) {
                "URGENT" -> Triple(
                    ContextCompat.getColor(context, R.color.red_500),
                    ContextCompat.getColor(context, R.color.red_500),
                    "URGENT"
                )
                "HIGH" -> Triple(
                    ContextCompat.getColor(context, R.color.orange_500),
                    ContextCompat.getColor(context, R.color.orange_500),
                    "HIGH PRIORITY"
                )
                "MEDIUM" -> Triple(
                    ContextCompat.getColor(context, R.color.blue_500),
                    ContextCompat.getColor(context, R.color.blue_500),
                    "INFO"
                )
                else -> Triple(
                    ContextCompat.getColor(context, R.color.grey_500),
                    ContextCompat.getColor(context, R.color.grey_500),
                    "LOW"
                )
            }

            binding.statusStripe.setBackgroundColor(stripeColor)
            
            // Only show badge for non-normal priorities to keep UI clean
            if (notification.priority.uppercase() in listOf("URGENT", "HIGH")) {
                binding.priorityBadge.visibility = View.VISIBLE
                binding.priorityBadge.text = badgeText
                binding.priorityBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor).withAlpha(30)
                binding.priorityBadge.setTextColor(badgeColor)
            } else {
                binding.priorityBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener { onNotificationClick(notification) }
            binding.root.setOnLongClickListener {
                onNotificationLongClick(notification)
                true
            }
        }

        private fun formatTimestamp(date: Date): String {
            val now = Date()
            val diff = now.time - date.time
            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
            }
        }
    }

    class NotificationDiffCallback(
        private val oldList: List<Notification>,
        private val newList: List<Notification>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = oldList[oldPos] == newList[newPos]
    }
}
