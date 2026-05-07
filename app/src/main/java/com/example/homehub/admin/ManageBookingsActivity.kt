package com.example.homehub.admin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.homehub.R
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.billing.Booking
import com.example.homehub.databinding.ActivityManageBookingsBinding
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ManageBookingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBookingsBinding
    private lateinit var adapter: AdminBookingAdapter
    private val db = FirebaseFirestore.getInstance()
    private var allBookings = mutableListOf<Booking>()
    
    private var currentSearchQuery = ""
    private var currentStatusFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setupToolbar()
        setupRecyclerView()
        setupFilters()
        setupSearch()
        loadBookings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminBookingAdapter(emptyList()) { booking, isDelete ->
            if (isDelete) {
                confirmDeleteBooking(booking)
            } else {
                // View booking details - show receipt or launch details
                com.example.homehub.utils.ReceiptGenerator.generateBookingReceipt(this, booking)
            }
        }
        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = adapter
    }

    private fun confirmDeleteBooking(booking: Booking) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Booking?")
            .setMessage("Are you sure you want to delete this booking for ${booking.studentName} at ${booking.propertyName}?\n\nThis action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                performDelete(booking)
            }
            .show()
    }

    private fun performDelete(booking: Booking) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("bookings").document(booking.id)
            .delete()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Booking deleted successfully", Toast.LENGTH_SHORT).show()
                // The snapshot listener will handle updating the list automatically
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupFilters() {
        binding.tabLayoutStatus.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentStatusFilter = tab?.text?.toString() ?: "All"
                applyFilters()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilters() {
        adapter.filter(currentSearchQuery, currentStatusFilter)
        updateEmptyState()
    }

    private fun loadBookings() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("bookings")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE
                if (error != null) {
                    android.util.Log.e("ManageBookings", "Error loading bookings: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Toast.makeText(this, "Session expired or access denied.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, UserLoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allBookings = snapshot.documents.mapNotNull { doc ->
                        Booking.fromDocument(doc.data ?: emptyMap()).apply {
                            id = doc.id
                        }
                    }.toMutableList()
                    
                    adapter.updateList(allBookings)
                    applyFilters()
                }
            }
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvBookings.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvBookings.visibility = View.VISIBLE
        }
    }
}
