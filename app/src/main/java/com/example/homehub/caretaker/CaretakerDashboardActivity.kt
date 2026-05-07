package com.example.homehub.caretaker

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.homehub.databinding.ActivityCaretakerDashboardBinding
import com.example.homehub.R
import com.example.homehub.auth.SessionManager
import com.example.homehub.utils.VerificationManager
import com.example.homehub.admin.RecentActivity
import com.example.homehub.admin.RecentActivityAdapter
import com.example.homehub.property.Property
import com.example.homehub.property.PropertyDetailsActivity
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.admin.ManageStudentsActivity
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.other.Extensions.loadCircularImage
import com.example.homehub.other.MaintenanceActivity
import com.example.homehub.caretaker.MyPropertiesActivity
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.billing.Booking
import com.example.homehub.billing.BookingCleanupManager
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import android.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class CaretakerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val activityList = mutableListOf<RecentActivity>()
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: RecentActivityAdapter
    private var isCaretakerVerified = false
    private var verificationStatus: String? = null
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val propertyList = mutableListOf<Property>()

    companion object {
        private var hasShownVerificationPrompt = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Standard Emerald Green status bar for consistency
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        binding = ActivityCaretakerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)
        com.example.homehub.utils.NotificationManager.setActiveRole("HOST")



        // Start 1-minute delayed verification check
        // Verification timer removed by user request

        setupUI()
        setupInstantUI()
        setupTabs()
        loadDashboardData()
        setupRecentActivity()

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
        // Refresh profile image and name from session cache instantly
        setupInstantUI()
    }

    private fun setupUI() {
        binding.profileIconCard.setOnClickListener {
            val intent = Intent(this, CaretakerProfileActivity::class.java).apply {
                putExtra("CARETAKER_ID", auth.currentUser?.uid)
            }
            startActivity(intent)
        }

        binding.btnNotificationHeader?.setOnClickListener {
            startActivity(Intent(this, CaretakerNotificationsActivity::class.java))
        }

        binding.propertiesActionCard?.setOnClickListener {
            val intent = Intent(this, ManageStudentsActivity::class.java)
            intent.putExtra("FILTER_OCCUPIED", true)
            startActivity(intent)
        }

        binding.tenantsActionCard?.setOnClickListener {
            Log.d("CaretakerDashboard", "Navigating to CaretakerBookingsActivity (Rent History)")
            val intent = Intent(this, CaretakerBookingsActivity::class.java)
            startActivity(intent)
        }

        binding.messagesActionCard?.setOnClickListener {
            startActivity(Intent(this, CaretakerMessagesActivity::class.java))
        }

        binding.leaveRequestsActionCard?.setOnClickListener {
            startActivity(Intent(this, CaretakerRoomRequestsActivity::class.java))
        }

        binding.btnWithdraw?.setOnClickListener {
            com.google.android.material.snackbar.Snackbar.make(binding.root, "Withdrawal feature coming soon!", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }

        binding.btnGenerateReportCaretaker?.setOnClickListener {
            showReceiptOptionsDialog()
        }

        binding.heroActionCard?.setOnClickListener {
            loadDashboardData()
            android.widget.Toast.makeText(this, "Refreshing caretaker statistics...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabs() {
        binding.caretakerTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { switchTab(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Properties Tab Actions
        binding.addPropertyCard.setOnClickListener {
            startActivity(Intent(this, MyPropertiesActivity::class.java))
        }
        binding.propertiesCountCard.setOnClickListener {
            loadDashboardData()
            android.widget.Toast.makeText(this, "Property statistics updated", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.reservedCountCard.setOnClickListener {
            val intent = Intent(this, ManageStudentsActivity::class.java)
            intent.putExtra("FILTER_BOOKED", true)
            startActivity(intent)
        }
        binding.occupiedCountCard.setOnClickListener {
            val intent = Intent(this, ManageStudentsActivity::class.java)
            intent.putExtra("FILTER_OCCUPIED", true)
            startActivity(intent)
        }
        binding.likesActionCard.setOnClickListener {
            if (propertyList.isNotEmpty()) {
                val bottomSheet = PerformanceBreakdownBottomSheet(propertyList)
                bottomSheet.show(supportFragmentManager, PerformanceBreakdownBottomSheet.TAG)
            } else {
                android.widget.Toast.makeText(this, "No property data available", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.statsActionCard.setOnClickListener {
            if (propertyList.isNotEmpty()) {
                val bottomSheet = PerformanceBreakdownBottomSheet(propertyList)
                bottomSheet.show(supportFragmentManager, PerformanceBreakdownBottomSheet.TAG)
            } else {
                android.widget.Toast.makeText(this, "No property data available", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchTab(position: Int) {
        binding.homeTabContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.propertiesTabContent.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.analyticsTabContent.visibility = if (position == 2) View.VISIBLE else View.GONE

        if (position == 1) {
            // Refresh list if needed
            if (propertyList.isEmpty()) loadDashboardData()
        } else if (position == 2) {
            setupAnalyticsTab()
        }
    }

    private fun logoutUser() {
        auth.signOut()
        sessionManager.clearSession()
        val intent = Intent(this, UserLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupInstantUI() {
        // Load cached info if available
        val currentUser = auth.currentUser
        val cachedName = sessionManager.getCachedUserName(currentUser?.uid)
        binding.userNameText.text = cachedName
            
        // Use unified loader for instant persistent image support
        val lastUpdate = sessionManager.getLastImageUpdate()
        binding.profileIcon.loadProfileImage(currentUser?.uid, null, if (lastUpdate > 0) lastUpdate else null)
    }

    private fun setupRecentActivity() {
        // Obsolete: Recent activity UI removed in favor of notifications
    }

    private fun loadDashboardData() {
        val user = auth.currentUser ?: return
        setupNotificationBadge(user.uid)
        setupMessageBadge(user.uid)
        setupLeaveRequestsBadge(user.uid)

        // INSTANT LOADING: Show cached data immediately
        loadDashboardDataFromCache(user.uid)

        // Background refresh for latest data
        refreshDashboardDataInBackground(user.uid)
    }

    private fun loadDashboardDataFromCache(userId: String) {
        // OPTIMIZED: Single-pass data processing with early computations
        val cachedProperties = GlobalDataCache.getProperties().filter { it.caretakerId == userId }
        val cachedBookings = GlobalDataCache.getBookings().filter { it["caretakerId"] == userId }
        val cachedReviews = GlobalDataCache.getReviews().filter { it["caretakerId"] == userId }

        // FAST COMPUTATIONS: Pre-calculate all values in one pass
        var confirmedRevenue = 0.0
        var expectedRevenue = 0.0
        var deferredRevenue = 0.0
        val occupiedPropertyIds = mutableSetOf<String>()
        val reservedPropertyIds = mutableSetOf<String>()
        var deferredCount = 0
        var totalLikes = 0
        var totalViews = 0

        // Single loop for all property metadata calculations
        for (property in cachedProperties) {
            totalLikes += property.likeCount
            totalViews += property.viewCount
        }

        // Single loop for all booking calculations
        for (booking in cachedBookings) {
            val status = (booking["status"] as? String ?: "").lowercase()
            val amountStr = booking["amount"]?.toString() ?: "0"
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
            val propertyId = booking["propertyId"] as? String
            val isCheckedIn = booking["isCheckedIn"] as? Boolean ?: false

            if (status == "confirmed" || status == "active" || status == "completed" || status == "paid") {
                if (isCheckedIn) {
                    confirmedRevenue += amount
                    if (status != "completed" && propertyId != null) occupiedPropertyIds.add(propertyId)
                } else {
                    expectedRevenue += amount
                    if (status != "completed" && propertyId != null) reservedPropertyIds.add(propertyId)
                }
            } else if (status == "pending_deferred") {
                deferredRevenue += amount
                deferredCount++
                if (propertyId != null) reservedPropertyIds.add(propertyId)
            }
        }

        // FAST UI UPDATES: Batch all UI changes
        val propertyCount = cachedProperties.size

        // Update counts instantly (no animation for speed)
        binding.totalPropertiesText.text = propertyCount.toString()
        binding.tvTotalPropertiesCount.text = propertyCount.toString()
        binding.rentedPropertiesText?.text = String.format("KSh %,.0f", expectedRevenue + deferredRevenue)
        binding.tvOccupiedCount.text = occupiedPropertyIds.size.toString()
        binding.tvReservedCount.text = reservedPropertyIds.size.toString()
        binding.propertiesBadgeText?.text = "${occupiedPropertyIds.size} Occupied"
        binding.leaveRequestsBadgeText?.text = "0 pending" // Initial cache value, real-time listener will update this
        
        // Interest Metrics
        binding.tvTotalLikesCount.text = "$totalLikes Likes"
        binding.tvTotalViewsCount.text = "$totalViews Views"

        // Revenue animation (only if changed significantly)
        val currentRevenue = binding.totalRevenueText?.text?.toString()?.replace("KSh ", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        if (Math.abs(confirmedRevenue - currentRevenue) > 100) { // Only animate if significant change
            animateRevenue(confirmedRevenue)
        } else {
            binding.totalRevenueText?.text = "KSh ${String.format("%,.0f", confirmedRevenue)}"
        }

        binding.tenantsBadgeText?.text = String.format("KSh %,.0f Pending", expectedRevenue + deferredRevenue)

        // Rating calculation (fast)
        if (cachedReviews.isNotEmpty()) {
            val avgRating = cachedReviews.sumOf { (it["rating"] as? Double ?: 0.0) } / cachedReviews.size
            binding.avgRatingText.text = String.format("%.1f", avgRating)
        }

        // Update property list instantly
        propertyList.clear()
        propertyList.addAll(cachedProperties)


        // Load user profile (fast)
        loadUserProfileFromCache(userId)
    }

    private fun loadUserProfileFromCache(userId: String) {
        val cachedUser = GlobalDataCache.getUsers().find { it["uid"] == userId || it["id"] == userId }
        if (cachedUser != null) {
            val name = cachedUser["fullName"] as? String ?:
                      cachedUser["name"] as? String ?:
                      cachedUser["caretakerFullName"] as? String ?:
                      sessionManager.getCachedUserName(userId)

            binding.userNameText.text = name

            val profileUrl = cachedUser["profileImageUrl"] as? String ?:
                            cachedUser["profilePictureUrl"] as? String ?: ""
            val lastUpdate = sessionManager.getLastImageUpdate()
            binding.profileIcon.loadProfileImage(userId, profileUrl, if (lastUpdate > 0) lastUpdate else null)
        }
    }

    private fun refreshDashboardDataInBackground(userId: String) {
        // 1. Fetch official name from 'users' collection for robustness
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                    val firestoreName = document.getString("fullName") ?:
                                     document.getString("name") ?:
                                     document.getString("caretakerFullName") ?:
                                     sessionManager.getCachedUserName(userId)

                    val currentName = binding.userNameText.text.toString()
                    if (currentName != firestoreName) {
                        binding.userNameText.text = firestoreName
                    }

                    // Load Profile Image
                    val profileUrl = document.getString("profileImageUrl") ?:
                                    document.getString("profilePictureUrl") ?: ""

                    val lastUpdate = sessionManager.getLastImageUpdate()
                    binding.profileIcon.loadProfileImage(userId, profileUrl, if (lastUpdate > 0) lastUpdate else null)

                    // Cache for next time
                    sessionManager.saveCachedUserProfile(
                        firestoreName,
                        ProfilePictureUtils.getInitials(firestoreName),
                        profileUrl.ifEmpty { sessionManager.getCachedUserImageUrl() }
                    )

                }

        // 2. Real-time Verification Status Monitoring
        userListener = db.collection("users").document(userId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e("CaretakerDashboard", "User listener failed", error)
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    verificationStatus = document.getString("verificationStatus")

                    // Cross-check with verifiedCaretakers collection for enhanced reliability
                    db.collection("verifiedCaretakers").document(userId).get()
                        .addOnSuccessListener { verifiedDoc ->
                            val isVerifiedByAdmin = verifiedDoc.exists() && verifiedDoc.getBoolean("isVerified") == true

                            if (isVerifiedByAdmin || verificationStatus == "APPROVED") {
                                updateVerificationUI("APPROVED")
                            } else {
                                updateVerificationUI(verificationStatus)
                            }
                        }
                }
            }

        // 3. Load Properties & Update All Summaries
        db.collection("properties")
            .whereEqualTo("caretakerId", userId)
            .get()
            .addOnSuccessListener { documents ->
                propertyList.clear()
                var totalLikes = 0
                for (doc in documents) {
                    val property = Property.fromDocument(doc.data ?: emptyMap())
                    property.let {
                        it.id = doc.id
                        propertyList.add(it)
                        totalLikes += doc.getLong("likeCount")?.toInt() ?: 0
                    }
                }

                // Update Likes metric (ID is avgRatingText in layout but used for Likes)
                binding.avgRatingText.text = totalLikes.toString()

                // Update Home Tab Count
                animateTextView(0, propertyList.size, binding.totalPropertiesText)

                // Update Properties Tab Total Count
                binding.tvTotalPropertiesCount.text = propertyList.size.toString()

                // Now load bookings to calculate revenue and vacancies
                loadBookingsAndRevenue(userId)
            }
            .addOnFailureListener { e ->
                Log.e("CaretakerDashboard", "Error loading properties", e)
            }
    }

    private fun loadBookingsAndRevenue(caretakerId: String) {
        db.collection("bookings")
            .whereEqualTo("caretakerId", caretakerId)
            .get()
            .addOnSuccessListener { documents ->
                var confirmedRevenue = 0.0
                var expectedRevenue = 0.0
                var deferredRevenue = 0.0
                val occupiedPropertyIds = mutableSetOf<String>()
                val reservedPropertyIds = mutableSetOf<String>()
                var deferredCount = 0
                
                var hasCleanedAtLeastOne = false
                for (doc in documents) {
                    val booking = Booking.fromDocument(doc.data ?: emptyMap()).apply { id = doc.id }
                    
                    // CHECK FOR EXPIRY: Integrated automatic cleanup
                    if (booking.paymentStatus == "pending_deferred") {
                        val deadline = booking.paymentDeadline
                        if (deadline != null && deadline.before(Date())) {
                            BookingCleanupManager.checkAndCancelIfExpired(booking)
                            hasCleanedAtLeastOne = true
                            continue // Skip this one in the calculations as it's now invalid
                        }
                    }

                    val status = doc.getString("status")?.lowercase() ?: ""
                    val amountStr = doc.get("amount")?.toString() ?: "0"
                    val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                    val propertyId = doc.getString("propertyId")
                    val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false
                    
                    if (status == "confirmed" || status == "active" || status == "completed" || status == "paid") {
                        if (isCheckedIn) {
                            confirmedRevenue += amount
                            if (status != "completed" && propertyId != null) occupiedPropertyIds.add(propertyId)
                        } else {
                            expectedRevenue += amount
                            if (status != "completed" && propertyId != null) reservedPropertyIds.add(propertyId)
                        }
                    } else if (status == "pending_deferred") {
                        deferredRevenue += amount
                        deferredCount++
                        if (propertyId != null) reservedPropertyIds.add(propertyId)
                    }
                }

                if (hasCleanedAtLeastOne) {
                    // One or more bookings expired, stats should refresh again soon or we can force it
                    // For now, the current loop already skipped them, so revenue is correct.
                }

                animateRevenue(confirmedRevenue)
                binding.rentedPropertiesText?.text = String.format("KSh %,.0f", expectedRevenue + deferredRevenue)
                
                // Update Properties Tab Summary Cards
                binding.tvOccupiedCount.text = occupiedPropertyIds.size.toString()
                binding.tvReservedCount.text = reservedPropertyIds.size.toString()
                
                // Update Home Tab Badges
                binding.propertiesBadgeText?.text = "${occupiedPropertyIds.size} Occupied"
                // binding.leaveRequestsBadgeText is handled by its own real-time listener in setupLeaveRequestsBadge
                binding.tenantsBadgeText?.text = String.format("KSh %,.0f Pending", expectedRevenue + deferredRevenue)
            }
    }

    private fun animateTextView(start: Int, end: Int, textView: TextView) {
        val animator = ValueAnimator.ofInt(start, end)
        animator.duration = 1000
        animator.addUpdateListener { textView.text = it.animatedValue.toString() }
        animator.start()
    }

    private fun animateRevenue(target: Double) {
        val animator = ValueAnimator.ofFloat(0f, target.toFloat())
        animator.duration = 1200
        animator.addUpdateListener { 
            val value = it.animatedValue as Float
            binding.totalRevenueText.text = "Ksh ${String.format("%,.0f", value)}" 
        }
        animator.start()
    }



    private fun updateVerificationUI(status: String?) {
        // Check actual verification status from admin approval
        val isApproved = status?.equals("APPROVED", ignoreCase = true) ?: false
        isCaretakerVerified = isApproved
        
        if (isApproved) {
            binding.verificationBadge.visibility = View.VISIBLE
            binding.verificationBadge.setImageResource(R.drawable.ic_verified)
            binding.verificationBadge.imageTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))
        } else {
            binding.verificationBadge.visibility = View.GONE
        }
    }



    private fun setupNotificationBadge(userId: String) {
        notificationListener?.remove()
        notificationListener = com.example.homehub.utils.NotificationManager.listenToUnreadCount(userId) { count ->
            binding.notificationBadge.text = count.toString()
            binding.notificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private fun setupMessageBadge(userId: String) {
        messagesListener?.remove()
        messagesListener = com.example.homehub.chat.ChatManager.getChatRooms { chatRooms ->
            val totalUnread = chatRooms.sumOf { it.getUnreadCount(userId) }
            binding.messagesBadgeText.text = if (totalUnread > 0) "$totalUnread new" else "0 new"
        }
    }
    private var leaveRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private fun setupLeaveRequestsBadge(userId: String) {
        leaveRequestsListener?.remove()
        leaveRequestsListener = db.collection("leave_requests")
            .whereEqualTo("caretakerId", userId)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                binding.leaveRequestsBadgeText?.text = if (count > 0) "$count pending" else "0 pending"
            }
    }

    private fun setupAnalyticsTab() {
        val userId = auth.currentUser?.uid ?: return
        
        // 1. Revenue Overview
        db.collection("bookings")
            .whereEqualTo("caretakerId", userId)
            .get()
            .addOnSuccessListener { documents ->
                var confirmedRevenue = 0f
                var expectedRevenue = 0f
                var deferredRevenue = 0f
                
                for (doc in documents) {
                    val status = doc.getString("status")?.lowercase() ?: ""
                    val amountStr = doc.get("amount")?.toString() ?: "0"
                    val amount = amountStr.replace(",", "").toFloatOrNull() ?: 0f
                    val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false

                    if (status in listOf("confirmed", "active", "completed", "paid")) {
                        if (isCheckedIn) confirmedRevenue += amount
                        else expectedRevenue += amount
                    } else if (status == "pending_deferred") {
                        deferredRevenue += amount
                    }
                }
                setupRevenueChart(confirmedRevenue, expectedRevenue + deferredRevenue)
                binding.revenueChartSubtitle.text = "Paid: KSh ${String.format("%,.0f", confirmedRevenue)} • Pending: KSh ${String.format("%,.0f", expectedRevenue + deferredRevenue)}"
            }

        // 2. Occupancy Performance
        db.collection("properties")
            .whereEqualTo("caretakerId", userId)
            .get()
            .addOnSuccessListener { propDocs ->
                var totalRoomsSum = 0
                for (doc in propDocs) {
                    var docRooms = doc.getLong("totalRooms")?.toInt() ?: 0
                    if (docRooms <= 0) docRooms = 1
                    totalRoomsSum += docRooms
                }

                db.collection("bookings")
                    .whereEqualTo("caretakerId", userId)
                    .get()
                    .addOnSuccessListener { bookingDocs ->
                        var occupiedRooms = 0
                        var reservedRooms = 0
                        for (doc in bookingDocs) {
                            val status = doc.getString("status")?.lowercase() ?: ""
                            val isCheckedIn = doc.getBoolean("isCheckedIn") ?: false
                            if (status in listOf("confirmed", "active", "paid") || status == "pending_deferred") {
                                if (isCheckedIn) occupiedRooms++
                                else reservedRooms++
                            }
                        }
                        
                        // Note: If a single booking refers to a single room, then booking count corresponds to room count.
                        val vacantRooms = (totalRoomsSum - occupiedRooms - reservedRooms).coerceAtLeast(0)
                        setupOccupancyChart(occupiedRooms.toFloat(), reservedRooms.toFloat(), vacantRooms.toFloat())
                        binding.occupancyChartSubtitle.text = "Rooms: $totalRoomsSum • Occupied: $occupiedRooms • Reserved: $reservedRooms • Vacant: $vacantRooms"
                    }
            }
    }

    private fun setupRevenueChart(confirmed: Float, pending: Float) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, confirmed))
        entries.add(BarEntry(1f, pending))

        val dataSet = BarDataSet(entries, "Revenue Breakdown")
        dataSet.colors = listOf(
            Color.parseColor("#43A047"), // Paid
            Color.parseColor("#FB8C00")  // Pending (Amber/Orange)
        )
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.parseColor("#757575")
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "KSh ${String.format("%,.0f", value)}"
        }

        binding.revenueBarChart.apply {
            extraBottomOffset = 45f
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(listOf("Paid", "Pending"))
                setDrawGridLines(false)
                textColor = Color.parseColor("#616161")
                labelRotationAngle = -35f
            }
            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.parseColor("#9E9E9E")
                gridColor = Color.parseColor("#EEEEEE")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1200)
            invalidate()
        }
    }

    private fun setupOccupancyChart(occupied: Float, reserved: Float, vacant: Float) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, occupied))
        entries.add(BarEntry(1f, reserved))
        entries.add(BarEntry(2f, vacant))

        val dataSet = BarDataSet(entries, "Property Distribution")
        dataSet.colors = listOf(
            Color.parseColor("#2E7D32"), // Occupied
            Color.parseColor("#00897B"), // Reserved
            Color.parseColor("#E53935")  // Vacant
        )
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.parseColor("#757575")
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        binding.occupancyBarChart.apply {
            extraBottomOffset = 45f
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(listOf("Occupied", "Reserved", "Vacant"))
                setDrawGridLines(false)
                textColor = Color.parseColor("#616161")
                labelRotationAngle = -35f
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                textColor = Color.parseColor("#9E9E9E")
                gridColor = Color.parseColor("#EEEEEE")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1500)
            invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        notificationListener?.remove()
        messagesListener?.remove()
        leaveRequestsListener?.remove()
    }

    private fun showReceiptOptionsDialog() {
        val options = arrayOf("Today's Performance", "Monthly Summary", "Full Rental History")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Generate Property Reports")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generateFilteredReport("Today")
                    1 -> generateFilteredReport("Monthly")
                    2 -> generateFilteredReport("All History")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateFilteredReport(filter: String) {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val snapshot = db.collection("bookings")
                    .whereEqualTo("caretakerId", userId)
                    .get()
                    .await()
                
                val caretakerBookings = snapshot.documents.mapNotNull { doc ->
                    com.example.homehub.billing.Booking.fromDocument(doc.data ?: emptyMap()).apply {
                        id = doc.id
                    }
                }.sortedByDescending { it.bookingDate }

                val filtered = when (filter) {
                    "Today" -> {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        caretakerBookings.filter { it.bookingDate != null && it.bookingDate.after(cal.time) }
                    }
                    "Monthly" -> {
                        val cal = java.util.Calendar.getInstance()
                        cal.add(java.util.Calendar.MONTH, -1)
                        caretakerBookings.filter { it.bookingDate != null && it.bookingDate.after(cal.time) }
                    }
                    else -> caretakerBookings
                }

                if (filtered.isEmpty()) {
                    android.widget.Toast.makeText(this@CaretakerDashboardActivity, "No data available for this selection", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    com.example.homehub.utils.ReceiptGenerator.generateSummaryReport(
                        this@CaretakerDashboardActivity,
                        filtered,
                        "Caretaker: $filter Activity Report"
                    )
                }
            } catch (e: Exception) {
                Log.e("CaretakerDashboard", "Export failed: ${e.message}")
                android.widget.Toast.makeText(this@CaretakerDashboardActivity, "Failed to export: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
