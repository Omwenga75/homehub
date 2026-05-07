package com.example.homehub.property

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.homehub.utils.AmenitiesManager
import com.example.homehub.utils.InteractionManager
import com.example.homehub.chat.ChatActivity
import com.example.homehub.chat.ChatManager
import com.example.homehub.property.ImageSliderAdapter
import com.example.homehub.property.FeaturesAdapter
import com.example.homehub.property.Property
import com.example.homehub.R

import com.example.homehub.property.RoomType
import com.example.homehub.property.RoomTypeSelectionAdapter
import com.example.homehub.utils.UsernameFormatter
import com.example.homehub.billing.Booking
import com.example.homehub.billing.BookingCleanupManager
import com.example.homehub.billing.PaymentDetailsActivity
import com.example.homehub.databinding.ActivityPropertyDetailsBinding
import com.bumptech.glide.Glide
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.animation.ValueAnimator
import android.animation.Animator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.viewpager2.widget.ViewPager2

class PropertyDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPropertyDetailsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var propertyId: String = ""
    private var property: Property? = null
    private var isLiked = false
    private var caretakerPhoneNumber: String = ""
    private var caretakerWhatsAppNumber: String = ""
    private var propertyListener: ListenerRegistration? = null
    private var currentAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPropertyDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Standard Emerald Green status bar for consistency
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        propertyId = intent.getStringExtra("PROPERTY_ID") ?: intent.getStringExtra("HOUSE_ID") ?: intent.getStringExtra("id") ?: ""
        
        // Try to get Property object from static holder FIRST
        property = PropertyDataHolder.selectedProperty ?: intent.getParcelableExtra<Property>("PROPERTY")
            
        if (property != null) {
            propertyId = property!!.id
            updateUI(property!!)
        }

        if (propertyId.isEmpty()) { finish(); return }

        setupUI()
        populateInstantUI()
        loadPropertyData()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { onBackPressed() }
        binding.likeButton.setOnClickListener { toggleLike() }
        binding.reviewsSection.setOnClickListener { showReviews() }
        binding.btnAddToCart.setOnClickListener { navigateToPayment() }
        binding.btnMessageHost.setOnClickListener { startChatWithHost() }
        binding.btnCallHost.setOnClickListener {
            if (caretakerPhoneNumber.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$caretakerPhoneNumber")))
            } else {
                Toast.makeText(this, "Caretaker number not available", Toast.LENGTH_SHORT).show()
            }
        }
        binding.imageViewPager.alpha = 0f
        checkExistingBooking()
    }

    /**
     * Checks if the current user already has an active room booking.
     * If so, disables the "Book Now" button to prevent double-booking.
     */
    private fun checkExistingBooking() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("bookings")
            .whereEqualTo("studentId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val hasActiveBooking = snapshot.documents.any { doc ->
                    val data = doc.data?.toMutableMap() ?: return@any false
                    data["id"] = doc.id
                    val booking = com.example.homehub.billing.Booking.fromDocument(data)
                    booking.isValidForMyRoom()
                }

                if (hasActiveBooking) {
                    binding.btnAddToCart.isEnabled = false
                    binding.btnAddToCart.text = "Restricted"
                    binding.btnAddToCart.alpha = 0.6f
                }
            }
    }

    private fun populateInstantUI() {
        val title = intent.getStringExtra("EXTRA_TITLE") ?: return
        binding.propertyTitle.text = title
        binding.propertyLocation.text = intent.getStringExtra("EXTRA_LOCATION")
        val price = intent.getStringExtra("EXTRA_PRICE")
        binding.priceTag.text = "$price / month"
        binding.bottomPrice.text = price
        binding.bottomPeriod.text = "per month"
        
        binding.nestedScrollView.visibility = View.VISIBLE
        binding.bottomActionBar.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        intent.getStringExtra("EXTRA_IMAGE_URL")?.let {
            Glide.with(this).load(it).into(binding.ivTransitionImage)
        }
    }

    private fun loadPropertyData() {
        val collection = if (intent.hasExtra("HOUSE_ID")) "houses" else "properties"
        propertyListener = db.collection(collection).document(propertyId)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null || !doc.exists()) {
                    if (property == null) showErrorState()
                    return@addSnapshotListener
                }
                
                val data = doc.data ?: emptyMap()
                val isHouse = collection == "houses"
                val property = if (isHouse) {
                    // Map house data to property
                    Property(
                        id = doc.id,
                        title = data["title"] as? String ?: "",
                        location = data["location"] as? String ?: "",
                        priceValue = (data["price"] as? Number)?.toDouble() ?: 0.0,
                        description = data["description"] as? String ?: "",
                        bedrooms = (data["bedrooms"] as? Number)?.toInt() ?: 0,
                        bathrooms = (data["bathrooms"] as? Number)?.toInt() ?: 0,
                        caretakerId = data["caretakerId"] as? String ?: "",
                        caretakerName = data["ownerName"] as? String ?: "",
                        caretakerProfilePicture = data["caretakerProfilePicture"] as? String ?: "",
                        caretakerVerified = data["caretakerVerified"] as? Boolean ?: false,
                        imageUrl = data["imageUrl"] as? String ?: "",
                        imageUrls = (data["imageUrls"] as? List<String>) ?: emptyList(),
                        firebaseImages = (data["firebaseImages"] as? List<String>) ?: emptyList(),
                        roomImages = (data["roomImages"] as? Map<String, List<String>>) ?: emptyMap(),
                        status = data["status"] as? String ?: "Active",
                        available = data["isAvailable"] as? Boolean ?: true,
                        // Add other mappings as needed
                    )
                } else {
                    Property.fromDocument(data).apply { id = doc.id }
                }
                
                val isFirstLoad = this.property == null
                this.property = property
                updateUI(property)
                if (isFirstLoad) {
                    InteractionManager.logView(property)
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(property: Property) {
        binding.propertyTitle.text = property.displayTitle
        binding.propertyLocation.text = property.location
        val price = property.getFormattedPrice()
        binding.priceTag.text = "$price / month"
        binding.bottomPrice.text = price
        binding.viewsCount.text = property.viewCount.toString()
        binding.likesCount.text = property.likeCount.toString()
        binding.viewsCountBottom.text = property.viewCount.toString()
        binding.likesCountBottom.text = property.likeCount.toString()
        binding.tvAmenity1.text = if (property.hasWifi()) "WiFi" else "No WiFi"
        binding.tvAmenity2.text = if (property.hasWater()) "Water" else "No Water"
        binding.areaText.text = property.getAvailableRoomsDisplay()
        binding.propertyDescription.text = property.description
        binding.ownerName.text = property.caretakerDisplayName
        binding.verifiedBadge.visibility = if (property.caretakerVerified) View.VISIBLE else View.GONE
        
        if (property.caretakerProfilePicture.isNotEmpty()) {
            Glide.with(this).load(property.caretakerProfilePicture).circleCrop().into(binding.ownerImage)
        }

        loadCaretakerDetails(property.caretakerId)
        loadPropertyImages(property)
        setupAmenities(property.getAllAmenities())
        setupPropertyRules(property.propertyRules)
        checkLikeStatus()
        setupMap(property)
    }

    private fun setupMap(property: Property) {
        binding.propertyMapCard.visibility = View.VISIBLE
        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        
        val cartoVoyager = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoVoyager",
            1, 20, 256, ".png", arrayOf(
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/"
            ), "© OpenStreetMap contributors, © CARTO"
        )
        binding.propertyMapView.setTileSource(cartoVoyager)
        binding.propertyMapView.setMultiTouchControls(false)
        
        // Fallback to Murang'a Center if coords are (0,0)
        val hasCoords = property.latitude != 0.0 && property.longitude != 0.0
        val point = if (hasCoords) {
            GeoPoint(property.latitude, property.longitude)
        } else {
            GeoPoint(-0.722, 37.151) // Murang'a Center
        }
        
        binding.propertyMapView.controller.setZoom(if (hasCoords) 16.5 else 13.0)
        binding.propertyMapView.controller.setCenter(point)
        
        if (hasCoords) {
            val marker = Marker(binding.propertyMapView)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = property.title
            
            // Set custom brand icon for the marker
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location)
            marker.icon?.setTint(ContextCompat.getColor(this, R.color.blue))
            
            binding.propertyMapView.overlays.clear()
            binding.propertyMapView.overlays.add(marker)
        }
        
        binding.propertyMapView.invalidate()
    }

    private fun loadCaretakerDetails(caretakerId: String) {
        if (caretakerId.isEmpty()) return

        val fetchFallback: () -> Unit = {
            db.collection("users").document(caretakerId).get().addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val userName = userDoc.getString("fullName") ?: userDoc.getString("name") ?: ""
                    if (userName.isNotBlank()) {
                        binding.ownerName.text = userName
                    }

                    if (caretakerPhoneNumber.isEmpty()) {
                        val phone = userDoc.getString("phone") ?: userDoc.getString("phoneNumber") ?: ""
                        if (phone.isNotEmpty()) {
                            caretakerPhoneNumber = phone
                            caretakerWhatsAppNumber = phone
                        }
                    }
                }
            }
        }

        db.collection("verifiedCaretakers").document(caretakerId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: ""
                if (phone.isNotEmpty()) {
                    caretakerPhoneNumber = phone
                    caretakerWhatsAppNumber = phone
                }

                // Update host name from verifiedCaretakers
                val realName = doc.getString("fullName") ?: doc.getString("name") ?: ""
                if (realName.isNotBlank()) {
                    binding.ownerName.text = realName
                } else {
                    fetchFallback()
                }
            } else {
                fetchFallback()
            }
        }.addOnFailureListener {
            fetchFallback()
        }

        db.collection("properties").whereEqualTo("caretakerId", caretakerId).get().addOnSuccessListener { snapshot ->
            val count = snapshot.size()
            binding.hostPropertiesCount.text = count.toString()
        }

        val hash = Math.abs(caretakerId.hashCode())
        val responseRate = 90 + (hash % 10)
        val responseTime = if (hash % 3 == 0) "< 1h" else if (hash % 2 == 0) "< 2h" else "< 5m"
        
        binding.hostResponseRate.text = "$responseRate%"
        binding.hostResponseTime.text = responseTime
        binding.ownerType.text = if (hash % 3 == 0) "Elite Partner" else "Property Manager"
    }

    private fun showPhoneNumberOptions() {
        MaterialAlertDialogBuilder(this).setTitle("Contact Caretaker")
            .setItems(arrayOf("Call")) { _, w ->
                if (w == 0) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$caretakerPhoneNumber")))
            }.show()
    }

    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var isScrollingForward = true

    private fun loadPropertyImages(property: Property) {
        val images = property.allImages
        if (images.isNotEmpty()) {
            val adapter = ImageSliderAdapter(images)
            binding.imageViewPager.adapter = adapter
            
            // Auto-scroll if multiple images
            if (images.size > 1) {
                startAutoScroll(images.size)
            }
            
            binding.imageViewPager.animate().alpha(1f).setDuration(300).start()
            binding.ivTransitionImage.animate().alpha(0f).setDuration(300).withEndAction { binding.ivTransitionImage.visibility = View.GONE }.start()
        }
    }

    private fun startAutoScroll(itemCount: Int) {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                val current = binding.imageViewPager.currentItem
                val next: Int
                
                if (isScrollingForward) {
                    if (current < itemCount - 1) {
                        next = current + 1
                    } else {
                        isScrollingForward = false
                        next = current - 1
                    }
                } else {
                    if (current > 0) {
                        next = current - 1
                    } else {
                        isScrollingForward = true
                        next = current + 1
                    }
                }
                
                // Slow transition: 1300ms glide instead of the default snappy jump
                binding.imageViewPager.setCurrentItemSlow(next, 1300)
                
                autoScrollHandler.postDelayed(this, 8000)
            }
        }
        autoScrollHandler.postDelayed(autoScrollRunnable!!, 8000)
    }

    /**
     * Custom extension to slow down ViewPager2 transition speed
     */
    private fun ViewPager2.setCurrentItemSlow(
        item: Int,
        duration: Long,
        interpolator: android.view.animation.Interpolator = AccelerateDecelerateInterpolator()
    ) {
        // Cancel any existing animation
        currentAnimator?.cancel()
        
        // Stop fake drag if already active
        if (this.isFakeDragging) {
            this.endFakeDrag()
        }
        
        val pxToDrag = width * (item - currentItem)
        val animator = ValueAnimator.ofInt(0, pxToDrag)
        currentAnimator = animator
        var previousValue = 0
        
        animator.addUpdateListener { valueAnimator ->
            // Check if activity is destroyed before updating
            if (this@PropertyDetailsActivity.isDestroyed) {
                animator.cancel()
                return@addUpdateListener
            }
            
            val currentValue = valueAnimator.animatedValue as Int
            val currentPxToDrag = (currentValue - previousValue).toFloat()
            try {
                if (this.isFakeDragging) {
                    fakeDragBy(-currentPxToDrag)
                }
            } catch (e: IllegalStateException) {
                // ViewPager2 may not be in fake drag state anymore
                animator.cancel()
            }
            previousValue = currentValue
        }
        
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                if (!this@PropertyDetailsActivity.isDestroyed) {
                    try {
                        beginFakeDrag()
                    } catch (e: Exception) {
                        animation.cancel()
                    }
                }
            }
            override fun onAnimationEnd(animation: Animator) {
                if (!this@PropertyDetailsActivity.isDestroyed && isFakeDragging) {
                    try {
                        endFakeDrag()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            override fun onAnimationCancel(animation: Animator) {
                if (!this@PropertyDetailsActivity.isDestroyed && isFakeDragging) {
                    try {
                        endFakeDrag()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            override fun onAnimationRepeat(animation: Animator) {}
        })
        
        animator.interpolator = interpolator
        animator.duration = duration
        animator.start()
    }


    private fun setupAmenities(amenities: List<String>) {
        binding.featuresRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.featuresRecyclerView.adapter = FeaturesAdapter(AmenitiesManager.getFeaturesForDisplay(amenities, this))
    }

    private fun setupPropertyRules(rules: List<String>) {
        if (rules.isEmpty()) {
            // Don't show the card if there are no rules
            return
        }

        val rulesCardLayout = LayoutInflater.from(this).inflate(R.layout.card_property_rules, binding.detailsContainer, false)
        val rulesRecyclerView = rulesCardLayout.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rulesRecyclerView)
        val emptyState = rulesCardLayout.findViewById<LinearLayout>(R.id.emptyRulesState)

        if (rules.isNotEmpty()) {
            rulesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            rulesRecyclerView.adapter = PropertyRulesAdapter(rules)
            emptyState.visibility = View.GONE
        } else {
            emptyState.visibility = View.VISIBLE
        }

        // Insert the rules card after the featuresRecyclerView
        binding.detailsContainer.addView(rulesCardLayout)
    }

    private fun checkLikeStatus() {
        val user = auth.currentUser ?: return
        val likeDocId = "${user.uid}_$propertyId"
        db.collection("uniqueLikes").document(likeDocId).get().addOnSuccessListener {
            isLiked = it.exists()
            updateLikeButtonUI()
        }
    }

    private fun toggleLike() {
        if (propertyId.isEmpty()) return
        
        val targetState = !isLiked
        InteractionManager.toggleLike(propertyId, targetState) { success ->
            if (success) {
                isLiked = targetState
                runOnUiThread { updateLikeButtonUI() }
            }
        }
    }

    private fun updateLikeButtonUI() {
        binding.likeButton.setImageResource(R.drawable.baseline_thumb_up_24)
        binding.likeButton.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isLiked) resources.getColor(R.color.blue) else resources.getColor(R.color.white)
        )
    }

    private fun showReviews() {
        val intent = Intent(this, ReviewsActivity::class.java)
        intent.putExtra("PROPERTY_ID", propertyId)
        startActivity(intent)
    }
    
    private fun navigateToPayment() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to book", Toast.LENGTH_SHORT).show()
            return
        }
        
        // PROACTIVE CLEANUP: Check for any expired reservations before proceeding
        db.collection("bookings")
            .whereEqualTo("studentId", user.uid)
            .whereEqualTo("paymentStatus", "pending_deferred")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val booking = Booking.fromDocument(doc.data ?: emptyMap()).apply { id = doc.id }
                    BookingCleanupManager.checkAndCancelIfExpired(booking)
                }
                
                // Allow proceeding to payment
                com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                    val propertyToPass = property
                    if (propertyToPass != null) {
                        val intent = Intent(this, PaymentDetailsActivity::class.java)
                        intent.putExtra("PROPERTY_ID", propertyToPass.id)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Property details not ready", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                // Proceded anyway if check fails
                com.example.homehub.utils.VerificationGuard.checkAndExecute(this) {
                    val propertyToPass = property
                    if (propertyToPass != null) {
                        val intent = Intent(this, PaymentDetailsActivity::class.java)
                        intent.putExtra("PROPERTY_ID", propertyToPass.id)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Property details not ready", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun logUniqueView() {
        val prop = property ?: return
        if (prop.id.isNotEmpty()) {
            InteractionManager.logView(prop)
        }
    }

    private fun startChatWithHost() {
        val prop = property ?: return
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to chat", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnMessageHost.isEnabled = false
        ChatManager.createChatRoom(prop) { roomId ->
            if (roomId != null) {
                ChatManager.getChatRoom(roomId) { chatRoom ->
                    binding.btnMessageHost.isEnabled = true
                    if (chatRoom != null) {
                        val chatIntent = Intent(this, ChatActivity::class.java)
                        chatIntent.putExtra("CHAT_ROOM", chatRoom)
                        startActivity(chatIntent)
                    } else {
                        Toast.makeText(this, "Failed to load chat", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.btnMessageHost.isEnabled = true
                Toast.makeText(this, "Failed to start chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showErrorState() { Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show() }
    
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) binding.propertyMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        currentAnimator?.cancel()
        if (::binding.isInitialized) binding.propertyMapView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel animation and cleanup fake drag
        currentAnimator?.cancel()
        currentAnimator = null
        
        // Stop fake drag if it's active
        try {
            if (binding.imageViewPager.isFakeDragging) {
                binding.imageViewPager.endFakeDrag()
            }
        } catch (e: Exception) {
            // Ignore exceptions during cleanup
        }
        
        // Remove auto-scroll callbacks
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        
        // Clean up listeners
        propertyListener?.remove()
    }
}
