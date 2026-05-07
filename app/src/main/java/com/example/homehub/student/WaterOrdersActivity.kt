package com.example.homehub.student

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.R
import com.example.homehub.supplier.SupplierOrder
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class WaterOrdersActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var container: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_water_orders)

        window.statusBarColor = resources.getColor(R.color.primary_dark)
        window.navigationBarColor = resources.getColor(R.color.background)

        initializeViews()
        loadWaterOrders()
    }

    private fun initializeViews() {
        container = findViewById(R.id.waterOrdersContainer)
        emptyText = findViewById(R.id.tvNoWaterOrders)
        progressBar = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadWaterOrders() {
        val userId = auth.currentUser?.uid ?: return
        
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("waterOrders")
            .whereEqualTo("studentId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                
                container.removeAllViews()
                if (snapshot.isEmpty) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    emptyText.visibility = View.GONE
                    snapshot.documents.forEach { doc ->
                        val order = SupplierOrder.fromDocument(doc.id, doc.data ?: emptyMap())
                        addOrderView(order)
                    }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load orders: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addOrderView(order: SupplierOrder) {
        val itemView = layoutInflater.inflate(R.layout.item_water_order_confirm, container, false)
        
        val tvSupplierName = itemView.findViewById<TextView>(R.id.tvSupplierName)
        val tvOrderDetails = itemView.findViewById<TextView>(R.id.tvOrderDetails)
        val tvOrderStatus = itemView.findViewById<TextView>(R.id.tvOrderStatus)
        val btnConfirm = itemView.findViewById<MaterialButton>(R.id.btnConfirmDelivery)
        val tvConfirmed = itemView.findViewById<TextView>(R.id.tvConfirmedLabel)

        tvSupplierName.text = "Order #${order.orderId.take(4).uppercase()}"
        tvOrderDetails.text = "Amount: KSh ${String.format("%,.0f", order.amount)} • ${order.waterType}"
        tvOrderStatus.text = order.status.uppercase()

        if (order.isCheckedIn) {
            btnConfirm.visibility = View.GONE
            tvConfirmed.visibility = View.VISIBLE
        } else if (order.status.lowercase() in listOf("delivered", "completed")) {
            btnConfirm.visibility = View.VISIBLE
            tvConfirmed.visibility = View.GONE
            btnConfirm.setOnClickListener {
                confirmDelivery(order.orderId)
            }
        } else {
            btnConfirm.visibility = View.GONE
            tvConfirmed.visibility = View.GONE
        }

        container.addView(itemView)
    }

    private fun confirmDelivery(orderId: String) {
        db.collection("waterOrders").document(orderId)
            .update("isCheckedIn", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Delivery confirmed! Funds released to supplier.", Toast.LENGTH_SHORT).show()
                loadWaterOrders()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to confirm delivery.", Toast.LENGTH_SHORT).show()
            }
    }
}
