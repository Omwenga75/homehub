package com.example.homehub.student

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homehub.utils.VerificationManager
import com.example.homehub.utils.InteractionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import com.example.homehub.R
import com.example.homehub.admin.AdminDashboardActivity
import com.example.homehub.admin.AdminSessionManager
import com.example.homehub.databinding.ActivityStudentDashboardBinding
import com.example.homehub.auth.SessionManager
import com.example.homehub.auth.UserLoginActivity
import com.example.homehub.billing.MyBookingsActivity
import com.example.homehub.property.CategoryAdapter
import com.example.homehub.property.UnifiedPropertyAdapter
import com.example.homehub.property.Property
import com.example.homehub.property.FilterData
import com.example.homehub.property.Category
import com.example.homehub.utils.GlobalDataCache

import com.example.homehub.chat.ChatManager
import com.example.homehub.other.NotificationsActivity
import com.example.homehub.property.AllPropertiesActivity
import com.example.homehub.property.FilterActivity
import com.example.homehub.property.PropertyDataHolder
import com.example.homehub.property.PropertyDetailsActivity
import com.example.homehub.supplier.WaterSuppliersActivity
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.other.Extensions.loadProfileImage
import com.example.homehub.chat.ChatListActivity

class StudentDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "StudentDashboard"
        private const val DEBOUNCE_DELAY = 500L
        private const val PAGE_SIZE = 20L
    }

    private lateinit var binding: ActivityStudentDashboardBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var categoryAdapter: CategoryAdapter

    // Single adapter with different data sources
    private val propertyAdapters = mutableMapOf<String, UnifiedPropertyAdapter>()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var currentQuery: Query? = null

    // State management
    private val propertiesFlow = MutableStateFlow<Map<String, List<Property>>>(emptyMap())
    private val userDataFlow = MutableStateFlow<UserData?>(null)
    private val isLoading = AtomicBoolean(false)
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var chatRoomsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var searchJob: Job? = null

    // Search debounce
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private var currentFilterData = FilterData()
    private var isFiltering = false

    private val filterLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getSerializableExtra("filter_data")?.let {
                currentFilterData = it as FilterData
                isFiltering = currentFilterData.hasActiveFilters()
                updateFilterIcon()
                loadProperties(forceRefresh = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        initializeManagers()

        // Permanent Login: Auto-repair session if authenticated but bits are missing
        repairSessionIfNeeded()

        if (!checkAuthentication()) return
        if (!checkOnboardingComplete()) return

        setupUI()
        // IMMEDIATE UI SETUP - Show something instantly from cache (HomeView pattern)
        setupInstantUI()
        setupDataFlow()
        
        loadUserData()
        
        // Load properties with a tiny delay to allow animations to settle
        lifecycleScope.launch {
            delay(100)
            loadProperties(showLoading = false) // Silent load on start
        }

        // Set default tab
        selectTabUI(R.id.navAddProperty)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPaddingTop = (12 * resources.displayMetrics.density).toInt()
            binding.fixedHeader.updatePadding(top = systemBars.top + extraPaddingTop)

            val floatingMargin = (16 * resources.displayMetrics.density).toInt()
            val horizontalMargin = (16 * resources.displayMetrics.density).toInt()

            val params = binding.bottomNavCard.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + floatingMargin
            params.leftMargin = horizontalMargin
            params.rightMargin = horizontalMargin
            binding.bottomNavCard.layoutParams = params

            insets
        }

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
    }

    private fun initializeManagers() {
        sessionManager = SessionManager(this)
    }

    /** Ensure session state is repaired if missing but Firebase user is active */
    private fun repairSessionIfNeeded() {
        val user = auth.currentUser ?: return
        if (!sessionManager.isLoggedIn() || !sessionManager.isRoleSelected()) {
            Log.d(TAG, "Syncing session for: ${user.uid}")
            sessionManager.saveUserId(user.uid)
            // Note: Role will be fetched in loadUserData() or handled by checkAuthentication()
        }
    }

    private fun checkAuthentication(): Boolean {
        if (auth.currentUser == null) {
            redirectToLogin()
            return false
        }
        return true
    }

    private fun checkOnboardingComplete(): Boolean {
        // Just rely on the assigned role in session
        return true
    }

    override fun onResume() {
        super.onResume()
        // Ensure "Home" tab is selected visually when returning to Dashboard
        selectTabUI(R.id.navAddProperty)
    }

    private fun setupUI() {
        setupGreeting()
        setupCategories()
        setupAdapters()
        setupClickListeners()
        setupSearch()

        binding.fabMyRoom.setOnClickListener { navigateToMyRoom() }
        binding.fabMyRoom.visibility = if (auth.currentUser != null) View.VISIBLE else View.GONE
        binding.fabMyRoom.isEnabled = true
        binding.fabMyRoom.alpha = 1.0f

        binding.swipeRefresh.setOnRefreshListener {
            loadProperties(forceRefresh = true, showLoading = true)
        }
    }

    private fun setupGreeting() {
        binding.tvGreeting.text = "Welcome home,"
    }

    private fun setupCategories() {
        val categories = listOf(
            Category("bedsitters", "Bedsitters", R.drawable.hs, 0),
            Category("1br", "1 Bedroom", R.drawable.hs1, 0),
            Category("2br", "2 Bedroom", R.drawable.hs2, 0),
            Category("single", "Single Rooms", R.drawable.hs3, 0),
            Category("shared", "Shared Properties", R.drawable.hs4, 0)
        )

        categoryAdapter = CategoryAdapter { scrollToCategory(it) }
        categoryAdapter.updateCategories(categories)
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@StudentDashboardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(10)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupAdapters() {
        val clickListener: (Property, View) -> Unit = { property, view ->
            val imageView = view.findViewById<ImageView?>(R.id.ivHouseImage) 
                ?: view.findViewById<ImageView?>(R.id.propertyImage)
            openPropertyDetails(property, imageView)
        }

        val favListener: (Property, View) -> Unit = { property, _ ->
            lifecycleScope.launch(Dispatchers.IO) {
                updateFavorite(property.id, !property.isFavorite)
            }
        }

        val layoutRes = R.layout.item_house_horizontal

        propertyAdapters["bedsitters"] = UnifiedPropertyAdapter(layoutRes, clickListener, favListener)
        propertyAdapters["oneBedroom"] = UnifiedPropertyAdapter(layoutRes, clickListener, favListener)
        propertyAdapters["twoBedrooms"] = UnifiedPropertyAdapter(layoutRes, clickListener, favListener)
        propertyAdapters["singleRooms"] = UnifiedPropertyAdapter(layoutRes, clickListener, favListener)
        propertyAdapters["sharedProperties"] = UnifiedPropertyAdapter(layoutRes, clickListener, favListener)

        binding.rvBedsitters.adapter = propertyAdapters["bedsitters"]
        binding.rvOneBedroom.adapter = propertyAdapters["oneBedroom"]
        binding.rvTwoBedrooms.adapter = propertyAdapters["twoBedrooms"]
        binding.rvSingleRooms.adapter = propertyAdapters["singleRooms"]
        binding.rvSharedHouses.adapter = propertyAdapters["sharedProperties"]

        val sharedPool = RecyclerView.RecycledViewPool()
        
        listOf(
            binding.rvBedsitters, binding.rvOneBedroom,
            binding.rvTwoBedrooms, binding.rvSingleRooms, binding.rvSharedHouses
        ).forEach {
            it.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            it.setHasFixedSize(true)
            it.setRecycledViewPool(sharedPool) // Vital for smooth scrolling with multiple horizontal lists
            it.setItemViewCacheSize(20)
            it.isNestedScrollingEnabled = false
        }
    }

    private fun setupDataFlow() {
        lifecycleScope.launch {
            userDataFlow
                .debounce(300)
                .collect { userData ->
                    userData?.let { updateUserUI(it) }
                }
        }

        lifecycleScope.launch {
            propertiesFlow
                .debounce(300)
                .collect { categorizedProperties ->
                    updateUIWithProperties(categorizedProperties)
                }
        }
    }

    private fun setupInstantUI() {
        // Show greeting instantly
        setupGreeting()
        
        // Load cached info if available
        val currentUser = auth.currentUser
        val cachedName = sessionManager.getCachedUserName(currentUser?.uid)
        binding.tvStudentName.text = cachedName
            
        // Show branded placeholder instantly while waiting for network/IO
        binding.ivProfile.loadProfileImage(currentUser?.uid)

        // INSTANT LOADING: Sync load from global cache to prevent empty screen flicker
        if (GlobalDataCache.isPropertiesCacheFresh()) {
            val cachedProperties = GlobalDataCache.getProperties()
            if (cachedProperties.isNotEmpty()) {
                processCachedProperties(cachedProperties)
            }
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        setupNotificationBadge(userId)
        setupMessageBadge(userId)

        // Real-time listener for verification status
        userListener = db.collection("users").document(userId)
            .addSnapshotListener { document, error ->
                if (error != null) return@addSnapshotListener
                if (document != null && document.exists()) {
                    val status = document.getString("verificationStatus") ?: "none"
                    val isVerified = status == "APPROVED"
                    
                    // Update badge visibility
                    binding.ivVerifiedBadge.visibility = if (isVerified) View.VISIBLE else View.GONE
                    
                    // Update booking button state based on verification
                    updateBookingButtonState(isVerified)
                    
                    // Update user flow state if needed
                    val name = document.getString("fullName") ?: 
                               document.getString("name") ?: 
                               sessionManager.getCachedUserName(userId)
                    val profileUrl = document.getString("profileImageUrl") ?: ""
                    userDataFlow.value = UserData(name, profileUrl, isVerified)
                }
            }
    }

    private fun updateBookingButtonState(isVerified: Boolean) {
        // My Room FAB is always enabled
        binding.fabMyRoom.isEnabled = true
        binding.fabMyRoom.alpha = 1.0f
    }

    private fun loadProperties(forceRefresh: Boolean = false, showLoading: Boolean = false) {
        if (isLoading.get()) return

        // INSTANT LOADING: Check global data cache
        val isFresh = GlobalDataCache.isPropertiesCacheFresh()
        if (!forceRefresh && isFresh) {
            val cachedProperties = GlobalDataCache.getProperties()
            if (cachedProperties.isNotEmpty()) {
                processCachedProperties(cachedProperties)
                refreshPropertiesInBackground()
                return
            }
        }

        // Fetch fresh data if needed
        isLoading.set(true)
        if (showLoading) binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch in background
                val propertiesSnapshot = db.collection("properties").limit(100L).get().await()
                val favoritesSnapshot = db.collection("users")
                    .document(auth.currentUser?.uid ?: "")
                    .collection("favorites")
                    .get().await()
                    
                val favoriteIds = favoritesSnapshot.documents.map { it.id }.toSet()

                val properties = withContext(Dispatchers.Default) {
                    val fetched = propertiesSnapshot.documents.size
                    Log.d(TAG, "Fetched $fetched properties from database")
                    
                    val filtered = propertiesSnapshot.documents.mapNotNull { doc ->
                        try {
                            Property.fromDocument(doc.data ?: emptyMap()).apply {
                                id = doc.id
                                isFavorite = favoriteIds.contains(id)
                            }.let { prop ->
                                val shouldShow = prop.shouldShowOnDashboard()
                                if (!shouldShow) {
                                    Log.d(TAG, "Filtered out property ${prop.id}: status=${prop.status}, available=${prop.available}, deleted=${prop.isDeleted}, archived=${prop.isArchived}")
                                }
                                if (shouldShow) prop else null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing property: ${e.message}", e)
                            null
                        }
                    }.sortedByDescending { it.createdAt.time }
                    
                    Log.d(TAG, "After filtering: ${filtered.size} properties to display")
                    filtered
                }

                // Update the app-wide cache
                GlobalDataCache.refreshAllData() // Triggers a full sync in background

                withContext(Dispatchers.Main) {
                    if (properties.isNotEmpty()) {
                        processCachedProperties(properties)
                    } else if (!isFresh) {
                        showEmptyState("No properties found. Pull to refresh.")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading properties", e)
                withContext(Dispatchers.Main) {
                    if (GlobalDataCache.getProperties().isEmpty()) {
                        showEmptyState("Connection error. Pull to refresh.")
                    }
                }
            } finally {
                isLoading.set(false)
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun processCachedProperties(properties: List<Property>) {
        lifecycleScope.launch(Dispatchers.Default) {
            // Categorize and filter in one pass
            val categorized = mutableMapOf<String, MutableList<Property>>()
            
            properties.forEach { property ->
                if (isFiltering) {
                    if (!matchesFilter(property)) return@forEach
                }
                
                val category = getCategoryKey(property.type)
                Log.d(TAG, "Categorizing: ${property.displayTitle} (type='${property.type}') -> category='$category'")
                categorized.getOrPut(category) { mutableListOf() }.add(property)
            }

            val limited = categorized.mapValues { it.value.take(10) }
            Log.d(TAG, "Categories after processing: ${categorized.mapValues { it.value.size }}")

            withContext(Dispatchers.Main) {
                propertiesFlow.value = limited
                updateUIWithProperties(limited)
            }
        }
    }

    private fun matchesFilter(property: Property): Boolean {
        val matchesLocation = currentFilterData.location.isEmpty() ||
                property.location.lowercase().contains(currentFilterData.location.lowercase())
        val matchesPrice = (currentFilterData.minPrice <= 0 || property.priceValue >= currentFilterData.minPrice) &&
                (currentFilterData.maxPrice >= 200000 || property.priceValue <= currentFilterData.maxPrice)
        return matchesLocation && matchesPrice
    }

    private fun getCategoryKey(type: String): String {
        val t = type.lowercase()
        val category = when {
            t.contains("bedsitter") || t.contains("studio") -> "bedsitters"
            t.contains("1 bedroom") -> "oneBedroom"
            t.contains("2 bedroom") -> "twoBedrooms"
            t.contains("single") -> "singleRooms"
            t.contains("shared") || t.contains("hostel") -> "sharedProperties"
            else -> {
                Log.w(TAG, "Property type '$type' doesn't match any category, defaulting to singleRooms")
                "singleRooms"
            }
        }
        return category
    }

    private fun refreshPropertiesInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val propertiesSnapshot = db.collection("properties").limit(100L).get().await()
                val favoritesSnapshot = db.collection("users")
                    .document(auth.currentUser?.uid ?: "")
                    .collection("favorites")
                    .get().await()

                val favoriteIds = favoritesSnapshot.documents.map { it.id }.toSet()

                val properties = withContext(Dispatchers.Default) {
                    val fetched = propertiesSnapshot.documents.size
                    Log.d(TAG, "[BG Refresh] Fetched $fetched properties")
                    
                    val filtered = propertiesSnapshot.documents.mapNotNull { doc ->
                        try {
                            Property.fromDocument(doc.data ?: emptyMap()).apply {
                                id = doc.id
                                isFavorite = favoriteIds.contains(id)
                            }.let { prop ->
                                val shouldShow = prop.shouldShowOnDashboard()
                                if (!shouldShow) {
                                    Log.d(TAG, "[BG Refresh] Filtered: ${prop.id} - status=${prop.status}, available=${prop.available}")
                                }
                                if (shouldShow) prop else null
                            }
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.createdAt.time }
                    
                    Log.d(TAG, "[BG Refresh] After filtering: ${filtered.size} properties")
                    filtered
                }

                withContext(Dispatchers.Main) {
                    // Only update if we actually got data to avoid "disappearing" on network blip
                    if (properties.isNotEmpty()) {
                        processCachedProperties(properties)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Background refresh skipped: ${e.message}")
            }
        }
    }

    private fun updateUserUI(userData: UserData) {
        binding.tvStudentName.text = userData.name
        binding.ivVerifiedBadge.visibility = if (userData.isVerified) View.VISIBLE else View.GONE
        
        // Proactive Caching: Save to SessionManager for next "Instant Load"
        sessionManager.saveCachedUserProfile(
            userData.name,
            ProfilePictureUtils.getInitials(userData.name),
            userData.profileUrl ?: ""
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        binding.ivProfile.loadProfileImage(userId, userData.profileUrl)
    }

    private fun updateUIWithProperties(categorizedProperties: Map<String, List<Property>>) {
        val total = categorizedProperties.values.sumOf { it.size }
        
        // Update adapters
        propertyAdapters["bedsitters"]?.submitList(categorizedProperties["bedsitters"] ?: emptyList())
        propertyAdapters["oneBedroom"]?.submitList(categorizedProperties["oneBedroom"] ?: emptyList())
        propertyAdapters["twoBedrooms"]?.submitList(categorizedProperties["twoBedrooms"] ?: emptyList())
        propertyAdapters["singleRooms"]?.submitList(categorizedProperties["singleRooms"] ?: emptyList())
        propertyAdapters["sharedProperties"]?.submitList(categorizedProperties["sharedProperties"] ?: emptyList())

        // Update category counts and section visibility gracefully
        val categories = listOf(
            Category("bedsitters", "Bedsitters", R.drawable.hs, categorizedProperties["bedsitters"]?.size ?: 0),
            Category("oneBedroom", "1 Bedroom", R.drawable.hs1, categorizedProperties["oneBedroom"]?.size ?: 0),
            Category("twoBedrooms", "2 Bedroom", R.drawable.hs2, categorizedProperties["twoBedrooms"]?.size ?: 0),
            Category("single", "Single Rooms", R.drawable.hs3, categorizedProperties["singleRooms"]?.size ?: 0),
            Category("shared", "Shared Properties", R.drawable.hs4, categorizedProperties["sharedProperties"]?.size ?: 0)
        )

        val sectionViews = listOf(
            binding.bedsittersSection, binding.oneBedroomSection,
            binding.twoBedroomsSection, binding.singleRoomsSection, binding.sharedHousesSection
        )

        // Minimize layout jumps: Only change visibility if necessary
        categories.zip(sectionViews).forEach { (cat, view) ->
            val shouldBeVisible = cat.count > 0
            if (view.visibility == View.VISIBLE && !shouldBeVisible) {
                // If it was visible but now empty, maybe it's just a refresh. 
                // We keep it visible for a split second or just let it stay if it was already visible.
                // For a cleaner look, we only hide if total is confirmed zero.
                if (total == 0) view.visibility = View.GONE
            } else if (shouldBeVisible) {
                view.visibility = View.VISIBLE
            }
        }

        categoryAdapter.updateCategories(categories)

        // Update empty state
        binding.emptyStateLayout.visibility = if (total == 0) View.VISIBLE else View.GONE
        if (total == 0) {
            binding.tvEmptyState.text = "No properties found\nCaretakers will start adding properties soon"
        }
    }

    private fun showEmptyState(message: String) {
        binding.tvEmptyState.text = message
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.bedsittersSection.visibility = View.GONE
        binding.oneBedroomSection.visibility = View.GONE
        binding.twoBedroomsSection.visibility = View.GONE
        binding.singleRoomsSection.visibility = View.GONE
        binding.sharedHousesSection.visibility = View.GONE
    }

    private fun setupSearch() {
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchEditText.text.toString().trim())
                true
            } else false
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                val query = s.toString().trim()
                searchRunnable = Runnable {
                    if (query.isNotEmpty() && query.length >= 2) {
                        performSearch(query)
                    } else if (query.isEmpty()) {
                        clearFilters()
                    }
                }
                searchRunnable?.let { searchHandler.postDelayed(it, DEBOUNCE_DELAY) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun performSearch(query: String) {
        currentFilterData.location = query
        isFiltering = true
        updateFilterIcon()
        
        // Use cached data immediately for instant filtering
        val cachedProperties = GlobalDataCache.getProperties()
        
        if (cachedProperties.isNotEmpty()) {
            processCachedProperties(cachedProperties)
        } else {
            loadProperties(forceRefresh = true, showLoading = true)
        }
    }

    private fun clearFilters() {
        currentFilterData.clear()
        binding.searchEditText.text?.clear()
        isFiltering = false
        updateFilterIcon()
        
        // Reapply unfiltered view from cache
        val cachedProperties = GlobalDataCache.getProperties()
        
        if (cachedProperties.isNotEmpty()) {
            processCachedProperties(cachedProperties)
        } else {
            loadProperties()
        }
    }

    private fun updateFilterIcon() {
        val color = ContextCompat.getColor(this, R.color.primary_dark)
        binding.filterIcon.setColorFilter(color)
        binding.searchEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (isFiltering) R.drawable.ic_close else 0, 0)
    }

    private fun setupClickListeners() {
        binding.btnNotificationHeader.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        binding.ivProfile.setOnClickListener { navigateToProfile() }
        binding.filterIcon.setOnClickListener { openFilterDialog() }

        binding.navAddProperty.setOnClickListener {
            clearFilters()
            selectTabUI(R.id.navAddProperty)
        }

        val messagesClickListener = View.OnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
            selectTabUI(R.id.navApplication)
        }

        binding.navApplication.setOnClickListener(messagesClickListener)
        binding.ivNavSaved.setOnClickListener(messagesClickListener)
        binding.tvNavSaved.setOnClickListener(messagesClickListener)

        binding.navSearch.setOnClickListener {
            navigateToWaterSuppliers()
            selectTabUI(R.id.navSearch)
        }
        binding.navMore.setOnClickListener { openMoreMenu() }


        setupSectionViewAllButtons()
    }

    private fun setupSectionViewAllButtons() {
        val sections = listOf(
            binding.bedsittersSection to "Bedsitters",
            binding.oneBedroomSection to "1 Bedroom",
            binding.twoBedroomsSection to "2 Bedroom",
            binding.singleRoomsSection to "Single Rooms",
            binding.sharedHousesSection to "Shared Properties"
        )

        sections.forEach { (section, title) ->
            section.findViewById<android.widget.TextView>(R.id.sectionTitle)?.text = title
            section.findViewById<View>(R.id.tvViewAll)?.setOnClickListener {
                val list = when (title) {
                    "Bedsitters" -> propertiesFlow.value["bedsitters"] ?: emptyList()
                    "1 Bedroom" -> propertiesFlow.value["oneBedroom"] ?: emptyList()
                    "2 Bedroom" -> propertiesFlow.value["twoBedrooms"] ?: emptyList()
                    "Single Rooms" -> propertiesFlow.value["singleRooms"] ?: emptyList()
                    "Shared Properties" -> propertiesFlow.value["sharedProperties"] ?: emptyList()
                    else -> emptyList()
                }
                openAllPropertiesActivity(title, list)
            }
        }
    }

    private fun selectTabUI(tabId: Int) {
        val isHome = tabId == R.id.navAddProperty
        binding.ivNavAddProperty.setColorFilter(ContextCompat.getColor(this, if (isHome) R.color.primary else R.color.textSecondary))
        binding.tvNavAddProperty.setTextColor(ContextCompat.getColor(this, if (isHome) R.color.primary else R.color.textSecondary))
        binding.tvNavAddProperty.typeface = if (isHome) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

        val isSaved = tabId == R.id.navApplication
        binding.ivNavSaved.setColorFilter(ContextCompat.getColor(this, if (isSaved) R.color.primary else R.color.textSecondary))
        binding.tvNavSaved.setTextColor(ContextCompat.getColor(this, if (isSaved) R.color.primary else R.color.textSecondary))

        val isSearch = tabId == R.id.navSearch
        binding.ivNavSearch.setColorFilter(ContextCompat.getColor(this, if (isSearch) R.color.primary else R.color.textSecondary))
        binding.tvNavSearch.setTextColor(ContextCompat.getColor(this, if (isSearch) R.color.primary else R.color.textSecondary))

        val isMore = tabId == R.id.navMore
        binding.ivNavProfile.setColorFilter(ContextCompat.getColor(this, if (isMore) R.color.primary else R.color.textSecondary))
        binding.tvNavProfile.setTextColor(ContextCompat.getColor(this, if (isMore) R.color.primary else R.color.textSecondary))
    }

    private fun navigateToProfile() {
        val userId = auth.currentUser?.uid ?: return
        startActivity(Intent(this, StudentProfileActivity::class.java).apply {
            putExtra("USER_ID", userId)
        })
    }

    private fun openFilterDialog() {
        filterLauncher.launch(Intent(this, FilterActivity::class.java).apply {
            putExtra("filter_data", currentFilterData)
        })
    }

    private fun navigateToFavorites() {
        startActivity(Intent(this, FavoritesActivity::class.java))
    }

    private fun navigateToWaterSuppliers() {
        com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
            startActivity(Intent(this, WaterSuppliersActivity::class.java))
        }
    }

    private fun navigateToMyBookings() {
        com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }
    }

    private fun navigateToMyRoom() {
        com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
            startActivity(Intent(this, StudentMyRoomActivity::class.java))
        }
    }

    private fun openMoreMenu() {
        val options = arrayOf("Profile", "My Bookings", "Help & Support", "Settings", "Logout")
        MaterialAlertDialogBuilder(this)
            .setTitle("More Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> navigateToProfile()
                    1 -> navigateToMyBookings()
                    4 -> showLogoutConfirmationDialog()
                }
            }
            .show()
    }

    private fun openAllPropertiesActivity(title: String, properties: List<Property>) {
        if (properties.isEmpty()) return
        PropertyDataHolder.setPropertyList(properties)
        startActivity(Intent(this, AllPropertiesActivity::class.java).apply {
            putExtra("SECTION_TITLE", title)
        })
    }

    private fun openPropertyDetails(property: Property, imageView: android.widget.ImageView?) {
        PropertyDataHolder.selectedProperty = property
        val intent = Intent(this, PropertyDetailsActivity::class.java).apply {
            putExtra("PROPERTY_ID", property.id)
            putExtra("EXTRA_TITLE", property.displayTitle)
            putExtra("EXTRA_LOCATION", property.location)
            putExtra("EXTRA_PRICE", property.getFormattedPrice())
            // No putExtra for the full Property to avoid TransactionTooLargeException
        }

        if (imageView != null) {
            startActivity(intent, androidx.core.app.ActivityOptionsCompat
                .makeSceneTransitionAnimation(this, imageView, "property_image_transition")
                .toBundle())
        } else {
            startActivity(intent)
        }
    }

    private suspend fun updateFavorite(propertyId: String, isFavorite: Boolean) {
        InteractionManager.toggleLike(propertyId, isFavorite) { success ->
            if (success) {
                // Background refresh will update the UI eventually, or we could force a local state update
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE).edit().clear().apply()
                sessionManager.clearSession()
                redirectToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
            .apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
            }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, UserLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun scrollToCategory(category: Category) {
        val targetView = when (category.name) {
            "Bedsitters" -> binding.bedsittersSection
            "1 Bedroom" -> binding.oneBedroomSection
            "2 Bedroom" -> binding.twoBedroomsSection
            "Single Rooms" -> binding.singleRoomsSection
            "Shared Properties" -> binding.sharedHousesSection
            else -> null
        }

        targetView?.let {
            (binding.swipeRefresh.getChildAt(0) as? androidx.core.widget.NestedScrollView)?.smoothScrollTo(0, it.top)
        }
    }

    override fun onBackPressed() {
        val adminSessionManager = AdminSessionManager(this)
        if (adminSessionManager.isAdminLoggedIn()) {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
            finish()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit HomeHub?")
                .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                .setNegativeButton("Cancel", null)
                .show()
                .apply {
                    getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
                }
        }
    }

    private fun setupNotificationBadge(userId: String) {
        notificationListener?.remove()
        notificationListener = com.example.homehub.utils.NotificationManager.listenToUnreadCount(userId) { count ->
            binding.notificationBadge.text = count.toString()
            binding.notificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private fun setupMessageBadge(userId: String) {
        chatRoomsListener?.remove()
        chatRoomsListener = ChatManager.getChatRooms { chatRooms ->
            val unreadTotal = chatRooms.sumOf { it.getUnreadCount(userId) }
            binding.roomBadgeText.text = if (unreadTotal > 9) "9+" else unreadTotal.toString()
            binding.roomBadgeText.visibility = if (unreadTotal > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchHandler.removeCallbacksAndMessages(null)
        searchJob?.cancel()
        userListener?.remove()
        notificationListener?.remove()
        chatRoomsListener?.remove()
    }


    data class UserData(
        val name: String,
        val profileUrl: String?,
        val isVerified: Boolean
    )
}
