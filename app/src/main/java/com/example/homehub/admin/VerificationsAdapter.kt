package com.example.homehub.admin

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import java.text.SimpleDateFormat
import java.util.Locale

class VerificationsAdapter(
    private var verificationList: List<VerificationRequest>,
    private val onVerificationClicked: (VerificationRequest, String) -> Unit
) : RecyclerView.Adapter<VerificationsAdapter.VerificationViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class VerificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roleText: TextView = itemView.findViewById(R.id.roleText)
        val nameText: TextView = itemView.findViewById(R.id.nameText)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        val phoneText: TextView = itemView.findViewById(R.id.phoneText)
        val idNumberText: TextView = itemView.findViewById(R.id.idNumberText)
        val docTypeText: TextView = itemView.findViewById(R.id.docTypeText)
        val regionText: TextView = itemView.findViewById(R.id.regionText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val verification = verificationList[position]
                    onVerificationClicked(verification, verification.documentId)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_verification_request, parent, false)
        return VerificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: VerificationViewHolder, position: Int) {
        val verification = verificationList[position]

        holder.roleText.text = verification.role.uppercase()
        holder.nameText.text = verification.fullName
        holder.phoneText.text = if (verification.phone.isNullOrEmpty()) "Not provided" else verification.phone
        holder.idNumberText.text = verification.idNumber.ifEmpty { "N/A" }
        holder.docTypeText.text = verification.documentType
        holder.regionText.text = verification.location.ifEmpty { "N/A" }

        // Format date
        if (verification.submittedAt != null) {
            holder.dateText.text = dateFormat.format(verification.submittedAt!!.toDate())
        } else {
            holder.dateText.text = "Unknown Date"
        }

        // Setup status badge
        setupStatusBadge(holder.statusBadge, verification.status)
    }

    private fun setupStatusBadge(badge: TextView, status: String) {
        val (bgRes, textColorStr) = when (status.lowercase()) {
            "pending" -> Pair(R.drawable.badge_pending, "#FF9800")
            "approved" -> Pair(R.drawable.badge_secure, "#FFFFFF")
            "rejected" -> Pair(R.drawable.badge_rejected, "#FFFFFF") // Use badge_rejected if exists or just color state
            else -> Pair(R.drawable.badge_pending, "#FF9800")
        }

        badge.text = status.replaceFirstChar { it.uppercase() }
        badge.setBackgroundResource(bgRes)
        
        // Custom coloring for specific badges if needed
        if (status.lowercase() == "rejected") {
            badge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
        } else if (status.lowercase() == "approved") {
            badge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        } else {
            badge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
        }
    }

    override fun getItemCount(): Int {
        return verificationList.size
    }

    fun updateVerifications(newList: List<VerificationRequest>) {
        verificationList = newList
        notifyDataSetChanged()
    }
}
