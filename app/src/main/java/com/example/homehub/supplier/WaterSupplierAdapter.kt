package com.example.homehub.supplier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.databinding.ItemWaterSupplierBinding
import android.widget.PopupMenu

class WaterSupplierAdapter(
    private var suppliers: List<WaterSupplier>,
    private val isAdmin: Boolean = false,
    private val onItemClick: (WaterSupplier) -> Unit,
    private val onCallClick: (WaterSupplier) -> Unit = {},
    private val onChatClick: (WaterSupplier) -> Unit = {},
    private val onSuspendClick: (WaterSupplier) -> Unit = {},
    private val onDeleteClick: (WaterSupplier) -> Unit = {},
    private val onResetPasswordClick: (WaterSupplier) -> Unit = {},
    private val onOrderWaterClick: (WaterSupplier) -> Unit = {}
) : RecyclerView.Adapter<WaterSupplierAdapter.SupplierViewHolder>() {

    fun updateSuppliers(newSuppliers: List<WaterSupplier>) {
        this.suppliers = newSuppliers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SupplierViewHolder {
        val binding = ItemWaterSupplierBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SupplierViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SupplierViewHolder, position: Int) {
        holder.bind(suppliers[position])
    }

    override fun getItemCount(): Int = suppliers.size

    inner class SupplierViewHolder(private val binding: ItemWaterSupplierBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(supplier: WaterSupplier) {
            // Name & Phone & Location
            binding.tvRoleLabel.text = if (supplier.businessName.isNotBlank()) supplier.businessName else supplier.name
            binding.tvSupplierPhone.text = supplier.phone.ifEmpty { "Phone not set" }
            binding.locationText.text = supplier.serviceArea.ifEmpty { "Location not set" }

            // Price per litre & Delivered count
            val price = supplier.pricePerLitre.takeIf { it > 0 } ?: supplier.drinkingPrice
            binding.pricePerLitreText.text = if (price > 0) "KSh ${String.format("%,.0f", price)}" else "Price not set"
            binding.deliveredCountText.text = "${supplier.deliveredCount}"

            // Verified Badge
            binding.verifiedBadge.visibility = if (supplier.verificationStatus.equals("APPROVED", true)) View.VISIBLE else View.GONE

            // Profile Image
            binding.supplierIcon.setImageResource(R.drawable.ks4)

            // Book button — visible for students, hidden for admin
            binding.btnOrderWater.visibility = if (isAdmin) View.GONE else View.VISIBLE
            binding.btnOrderWater.setOnClickListener {
                onOrderWaterClick(supplier)
            }

            // More button for admin actions
            binding.moreButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
            binding.moreButton.setOnClickListener { view ->
                showManagementMenu(view, supplier)
            }
        }

        private fun showManagementMenu(view: View, supplier: WaterSupplier) {
            val popup = PopupMenu(binding.root.context, view)

            popup.menu.add(0, 2, 1, "Call Supplier")
            popup.menu.add(0, 3, 2, "WhatsApp Message")

            if (isAdmin) {
                popup.menu.add(0, 4, 3, if (supplier.status.equals("active", true)) "Suspend Account" else "Activate Account")
                popup.menu.add(0, 5, 4, "Delete Supplier")
                popup.menu.add(0, 6, 5, "Reset Password")
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onItemClick(supplier); true }
                    2 -> { onCallClick(supplier); true }
                    3 -> { onChatClick(supplier); true }
                    4 -> { onSuspendClick(supplier); true }
                    5 -> { onDeleteClick(supplier); true }
                    6 -> { onResetPasswordClick(supplier); true }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
