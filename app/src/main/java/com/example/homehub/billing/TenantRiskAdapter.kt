package com.example.homehub.billing

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import android.content.Intent
import android.graphics.Color
import android.net.Uri

import com.example.homehub.R

class TenantRiskAdapter(
    private var bookings: List<Booking>,
    private val onDetailsClick: (Booking) -> Unit
) : RecyclerView.Adapter<TenantRiskAdapter.RiskViewHolder>() {

    inner class RiskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tenantName)
        val property: TextView = itemView.findViewById(R.id.propertyName)
        val image: ImageView = itemView.findViewById(R.id.tenantImage)
        val badge: TextView = itemView.findViewById(R.id.riskLevelBadge)
        val score: TextView = itemView.findViewById(R.id.riskScore)
        val reasons: TextView = itemView.findViewById(R.id.riskReasons)
        val contactBtn: MaterialButton = itemView.findViewById(R.id.contactTenantBtn)
        val detailsBtn: MaterialButton = itemView.findViewById(R.id.viewDetailsBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tenant_risk, parent, false)
        return RiskViewHolder(view)
    }

    override fun onBindViewHolder(holder: RiskViewHolder, position: Int) {
        val booking = bookings[position]
        
        // Use simulated payment history for now
        val analysis = RentRiskService.analyzeRisk(booking, emptyList())

        holder.name.text = booking.studentName
        holder.property.text = "Property: ${booking.propertyName}"
        
        holder.badge.text = analysis.level.label
        holder.badge.setBackgroundColor(Color.parseColor(analysis.level.colorHex))
        
        holder.score.text = "Score: ${analysis.score}/100"
        
        val reasonsText = if (analysis.reasons.isEmpty()) {
            "• Consistent activity patterns detected"
        } else {
            analysis.reasons.joinToString("\n") { "• $it" }
        }
        holder.reasons.text = reasonsText

        // Tenant image
        if (booking.studentImage.isNotEmpty()) {
            holder.image.load(booking.studentImage) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.baseline_account_circle_24)
            }
        }

        holder.contactBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${booking.studentPhone}"))
            it.context.startActivity(intent)
        }

        holder.detailsBtn.setOnClickListener {
            onDetailsClick(booking)
        }
    }

    override fun getItemCount(): Int = bookings.size

    fun updateBookings(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }
}
