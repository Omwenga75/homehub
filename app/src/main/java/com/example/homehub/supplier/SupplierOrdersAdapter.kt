package com.example.homehub.supplier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.databinding.ItemSupplierOrderBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.example.homehub.other.Extensions.loadProfileImage
import java.text.SimpleDateFormat
import java.util.*

class SupplierOrdersAdapter(
    private var orders: List<SupplierOrder>,
    private val onActionClick: (SupplierOrder, String) -> Unit
) : RecyclerView.Adapter<SupplierOrdersAdapter.OrderViewHolder>() {

    fun updateOrders(newOrders: List<SupplierOrder>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemSupplierOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(private val binding: ItemSupplierOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(order: SupplierOrder) {
            val db = FirebaseFirestore.getInstance()
            val context = binding.root.context
            
            // Format Date - FIXED: tvOrderDate -> tvDate to match modernized layout
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            binding.tvDate.text = sdf.format(order.timestamp)
            
            // Status Badge - FIXED: tvOrderStatus -> tvStatus, and utilizing cardStatus
            binding.tvStatus.text = order.status.uppercase()
            updateStatusUI(order.status)
            
            // Order Details
            binding.tvTotalAmount.text = "KSh ${String.format("%,.0f", order.amount)}"
            binding.tvQuantity.text = order.waterType
            
            // Display Payment Method in the "Ordered" slot for clarity
            binding.tvDate.text = if (order.paymentMethod == "CASH_ON_DELIVERY") "POD" else "M-PESA"

            // Fetch Customer Details
            binding.tvCustomerName.text = "Loading applicant..."
            db.collection("users").document(order.studentId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("fullName") ?: doc.getString("username") ?: "HomeHub User"
                        val phone = doc.getString("phone") ?: "No phone contact"
                        val address = doc.getString("address") ?: doc.getString("residenceAddress") ?: "Location not set"
                        val profileUrl = doc.getString("profileImageUrl") ?: doc.getString("profilePictureUrl") ?: ""
                        val lastUpdate = doc.getLong("lastProfileUpdate")
                        
                        binding.tvCustomerName.text = name
                        binding.tvCustomerPhone.text = order.contactPhone.ifEmpty { phone }
                        binding.tvDeliveryAddress.text = order.deliveryAddress.ifEmpty { address }

                        // Premium Instant Image Loading with Signature
                        binding.ivCustomerProfile.loadProfileImage(order.studentId, profileUrl, lastUpdate)
                    } else {
                        binding.tvCustomerName.text = "Unknown Customer"
                        binding.ivCustomerProfile.setImageResource(R.drawable.ic_profile)
                    }
                }
                .addOnFailureListener {
                    binding.tvCustomerName.text = "Error loading info"
                }

            // Action Buttons
            val status = order.status.lowercase()
            if (status == "pending" || status == "pending_cod") {
                binding.actionLayout.visibility = View.VISIBLE
                binding.btnAccept.setOnClickListener { onActionClick(order, "accepted") }
                binding.btnDecline.setOnClickListener { onActionClick(order, "declined") }
            } else {
                binding.actionLayout.visibility = View.GONE
            }
        }

        private fun updateStatusUI(status: String) {
            val context = binding.root.context
            
            val (statusText, colorRes) = when (status.lowercase()) {
                "pending" -> Pair("PENDING", R.color.orange)
                "pending_cod" -> Pair("PENDING POD", R.color.orange)
                "accepted", "active", "delivering" -> Pair("ACTIVE", R.color.primary_dark)
                "delivered", "completed" -> Pair("COMPLETED", R.color.green)
                "declined", "cancelled", "rejected" -> Pair("REJECTED", R.color.red_500)
                else -> Pair(status.uppercase(), R.color.gray_600)
            }
            
            binding.tvStatus.text = statusText
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
