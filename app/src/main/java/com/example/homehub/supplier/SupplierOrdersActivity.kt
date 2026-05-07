package com.example.homehub.supplier

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.databinding.ActivitySupplierOrdersBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SupplierOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplierOrdersBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SupplierOrdersAdapter
    private var orderList = mutableListOf<SupplierOrder>()
    private var currentTab = 0 // 0: Active, 1: History

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupplierOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
        loadOrders()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        adapter = SupplierOrdersAdapter(orderList) { order, newStatus ->
            updateOrderStatus(order, newStatus)
        }
        binding.rvOrders.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadOrders()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                filterOrders()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadOrders() {
        val user = auth.currentUser ?: return
        binding.swipeRefreshLayout.isRefreshing = true

        db.collection("waterOrders")
            .whereEqualTo("supplierId", user.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                binding.swipeRefreshLayout.isRefreshing = false
                if (error != null) {
                    Log.e("SupplierOrders", "Error loading orders", error)
                    return@addSnapshotListener
                }

                orderList.clear()
                snapshots?.forEach { doc ->
                    val order = SupplierOrder.fromDocument(doc.id, doc.data)
                    orderList.add(order)
                }
                filterOrders()
            }
    }

    private fun filterOrders() {
        val filtered = when (currentTab) {
            0 -> orderList.filter { it.status.lowercase() == "pending" }
            1 -> orderList.filter { it.status.lowercase() in listOf("accepted", "active", "delivering") }
            else -> orderList.filter { it.status.lowercase() in listOf("completed", "delivered", "declined", "cancelled", "rejected") }
        }

        // Update stats in header
        val pendingCount = orderList.count { it.status.lowercase() == "pending" }
        val activeCount = orderList.count { it.status.lowercase() in listOf("accepted", "active", "delivering") }
        binding.tvOrderStats.text = "$pendingCount Pending • $activeCount Active Orders"

        adapter.updateOrders(filtered)
        binding.emptyStateLayout.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateOrderStatus(order: SupplierOrder, newStatus: String) {
        db.collection("waterOrders").document(order.orderId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Order marked as $newStatus", Toast.LENGTH_SHORT).show()
                // The snapshot listener will automatically update the UI
            }
            .addOnFailureListener { e ->
                Log.e("SupplierOrders", "Error updating status", e)
                Toast.makeText(this, "Failed to update order", Toast.LENGTH_SHORT).show()
            }
    }
}
