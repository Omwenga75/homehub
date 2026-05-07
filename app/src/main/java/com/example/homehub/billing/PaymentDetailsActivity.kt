package com.example.homehub.billing

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
// No imports needed for Booking, MpesaService, etc. as they are in the same package.
import com.example.homehub.billing.MyBookingsActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

import com.example.homehub.R
import com.example.homehub.property.Property
import com.example.homehub.property.RoomType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers

class PaymentDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PaymentDetails"
        const val PAYMENT_TYPE_ROOM = "ROOM"
    }

    private var paymentType = PAYMENT_TYPE_ROOM

    private lateinit var property: Property
    private var isPolling = false
    private var isPaymentConfirmed = false
    private var selectedRoomType: RoomType? = null
    private var selectedRoomNumber: String? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var propertyImage: ImageView
    private lateinit var propertyTitle: TextView
    private lateinit var propertyLocation: TextView
    private lateinit var propertyCaretaker: TextView
    private lateinit var rentAmount: TextView
    private lateinit var rentDuration: TextView
    private lateinit var totalAmount: TextView
    private lateinit var phoneInput: TextInputEditText
    private lateinit var phoneInputStep2: TextInputEditText
    private lateinit var phoneInputCardStep2: MaterialCardView
    private lateinit var roomInput: android.widget.Spinner
    private lateinit var payButton: MaterialButton
    private lateinit var stepAnimator: ViewAnimator
    private lateinit var mainScrollView: androidx.core.widget.NestedScrollView
    private lateinit var studentDiscountRow: LinearLayout
    private lateinit var studentDiscountAmount: TextView
    private lateinit var pollingOverlay: FrameLayout
    private lateinit var pollingStatusText: TextView
    private lateinit var btnCancelPolling: MaterialButton

    private var checkoutRequestId: String? = null
    private var isSimulated = false
    private var finalAmount: Double = 0.0
    private var depositAmount: Double = 0.0
    private var isFirstTimeBooking: Boolean = true
    private var selectedPaymentOption: String = "instant" // instant, 24h, 3d
    private var existingBookingId: String? = null
    private var studentName: String = "Student"

    private lateinit var paymentTimingGroup: com.google.android.material.chip.ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_details)

        window.statusBarColor = resources.getColor(R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.navigationBarColor = resources.getColor(R.color.background)

        // Get data from intent
        paymentType = intent.getStringExtra("PAYMENT_TYPE") ?: PAYMENT_TYPE_ROOM
        val propertyFromIntent = intent.getParcelableExtra<Property>("PROPERTY")
        val propertyId = intent.getStringExtra("PROPERTY_ID")

        if (propertyFromIntent != null) {
            property = propertyFromIntent
            selectedRoomType = intent.getParcelableExtra("SELECTED_ROOM_TYPE")
            selectedRoomNumber = intent.getStringExtra("SELECTED_ROOM_NUMBER")
            initializeViews()
            populatePropertyInfo()
        } else if (propertyId != null) {
            selectedRoomType = intent.getParcelableExtra("SELECTED_ROOM_TYPE")
            selectedRoomNumber = intent.getStringExtra("SELECTED_ROOM_NUMBER")
            initializeViews()
            loadPropertyFromFirestore(propertyId)
        } else {
            Toast.makeText(this, "No payment context received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        existingBookingId = intent.getStringExtra("EXISTING_BOOKING_ID")
        if (existingBookingId != null) {
            // Re-entry mode logic: skip step 1
            stepAnimator.displayedChild = 1
            mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
            updateStepIndicators(2)
            findViewById<MaterialCardView>(R.id.phoneInputCardStep2).visibility = View.VISIBLE
            loadBookingDetails(existingBookingId!!)
        }

        fetchUserProfile()
    }

    private fun loadBookingDetails(bookingId: String) {
        db.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val booking = Booking.fromDocument(document.data ?: emptyMap())
                    selectedRoomNumber = booking.roomNumber
                    finalAmount = booking.amount
                    
                    // Mock a selectedRoomType object for display logic
                    if (booking.roomTypeName.isNotEmpty()) {
                        selectedRoomType = RoomType(
                            id = booking.roomTypeId,
                            name = booking.roomTypeName,
                            price = booking.amount,
                            imageUrl = booking.propertyImage
                        )
                    }
                    
                    runOnUiThread {
                        populatePropertyInfo()
                        // Ensure we use the exact amount from the booking for existing transactions
                        fetchRealCaretakerName(booking.caretakerId)
                        calculateFinalTotal()
                    }
                }
            }
    }

    private fun fetchUserProfile() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                    val name = document.getString("fullName") ?: document.getString("username") ?: currentUser.displayName
                    if (!name.isNullOrBlank()) {
                        studentName = name
                    }

                    val phone = document.getString("phone")
                    if (!phone.isNullOrEmpty()) {
                        // Strip +254 or 254 for display
                        var rawPhone = phone
                        if (rawPhone.startsWith("+254")) rawPhone = rawPhone.substring(4)
                        else if (rawPhone.startsWith("254")) rawPhone = rawPhone.substring(3)
                        
                        runOnUiThread {
                            phoneInput.setText(rawPhone)
                            phoneInputStep2.setText(rawPhone)
                        }
                }
            }
    }

    private fun initializeViews() {
        propertyImage = findViewById(R.id.propertyImage)
        propertyTitle = findViewById(R.id.propertyTitle)
        propertyLocation = findViewById(R.id.propertyLocation)
        propertyCaretaker = findViewById(R.id.propertyCaretaker)
        rentAmount = findViewById(R.id.rentAmount)
        rentDuration = findViewById(R.id.rentDuration)
        totalAmount = findViewById(R.id.totalAmount)
        
        // Step 1 IDs
        phoneInput = findViewById(R.id.phoneInputStep1)
        roomInput = findViewById(R.id.roomInputStep1)
        val btnNextToStep2 = findViewById<MaterialButton>(R.id.btnStep1Next)
        
        // Step 2 IDs
        phoneInputStep2 = findViewById(R.id.phoneInputStep2)
        phoneInputCardStep2 = findViewById(R.id.phoneInputCardStep2)
        payButton = findViewById(R.id.btnStep2Pay)
        val btnBackToStep1 = findViewById<MaterialButton>(R.id.btnStep2Back)
        
        setupPhoneSynchronizer()
        
        // Step 3 IDs
        val btnViewBookings = findViewById<MaterialButton>(R.id.btnViewBookings)
        
        stepAnimator = findViewById(R.id.stepAnimator)
        mainScrollView = findViewById(R.id.mainScrollView)
        studentDiscountRow = findViewById(R.id.studentDiscountRow)
        studentDiscountAmount = findViewById(R.id.studentDiscountAmount)
        
        pollingOverlay = findViewById(R.id.pollingOverlay)
        pollingStatusText = findViewById(R.id.pollingStatusText)
        btnCancelPolling = findViewById(R.id.btnCancelPolling)
        
        // Added for deposit tracking
        val totalPayableLabel = findViewById<TextView>(R.id.totalPayableLabel)
        
        paymentTimingGroup = findViewById(R.id.paymentTimingGroup)
        
        // Set up next step listeners
        
        btnNextToStep2.setOnClickListener {
            if (validateStep1()) {
                stepAnimator.displayedChild = 1 // Move to Step 2
                mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
                updateStepIndicators(2)
            }
        }
        
        btnBackToStep1.setOnClickListener {
            stepAnimator.displayedChild = 0 // Back to Step 1
            mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
            updateStepIndicators(1)
        }

        paymentTimingGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPaymentOption = when (checkedId) {
                R.id.chip24h -> "24h"
                R.id.chip48h -> "48h"
                else -> "instant"
            }
            calculateFinalTotal() // Refresh button text
        }

        payButton.setOnClickListener {
            initiatePayment()
        }
        
        btnViewBookings.setOnClickListener {
                startActivity(Intent(this, MyBookingsActivity::class.java))
            finish()
        }

        btnCancelPolling.setOnClickListener {
            isPolling = false
            pollingOverlay.visibility = View.GONE
        }

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupPhoneSynchronizer() {
        val watcherStep1 = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (phoneInputStep2.text.toString() != s.toString()) {
                    phoneInputStep2.setText(s.toString())
                }
            }
        }
        val watcherStep2 = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (phoneInput.text.toString() != s.toString()) {
                    phoneInput.setText(s.toString())
                }
            }
        }
        phoneInput.addTextChangedListener(watcherStep1)
        phoneInputStep2.addTextChangedListener(watcherStep2)
    }

    private fun validateStep1(): Boolean {
        val phone = phoneInput.text.toString().trim()
        if (phone.isEmpty()) {
            phoneInput.error = "Phone number required"
            return false
        }
        return true
    }

    private fun updateStepIndicators(step: Int) {
        val step1Circle = findViewById<FrameLayout>(R.id.step1Circle)
        val step2Circle = findViewById<FrameLayout>(R.id.step2Circle)
        val step3Circle = findViewById<FrameLayout>(R.id.step3Circle)
        val line1 = findViewById<View>(R.id.line1)
        val line2 = findViewById<View>(R.id.line2)
        
        val activeColor = resources.getColor(R.color.themeColor)
        val inactiveColor = resources.getColor(R.color.light_gray) // Fallback if not defined

        when (step) {
            1 -> {
                step1Circle.setBackgroundResource(R.drawable.bg_step_indicator_active)
                step2Circle.setBackgroundResource(R.drawable.bg_step_indicator_inactive)
                line1.setBackgroundColor(resources.getColor(R.color.divider))
            }
            2 -> {
                step1Circle.setBackgroundResource(R.drawable.bg_step_indicator_active)
                findViewById<ImageView>(R.id.step1Check).visibility = View.VISIBLE
                findViewById<TextView>(R.id.step1Num).visibility = View.GONE
                step2Circle.setBackgroundResource(R.drawable.bg_step_indicator_active)
                line1.setBackgroundColor(activeColor)
            }
            3 -> {
                step2Circle.setBackgroundResource(R.drawable.bg_step_indicator_active)
                findViewById<ImageView>(R.id.step2Check).visibility = View.VISIBLE
                findViewById<TextView>(R.id.step2Num).visibility = View.GONE
                step3Circle.setBackgroundResource(R.drawable.bg_step_indicator_active)
                line2.setBackgroundColor(activeColor)
            }
        }
    }

    private fun loadPropertyFromFirestore(propertyId: String) {
        db.collection("properties").document(propertyId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = document.id
                    property = Property.fromDocument(data)
                    populatePropertyInfo()
                } else {
                    Toast.makeText(this, "Property not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading property: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populatePropertyInfo() {
        // Prioritize Selected Room image, then main property image
        val roomImageUrl = selectedRoomType?.imageUrl
        val propertyImageUrl = property.getFirstImagePath()
        val finalImageUrl = if (!roomImageUrl.isNullOrBlank()) roomImageUrl else propertyImageUrl

        if (!finalImageUrl.isNullOrBlank()) {
            if (!isFinishing && !isDestroyed) {
                com.bumptech.glide.Glide.with(this)
                    .load(finalImageUrl)
                    .placeholder(R.drawable.ic_house_placeholder)
                    .error(R.drawable.ic_house_placeholder)
                    .centerCrop()
                    .into(propertyImage)
            }
        }

        val price = selectedRoomType?.price ?: property.priceValue
        val formattedPrice = "KSh ${String.format("%,.0f", price)}"
        
        if (selectedRoomNumber != null) {
            propertyTitle.text = "${property.displayTitle} - Room $selectedRoomNumber"
            rentAmount.text = formattedPrice
        } else if (selectedRoomType != null) {
            propertyTitle.text = "${property.displayTitle} - ${selectedRoomType!!.name}"
            rentAmount.text = formattedPrice
        } else {
            propertyTitle.text = property.displayTitle
            rentAmount.text = formattedPrice
        }
        
        // Use calculateFinalTotal() instead of manual updates here to prevent flickering
        calculateFinalTotal()
        
        // Initialize deposit from property details
        depositAmount = if (property.deposit > 0) property.deposit else property.securityDeposit
        
        propertyLocation.text = "📍 ${property.location}"
        propertyCaretaker.text = "🏠 Caretaker: Loading..."
        fetchRealCaretakerName(property.caretakerId)
        rentDuration.text = property.getRentalPeriod()
        
        // Populate Room Spinner (Sorted numerically and filtered for availability)
        val availableRooms = property.getAvailableSortedRoomNumbers()
        val spinnerItems = mutableListOf<String>()
        if (availableRooms.isNotEmpty()) {
            spinnerItems.add("Select a Room")
            spinnerItems.addAll(availableRooms)
        } else {
            spinnerItems.add("Any Room")
        }
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roomInput.adapter = adapter

        checkIfFirstTimeBooking()
    }
    
    private fun fetchRealCaretakerName(caretakerId: String) {
        if (caretakerId.isEmpty()) return
        db.collection("users").document(caretakerId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val realName = document.getString("name") ?: document.getString("fullName")
                    if (!realName.isNullOrEmpty()) {
                        runOnUiThread {
                            propertyCaretaker.text = "🏠 Caretaker: $realName"
                        }
                    }
                }
            }
    }

    private fun checkIfFirstTimeBooking() {
        val currentUser = auth.currentUser ?: return
        // Disable pay button while checking constraints
        payButton.isEnabled = false
        payButton.text = "Checking constraints..."

        // Query 1: Check if user has ANY booking in history to determine first-time deposit
        db.collection("bookings")
            .whereEqualTo("studentId", currentUser.uid)
            .whereEqualTo("paymentStatus", "paid")
            .get()
            .addOnSuccessListener { snapshot ->
                isFirstTimeBooking = snapshot.isEmpty
                
                // Query 2: Check for ANY active/confirmed booking to prevent double booking
                db.collection("bookings")
                    .whereEqualTo("studentId", currentUser.uid)
                    .whereIn("status", listOf("confirmed", "active", "pending"))
                    .get()
                    .addOnSuccessListener { activeSnapshot ->
                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                        val hasActive = !activeSnapshot.isEmpty
                        if (hasActive) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) showDoubleBookingWarning()
                            }
                        } else {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    calculateFinalTotal()
                                    checkStudentVerification()
                                    payButton.isEnabled = true
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        calculateFinalTotal()
                        payButton.isEnabled = true
                    }
            }
            .addOnFailureListener {
                calculateFinalTotal()
                payButton.isEnabled = true
            }
    }

    private fun showDoubleBookingWarning() {
        if (isFinishing || isDestroyed) return
        
        payButton.isEnabled = false
        payButton.text = "Active Rental Found"
        payButton.setBackgroundColor(resources.getColor(R.color.error))
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Rental Restriction")
            .setMessage("You cannot rent another room until your current stay is cleared by the landlord.")
            .setPositiveButton("View My Rooms") { _, _ ->
                startActivity(Intent(this, MyBookingsActivity::class.java))
                finish()
            }
            .setNegativeButton("Back") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun calculateFinalTotal() {
        val rent = selectedRoomType?.price ?: property.priceValue
        val baseAmount = if (isFirstTimeBooking) rent + depositAmount else rent
        finalAmount = baseAmount
        
        val formattedRent = "KSh ${String.format("%,.0f", rent)}"
        val formattedTotal = "KSh ${String.format("%,.0f", finalAmount)}"
        val formattedDeposit = "KSh ${String.format("%,.0f", depositAmount)}"
        
        rentAmount.text = formattedRent
        totalAmount.text = formattedTotal
        
        val label = if (isFirstTimeBooking && depositAmount > 0) "RENT + DEPOSIT ($formattedDeposit)" else "RENT ONLY"
        findViewById<TextView>(R.id.totalPayableLabel).text = label

        val displayPhone = phoneInput.text.toString().trim().let { raw ->
            if (raw.isEmpty()) "your number"
            else "+254 $raw"
        }

        if (selectedPaymentOption == "instant" || existingBookingId != null) {
            payButton.text = "PAY $formattedTotal ✓"
            findViewById<TextView>(R.id.stkPromptText).text = "An STK Push request will be sent to you. Enter your M-Pesa PIN to authorize."
        } else {
            val info = if (selectedPaymentOption == "24h") "24 Hours" else "48 Hours"
            payButton.text = "RESERVE ➔"
            findViewById<TextView>(R.id.stkPromptText).text = "Your booking will be reserved for $info . You must complete payment within this window to check in."
        }
    }

    private fun checkStudentVerification() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val isVerified = document.getBoolean("isStudentVerified") ?: false
                if (isVerified) {
                    runOnUiThread { applyStudentDiscount() }
                }
            }
    }

    private fun applyStudentDiscount() {
        val rent = selectedRoomType?.price ?: property.priceValue
        val discount = rent * 0.10
        
        // Re-calculate with deposit if needed
        finalAmount = if (isFirstTimeBooking) {
            (rent - discount) + depositAmount
        } else {
            rent - discount
        }
 
        studentDiscountRow.visibility = View.VISIBLE
        studentDiscountAmount.text = "- KSh ${String.format("%,.0f", discount)}"
        
        val formattedFinal = "KSh ${String.format("%,.0f", finalAmount)}"
        totalAmount.text = formattedFinal
        payButton.text = "Pay $formattedFinal via M-Pesa"
        
        Toast.makeText(this, "Student Discount Applied!", Toast.LENGTH_SHORT).show()
    }

    private fun getButtonLabel(): String {
        return if (selectedPaymentOption == "instant" || existingBookingId != null) {
            "PAY KSh ${String.format("%,.0f", finalAmount)} ✓"
        } else {
            "RESERVE ➔"
        }
    }

    private fun initiatePayment() {
        // Disable button immediately to prevent double booking
        payButton.isEnabled = false
        payButton.text = "Processing..."

        // Use visible phone input
        val activePhoneInput = if (phoneInputCardStep2.visibility == View.VISIBLE) phoneInputStep2 else phoneInput
        var phone = activePhoneInput.text.toString().trim()
        
        val selectedItem = roomInput.selectedItem?.toString() ?: ""
        val typedRoom = if (selectedItem == "Select a Room" || selectedItem == "Any Room") "" else selectedItem
        if (typedRoom.isNotEmpty() && selectedRoomNumber.isNullOrEmpty()) {
            selectedRoomNumber = typedRoom
        }
        
        if (finalAmount == 0.0) finalAmount = property.priceValue

        if (phone.isEmpty()) {
            activePhoneInput.error = "Please enter your M-Pesa phone number"
            activePhoneInput.requestFocus()
            // Re-enable button on validation failure
            payButton.isEnabled = true
            payButton.text = getButtonLabel()
            return
        }

        // Auto-fix common phone formats
        if (phone.startsWith("0")) phone = "254" + phone.substring(1)
        if (!phone.startsWith("254")) phone = "254" + phone

        if (phone.length != 12) {
            phoneInput.error = "Please enter a valid phone number (254...)"
            // Re-enable button on validation failure
            payButton.isEnabled = true
            payButton.text = getButtonLabel()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to make a payment", Toast.LENGTH_SHORT).show()
            // Re-enable button on validation failure
            payButton.isEnabled = true
            payButton.text = getButtonLabel()
            return
        }

        checkPropertyAvailability { isAvailable ->
            if (isAvailable) {
                if (selectedPaymentOption == "instant" || existingBookingId != null) {
                    processMpesaPayment(phone)
                } else {
                    createDeferredBooking()
                }
            } else {
                runOnUiThread {
                    val msg = when {
                        selectedRoomType != null -> "This unit type is no longer available"
                        selectedRoomNumber != null -> "Room $selectedRoomNumber is no longer available"
                        else -> "This property has already been booked"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun checkPropertyAvailability(callback: (Boolean) -> Unit) {
        // If we are paying for an existing reservation, availability is already guaranteed
        if (existingBookingId != null) {
            callback(true)
            return
        }

        db.collection("properties").document(property.id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "Active"
                    val isRoomBooking = selectedRoomType != null || selectedRoomNumber != null
                    
                    if (status.equals("Inactive", ignoreCase = true)) {
                        callback(false)
                        return@addOnSuccessListener
                    }

                    // For whole-property booking, status must be Active
                    if (!isRoomBooking && !status.equals("Active", ignoreCase = true)) {
                        callback(false)
                        return@addOnSuccessListener
                    }

                    if (selectedRoomType != null) {
                        val roomTypesData = document.get("roomTypes") as? List<Map<String, Any>>
                        val matchingType = roomTypesData?.find { it["id"] == selectedRoomType!!.id }
                        val availableQty = getLongFromAny(matchingType?.get("availableQuantity"))
                        callback(availableQty > 0)
                    } else if (selectedRoomNumber != null) {
                        val statuses = document.get("roomStatuses") as? Map<String, String> ?: emptyMap()
                        val matchingKey = statuses.keys.find { it.equals(selectedRoomNumber, ignoreCase = true) }
                        if (matchingKey != null) {
                            selectedRoomNumber = matchingKey // update to exact case
                            callback(statuses[matchingKey]?.equals("Available", ignoreCase = true) == true)
                        } else {
                            val available = document.getBoolean("available") ?: true
                            callback(available)
                        }
                    } else {
                        val available = document.getBoolean("available") ?: true
                        callback(available)
                    }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    private fun getLongFromAny(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun countAvailableRooms(statuses: Map<String, String>): Int {
        return statuses.count { it.value.equals("Available", ignoreCase = true) }
    }

    private fun processMpesaPayment(phone: String) {
        // Show overlay
        pollingOverlay.visibility = View.VISIBLE
        pollingStatusText.text = "Connecting to M-Pesa..."
        
        lifecycleScope.launch {
            val amountInt = finalAmount.toInt()
            val accountRef = "HH-${property.id.take(8)}"
            val response = MpesaService.sendSTKPushAsync(phone, amountInt, accountRef)
            
            if (response != null && response.responseCode == "0") {
                pollingStatusText.text = "Waiting for PIN...\n📱 Please enter PIN on your phone"
                startPolling(response.checkoutRequestID)
            } else {
                pollingOverlay.visibility = View.GONE
                Toast.makeText(this@PaymentDetailsActivity, response?.customerMessage ?: "STK Push failed", Toast.LENGTH_LONG).show()
                // Re-enable button on STK push failure
                payButton.isEnabled = true
                payButton.text = getButtonLabel()
            }
        }
    }

    private fun startPolling(checkoutId: String) {
        checkoutRequestId = checkoutId
        isPolling = true
        lifecycleScope.launch {
            // Give M-Pesa a moment to process before first query
            delay(5000)

            var attempts = 0
            val maxAttempts = 60 // ~3 minutes total
            var consecutiveNulls = 0

            while (isPolling && attempts < maxAttempts) {
                attempts++
                
                // Update status text with heartbeat
                val elapsed = attempts * 3
                runOnUiThread {
                    val heartbeat = when (attempts % 3) {
                        0 -> "."
                        1 -> ".."
                        else -> "..."
                    }
                    pollingStatusText.text = "Confirming payment with M-Pesa$heartbeat\n⏳ Verifying... (${elapsed}s)"
                }
                
                val result = MpesaService.querySTKStatusAsync(checkoutId)

                if (result == null) {
                    consecutiveNulls++
                    Log.d(TAG, "Query returned null (attempt $attempts, consecutive=$consecutiveNulls)")

                    // Show reassuring message if we keep getting nulls (Safaricom is still processing)
                    if (consecutiveNulls >= 5 && consecutiveNulls % 5 == 0) {
                        runOnUiThread {
                            pollingStatusText.text = "Still waiting for Safaricom...\n📱 Payment is being processed"
                        }
                    }

                    // Adaptive delay: wait longer early on (M-Pesa needs time), shorter later
                    delay(if (attempts < 10) 5000 else 3000)
                    continue
                }

                // We got a result — reset null counter
                consecutiveNulls = 0

                // Normalize ResultCode (e.g., "0.0" -> "0") to handle GSON numeric parsing variations
                val rawCode = result.resultCode?.toString()?.trim() ?: ""
                val resultCode = if (rawCode.contains(".")) rawCode.substringBefore(".") else rawCode
                val resultDesc = result.resultDesc ?: ""

                Log.d(TAG, "M-Pesa Poll #$attempts: raw=$rawCode normalized=$resultCode desc=$resultDesc")
                
                // Detect success via ResultCode or ResultDesc
                var finalResultCode = resultCode
                if (finalResultCode == "0" || 
                    resultDesc.contains("processed successfully", ignoreCase = true) ||
                    resultDesc.contains("success", ignoreCase = true)) {
                    Log.i(TAG, "Payment success detected (code=$resultCode desc=$resultDesc)")
                    finalResultCode = "0"
                } else if (finalResultCode.isEmpty() || 
                           resultDesc.contains("being processed", ignoreCase = true) ||
                           resultDesc.contains("pending", ignoreCase = true)) {
                    // Still pending
                    Log.d(TAG, "Payment still pending, continuing to poll...")
                    delay(3000)
                    continue
                }

                isPolling = false

                when (finalResultCode) {
                    "0" -> {
                        // ✅ ABSOLUTE SUCCESS — Safaricom confirmed payment
                        val receiptRegex = Regex("[A-Z0-9]{10,}")
                        val mpesaReceipt = receiptRegex.find(resultDesc)?.value ?: "MPESA_${System.currentTimeMillis()}"
                        
                        isPolling = false
                        isPaymentConfirmed = true
                        runOnUiThread {
                            pollingStatusText.text = "✅ Payment Confirmed!"
                            showPaymentSuccess(mpesaReceipt)
                        }
                        
                        // Proceed with background sync, but user already sees success
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                createBookingAfterPayment(mpesaReceipt, checkoutId)
                            } catch (e: Exception) {
                                Log.e(TAG, "Background sync failed after successful payment", e)
                            }
                        }
                        return@launch
                    }
                    "1032" -> {
                        // ❌ CANCELLED — user dismissed the M-Pesa prompt
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("Transaction was cancelled. Please try again.")
                        }
                        return@launch
                    }
                    "1037" -> {
                        // ❌ TIMEOUT — user didn't respond to STK push in time
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("Payment request expired. Please try again.")
                        }
                        return@launch
                    }
                    "2001" -> {
                        // ❌ WRONG PIN — user entered incorrect PIN
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("Wrong M-Pesa PIN entered. Please try again with the correct PIN.")
                        }
                        return@launch
                    }
                    "1019" -> {
                        // ❌ WRONG PIN / authentication failure
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("Authentication failed. Please check your PIN and try again.")
                        }
                        return@launch
                    }
                    "1" -> {
                        // ❌ INSUFFICIENT FUNDS
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("Insufficient M-Pesa balance. Please top up and try again.")
                        }
                        return@launch
                    }
                    "1001" -> {
                        // ❌ Unable to lock subscriber
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            showPaymentFailed("M-Pesa unavailable. The subscriber could not be reached. Try later.")
                        }
                        return@launch
                    }
                    else -> {
                        // ❌ GENERIC FAILURE — show Safaricom's description
                        runOnUiThread {
                            pollingOverlay.visibility = View.GONE
                            val userMessage = when {
                                resultDesc.contains("cancelled", ignoreCase = true) -> "Transaction cancelled."
                                resultDesc.contains("pin", ignoreCase = true) -> "Wrong PIN provided. Please try again."
                                resultDesc.contains("insufficient", ignoreCase = true) -> "Insufficient funds. Please top up."
                                resultDesc.isNotEmpty() -> "Payment failed: $resultDesc"
                                else -> "Payment failed (Code: $resultCode). Please try again."
                            }
                            showPaymentFailed(userMessage)
                        }
                        return@launch
                    }
                }
            }

            // ⏰ Polling timeout — we never got a terminal result
            if (isPolling) {
                isPolling = false
                runOnUiThread {
                    pollingOverlay.visibility = View.GONE
                    android.app.AlertDialog.Builder(this@PaymentDetailsActivity)
                        .setTitle("⏰ Payment Timeout")
                        .setMessage("We couldn't confirm your payment within the expected time.\n\n" +
                                "If your M-Pesa balance was deducted, please contact support with your phone number and we will confirm manually.\n\n" +
                                "Otherwise, please try again.")
                        .setPositiveButton("Try Again") { _, _ ->
                            payButton.isEnabled = true
                            payButton.text = getButtonLabel()
                        }
                        .setNegativeButton("Contact Support") { _, _ ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:+254111307585"))
                            startActivity(intent)
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }



    private fun createBookingAfterPayment(mpesaReceipt: String, transactionId: String) {
        val currentUser = auth.currentUser ?: return
        val userName = studentName // Use real name fetched from Firestore
        
        val bookingId = db.collection("bookings").document().id
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, 1)
        val leaseEnd = calendar.time

        val booking = hashMapOf(
            "id" to bookingId,
            "propertyId" to property.id,
            "propertyName" to property.displayTitle,
            "propertyLocation" to property.location,
            "propertyImage" to (selectedRoomType?.imageUrl ?: property.getFirstImagePath() ?: ""),
            "studentId" to currentUser.uid,
            "studentName" to userName,
            "studentPhone" to phoneInput.text.toString().trim(),
            "caretakerId" to property.caretakerId,
            "roomTypeId" to (selectedRoomType?.id ?: ""),
            "roomTypeName" to (selectedRoomType?.name ?: ""),
            "roomNumber" to (selectedRoomNumber ?: ""),
            "amount" to finalAmount,
            "paymentStatus" to "completed",
            "mpesaReceiptNumber" to mpesaReceipt,
            "status" to "confirmed",
            "createdAt" to now,
            "leaseStart" to now,
            "leaseEnd" to leaseEnd,
            "paymentType" to "room"
        )

        // Perform atomic transaction to update inventory
        db.runTransaction { transaction ->
            val propertyRef = db.collection("properties").document(property.id)
            val snapshot = transaction.get(propertyRef)
            
            if (selectedRoomNumber != null) {
                // Update specific room status in the map
                val statuses = (snapshot.get("roomStatuses") as? Map<String, String> ?: emptyMap()).toMutableMap()
                if (statuses[selectedRoomNumber]?.equals("Available", ignoreCase = true) == true) {
                    statuses[selectedRoomNumber!!] = "Booked"
                    transaction.update(propertyRef, "roomStatuses", statuses)

                    val remainingAvailable = countAvailableRooms(statuses)
                    transaction.update(propertyRef, "availableRooms", remainingAvailable)
                    if (remainingAvailable <= 0) {
                        transaction.update(propertyRef, "status", "Booked")
                        transaction.update(propertyRef, "available", false)
                    } else {
                        transaction.update(propertyRef, "status", "Active")
                        transaction.update(propertyRef, "available", true)
                    }
                } else {
                    throw Exception("Room already booked")
                }
            } else if (selectedRoomType != null) {
                // Update specific room type quantity
                val roomTypes = snapshot.get("roomTypes") as? List<Map<String, Any>> ?: emptyList()
                val updatedRoomTypes = roomTypes.map { type ->
                    val mutableType = type.toMutableMap()
                    if (type["id"] == selectedRoomType!!.id) {
                        val currentQty = getLongFromAny(type["availableQuantity"])
                        if (currentQty <= 0) throw Exception("Room sold out")
                        mutableType["availableQuantity"] = currentQty - 1
                    }
                    mutableType
                }
                
                transaction.update(propertyRef, "roomTypes", updatedRoomTypes)
                val totalAvailable = updatedRoomTypes.sumOf { getLongFromAny(it["availableQuantity"]) }
                transaction.update(propertyRef, "availableRooms", totalAvailable.toInt())
                if (totalAvailable <= 0L) {
                    transaction.update(propertyRef, "status", "Booked")
                    transaction.update(propertyRef, "available", false)
                } else {
                    transaction.update(propertyRef, "status", "Active")
                    transaction.update(propertyRef, "available", true)
                }
            } else {
                // Simple plot/house booking
                transaction.update(propertyRef, "status", "Booked")
                transaction.update(propertyRef, "available", false)
                transaction.update(propertyRef, "availableRooms", 0)
            }
            
            // Create booking document inside transaction
            if (existingBookingId != null) {
                transaction.update(db.collection("bookings").document(existingBookingId!!), 
                    "paymentStatus", "completed",
                    "mpesaReceiptNumber", mpesaReceipt,
                    "status", "confirmed",
                    "updatedAt", Date())
            } else {
                transaction.set(db.collection("bookings").document(bookingId), booking)
            }
        }.addOnSuccessListener {
            // Send automated notifications
            com.example.homehub.utils.NotificationManager.sendBookingConfirmedNotification(
                property.caretakerId,
                userName,
                property.displayTitle,
                finalAmount
            )
            com.example.homehub.utils.NotificationManager.sendBookingReceiptNotification(
                currentUser.uid,
                property.displayTitle,
                mpesaReceipt
            )
            
            showPaymentSuccess(mpesaReceipt)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Transaction failed", e)
            showPaymentFailed("Booking failed: ${e.message}")
        }
    }


    private fun showPaymentProcessing() {
        payButton.isEnabled = false
        payButton.text = "Processing..."
        findViewById<FrameLayout>(R.id.pollingOverlay).visibility = View.VISIBLE
    }

    private fun showPaymentSuccess(receipt: String) {
        runOnUiThread {
            pollingOverlay.visibility = View.GONE
            stepAnimator.displayedChild = 2 // Move to Step 3 (Success)
            mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
            updateStepIndicators(3)
            // Display real receipt on success screen
            try {
                val receiptView = findViewById<TextView>(R.id.step3Msg)
                val successTitle = findViewById<TextView>(R.id.step3Title)
                
                successTitle?.text = "Booking Successful!"
                receiptView.text = "Your payment was successful! 🎉\n\n" +
                    "M-Pesa Receipt: $receipt\n\n" +
                    "Your room booking has been confirmed. Welcome to HomeHub!"
            } catch (e: Exception) {
                Log.w(TAG, "Receipt view not found: ${e.message}")
            }
        }
    }

    private fun showPaymentFailed(message: String) {
        if (isPaymentConfirmed) {
            // Priority: Payment is confirmed. Do not show failure even if sync fails.
            Log.e(TAG, "Sync failed but payment was confirmed: $message")
            return
        }
        runOnUiThread {
            pollingOverlay.visibility = View.GONE
            payButton.isEnabled = true
            payButton.text = getButtonLabel()

            MaterialAlertDialogBuilder(this)
                .setTitle("❌ Payment Failed")
                .setMessage(message)
                .setPositiveButton("Try Again") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .show()
        }
    }

    private fun createDeferredBooking() {
        showPaymentProcessing()
        pollingStatusText.text = "Reserving your room..."
        
        val currentUser = auth.currentUser ?: return
        val userName = studentName // Use real name fetched from Firestore
        
        val bookingId = db.collection("bookings").document().id
        val now = Date()
        val calendar = Calendar.getInstance()
        
        val deadlineCal = Calendar.getInstance()
        when (selectedPaymentOption) {
            "24h" -> deadlineCal.add(Calendar.HOUR, 24)
            "48h" -> deadlineCal.add(Calendar.HOUR, 48)
            else -> deadlineCal.add(Calendar.HOUR, 48)
        }
        val paymentDeadline = deadlineCal.time

        calendar.add(Calendar.MONTH, 1)
        val leaseEnd = calendar.time

        val booking = hashMapOf(
            "id" to bookingId,
            "propertyId" to property.id,
            "propertyName" to property.displayTitle,
            "propertyLocation" to property.location,
            "propertyImage" to (selectedRoomType?.imageUrl ?: property.getFirstImagePath() ?: ""),
            "studentId" to currentUser.uid,
            "studentName" to userName,
            "studentPhone" to phoneInput.text.toString().trim(),
            "caretakerId" to property.caretakerId,
            "roomTypeId" to (selectedRoomType?.id ?: ""),
            "roomTypeName" to (selectedRoomType?.name ?: ""),
            "roomNumber" to (selectedRoomNumber ?: ""),
            "amount" to finalAmount,
            "paymentOption" to selectedPaymentOption,
            "paymentDeadline" to paymentDeadline,
            "paymentStatus" to "pending_deferred",
            "status" to "pending_deferred",
            "createdAt" to now,
            "leaseStart" to now,
            "leaseEnd" to leaseEnd,
            "paymentType" to "room"
        )

        db.runTransaction { transaction ->
            val propertyRef = db.collection("properties").document(property.id)
            val snapshot = transaction.get(propertyRef)
            
            // Logic similar to createBookingAfterPayment but without receipt
            if (selectedRoomNumber != null) {
                val statuses = (snapshot.get("roomStatuses") as? Map<String, String> ?: emptyMap()).toMutableMap()
                if (statuses[selectedRoomNumber]?.equals("Available", ignoreCase = true) == true) {
                    statuses[selectedRoomNumber!!] = "Booked"
                    transaction.update(propertyRef, "roomStatuses", statuses)
                    val remainingAvailable = countAvailableRooms(statuses)
                    transaction.update(propertyRef, "availableRooms", remainingAvailable)
                    if (remainingAvailable <= 0) {
                        transaction.update(propertyRef, "status", "Booked")
                        transaction.update(propertyRef, "available", false)
                    } else {
                        // Multi-room support for deferred bookings
                        transaction.update(propertyRef, "status", "Active")
                        transaction.update(propertyRef, "available", true)
                    }
                } else throw Exception("Room already booked")
            } else if (selectedRoomType != null) {
                val roomTypes = snapshot.get("roomTypes") as? List<Map<String, Any>> ?: emptyList()
                val updatedRoomTypes = roomTypes.map { type ->
                    val mutableType = type.toMutableMap()
                    if (type["id"] == selectedRoomType!!.id) {
                        val currentQty = getLongFromAny(type["availableQuantity"])
                        if (currentQty <= 0) throw Exception("Room sold out")
                        mutableType["availableQuantity"] = currentQty - 1
                    }
                    mutableType
                }
                transaction.update(propertyRef, "roomTypes", updatedRoomTypes)
                val totalAvailable = updatedRoomTypes.sumOf { getLongFromAny(it["availableQuantity"]) }
                transaction.update(propertyRef, "availableRooms", totalAvailable.toInt())
                if (totalAvailable <= 0L) {
                    transaction.update(propertyRef, "status", "Booked")
                    transaction.update(propertyRef, "available", false)
                } else {
                    transaction.update(propertyRef, "status", "Active")
                    transaction.update(propertyRef, "available", true)
                }
            } else {
                transaction.update(propertyRef, "status", "Booked")
                transaction.update(propertyRef, "available", false)
                transaction.update(propertyRef, "availableRooms", 0)
            }
            
            transaction.set(db.collection("bookings").document(bookingId), booking)
        }.addOnSuccessListener {
            pollingOverlay.visibility = View.GONE
            stepAnimator.displayedChild = 2
            mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
            findViewById<TextView>(R.id.step3Label).text = "RESERVED"
            updateSuccessScreenForDeferred()
        }.addOnFailureListener { e ->
            pollingOverlay.visibility = View.GONE
            Toast.makeText(this, "Reservation failed: ${e.message}", Toast.LENGTH_LONG).show()
            // Re-enable button on failure
            payButton.isEnabled = true
            payButton.text = getButtonLabel()
        }
    }

    private fun updateSuccessScreenForDeferred() {
        findViewById<TextView>(R.id.step3Title).text = "Room Reserved!"
        findViewById<TextView>(R.id.step3Msg).text = "You have selected to pay later. Your room is held, but you must pay within the deadline to check in."
        updateStepIndicators(3)
    }
}
