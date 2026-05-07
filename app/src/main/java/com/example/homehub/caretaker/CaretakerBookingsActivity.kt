package com.example.homehub.caretaker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.R
import com.example.homehub.billing.Booking
import com.example.homehub.billing.BookingDetailsActivity
import com.example.homehub.billing.BookingsAdapter
import com.example.homehub.utils.BitmapResizer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaretakerBookingsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var bookingsListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var bookingsAdapter: BookingsAdapter
    private lateinit var titleText: TextView

    private val bookingsList = mutableListOf<Booking>()
    private var propertyId: String? = null
    private var propertyName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caretaker_bookings)

        propertyId = intent.getStringExtra("PROPERTY_ID")
        propertyName = intent.getStringExtra("PROPERTY_NAME")

        initializeViews()
        setupRecyclerView()
        loadBookings()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.bookingsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        titleText = findViewById(R.id.titleText)

        if (propertyName != null) {
            titleText.text = "History: $propertyName"
        } else {
            titleText.text = "Rent History"
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
            layoutManager = LinearLayoutManager(this@CaretakerBookingsActivity)
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

        var query = db.collection("bookings")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // Filter by property if provided, otherwise by caretaker
        query = if (propertyId != null) {
            query.whereEqualTo("propertyId", propertyId)
        } else {
            query.whereEqualTo("caretakerId", currentUser.uid)
        }

        bookingsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("CaretakerBookings", "Error loading bookings: ${error.message}")
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
                            // Lite model for list
                            booking.copy(
                                propertyImage = if (booking.propertyImage.isNotEmpty()) 
                                    BitmapResizer.resizeBase64(booking.propertyImage, 400, 300) else ""
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

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
                }
            }
        }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        
        if (propertyId != null) {
            findViewById<TextView>(R.id.emptyStateMessage).text = "No bookings found for this property."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bookingsListener?.remove()
    }
}
