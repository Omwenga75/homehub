package com.example.homehub.supplier

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.databinding.ActivityWaterSupplierDashboardBinding
import com.example.homehub.utils.ProfilePictureUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import com.example.homehub.utils.GlobalDataCache
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import com.example.homehub.other.Extensions.loadCircularImage
import com.example.homehub.other.Extensions.loadProfileImage
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class WaterSupplierDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterSupplierDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private var isSupplierVerified = false
    private var verificationStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Standard Emerald Green status bar for consistency
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        binding = ActivityWaterSupplierDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)
        
        // Ensure notification gating is active for this role
        com.example.homehub.utils.NotificationManager.setActiveRole("SUPPLIER")

        setupUI()
        setupTabs()
        setupAnalyticsCharts()
        loadDashboardData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile image and name from session cache instantly
        loadUserProfileFromCache(auth.currentUser?.uid ?: "")
    }

    private fun setupUI() {
        binding.profileIconCard.setOnClickListener {
            startActivity(Intent(this, WaterSupplierProfileActivity::class.java))
        }

        binding.btnNotificationHeader.setOnClickListener {
            startActivity(Intent(this, WaterSupplierNotificationsActivity::class.java))
        }

        binding.ordersActionCard.setOnClickListener {
            startActivity(Intent(this, SupplierOrdersActivity::class.java))
        }

        binding.btnWithdraw.setOnClickListener {
            com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                Toast.makeText(this, "Withdrawal feature coming soon!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.inventoryActionCard.setOnClickListener {
            Toast.makeText(this, "Inventory management coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.deliveryActionCard.setOnClickListener {
            com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                startActivity(Intent(this, ServicesActivity::class.java))
            }
        }

        binding.reportsActionCard.setOnClickListener {
            com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                showReceiptOptionsDialog()
            }
        }

        binding.messagesActionCard.setOnClickListener {
            com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                startActivity(Intent(this, com.example.homehub.chat.ChatListActivity::class.java))
            }
        }

        binding.btnGenerateReportSupplier?.setOnClickListener {
            showReceiptOptionsDialog()
        }

        binding.heroActionCard?.setOnClickListener {
            loadDashboardData()
            Toast.makeText(this, "Refreshing water service statistics...", Toast.LENGTH_SHORT).show()
        }

        // Inventory Tab Placeholders
        binding.createServiceActionCard.setOnClickListener {
            startActivity(Intent(this, AddWaterServiceActivity::class.java))
        }

        binding.stockRequestActionCard.setOnClickListener {
            Toast.makeText(this, "Refill request for 200L Water sent!", Toast.LENGTH_SHORT).show()
        }

        binding.equipmentActionCard.setOnClickListener {
            Toast.makeText(this, "Delivery tracking coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabs() {
        binding.supplierTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let { 
                    switchTab(it.position) 
                    if (it.position == 1) { // Orders tab (formerly Inventory)
                        val userId = auth.currentUser?.uid ?: ""
                        if (userId.isNotEmpty()) {
                            com.example.homehub.utils.NotificationManager.clearUnread(userId, "WATER_ORDER")
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun switchTab(position: Int) {
        // Hide all tab contents
        binding.homeTabContent.visibility = View.GONE
        binding.inventoryTabContent.visibility = View.GONE
        binding.analyticsTabContent.visibility = View.GONE

        // Show selected tab content
        when (position) {
            0 -> binding.homeTabContent.visibility = View.VISIBLE
            1 -> binding.inventoryTabContent.visibility = View.VISIBLE
            2 -> binding.analyticsTabContent.visibility = View.VISIBLE
        }
    }

    private fun loadDashboardData() {
        val user = auth.currentUser ?: return

        // Load user profile data directly for immediate display
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("fullName") ?: doc.getString("username") ?: ""
                    val businessName = doc.getString("businessName") ?: ""
                    
                    // Prioritize Real Name over Business Name as requested
                    val displayName = if (name.isNotBlank()) name else if (businessName.isNotBlank()) businessName else "Water Supplier"
                    
                    binding.userNameText.text = displayName
                    
                    // Update session cache
                    sessionManager.saveCachedUserName(displayName)
                }
            }

        setupNotificationBadge(user.uid)
        setupMessageBadge(user.uid)

        // Show cached data instantly
        loadDashboardDataFromCache(user.uid)

        // Background refresh for latest Firestore data
        lifecycleScope.launch {
            refreshDashboardDataInBackground(user.uid)
        }
    }

    // ─── Cache Layer ───────────────────────────────────────────────────────────

    private fun loadDashboardDataFromCache(userId: String) {
        val cachedOrders = GlobalDataCache.getBookings()
            .filter { it["supplierId"] == userId || it["caretakerId"] == userId }
        val cachedReviews = GlobalDataCache.getReviews()
            .filter { it["supplierId"] == userId }

        var confirmedRevenue = 0.0
        var expectedRevenue = 0.0
        var pendingOrders = 0
        var totalLitresSold = 0
        var currentLitres = 0
        var deliveredCount = 0

        for (order in cachedOrders) {
            val amountStr = order["amount"]?.toString() ?: "0"
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
            val status = (order["status"] as? String ?: "").lowercase()
            
            val qty = order["quantity"]
            val liters = when (qty) {
                is Number -> qty.toInt()
                is String -> qty.filter { it.isDigit() }.toIntOrNull() ?: 0
                else -> 0
            }
            currentLitres += liters

            if (status == "completed" || status == "delivered" || status == "paid") {
                confirmedRevenue += amount
                totalLitresSold += liters
                deliveredCount++
            } else if (status == "pending" || status == "new" || status == "pending_cod") {
                pendingOrders++
                expectedRevenue += amount
            }
        }

        binding.totalOrdersText.text = cachedOrders.size.toString()
        binding.expectedRevenueHeaderText.text = String.format("KSh %,.0f", expectedRevenue)
        binding.ordersBadgeText.text = "$pendingOrders pending"
        
        val cachedUser = GlobalDataCache.getUsers()
            .find { it["uid"] == userId || it["id"] == userId }
        
        binding.inventoryBadgeText?.text = "${(cachedUser?.get("stockLiters") as? Number)?.toInt() ?: 1000}L"
        binding.stockRequestBadgeText?.text = "${totalLitresSold}L"
        binding.equipmentBadgeText?.text = "$deliveredCount delivered"
        
        // Count services from cache - for now checking businessName
        val hasService = !(cachedUser?.get("businessName") as? String).isNullOrBlank()
        binding.expectedPaymentBadgeText.text = if (hasService) "1 Service" else "0 Services"

        val currentRevenue = binding.totalRevenueText?.text?.toString()
            ?.replace("KSh ", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        if (confirmedRevenue != currentRevenue) {
            animateRevenue(confirmedRevenue)
        } else {
            binding.totalRevenueText.text = "KSh ${String.format("%,.0f", confirmedRevenue)}"
        }

        if (cachedReviews.isNotEmpty()) {
            val avgRating = cachedReviews.sumOf { (it["rating"] as? Double ?: 0.0) } / cachedReviews.size
            binding.avgRatingText.text = String.format("%.1f", avgRating)
        }

        loadUserProfileFromCache(userId)
    }

    private fun loadUserProfileFromCache(userId: String) {
        val cachedUser = GlobalDataCache.getUsers()
            .find { it["uid"] == userId || it["id"] == userId } ?: return

        val name = cachedUser["fullName"] as? String
            ?: cachedUser["username"] as? String
            ?: sessionManager.getCachedUserName(userId)
        val businessName = cachedUser["businessName"] as? String
        val profileUrl = cachedUser["profileImageUrl"] as? String
            ?: cachedUser["profilePictureUrl"] as? String
            ?: ""

        val displayName = when {
            name.isNotBlank() -> name // Prioritize Real Name
            !businessName.isNullOrBlank() -> businessName
            else -> auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: sessionManager.getCachedUserName(userId)
        }

        binding.userNameText.text = displayName

        if (profileUrl.isNotBlank()) {
            val lastUpdate = sessionManager.getLastImageUpdate()
            binding.profileIcon.loadProfileImage(userId, profileUrl, if (lastUpdate > 0) lastUpdate else null)
        } else {
            val bitmap = ProfilePictureUtils.generateProfilePicture(displayName, 200, this)
            binding.profileIcon.setImageBitmap(bitmap)
        }
    }

    // ─── Background Refresh ────────────────────────────────────────────────────

    private suspend fun refreshDashboardDataInBackground(userId: String) = coroutineScope {
        val ordersDeferred = async {
            db.collection("waterOrders")
                .whereEqualTo("supplierId", userId)
                .get().await()
        }
        val reviewsDeferred = async {
            db.collection("supplierReviews")
                .whereEqualTo("supplierId", userId)
                .get().await()
        }
        val userDeferred = async {
            db.collection("users").document(userId).get().await()
        }

        try {
            val ordersSnapshot = ordersDeferred.await()
            val reviewsSnapshot = reviewsDeferred.await()
            val userDoc = userDeferred.await()

            var confirmedRevenue = 0.0
            var expectedRevenue = 0.0
            var pendingOrders = 0
            var totalLitresSold = 0
            var currentLitres = 0
            var deliveredCount = 0

            for (doc in ordersSnapshot.documents) {
                val amountStr = doc.get("amount")?.toString() ?: "0"
                val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                val status = doc.getString("status")?.lowercase() ?: ""

                val qty = doc.get("quantity")
                val liters = when (qty) {
                    is Number -> qty.toInt()
                    is String -> qty.filter { it.isDigit() }.toIntOrNull() ?: 0
                    else -> 0
                }
                currentLitres += liters

                if (status == "completed" || status == "delivered" || status == "paid") {
                    confirmedRevenue += amount
                    totalLitresSold += liters
                    deliveredCount++
                } else if (status == "pending" || status == "new" || status == "pending_cod") {
                    pendingOrders++
                    expectedRevenue += amount
                }
            }

            val orderCount = ordersSnapshot.size()

            withContext(Dispatchers.Main) {
                if (binding.totalOrdersText.text != orderCount.toString()) {
                    animateTextView(0, orderCount, binding.totalOrdersText)
                }
                
                binding.expectedRevenueHeaderText.text = String.format("KSh %,.0f", expectedRevenue)
                
                if (binding.ordersBadgeText.text != "$pendingOrders pending") {
                    binding.ordersBadgeText.text = "$pendingOrders pending"
                }

                val stock = (userDoc.get("stockLiters") as? Number)?.toInt() ?: 1000
                binding.inventoryBadgeText?.text = "${stock}L"
                binding.stockRequestBadgeText?.text = "${totalLitresSold}L"
                binding.equipmentBadgeText?.text = "$deliveredCount delivered"
                
                // Fetch service count from database document
                val businessName = userDoc.getString("businessName") ?: ""
                binding.expectedPaymentBadgeText.text = if (businessName.isNotBlank()) "1 Service" else "0 Services"

                val currentRevenue = binding.totalRevenueText?.text?.toString()
                    ?.replace("KSh ", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                if (confirmedRevenue != currentRevenue) {
                    animateRevenue(confirmedRevenue)
                } else {
                    binding.totalRevenueText.text = "KSh ${String.format("%,.0f", confirmedRevenue)}"
                }

                if (!reviewsSnapshot.isEmpty) {
                    val totalRating = reviewsSnapshot.documents.sumOf { it.getDouble("rating") ?: 0.0 }
                    val avg = totalRating / reviewsSnapshot.size()
                    val formatted = String.format("%.1f", avg)
                    if (binding.avgRatingText.text != formatted) {
                        binding.avgRatingText.text = formatted
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WaterDashboard", "Error refreshing data", e)
        }

        // Real-time user profile listener
        withContext(Dispatchers.Main) {
            userListener = db.collection("users").document(userId)
                .addSnapshotListener { document, error ->
                    if (error != null) {
                        Log.e("WaterDashboard", "Error listening to user data", error)
                        return@addSnapshotListener
                    }
                    if (document != null && document.exists()) {
                        val rawName = document.getString("fullName")
                            ?: document.getString("username")
                            ?: sessionManager.getCachedUserName(userId)
                            ?: ""
                        val businessName = document.getString("businessName") ?: ""
                        val displayName = if (rawName.isNotBlank()) rawName else if (businessName.isNotBlank()) businessName else "Water Supplier"

                        if (binding.userNameText.text != displayName) {
                            binding.userNameText.text = displayName
                        }

                        val status = document.getString("verificationStatus")
                        updateVerificationUI(status)

                        val profileUrl = document.getString("profileImageUrl") ?: ""
                        if (profileUrl.isNotEmpty()) {
                            val lastUpdate = sessionManager.getLastImageUpdate()
                            binding.profileIcon.loadProfileImage(userId, profileUrl, if (lastUpdate > 0) lastUpdate else null)
                        }
                        
                        val stock = (document.get("stockLiters") as? Number)?.toInt() ?: 1000
                        binding.inventoryBadgeText?.text = "${stock}L"

                        sessionManager.saveCachedUserProfile(
                            rawName,
                            ProfilePictureUtils.getInitials(rawName),
                            profileUrl.ifEmpty { sessionManager.getCachedUserImageUrl() }
                        )
                    }
                }
        }
    }

    // ─── Verification UI ───────────────────────────────────────────────────────

    private fun updateVerificationUI(status: String?) {
        // Check actual verification status from admin approval
        val isApproved = status?.equals("APPROVED", ignoreCase = true) ?: false
        isSupplierVerified = isApproved
        
        if (isApproved) {
            binding.verificationBadge.visibility = View.VISIBLE
            binding.verificationBadge.setImageResource(R.drawable.ic_verified)
            binding.verificationBadge.imageTintList =
                android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))
        } else {
            binding.verificationBadge.visibility = View.GONE
        }
    }

    // ─── Analytics Charts (Water Supplier) ────────────────────────────────────────────────────────────
    
    private fun setupAnalyticsCharts() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("waterOrders")
            .whereEqualTo("supplierId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                var confirmedRevenue = 0f
                var expectedRevenue = 0f
                
                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    val amountStr = doc.get("amount")?.toString() ?: "0"
                    val amount = amountStr.replace(",", "").toFloatOrNull() ?: 0f

                    if (status in listOf("completed", "delivered", "paid")) {
                        confirmedRevenue += amount
                    } else if (status in listOf("pending", "new", "pending_cod")) {
                        expectedRevenue += amount
                    }
                }

                val entries = ArrayList<BarEntry>()
                entries.add(BarEntry(0f, confirmedRevenue))
                entries.add(BarEntry(1f, expectedRevenue))

                val dataSet = BarDataSet(entries, "Revenue Status")
                dataSet.colors = listOf(Color.parseColor("#43A047"), Color.parseColor("#FB8C00"))
                dataSet.valueTextColor = Color.parseColor("#757575")
                dataSet.valueTextSize = 10f
                dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "KSh ${String.format("%,.0f", value)}"
                }

                binding.ordersBarChart?.apply {
                    data = BarData(dataSet).apply { barWidth = 0.5f }
                    description.isEnabled = false
                    setDrawGridBackground(false)
                    setFitBars(true)
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        valueFormatter = IndexAxisValueFormatter(listOf("Received", "Pending"))
                        setDrawGridLines(false)
                        textColor = Color.parseColor("#616161")
                        labelRotationAngle = -35f
                    }
                    axisLeft.apply {
                        axisMinimum = 0f
                        spaceTop = 35f
                        textColor = Color.parseColor("#9E9E9E")
                        gridColor = Color.parseColor("#EEEEEE")
                    }
                    axisRight.isEnabled = false
                    legend.isEnabled = false
                    animateY(1200)
                    invalidate()
                }

                // Real Order Status Breakdown (Replacing fake inventory data)
                var newOrdersCount = 0f
                var completedOrdersCount = 0f
                
                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    if (status == "new" || status == "pending" || status == "pending_cod") newOrdersCount++
                    else if (status == "completed" || status == "delivered" || status == "paid") completedOrdersCount++
                }

                binding.totalOrdersChartText?.text = "${completedOrdersCount.toInt()} Fulfilled Orders"

                val invEntries = ArrayList<BarEntry>()
                invEntries.add(BarEntry(0f, newOrdersCount))
                invEntries.add(BarEntry(1f, completedOrdersCount))

                val invDataSet = BarDataSet(invEntries, "Service Pipeline")
                invDataSet.colors = listOf(Color.parseColor("#FB8C00"), Color.parseColor("#388E3C"))
                invDataSet.valueTextColor = Color.parseColor("#757575")
                invDataSet.valueTextSize = 10f
                invDataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }

                binding.inventoryBarChart?.apply {
                    extraBottomOffset = 45f
                    data = BarData(invDataSet).apply { barWidth = 0.4f }
                    description.isEnabled = false
                    setDrawGridBackground(false)
                    setFitBars(true)
                    xAxis.apply {
                        position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(listOf("New Orders", "Fulfilled"))
                        setDrawGridLines(false)
                        textColor = Color.parseColor("#616161")
                        labelRotationAngle = -35f
                    }
                    axisLeft.apply {
                        axisMinimum = 0f
                        spaceTop = 35f
                        textColor = Color.parseColor("#9E9E9E")
                        gridColor = Color.parseColor("#EEEEEE")
                    }
                    axisRight.isEnabled = false
                    legend.isEnabled = false
                    animateY(1500)
                    invalidate()
                }
            }
    }

    // ─── Animations ────────────────────────────────────────────────────────────

    private fun animateTextView(start: Int, end: Int, textView: TextView) {
        ValueAnimator.ofInt(start, end).apply {
            duration = 1000
            addUpdateListener { textView.text = it.animatedValue.toString() }
            start()
        }
    }

    private fun animateRevenue(target: Double) {
        ValueAnimator.ofFloat(0f, target.toFloat()).apply {
            duration = 1200
            addUpdateListener {
                val value = it.animatedValue as Float
                binding.totalRevenueText.text = "KSh ${String.format("%,.0f", value)}"
            }
            start()
        }
    }

    // ─── Badges ────────────────────────────────────────────────────────────────

    private fun setupNotificationBadge(userId: String) {
        notificationListener?.remove()
        notificationListener = com.example.homehub.utils.NotificationManager
            .listenToUnreadCount(userId) { count ->
                binding.notificationBadge.text = count.toString()
                binding.notificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
    }

    private fun setupMessageBadge(userId: String) {
        messagesListener?.remove()
        messagesListener = com.example.homehub.chat.ChatManager.getChatRooms { chatRooms ->
            val totalUnread = chatRooms.sumOf { it.getUnreadCount(userId) }
            binding.messagesBadgeText.text = if (totalUnread > 0) "$totalUnread new" else "0 new"
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        notificationListener?.remove()
        messagesListener?.remove()
    }

    private fun showReceiptOptionsDialog() {
        val options = arrayOf("Today's Deliveries", "Monthly Summary", "Order History")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Generate Order Reports")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generateFilteredReport("Today")
                    1 -> generateFilteredReport("Monthly")
                    2 -> generateFilteredReport("All Time")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateFilteredReport(filter: String) {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                // For Water Suppliers, we fetch from waterOrders collection
                // REMOVED server-side orderBy to avoid "failed precondition" (missing index) errors
                val snapshot = db.collection("waterOrders")
                    .whereEqualTo("supplierId", userId)
                    .get()
                    .await()
                
                // Map to Booking model for the ReceiptGenerator (or we could adapt the generator)
                // Assuming Booking model is compatible enough or we map water orders to a report format
                val ordersAsBookings = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    com.example.homehub.billing.Booking(
                        id = doc.id,
                        propertyName = "Water Delivery (${data["liters"]}L)",
                        propertyLocation = data["location"] as? String ?: "Client Location",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        bookingDate = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                        studentName = data["customerName"] as? String ?: "Customer",
                        mpesaReceiptNumber = data["mpesaReceipt"] as? String ?: "Direct"
                    )
                }

                val filtered = when (filter) {
                    "Today" -> {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        ordersAsBookings.filter { it.bookingDate.after(cal.time) }
                    }
                    "Monthly" -> {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MONTH, -1)
                        ordersAsBookings.filter { it.bookingDate.after(cal.time) }
                    }
                    else -> ordersAsBookings
                }

                if (filtered.isEmpty()) {
                    Toast.makeText(this@WaterSupplierDashboardActivity, "No orders found", Toast.LENGTH_SHORT).show()
                } else {
                    com.example.homehub.utils.ReceiptGenerator.generateSummaryReport(
                        this@WaterSupplierDashboardActivity,
                        filtered,
                        "Water Supplier: $filter Activity"
                    )
                }
            } catch (e: Exception) {
                Log.e("WaterDashboard", "Export failed", e)
                Toast.makeText(this@WaterSupplierDashboardActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
