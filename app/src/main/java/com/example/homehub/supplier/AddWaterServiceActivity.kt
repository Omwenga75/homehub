package com.example.homehub.supplier

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityAddWaterServiceBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddWaterServiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddWaterServiceBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWaterServiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
        loadCurrentService()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            saveServiceDetails()
        }
    }

    private fun loadCurrentService() {
        val user = auth.currentUser ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                if (doc.exists()) {
                    binding.etBusinessName.setText(doc.getString("businessName") ?: doc.getString("username") ?: "")
                    binding.etPhone.setText(doc.getString("phone") ?: "")
                    binding.etLocation.setText(doc.getString("serviceArea") ?: "")
                    binding.etDescription.setText(doc.getString("bio") ?: "")
                    
                    val drinkingPrice = doc.get("drinkingPrice") ?: doc.get("pricePerUnit")
                    
                    binding.etDrinkingPrice.setText(drinkingPrice?.toString() ?: "0")
                    binding.etStock.setText(doc.get("stockLiters")?.toString() ?: "1000")
                    binding.etUnit.setText(doc.getString("unitSize") ?: "20L")
                    binding.etDeliveryFee.setText(doc.get("deliveryFee")?.toString() ?: "0")
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load service details", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveServiceDetails() {
        val user = auth.currentUser ?: return

        val businessName = binding.etBusinessName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val drinkingPrice = binding.etDrinkingPrice.text.toString().trim()
        val stock = binding.etStock.text.toString().trim()
        val unit = binding.etUnit.text.toString().trim()
        val deliveryFee = binding.etDeliveryFee.text.toString().trim()

        if (businessName.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Business name and phone are required", Toast.LENGTH_SHORT).show()
            return
        }

        val dPriceValue = drinkingPrice.toDoubleOrNull() ?: 0.0
        val stockValue = stock.toIntOrNull() ?: 1000
        val deliveryFeeValue = deliveryFee.toDoubleOrNull() ?: 0.0

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val updates = hashMapOf<String, Any>(
            "businessName" to businessName,
            "phone" to phone,
            "serviceArea" to location,
            "bio" to description,
            "drinkingPrice" to dPriceValue,
            "stockLiters" to stockValue,
            "unitSize" to unit,
            "deliveryFee" to deliveryFeeValue
        )

        db.collection("users").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Service details updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Failed to update details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
