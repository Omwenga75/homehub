package com.example.homehub.supplier

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityServicesBinding
import com.example.homehub.utils.GlobalDataCache
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ServicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServicesBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
        loadServices()
    }

    override fun onResume() {
        super.onResume()
        loadServices()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }

        binding.fabUpdateService.setOnClickListener {
            startActivity(Intent(this, AddWaterServiceActivity::class.java))
        }

        binding.moreButton.setOnClickListener {
            showServiceOptions()
        }
    }

    private fun loadServices() {
        val user = auth.currentUser ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        // Try cache first
        val cachedUser = GlobalDataCache.getUsers().find { it["uid"] == user.uid }
        if (cachedUser != null) {
            updateUIWithUserData(cachedUser)
            binding.progressBar.visibility = View.GONE
        }

        // Always fetch latest from DB
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                if (doc.exists()) {
                    val data = doc.data ?: return@addOnSuccessListener
                    updateUIWithUserData(data)
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                if (cachedUser == null) {
                    Toast.makeText(this, "Failed to load services", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showServiceOptions() {
        val popup = PopupMenu(this, binding.moreButton)
        popup.menu.add(0, 1, 0, "Edit Service")
        popup.menu.add(0, 2, 1, "Delete Service")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    startActivity(Intent(this, AddWaterServiceActivity::class.java))
                    true
                }
                2 -> {
                    confirmDeleteService()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDeleteService() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Water Service")
            .setMessage("Are you sure you want to remove your water service listing? This will hide the card and clear the service details.")
            .setPositiveButton("Delete") { _, _ -> deleteService() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteService() {
        val user = auth.currentUser ?: return
        binding.progressBar.visibility = View.VISIBLE
        val updates = mapOf<String, Any>(
            "businessName" to "",
            "bio" to "",
            "drinkingPrice" to 0.0,
            "unitSize" to "",
            "deliveryFee" to 0.0,
            "stockLiters" to 0,
            "serviceArea" to ""
        )

        db.collection("users").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.primaryServiceCard.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                Toast.makeText(this, "Water service removed.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Unable to delete service.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUIWithUserData(data: Map<String, Any>) {
        val businessName = data["businessName"] as? String ?: ""
        val drinkingPrice = data["drinkingPrice"] ?: data["pricePerUnit"]
        val deliveryFee = data["deliveryFee"] ?: 0.0
        val bio = data["bio"] as? String ?: ""
        val phone = data["phone"] as? String ?: ""
        val serviceArea = data["serviceArea"] as? String ?: ""

        if (businessName.isNotBlank() && drinkingPrice != null) {
            binding.primaryServiceCard.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            
            binding.serviceNameText.text = businessName
            binding.serviceContactText.text = if (phone.isNotBlank() || serviceArea.isNotBlank()) {
                listOf(phone.ifBlank { "Phone not set" }, serviceArea.ifBlank { "Service area not set" })
                    .joinToString(" | ")
            } else {
                "Phone not set | Service area not set"
            }
            binding.serviceDescriptionText.text = if (bio.isNotBlank()) bio else "High quality water delivery services."
            binding.servicePriceText.text = "KSh ${drinkingPrice.toString()} per litre"
            binding.serviceDeliveryText.text = "Delivery KSh ${deliveryFee.toString()}"
        } else {
            binding.primaryServiceCard.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        }
    }
}
