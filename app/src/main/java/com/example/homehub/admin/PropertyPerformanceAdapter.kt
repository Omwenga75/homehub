package com.example.homehub.admin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.example.homehub.R

class PropertyPerformanceAdapter(
    private val properties: List<PropertyPerformance>,
    private val onItemClick: (PropertyPerformance) -> Unit = {}
) : RecyclerView.Adapter<PropertyPerformanceAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.propertyName)
        val rating: TextView = itemView.findViewById(R.id.propertyRating)
        val occupancy: TextView = itemView.findViewById(R.id.propertyOccupancy)
        val revenue: TextView = itemView.findViewById(R.id.propertyRevenue)
        val nightsBooked: TextView = itemView.findViewById(R.id.nightsBooked)
        val averageRate: TextView = itemView.findViewById(R.id.averageRate)
        val occupancyProgressBar: LinearProgressIndicator = itemView.findViewById(R.id.occupancyProgressBar)
        val occupancyProgressText: TextView = itemView.findViewById(R.id.occupancyProgressText)
        val performanceBadge: TextView = itemView.findViewById(R.id.performanceBadge)
        val trendingIcon: ImageView = itemView.findViewById(R.id.trendingIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_property_performance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val property = properties[position]

        holder.name.text = property.name
        holder.rating.text = String.format("%.1f", property.rating)
        holder.occupancy.text = "${property.occupancy}%"
        holder.revenue.text = formatCurrency(property.revenue)
        holder.nightsBooked.text = "${calculateNightsBooked(property)} nights"
        holder.averageRate.text = "KSh ${calculateAverageRate(property)}/night"

        // FIXED: Set progress programmatically instead of in XML
        holder.occupancyProgressBar.progress = property.occupancy
        holder.occupancyProgressText.text = "${property.occupancy}%"

        // Set performance badge and trending icon
        val (badgeText, badgeColor, trendingIconRes) = getPerformanceBadge(property)
        holder.performanceBadge.text = badgeText
        holder.performanceBadge.setTextColor(badgeColor)
        holder.trendingIcon.setImageResource(trendingIconRes)
        holder.trendingIcon.setColorFilter(badgeColor)

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick(property)
        }

        // Add animation for item entry
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 50f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(position * 100L)
            .start()
    }

    override fun getItemCount() = properties.size

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = java.text.NumberFormat.getNumberInstance()
            format.maximumFractionDigits = 0
            "KSh ${format.format(amount)}"
        } catch (e: Exception) {
            "KSh ${amount.toInt()}"
        }
    }

    private fun calculateNightsBooked(property: PropertyPerformance): Int {
        // Simple calculation based on occupancy and 30-day month
        return (property.occupancy * 30) / 100
    }

    private fun calculateAverageRate(property: PropertyPerformance): Int {
        // Simple calculation: revenue / nights booked
        val nights = calculateNightsBooked(property)
        return if (nights > 0) (property.revenue / nights).toInt() else 0
    }

    private fun getPerformanceBadge(property: PropertyPerformance): Triple<String, Int, Int> {
        return when {
            property.occupancy >= 80 -> Triple(
                "High Performance",
                Color.parseColor("#10B981"),
                R.drawable.ic_trending_up
            )
            property.occupancy >= 60 -> Triple(
                "Good Performance",
                Color.parseColor("#3B82F6"),
                R.drawable.ic_trending_up
            )
            property.occupancy >= 40 -> Triple(
                "Average Performance",
                Color.parseColor("#F59E0B"),
                R.drawable.ic_trending_flat
            )
            else -> Triple(
                "Needs Attention",
                Color.parseColor("#EF4444"),
                R.drawable.ic_trending_up
            )
        }
    }
}
