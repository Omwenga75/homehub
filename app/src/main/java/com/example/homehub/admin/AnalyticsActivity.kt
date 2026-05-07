package com.example.homehub.admin

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.slider.RangeSlider
import android.animation.ValueAnimator
import android.view.ViewGroup
import com.example.homehub.utils.DonutChartView
import java.text.NumberFormat
import java.util.*
import com.example.homehub.R

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var occupancyRate: TextView
    private lateinit var averageRating: TextView
    private lateinit var revenuePerNight: TextView
    private lateinit var revenueSummary: TextView
    private lateinit var bookingSummary: TextView
    private lateinit var occupancyChange: TextView
    private lateinit var ratingChange: TextView
    private lateinit var revenueChange: TextView
    private lateinit var priceSlider: RangeSlider
    private lateinit var tvMinPrice: TextView
    private lateinit var tvMaxPrice: TextView
    private lateinit var propertiesRecyclerView: RecyclerView
    private lateinit var donutChart: DonutChartView

    private var currentTimePeriod = TimePeriod.WEEK

    enum class TimePeriod {
        WEEK, MONTH, YEAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        initializeViews()
        setupClickListeners()
        loadKeyMetrics()
        loadBookingStats()
        loadPropertyPerformance()
    }

    private fun initializeViews() {
        // Initialize metrics (Match XML IDs)
        occupancyRate = findViewById(R.id.occupancyRate)
        occupancyChange = findViewById(R.id.id_occupancy_trend)
        averageRating = findViewById(R.id.averageRating)
        ratingChange = findViewById(R.id.id_rating_trend)
        revenuePerNight = findViewById(R.id.revenuePerNight)
        revenueChange = findViewById(R.id.id_revenue_trend)
        revenueSummary = findViewById(R.id.revenueSummary)
        bookingSummary = findViewById(R.id.bookingSummary)
        
        donutChart = findViewById(R.id.donutChart)

        // Initialize recycler view
        propertiesRecyclerView = findViewById(R.id.propertiesRecyclerView)
        propertiesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        val btnWeek = findViewById<Button>(R.id.btnWeek)
        val btnMonth = findViewById<Button>(R.id.btnMonth)
        val btnYear = findViewById<Button>(R.id.btnYear)

        // Force set text to resolve any rendering issues
        btnWeek.text = "Week"
        btnMonth.text = "Month"
        btnYear.text = "Year"

        btnWeek.setOnClickListener { setTimePeriod(TimePeriod.WEEK) }
        btnMonth.setOnClickListener { setTimePeriod(TimePeriod.MONTH) }
        btnYear.setOnClickListener { setTimePeriod(TimePeriod.YEAR) }

        findViewById<ImageButton>(R.id.filterButton).setOnClickListener {
            showFilterDialog()
        }

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun setupPriceSlider() {
        priceSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val min = values[0].toDouble()
            val max = values[1].toDouble()
            
            tvMinPrice.text = formatCurrency(min)
            tvMaxPrice.text = if (max >= 200000) "KSh 200,000+" else formatCurrency(max)
            
            // In a real app, we would trigger a filter here
            filterPropertiesByPrice(min, max)
        }
    }

    private fun filterPropertiesByPrice(min: Double, max: Double) {
        // Placeholder for real filtering logic
    }

    private fun setTimePeriod(period: TimePeriod) {
        currentTimePeriod = period

        // Update button states
        val btnWeek = findViewById<Button>(R.id.btnWeek)
        val btnMonth = findViewById<Button>(R.id.btnMonth)
        val btnYear = findViewById<Button>(R.id.btnYear)

        val buttons = listOf(btnWeek, btnMonth, btnYear)
        val periods = listOf(TimePeriod.WEEK, TimePeriod.MONTH, TimePeriod.YEAR)

        for (i in buttons.indices) {
            val isSelected = periods[i] == period
            (buttons[i] as? com.google.android.material.button.MaterialButton)?.apply {
                if (isSelected) {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this@AnalyticsActivity, R.color.primary))
                    setTextColor(Color.WHITE)
                    elevation = 8f
                } else {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(Color.parseColor("#64748B")) 
                    elevation = 0f
                }
            }
        }

        // Reload data for selected period
        loadAnalyticsData()
    }

    private fun loadAnalyticsData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            loadKeyMetrics()
            loadBookingStats()
            loadPropertyPerformance()
        } else {
            showDemoData()
        }
    }

    private fun loadKeyMetrics() {
        // Calculate Occupancy strictly from live properties
        db.collection("properties").get().addOnSuccessListener { snapshot ->
            val total = snapshot.size()
            var rented = 0
            var sumRating = 0.0
            var countRating = 0
            
            for (doc in snapshot.documents) {
                val status = doc.getString("status")?.lowercase() ?: ""
                if (status == "rented" || status == "booked") rented++
                
                val rating = doc.getDouble("rating") ?: 0.0
                if (rating > 0) {
                    sumRating += rating
                    countRating++
                }
            }
            
            val rate = if (total > 0) (rented.toFloat() / total * 100).toInt() else 0
            occupancyRate.text = "$rate%"
            averageRating.text = if (countRating > 0) String.format("%.1f", sumRating / countRating) else "0.0"
        }

        val calendar = Calendar.getInstance()
        when (currentTimePeriod) {
            TimePeriod.WEEK -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            TimePeriod.MONTH -> calendar.add(Calendar.MONTH, -1)
            TimePeriod.YEAR -> calendar.add(Calendar.YEAR, -1)
        }
        val startTime = calendar.time

        // Real-time Revenue & Occupancy Query
        db.collection("bookings")
            .whereGreaterThanOrEqualTo("createdAt", startTime)
            .get()
            .addOnSuccessListener { snapshot ->
                var totalRevenue = 0.0
                val dailyRevenue = mutableMapOf<Int, Double>()
                var confirmedCount = 0
                
                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    val paymentStatus = doc.getString("paymentStatus")?.lowercase() ?: ""
                    val createdAt = doc.getDate("createdAt") ?: startTime
                    
                    if (status in listOf("confirmed", "active", "completed") || paymentStatus == "completed") {
                        val amt = doc.get("amount")
                        val amount = when (amt) {
                            is Number -> amt.toDouble()
                            is String -> amt.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        
                        totalRevenue += amount
                        confirmedCount++
                        
                        val cal = Calendar.getInstance().apply { time = createdAt }
                        val day = cal.get(Calendar.DAY_OF_WEEK)
                        dailyRevenue[day] = (dailyRevenue[day] ?: 0.0) + amount
                    }
                }

                // Update UI based on period
                val periodLabel = when(currentTimePeriod) {
                    TimePeriod.WEEK -> "Weekly"
                    TimePeriod.MONTH -> "Monthly"
                    TimePeriod.YEAR -> "Annual"
                }
                revenueSummary.text = "$periodLabel Revenue: ${formatCurrency(totalRevenue)}"
                revenuePerNight.text = formatCurrency(if (confirmedCount > 0) totalRevenue / confirmedCount else 0.0)

                // Populate Weekly Chart (if Weekly selected)
                if (currentTimePeriod == TimePeriod.WEEK) {
                    val heights = mutableListOf<Int>()
                    val labels = mutableListOf<String>()
                    val days = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
                    
                    for (day in days) {
                        val rev = dailyRevenue[day] ?: 0.0
                        heights.add(((rev / (totalRevenue.coerceAtLeast(1.0) / 10.0)) * 10).toInt().coerceIn(10, 80))
                        labels.add(if (rev >= 1000) "${(rev/1000).toInt()}K" else rev.toInt().toString())
                    }
                    updateChartData(heights, labels)
                }
            }
    }

    private fun updateChartData(heights: List<Int>, labels: List<String>) {
        val barIds = listOf(R.id.colMon, R.id.colTue, R.id.colWed, R.id.colThu, R.id.colFri, R.id.colSat, R.id.colSun)
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        for (i in barIds.indices) {
            val barView = findViewById<View>(barIds[i])
            barView.findViewById<TextView>(R.id.barValue).text = labels[i]
            barView.findViewById<TextView>(R.id.barLabel).text = dayLabels[i]
            
            val barCard = barView.findViewById<View>(R.id.barGraphic)
            val finalHeight = (heights[i] * resources.displayMetrics.density * 2).toInt() // Increased scale
            
            // Animate height
            val initialHeight = barCard.layoutParams.height
            val animator = ValueAnimator.ofInt(initialHeight, finalHeight)
            animator.duration = 800
            animator.addUpdateListener { animation ->
                val params = barCard.layoutParams
                params.height = animation.animatedValue as Int
                barCard.layoutParams = params
            }
            animator.start()
        }
    }

    private fun loadBookingStats() {
        db.collection("bookings").get().addOnSuccessListener { snapshot ->
            var confirmedValue = 0f
            var deferredValue = 0f
            var pendingValue = 0f
            var cancelledValue = 0f
            
            for (doc in snapshot.documents) {
                val status = doc.getString("status")?.lowercase() ?: ""
                when (status) {
                    "confirmed", "completed" -> confirmedValue++
                    "pending_deferred" -> deferredValue++
                    "pending" -> pendingValue++
                    "cancelled" -> cancelledValue++
                }
            }
            
            val total = (confirmedValue + deferredValue + pendingValue + cancelledValue).toInt()

            // Update Donut Chart
            donutChart.setData(listOf(
                DonutChartView.Segment(confirmedValue, Color.parseColor("#10B981"), "Confirmed"),
                DonutChartView.Segment(deferredValue, Color.parseColor("#6366F1"), "Pay Later"),
                DonutChartView.Segment(pendingValue, Color.parseColor("#F59E0B"), "Pending"),
                DonutChartView.Segment(cancelledValue, Color.parseColor("#EF4444"), "Cancelled")
            ))

            // Update row components
            updateBookingRow(R.id.rowConfirmed, "Confirmed", confirmedValue.toInt().toString(), R.drawable.ic_check_circle, Color.parseColor("#10B981"))
            updateBookingRow(R.id.rowDeferred, "Reserved (Pay Later)", deferredValue.toInt().toString(), R.drawable.ic_clock, Color.parseColor("#6366F1"))
            updateBookingRow(R.id.rowPending, "Pending", pendingValue.toInt().toString(), R.drawable.ic_check_circle, Color.parseColor("#F59E0B"))
            updateBookingRow(R.id.rowCancelled, "Cancelled", cancelledValue.toInt().toString(), R.drawable.ic_close, Color.parseColor("#EF4444"))
            
            bookingSummary.text = "Total Transactions: $total"
        }
    }

    private fun updateBookingRow(id: Int, title: String, count: String, iconRes: Int, color: Int) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.bookingTitle).text = title
        row.findViewById<TextView>(R.id.bookingCount).text = count
        
        val iconView = row.findViewById<android.widget.ImageView>(R.id.bookingIcon)
        iconView.setImageResource(iconRes)
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(color)
        
        val card = row.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iconCard)
        // Set a subtle semi-transparent background (alpha ~10%)
        val alphaColor = (color and 0x00FFFFFF) or 0x1A000000 
        card.setCardBackgroundColor(alphaColor)
    }

    private fun loadPropertyPerformance() {
        db.collection("properties").get().addOnSuccessListener { snapshot ->
            val performanceList = mutableListOf<PropertyPerformance>()
            
            for (doc in snapshot.documents) {
                val propertyName = doc.getString("propertyName") 
                    ?: doc.getString("title") 
                    ?: "Unnamed Property"
                
                val rating = doc.getDouble("rating")?.toFloat() ?: 0f
                val revenue = doc.getDouble("totalRevenue") ?: 0.0
                
                // Calculate Occupancy
                val status = doc.getString("status")?.lowercase() ?: ""
                val isPlot = doc.getBoolean("isPlot") ?: false
                val occupancy = if (isPlot) {
                    val total = doc.getLong("totalRooms")?.toInt() ?: 1
                    val available = doc.getLong("availableRooms")?.toInt() ?: 0
                    if (total > 0) ((total - available) * 100) / total else 0
                } else {
                    if (status == "rented" || status == "booked") 100 else 0
                }
                
                performanceList.add(PropertyPerformance(
                    name = propertyName,
                    rating = rating,
                    occupancy = occupancy,
                    revenue = revenue
                ))
            }
            
            // Sort by revenue descending
            val sortedList = performanceList.sortedByDescending { it.revenue }
            
            val adapter = PropertyPerformanceAdapter(sortedList)
            propertiesRecyclerView.adapter = adapter
            
            if (sortedList.isEmpty()) {
                findViewById<TextView>(R.id.analyticsSubtitle).text = "No property performance data available"
            }
        }.addOnFailureListener {
            android.widget.Toast.makeText(this, "Failed to load property performance", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDemoData() {
        loadKeyMetrics()
        loadBookingStats()
        loadPropertyPerformance()
    }

    private fun showFilterDialog() {
        android.widget.Toast.makeText(this, "Filter options coming soon", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
            format.maximumFractionDigits = 0
            format.format(amount)
        } catch (e: Exception) {
            "KSh ${amount.toInt()}"
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
// Data class for property performance
data class PropertyPerformance(
    val name: String,
    val rating: Float,
    val occupancy: Int,
    val revenue: Double
)
