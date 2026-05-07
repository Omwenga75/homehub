package com.example.homehub.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import java.text.SimpleDateFormat
import java.util.*

class RecentActivityAdapter(
    private var activities: List<RecentActivity> = emptyList()
) : RecyclerView.Adapter<RecentActivityAdapter.ActivityViewHolder>() {

    fun updateActivities(newActivities: List<RecentActivity>) {
        activities = newActivities
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(activities[position])
    }

    override fun getItemCount(): Int = activities.size

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val iconView: ImageView? = itemView.findViewById(R.id.activityIcon)

        fun bind(activity: RecentActivity) {
            titleText.text = activity.title
            descriptionText.text = activity.description
            
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            timeText.text = sdf.format(Date(activity.timestamp))

            // Set icon based on type if iconView exists
            iconView?.let {
                val context = it.context
                when (activity.activityType) {
                    ActivityType.NEW_PROPERTY_LISTED, ActivityType.NEW_PROPERTY -> {
                        it.setImageResource(R.drawable.ic_house_placeholder)
                        it.setColorFilter(ContextCompat.getColor(context, R.color.blue_500))
                    }
                    ActivityType.NEW_BOOKING -> {
                        it.setImageResource(R.drawable.time)
                        it.setColorFilter(ContextCompat.getColor(context, R.color.green_500))
                    }
                    ActivityType.USER_BANNED, ActivityType.PROPERTY_SUSPENDED -> {
                        it.setImageResource(R.drawable.ic_verified) // Use as warning for now
                        it.setColorFilter(ContextCompat.getColor(context, R.color.red_500))
                    }
                    else -> {
                        it.setImageResource(R.drawable.person)
                        it.setColorFilter(ContextCompat.getColor(context, R.color.gray_500))
                    }
                }
            }
        }
    }
}
