package com.example.homehub.caretaker

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.homehub.R
import com.example.homehub.billing.Booking
import com.example.homehub.admin.ReportGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class ReportsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReportsActivity"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var totalEarningsText: TextView
    private lateinit var generateEarningsBtn: View
    private lateinit var generateOccupancyBtn: View

    private var bookingsList = mutableListOf<Booking>()
    private var totalEarnings = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        initializeViews()
        loadData()
    }

    private fun initializeViews() {
        totalEarningsText = findViewById(R.id.totalEarningsText)
        generateEarningsBtn = findViewById(R.id.generateEarningsBtn)
        generateOccupancyBtn = findViewById(R.id.generateOccupancyBtn)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        generateEarningsBtn.setOnClickListener {
            handleGenerateEarningsReport()
        }

        generateOccupancyBtn.setOnClickListener {
            showToast("Occupancy report feature coming soon!")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadData() {
        val currentUser = auth.currentUser ?: return
        // Load all confirmed/active bookings for this caretaker
        db.collection("bookings")
            .whereEqualTo("caretakerId", currentUser.uid)
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { snapshot ->
                bookingsList.clear()
                totalEarnings = 0.0
                
                snapshot.documents.forEach { doc ->
                    val booking = Booking.fromDocument(doc.data ?: emptyMap())
                    bookingsList.add(booking)
                    totalEarnings += booking.amount
                }
                totalEarningsText.text = "Ksh ${String.format("%,.0f", totalEarnings)}"
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading bookings for report: ${e.message}")
            }
    }

    private fun handleGenerateEarningsReport() {
        val currentUser = auth.currentUser ?: return
        
        if (bookingsList.isEmpty()) {
            showToast("No bookings found to generate report.")
            return
        }

        val caretakerName = currentUser.displayName ?: "Caretaker"
        val reportFile = ReportGenerator.generateCaretakerEarningsReport(
            this, caretakerName, bookingsList, totalEarnings
        )

        if (reportFile != null) {
            showToast("Report generated successfully!")
            openPdf(reportFile)
        } else {
            showToast("Failed to generate report.")
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF: ${e.message}")
            showToast("No PDF viewer found. Report saved in Documents.")
        }
    }
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
