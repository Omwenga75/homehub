package com.example.homehub.billing

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

import com.example.homehub.R

class RentTrackingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RentTracking"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var tenantsListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: TenantRiskAdapter

    private val activeBookings = mutableListOf<Booking>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rent_tracking)

        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        initializeViews()
        setupRecyclerView()
        loadTenants()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.tenantsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = TenantRiskAdapter(activeBookings) { booking ->
            // Open booking details (could be shared or host-specific)
            val intent = Intent(this, BookingDetailsActivity::class.java)
            intent.putExtra("BOOKING", booking)
            startActivity(intent)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RentTrackingActivity)
            adapter = this@RentTrackingActivity.adapter
        }
    }

    private fun loadTenants() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState()
            return
        }

        progressBar.visibility = View.VISIBLE
        activeBookings.clear()

        // Load all confirmed/active bookings for this caretaker
        tenantsListener = db.collection("bookings")
            .whereEqualTo("caretakerId", currentUser.uid)
            .whereIn("status", listOf("confirmed", "active"))
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e(TAG, "Error loading tenants: ${error.message}")
                    showEmptyState()
                    return@addSnapshotListener
                }

                activeBookings.clear()
                snapshot?.documents?.forEach { document ->
                    try {
                        val data = document.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = document.id
                        val booking = Booking.fromDocument(data)
                        activeBookings.add(booking)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing booking: ${e.message}")
                    }
                }

                if (activeBookings.isEmpty()) {
                    showEmptyState()
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateBookings(activeBookings)
                }
            }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        tenantsListener?.remove()
    }
}
