package com.example.homehub.billing

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageButton
import android.widget.LinearLayout

import com.example.homehub.R
import com.example.homehub.utils.BitmapResizer

class MyBookingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MyBookings"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var bookingsListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var bookingsAdapter: BookingsAdapter

    private val bookingsList = mutableListOf<Booking>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_bookings)

        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.navigationBarColor = resources.getColor(R.color.white)

        initializeViews()
        setupRecyclerView()
        loadBookings()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.bookingsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        bookingsAdapter = BookingsAdapter(bookingsList, { booking ->
            // Handle Download Receipt click
            com.example.homehub.utils.ReceiptGenerator.generateBookingReceipt(this, booking)
        }, { booking ->
            // Navigate to booking details
            val intent = Intent(this, BookingDetailsActivity::class.java)
            intent.putExtra("BOOKING", booking)
            startActivity(intent)
        })

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MyBookingsActivity)
            adapter = bookingsAdapter
        }
    }

    private fun loadBookings() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState()
            return
        }

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE

        // Real-time listener for bookings
        bookingsListener = db.collection("bookings")
            .whereEqualTo("studentId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val errorMsg = error.message ?: ""
                    if (errorMsg.contains("index")) {
                        Log.e(TAG, "Missing Index: Please click the link in Logcat to create it.")
                    }
                    Log.e(TAG, "Error loading bookings: $errorMsg")
                    progressBar.visibility = View.GONE
                    showEmptyState()
                    return@addSnapshotListener
                }

                lifecycleScope.launch(Dispatchers.Default) {
                    val parsedBookings = snapshot?.documents?.mapNotNull { document ->
                        try {
                            val data = document.data?.toMutableMap() ?: mutableMapOf()
                            data["id"] = document.id
                            Booking.fromDocument(data).let { booking ->
                                // Lite model for list: resize property image and strip student image
                                booking.copy(
                                    studentImage = "",
                                    propertyImage = BitmapResizer.resizeBase64(booking.propertyImage, 400, 300)
                                ) 
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing booking: ${e.message}")
                            null
                        }
                    }?.filter { it.paymentStatus == "completed" }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()

                    withContext(Dispatchers.Main) {
                        bookingsList.clear()
                        bookingsList.addAll(parsedBookings)
                        progressBar.visibility = View.GONE

                        if (bookingsList.isEmpty()) {
                            showEmptyState()
                        } else {
                            emptyState.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            bookingsAdapter.updateBookings(bookingsList)
                        }
                        Log.d(TAG, "Loaded ${bookingsList.size} bookings")
                    }
                }
            }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        bookingsListener?.remove()
    }
}
