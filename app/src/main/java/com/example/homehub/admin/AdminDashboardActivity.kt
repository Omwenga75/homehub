package com.example.homehub.admin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.homehub.R
import com.example.homehub.databinding.ActivityAdminDashboardBinding
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.BarData
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.LegendEntry
import com.example.homehub.admin.AdminSessionManager
import com.example.homehub.auth.SessionManager
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.admin.ManageApplicationsActivity
import com.example.homehub.admin.VerifiedUsersActivity
import com.example.homehub.admin.SuspendedUsersActivity
import com.example.homehub.other.SettingsActivity
import com.example.homehub.admin.ManagePropertiesActivity
import com.example.homehub.student.StudentDashboardActivity
import com.example.homehub.admin.ManageStudentsActivity
import com.example.homehub.admin.ManageCaretakersActivity
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.utils.DashboardCache
import com.example.homehub.supplier.WaterSuppliersActivity
import com.example.homehub.admin.AdminProfileActivity
import com.example.homehub.admin.AdminCredentials
import com.example.homehub.admin.AdminNotificationsActivity
import com.example.homehub.admin.ManageUsersActivity
import com.example.homehub.admin.ManageBookingsActivity

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adminSessionManager: AdminSessionManager
    private lateinit var sessionManager: SessionManager
    private lateinit var dashboardCache: DashboardCache
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var verificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val sessionStartTime = com.google.firebase.Timestamp.now()
    private val TAG = "AdminDashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        adminSessionManager = AdminSessionManager(this)
        sessionManager = SessionManager(this)
        dashboardCache = DashboardCache(this)

        // Standard Emerald Green status bar for consistency
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        com.example.homehub.utils.NotificationManager.setActiveRole("ADMIN")
 
        checkAdminAccess()
        setupUI()
        setupInstantUI()
        setupListeners()
        setupNotificationBadge()
        loadDashboardData()

        // Monitor connection for offline indicator
        lifecycleScope.launch {
            com.example.homehub.utils.NetworkUtils.isNetworkAvailable.collect { available ->
                binding.root.post {
                   binding.offlineBanner.root.visibility = if (available) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh admin profile image and name from session cache instantly
        setupInstantUI()
    }

    private fun checkAdminAccess() {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            redirectToLogin()
            return
        }
        
        // Strictly check role field in session/preferences
        val role = sessionManager.getUserRole()
        if (role?.lowercase() != "admin") {
            Log.e(TAG, "Unauthorized access attempt by: ${currentUser.email} with role: $role")
            android.widget.Toast.makeText(this, "Access Denied: Admin privileges required", android.widget.Toast.LENGTH_LONG).show()
            redirectToLogin()
            return
        }
    }

    private fun setupUI() {
        setGreeting()
        binding.userNameText.text = adminSessionManager.getAdminName()
        
        // Ensure tabs have text (Redundant check for XML TabItems)
        val tabNames = listOf("Home", "Users", "Analytics")
        for (i in 0 until binding.adminTabLayout.tabCount) {
            binding.adminTabLayout.getTabAt(i)?.text = tabNames.getOrNull(i)
        }
    }

    private fun setupInstantUI() {
        // 1. Load cached identity info
        val currentUser = auth.currentUser
        val cachedName = sessionManager.getCachedUserName() ?: adminSessionManager.getAdminName()
        binding.userNameText.text = cachedName
        
        // Ensure UID and URL are valid for profile load - vital for "Instant" feel
        val uid = currentUser?.uid ?: auth.currentUser?.uid
        val cachedImageUrl = sessionManager.getCachedUserImageUrl()
        val lastUpdate = adminSessionManager.getLastAdminImageUpdate()
        binding.profileIcon.loadProfileImage(uid, cachedImageUrl, if (lastUpdate > 0) lastUpdate else null)

        // 2. Load cached platform stats for "Zero Lag" feel
        val snapshot = dashboardCache.getSnapshot()
        if (snapshot.lastUpdate > 0) {
            binding.totalPropertiesText.text = snapshot.properties.toString()
            binding.totalVacantText.text = snapshot.vacant.toString()
            binding.propertiesBadgeText.text = "${snapshot.properties} active"
            binding.bookingsBadgeText.text = "${snapshot.bookings} booked"
            binding.totalUsersHeader.text = snapshot.totalUsers.toString()
            binding.totalCaretakersHeader.text = snapshot.caretakers.toString()
            binding.totalStudentsHeader.text = snapshot.students.toString()
            binding.totalSuppliersHeader.text = snapshot.suppliers.toString()
            binding.tasksBadgeText.text = "0 new tasks"
        }
    }

    private fun setupListeners() {
        binding.profileIcon.setOnClickListener {
            startActivity(Intent(this, AdminProfileActivity::class.java))
        }

        binding.btnNotificationHeader.setOnClickListener {
            val intent = Intent(this, AdminNotificationsActivity::class.java)
            startActivity(intent)
        }

        binding.adminTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let { switchTab(it.position) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })


        // Properties Card
        binding.propertiesActionCard.setOnClickListener {
            startActivity(Intent(this, ManagePropertiesActivity::class.java))
        }

        // Bookings Card
        binding.bookingsActionCard.setOnClickListener {
            startActivity(Intent(this, ManageBookingsActivity::class.java))
        }

        binding.tasksActionCard.setOnClickListener {
            startActivity(Intent(this, ManageVerificationsActivity::class.java))
        }

        // Users Tab Cards
        binding.studentsCard.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            intent.putExtra("tab_index", 0) // Students Tab
            startActivity(intent)
        }

        binding.caretakersCard.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            intent.putExtra("tab_index", 1) // Caretakers Tab
            startActivity(intent)
        }
        
        binding.suppliersCard.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            intent.putExtra("tab_index", 2) // Suppliers Tab
            startActivity(intent)
        }

        binding.createUserCard.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            intent.putExtra("show_add_dialog", true)
            startActivity(intent)
        }

        binding.createUserCardShortcut?.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            intent.putExtra("show_add_dialog", true)
            startActivity(intent)
        }

        binding.btnGenerateReportAdmin.setOnClickListener {
            showReceiptOptionsDialog()
        }

        binding.heroActionCard.setOnClickListener {
            loadDashboardData()
            android.widget.Toast.makeText(this, "Refreshing dashboard data...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTab(position: Int) {
        binding.homeTabContent.visibility = View.GONE
        binding.studentsTabContent.visibility = View.GONE
        binding.analyticsTabContent.visibility = View.GONE

        when (position) {
            0 -> {
                binding.homeTabContent.visibility = View.VISIBLE
                loadDashboardData()
            }
            1 -> {
                binding.studentsTabContent.visibility = View.VISIBLE
                loadUsersData()
            }
            2 -> {
                binding.analyticsTabContent.visibility = View.VISIBLE
                setupAnalyticsTab()
            }
        }
    }

    private fun loadDashboardData() {
        // INSTANT LOADING: Show cached data immediately
        loadDashboardDataFromCache()

        // Background refresh for latest data
        lifecycleScope.launch {
            refreshDashboardDataInBackground()
        }
    }

    private fun loadDashboardDataFromCache() {
        // OPTIMIZED: Single-pass data processing
        val cachedProperties = GlobalDataCache.getProperties()
        val cachedBookings = GlobalDataCache.getBookings()
        val cachedUsers = GlobalDataCache.getUsers()

        // FAST COMPUTATIONS: Pre-calculate all values in one pass
        val propertyCount = cachedProperties.size
        val bookingCount = cachedBookings.size
        
        var pendingRevenue = 0.0
        var confirmedRevenue = 0.0

        for (booking in cachedBookings) {
            val amountStr = booking["amount"]?.toString() ?: "0"
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
            val isCheckedIn = booking["isCheckedIn"] as? Boolean ?: false
            val paymentStatus = (booking["status"] as? String ?: "").lowercase()

            if (paymentStatus == "completed" || paymentStatus == "paid") {
                if (isCheckedIn) {
                    confirmedRevenue += amount
                } else {
                    pendingRevenue += amount
                }
            } else if (paymentStatus == "pending_deferred") {
                pendingRevenue += amount
            }
        }

        var studentCount = 0
        var caretakerCount = 0
        var supplierCount = 0

        for (user in cachedUsers) {
            val role = (user["role"] as? String)?.lowercase() ?: (user["userType"] as? String)?.lowercase()
            val email = (user["email"] as? String)?.lowercase() ?: ""
            
            when {
                role == "admin" || email == "admin@homehub.com" -> { /* Skip admins in student/caretaker stats if needed, or count them */ }
                role == "caretaker" || role == "host" || email.endsWith("@caretaker.com") -> caretakerCount++
                role == "supplier" || role == "water_supplier" || email.contains("waters") -> supplierCount++
                else -> {
                    if (role == "student" || role.isNullOrEmpty()) studentCount++
                }
            }
        }

        // Count pending bookings for notifications
        val pendingBookings = cachedBookings.count { booking ->
            val status = (booking["status"] as? String ?: "").lowercase()
            status in listOf("pending", "new", "pending_deferred")
        }

        val rentedBookedCached = cachedProperties.count {
            val status = it.status?.lowercase()?.trim() ?: ""
            status == "rented" || status == "booked"
        }
        val vacantCached = (propertyCount - rentedBookedCached).coerceAtLeast(0)

        // INSTANT UI UPDATES: Batch all changes
        binding.totalRevenueText.text = String.format("KSh %,.0f", confirmedRevenue)
        binding.totalUsersHeader.text = cachedUsers.size.toString()
        binding.totalCaretakersHeader.text = caretakerCount.toString()
        binding.totalStudentsHeader.text = String.format("KSh %,.0f", pendingRevenue)
        binding.totalSuppliersHeader.text = supplierCount.toString()

        // Notification badge
        binding.notificationBadge.text = pendingBookings.toString()
        binding.notificationBadge.visibility = if (pendingBookings > 0) View.VISIBLE else View.GONE

        // Persist counts for next "Instant" load
        dashboardCache.saveSnapshot(DashboardCache.StatsSnapshot(
            totalUsers = cachedUsers.size,
            students = studentCount,
            caretakers = caretakerCount,
            suppliers = supplierCount,
            properties = propertyCount,
            vacant = vacantCached,
            bookings = bookingCount
        ))

        binding.studentStatText.text = studentCount.toString()
        // Tasks badge updated in background refresh
    }

    private suspend fun refreshDashboardDataInBackground() {
        coroutineScope {
        // OPTIMIZED: Parallel data fetching instead of sequential
        val propertiesDeferred = async { db.collection("properties").get().await() }
        val bookingsDeferred = async { db.collection("bookings").get().await() }
        val usersDeferred = async { db.collection("users").get().await() }
        val notificationsDeferred = async {
            db.collection("notifications")
                .whereEqualTo("type", "ADMIN")
                .whereEqualTo("isRead", false)
                .get().await()
        }

        try {
            // Wait for all data simultaneously
            val propertiesSnapshot = propertiesDeferred.await()
            val bookingsSnapshot = bookingsDeferred.await()
            val usersSnapshot = usersDeferred.await()
            val notificationsSnapshot = notificationsDeferred.await()
            
            // Fetch pending verifications
            val pendingVerifications = db.collection("verificationRequests")
                .whereEqualTo("status", "PENDING")
                .get().await()
            val pendingVerifyCount = pendingVerifications.size()

            val userId = auth.currentUser?.uid ?: ""

            // SYNC: Update Admin's own profile info in dashboard from the users collection
            val adminDoc = usersSnapshot.documents.find { it.id == userId }
            if (adminDoc != null) {
                val officialName = adminDoc.getString("fullName") ?: adminDoc.getString("username") ?: adminSessionManager.getAdminName()
                val profileUrl = adminDoc.getString("profileImageUrl") ?: adminDoc.getString("profilePictureUrl") ?: ""
                
                withContext(Dispatchers.Main) {
                    binding.userNameText.text = officialName
                    // Update session for next instant load
                    adminSessionManager.setAdminName(officialName)
                    sessionManager.saveCachedUserProfile(officialName, ProfilePictureUtils.getInitials(officialName), profileUrl)
                    
                    // Refresh profile icon with unified loader
                    binding.profileIcon.loadProfileImage(userId, profileUrl)
                }
            }

            // FAST COMPUTATIONS: Single pass through users for counting roles
            var studentCount = 0
            var caretakerCount = 0
            var adminCount = 0
            var supplierCount = 0

            var pendingRevenue = 0.0
            var confirmedRevenue = 0.0

            for (doc in usersSnapshot.documents) {
                val role = doc.getString("role")?.lowercase() ?: doc.getString("userType")?.lowercase()
                val email = doc.getString("email")?.lowercase() ?: ""
                when {
                    role == "admin" || email == "admin@homehub.com" || email.endsWith("@homehub.admin") -> adminCount++
                    role == "caretaker" || role == "host" || email.endsWith("@caretaker.com") -> caretakerCount++
                    role == "supplier" || role == "water_supplier" || email.contains("waters") -> supplierCount++
                    else -> studentCount++
                }
            }

            for (doc in bookingsSnapshot.documents) {
                val amountStr = doc.get("amount")?.toString() ?: "0"
                val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false
                val status = doc.getString("status")?.lowercase() ?: ""

                if (status == "completed" || status == "paid") {
                    if (isCheckedIn) {
                        confirmedRevenue += amount
                    } else {
                        pendingRevenue += amount
                    }
                } else if (status == "pending_deferred") {
                    pendingRevenue += amount
                }
            }

            // INSTANT UI UPDATES: Batch all changes
            val propertyCount = propertiesSnapshot.size()
            val bookingCount = bookingsSnapshot.size()
            val userCount = usersSnapshot.size()
            val notificationCount = notificationsSnapshot.size()

            var rentedBookedLive = 0
            for (doc in propertiesSnapshot.documents) {
                val status = doc.getString("status")?.lowercase()?.trim() ?: ""
                if (status == "rented" || status == "booked") {
                    rentedBookedLive++
                }
            }
            val vacantLive = (propertyCount - rentedBookedLive).coerceAtLeast(0)

            // Only update if values changed (avoid unnecessary animations)
            if (binding.totalPropertiesText.text != propertyCount.toString()) {
                binding.totalPropertiesText.text = propertyCount.toString()
                binding.propertiesBadgeText.text = "$propertyCount active"
            }
            
            if (binding.totalVacantText.text != vacantLive.toString()) {
                binding.totalVacantText.text = vacantLive.toString()
            }

            if (binding.bookingsBadgeText.text != "$bookingCount booked") {
                binding.bookingsBadgeText.text = "$bookingCount booked"
            }

            // Hero Card Stats (in Home Tab)
            binding.totalRevenueText.text = String.format("KSh %,.0f", confirmedRevenue)
            binding.totalStudentsHeader.text = studentCount.toString()
            binding.totalSuppliersHeader.text = supplierCount.toString()
            
            // Action Card in Home Tab
            binding.tasksBadgeText.text = "$pendingVerifyCount new task${if (pendingVerifyCount == 1) "" else "s"}"
            
            // Stats in Students Tab
            binding.studentStatText.text = studentCount.toString()

            if (binding.totalUsersHeader.text != userCount.toString()) {
                binding.totalUsersHeader.text = userCount.toString()
            }

            if (binding.totalCaretakersHeader.text != caretakerCount.toString()) {
                binding.totalCaretakersHeader.text = caretakerCount.toString()
            }

            
            if (binding.totalSuppliersHeader.text != supplierCount.toString()) {
                binding.totalSuppliersHeader.text = supplierCount.toString()
            }

            if (binding.notificationBadge.text != notificationCount.toString()) {
                binding.notificationBadge.text = notificationCount.toString()
                binding.notificationBadge.visibility = if (notificationCount > 0) View.VISIBLE else View.GONE
            }

            // Persist latest values to cache for next instant load
            dashboardCache.saveSnapshot(DashboardCache.StatsSnapshot(
                totalUsers = userCount,
                students = studentCount,
                caretakers = caretakerCount,
                suppliers = supplierCount,
                properties = propertyCount,
                vacant = vacantLive,
                bookings = bookingCount
            ))

            Unit
        } catch (e: Exception) {
            Log.e("AdminDashboard", "Error refreshing data", e)
        }
        }
    }


    private fun setGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    private fun loadUsersData() {
        db.collection("users").get().addOnSuccessListener { snapshot ->
            var students = 0
            var caretakers = 0
            var suppliers = 0
            var admins = 0
            
            for (doc in snapshot.documents) {
                val role = doc.getString("role")?.lowercase() ?: doc.getString("userType")?.lowercase()
                val email = doc.getString("email")?.lowercase() ?: ""
                
                when {
                    role == "admin" || email == "admin@homehub.com" || email.endsWith("@homehub.admin") -> admins++
                    role == "caretaker" || role == "host" || email.endsWith("@caretaker.com") -> caretakers++
                    role == "supplier" || role == "water_supplier" || email.contains("waters") -> suppliers++
                    else -> students++
                }
            }
            
            // Removed studentStatText update here as it shows Pending Payment (money), not count
            binding.caretakerStatText.text = caretakers.toString()
            binding.supplierStatText.text = suppliers.toString()
            // Removed adminStatText as it's no longer in the layout
        }
    }

    private fun setupAnalyticsTab() {
        setupPropertyOverviewChart()
        setupUsersBarChart()
        setupPropertiesBarChart()
    }

    private fun setupPropertyOverviewChart() {
        db.collection("properties").get().addOnSuccessListener { snapshot ->
            var totalRoomsSum = 0
            var occupiedCount = 0
            var reservedCount = 0

            for (doc in snapshot.documents) {
                var docRooms = doc.getLong("totalRooms")?.toInt() ?: 0
                // If it's a regular property or totalRooms not specified, assume 1 room
                if (docRooms <= 0) {
                    docRooms = 1
                }
                totalRoomsSum += docRooms

                val roomStatuses = doc.get("roomStatuses") as? Map<*, *>
                if (roomStatuses != null && roomStatuses.isNotEmpty()) {
                    for ((_, statusVal) in roomStatuses) {
                        val statusStr = statusVal?.toString()?.lowercase()?.trim() ?: ""
                        if (statusStr == "rented" || statusStr == "occupied") {
                            occupiedCount++
                        } else if (statusStr == "booked" || statusStr == "reserved") {
                            reservedCount++
                        }
                    }
                } else {
                    val status = doc.getString("status")?.lowercase()?.trim() ?: ""
                    if (status == "rented" || status == "occupied") {
                        occupiedCount += docRooms
                    } else if (status == "booked" || status == "reserved") {
                        reservedCount += docRooms
                    }
                }
            }

            val vacant = (totalRoomsSum - occupiedCount - reservedCount).coerceAtLeast(0)
            binding.totalRevenueChartText.text = "$totalRoomsSum Rooms • $occupiedCount Occupied • $reservedCount Reserved • $vacant Vacant"

            val entries = ArrayList<BarEntry>()
            entries.add(BarEntry(0f, totalRoomsSum.toFloat()))
            entries.add(BarEntry(1f, occupiedCount.toFloat()))
            entries.add(BarEntry(2f, reservedCount.toFloat()))
            entries.add(BarEntry(3f, vacant.toFloat()))

            val dataSet = BarDataSet(entries, "Room Overview")
            dataSet.colors = listOf(
                Color.parseColor("#1E88E5"), // Total (Blue)
                Color.parseColor("#43A047"), // Occupied (Green)
                Color.parseColor("#6366F1"), // Reserved (Indigo)
                Color.parseColor("#FB8C00")  // Vacant (Orange)
            )
            dataSet.valueTextColor = Color.parseColor("#616161")
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            val barData = BarData(dataSet)
            barData.barWidth = 0.6f

            binding.revenueBarChart.apply {
                data = barData
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setFitBars(true)
                setExtraBottomOffset(45f) // Add space for rotated labels

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                        listOf("Total", "Occupied", "Reserved", "Vacant")
                    )
                    textColor = Color.parseColor("#757575")
                    textSize = 12f
                    setDrawAxisLine(true)
                    axisLineColor = Color.parseColor("#E0E0E0")
                    labelCount = 4
                    labelRotationAngle = -35f
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#EEEEEE")
                    axisMinimum = 0f
                    textColor = Color.parseColor("#757575")
                }

                axisRight.isEnabled = false
                legend.isEnabled = false
                animateY(1000)
                invalidate()
            }
        }.addOnFailureListener {
            binding.totalRevenueChartText.text = "Property overview unavailable"
        }
    }

    private fun setupUsersBarChart() {
        db.collection("bookings").get().addOnSuccessListener { snapshot ->
            var confirmedRevenue = 0f
            var pendingRevenue = 0f
            var deferredRevenue = 0f
            
            binding.totalUsersChartText.text = "Revenue Status Overview"
            for (doc in snapshot.documents) {
                val amountStr = doc.get("amount")?.toString() ?: "0"
                val amount = amountStr.replace(",", "").toFloatOrNull() ?: 0f
                val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false
                val status = doc.getString("status")?.lowercase() ?: ""

                if (status == "completed" || status == "paid") {
                    if (isCheckedIn) confirmedRevenue += amount
                    else pendingRevenue += amount
                } else if (status == "pending_deferred") {
                    deferredRevenue += amount
                }
            }

            val entries = ArrayList<BarEntry>()
            entries.add(BarEntry(0f, confirmedRevenue))
            entries.add(BarEntry(1f, deferredRevenue))
            entries.add(BarEntry(2f, pendingRevenue))

            val dataSet = BarDataSet(entries, "Revenue Status")
            dataSet.colors = listOf(
                Color.parseColor("#43A047"), // Paid
                Color.parseColor("#6366F1"), // Pay Later (Indigo)
                Color.parseColor("#FB8C00")  // Pending
            )
            dataSet.valueTextColor = Color.parseColor("#757575")
            dataSet.valueTextSize = 10f
            
            dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("KSh %,.0f", value)
                }
            }

            val barData = BarData(dataSet)
            barData.barWidth = 0.5f

            binding.usersBarChart.apply {
                data = barData
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setFitBars(true)
                setExtraBottomOffset(45f)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
                        listOf("Paid", "Pay Later", "Pending")
                    )
                    textColor = Color.parseColor("#616161")
                    textSize = 10f
                    setDrawAxisLine(true)
                    axisLineColor = Color.parseColor("#E0E0E0")
                    labelCount = 2
                    labelRotationAngle = -35f
                }
                
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#F5F5F5")
                    axisMinimum = 0f
                    textColor = Color.parseColor("#9E9E9E")
                    granularity = 1f
                }
                
                axisRight.isEnabled = false
                legend.isEnabled = false
                setBackgroundColor(Color.TRANSPARENT)
                
                animateY(1500, com.github.mikephil.charting.animation.Easing.EaseOutQuart)
                invalidate()
            }
        }
    }

    private fun setupPropertiesBarChart() {
        db.collection("properties").get().addOnSuccessListener { snapshot ->
            val trueCount = snapshot.size()
            
            if (trueCount == 0) {
                binding.totalPropertiesChartText.text = "0 Properties Listed"
                binding.propertiesBarChart.clear()
                binding.propertiesBarChart.setNoDataText("No property data available yet")
                binding.propertiesBarChart.invalidate()
                return@addOnSuccessListener
            }

            binding.totalPropertiesChartText.text = "$trueCount Total Properties Listed"
            
            // DYNAMIC CATEGORY MAPPING
            val categoryCounts = mutableMapOf<String, Int>()
            for (doc in snapshot.documents) {
                // Use 'propertyType' or 'type' or fallback to 'Other'
                val type = doc.getString("propertyType") 
                    ?: doc.getString("type") 
                    ?: doc.getString("category") 
                    ?: "Other"
                
                // Capitalize for cleaner labels
                val label = type.replaceFirstChar { it.uppercase() }.ifBlank { "Other" }
                categoryCounts[label] = (categoryCounts[label] ?: 0) + 1
            }

            // Convert map to sorted entries for consistent charting
            val sortedCategories = categoryCounts.entries.sortedByDescending { it.value }.take(4)
            val entries = ArrayList<BarEntry>()
            val labels = mutableListOf<String>()

            sortedCategories.forEachIndexed { index, entry ->
                entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
                labels.add(entry.key)
            }

            val dataSet = BarDataSet(entries, "Property Distribution")
            dataSet.colors = listOf(
                Color.parseColor("#00E676"), // Vibrant Green
                Color.parseColor("#00C853"), // Emerald
                Color.parseColor("#00BFA5"), // Teal
                Color.parseColor("#00897B")  // Deep Teal
            )
            dataSet.valueTextColor = Color.parseColor("#757575")
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }

            val barData = BarData(dataSet)
            barData.barWidth = 0.65f

            binding.propertiesBarChart.apply {
                data = barData
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setFitBars(true)
                setExtraBottomOffset(45f)
                
                xAxis.apply {
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
                    textColor = Color.parseColor("#616161")
                    textSize = 10f
                    setDrawAxisLine(true)
                    axisLineColor = Color.parseColor("#E0E0E0")
                    labelCount = labels.size
                    labelRotationAngle = -35f
                }
                
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#F5F5F5")
                    axisMinimum = 0f
                    textColor = Color.parseColor("#9E9E9E")
                    granularity = 1f
                }
                
                axisRight.isEnabled = false
                legend.isEnabled = false
                animateY(1500, com.github.mikephil.charting.animation.Easing.EaseOutQuart)
                invalidate()
            }
        }.addOnFailureListener {
            binding.totalPropertiesChartText.text = "Property distribution unavailable"
        }
    }

    private fun logout() {
        // Remove all listeners first to avoid 'Insufficient Permission' errors after sign out
        notificationListener?.remove()
        notificationListener = null
        
        adminSessionManager.clearAdminSession()
        getSharedPreferences("LoginPrefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        sessionManager.clearSession()
        auth.signOut()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to log out from the administrative dashboard?")
            .setPositiveButton("Sign Out") { _, _ -> 
                logout()
            }
            .setNegativeButton("Stay", null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
            }
    }

    override fun onBackPressed() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit HomeHub?")
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
            }
    }

    private fun setupNotificationBadge() {
        notificationListener?.remove()
        notificationListener = com.example.homehub.utils.NotificationManager.listenToAdminUnreadCount { count ->
            binding.notificationBadge.text = count.toString()
            binding.notificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            
            if (count > 0) {
                // Peek at latest notification to show a Toast if it's new
                com.example.homehub.utils.NotificationManager.getAdminNotifications(true) { list ->
                    val latest = list.find { !it.isRead }
                    if (latest != null) {
                        runOnUiThread {
                            android.widget.Toast.makeText(this, "Admin Alert: ${latest.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.remove()
        verificationListener?.remove()
    }

    private fun showReceiptOptionsDialog() {
        val options = arrayOf("Today's Activity", "Monthly Summary", "All Time Ledger")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Generate Administrative Reports")
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
        lifecycleScope.launch {
            try {
                // 1. Fetch latest bookings for the report
                val snapshot = db.collection("bookings")
                    .orderBy("bookingDate", Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                val allBookings = snapshot.documents.mapNotNull { doc ->
                    com.example.homehub.billing.Booking.fromDocument(doc.data ?: emptyMap()).apply {
                        id = doc.id
                    }
                }

                val filtered = when (filter) {
                    "Today" -> {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        allBookings.filter { it.bookingDate != null && it.bookingDate.after(cal.time) }
                    }
                    "Monthly" -> {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MONTH, -1)
                        allBookings.filter { it.bookingDate != null && it.bookingDate.after(cal.time) }
                    }
                    else -> allBookings
                }

                if (filtered.isEmpty() && filter != "All Time") {
                    Toast.makeText(this@AdminDashboardActivity, "No data found for this period", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 2. Fetch LIVE Stats for the Snapshot Section
                val propsSnapshot = db.collection("properties").get().await()
                val usersSnapshot = db.collection("users").get().await()
                
                val totalProps = propsSnapshot.size()
                val totalUsers = usersSnapshot.size()
                
                var rentedCount = 0
                val distribution = mutableMapOf<String, Int>()
                
                for (doc in propsSnapshot.documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    if (status == "rented" || status == "booked") rentedCount++
                    
                    val type = doc.getString("propertyType") ?: doc.getString("type") ?: "Other"
                    val label = type.replaceFirstChar { it.uppercase() }.ifBlank { "Other" }
                    distribution[label] = (distribution[label] ?: 0) + 1
                }
                
                val occupancy = if (totalProps > 0) (rentedCount * 100) / totalProps else 0
                
                val stats = mapOf(
                    "totalUsers" to totalUsers,
                    "totalProperties" to totalProps,
                    "occupancy" to occupancy
                )

                // 3. Generate the comprehensive report
                com.example.homehub.utils.ReceiptGenerator.generateSummaryReport(
                    this@AdminDashboardActivity,
                    filtered,
                    "HomeHub $filter Administrative Report",
                    stats,
                    distribution
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate report: ${e.message}")
                Toast.makeText(this@AdminDashboardActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
