package com.example.homehub.supplier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivityWaterSuppliersBinding
import com.google.firebase.firestore.FirebaseFirestore

class WaterSuppliersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterSuppliersBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: WaterSupplierAdapter
    private var supplierList = mutableListOf<WaterSupplier>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaterSuppliersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupUI()
        loadSuppliers()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }
        binding.suppliersRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WaterSupplierAdapter(supplierList, 
            onItemClick = { supplier ->
                // Maybe open a detail view or place order dialog
                Toast.makeText(this, "Opening ${supplier.businessName}", Toast.LENGTH_SHORT).show()
            },
            onCallClick = { supplier ->
                makeCall(supplier.phone)
            },
            onChatClick = { supplier ->
                openWhatsApp(supplier.phone)
            },
            onOrderWaterClick = { supplier ->
                val bottomSheet = WaterBookingBottomSheet.newInstance(supplier)
                bottomSheet.show(supportFragmentManager, "water_booking")
            }
        )
        binding.suppliersRecyclerView.adapter = adapter
    }

    private fun loadSuppliers() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("users")
            .whereIn("role", listOf("supplier", "water_supplier", "WATER_SUPPLIER"))
            .get()
            .addOnSuccessListener { snapshots ->
                binding.progressBar.visibility = View.GONE
                supplierList.clear()
                for (doc in snapshots) {
                    val supplier = WaterSupplier.fromDocument(doc.id, doc.data)
                    supplierList.add(supplier)
                }
                
                adapter.updateSuppliers(supplierList)
                binding.emptyState.visibility = if (supplierList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("WaterSuppliers", "Error loading suppliers", e)
                Toast.makeText(this, "Failed to load suppliers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun makeCall(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phone")
        startActivity(intent)
    }

    private fun openWhatsApp(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val url = "https://api.whatsapp.com/send?phone=$phone"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
