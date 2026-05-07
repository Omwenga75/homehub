package com.example.homehub.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.homehub.R
import com.example.homehub.caretaker.CaretakerApplication

class ApplicationsAdapter(
    private var applications: List<CaretakerApplication>,
    private val onItemClick: (CaretakerApplication, String) -> Unit
) : RecyclerView.Adapter<ApplicationsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.applicationCard)
        val nameText: TextView = itemView.findViewById(R.id.nameText)
        val emailText: TextView = itemView.findViewById(R.id.emailText)
        val propertyTypeText: TextView = itemView.findViewById(R.id.propertyTypeText)
        val locationText: TextView = itemView.findViewById(R.id.locationText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        val priceText: TextView = itemView.findViewById(R.id.priceText)
        val roomsText: TextView = itemView.findViewById(R.id.roomsText)
        val secureBadge: TextView = itemView.findViewById(R.id.secureBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = applications[position]

        // Set the basic information
        holder.nameText.text = application.fullName.ifEmpty { "Unknown Applicant" }
        holder.emailText.text = application.email.ifEmpty { "No email provided" }
        holder.propertyTypeText.text = application.propertyType.ifEmpty { "Not specified" }
        holder.locationText.text = application.getFormattedLocation()
        holder.priceText.text = application.getFormattedPrice()
        holder.dateText.text = application.getFormattedApplicationDate()

        // Set the rooms text dynamically
        holder.roomsText.text = application.getRoomText()

        // Set secure badge visibility
        holder.secureBadge.visibility = if (application.isSecure) View.VISIBLE else View.GONE

        // Set status badge
        holder.statusBadge.text = application.getStatusDisplayText()
        holder.statusBadge.setBackgroundResource(
            when (application.status) {
                CaretakerApplication.STATUS_APPROVED -> R.drawable.badge_approved
                CaretakerApplication.STATUS_REJECTED -> R.drawable.badge_rejected
                else -> R.drawable.badge_pending
            }
        )

        // Set card click listener
        holder.cardView.setOnClickListener {
            onItemClick(application, application.documentId)
        }
    }

    override fun getItemCount(): Int = applications.size

    fun updateApplications(newApplications: List<CaretakerApplication>) {
        this.applications = newApplications
        notifyDataSetChanged()
    }
}
