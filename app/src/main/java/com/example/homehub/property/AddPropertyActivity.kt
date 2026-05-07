package com.example.homehub.property

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.homehub.utils.UserVerificationBottomSheet
import com.example.homehub.property.Property
import com.example.homehub.property.PropertyImageAdapter
import com.example.homehub.utils.HybridPropertyManager
import com.example.homehub.R
import com.example.homehub.property.RoomType
import com.example.homehub.property.RoomTypeEditAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.example.homehub.other.MapLocationPickerActivity
import com.example.homehub.utils.toastError
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.config.Configuration
import java.util.*

class AddPropertyActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val roomImages = mutableMapOf<String, MutableList<Uri>>()
    private var currentRoomType: String = "full_house"
    private lateinit var imagesAdapter: PropertyImageAdapter
    private lateinit var propertyName: TextInputEditText
    private lateinit var location: TextInputEditText
    private lateinit var price: TextInputEditText
    private lateinit var deposit: TextInputEditText
    private lateinit var totalRooms: TextInputEditText
    private lateinit var propertyType: AutoCompleteTextView
    private lateinit var description: TextInputEditText
    private lateinit var roomTypeChipGroup: ChipGroup
    private lateinit var amenitiesChipGroup: ChipGroup
    private lateinit var btnSubmit: MaterialButton
    private lateinit var etRoomPrefix: TextInputEditText
    private lateinit var statusCheckProgressBar: ProgressBar
    private lateinit var mapPreview: MapView
    private lateinit var mapPreviewCard: com.google.android.material.card.MaterialCardView
    private lateinit var rulesRecyclerView: RecyclerView
    private lateinit var btnAddRule: MaterialButton
    private lateinit var emptyRulesText: TextView
    private lateinit var rulesAdapter: PropertyRulesEditAdapter
    private val propertyRules = mutableListOf<String>()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) handleImageSelection(result.data)
    }

    private var editingProperty: Property? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            latitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            longitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            val address = data?.getStringExtra(MapLocationPickerActivity.EXTRA_SELECTED_ADDRESS)
            if (!address.isNullOrEmpty()) {
                location.setText(address)
            }
            updateMapPreview()
            Toast.makeText(this, "Location set on map", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val BEDROOM_OPTIONS = arrayOf("0", "1", "2", "3", "4", "5", "6+")
        private val BATH_OPTIONS = arrayOf("1", "2", "3", "4", "5+")
        private val PROPERTY_TYPE_CATEGORIES = arrayOf("Single Room", "Bedsitter", "1 Bedroom", "2 Bedroom")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@AddPropertyActivity, getSharedPreferences("osm", MODE_PRIVATE))
        }
        setContentView(R.layout.activity_add_property)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        editingProperty = intent.getParcelableExtra("PROPERTY") ?:
                intent.getParcelableExtra("property") ?:
                PropertyDataHolder.selectedProperty

        initializeViews()
        setupSpinners()
        setupRoomTypeChips()
        setupAmenitiesChips()
        setupRules()
        setupImageUpload()

        listOf("full_house", "living_room", "bedroom", "kitchen", "toilet", "compound").forEach { roomImages[it] = mutableListOf() }

        if (editingProperty != null) {
            prefillForm(editingProperty!!)
            btnSubmit.text = "Save Changes"
        }
    }

    private fun initializeViews() {
        propertyName = findViewById(R.id.propertyName)
        location = findViewById(R.id.location)
        price = findViewById(R.id.price)
        deposit = findViewById(R.id.deposit)
        totalRooms = findViewById(R.id.rooms)
        propertyType = findViewById(R.id.propertyType)
        description = findViewById(R.id.description)
        roomTypeChipGroup = findViewById(R.id.roomTypeChipGroup)
        amenitiesChipGroup = findViewById(R.id.amenitiesChipGroup)
        btnSubmit = findViewById(R.id.btnSubmit)
        statusCheckProgressBar = findViewById(R.id.statusCheckProgressBar)

        mapPreview = findViewById(R.id.mapPreview)
        mapPreviewCard = findViewById(R.id.mapPreviewCard)

        // Rules initialization
        rulesRecyclerView = findViewById(R.id.rulesRecyclerViewAdd)
        btnAddRule = findViewById(R.id.btnAddRule)
        emptyRulesText = findViewById(R.id.emptyRulesText)
        
        location.setOnClickListener {
            openMapPicker()
        }
        
        findViewById<MaterialButton>(R.id.btnLiveLocation).setOnClickListener { requestLiveLocation() }
        findViewById<MaterialButton>(R.id.btnSelectOnMap).setOnClickListener { openMapPicker() }

        if (editingProperty != null) {
            findViewById<TextView>(R.id.titleText)?.text = "Edit Property"
        }

        etRoomPrefix = findViewById(R.id.etRoomPrefix)
        statusCheckProgressBar = findViewById(R.id.statusCheckProgressBar)
        btnSubmit.setOnClickListener { checkCaretakerStatusAndProceed() }
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { onBackPressed() }
    }

    private fun updateMapPreview() {
        if (latitude == 0.0 && longitude == 0.0) {
            mapPreviewCard.visibility = View.GONE
            return
        }
        
        mapPreviewCard.visibility = View.VISIBLE
        
        val cartoVoyager = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoVoyager",
            1, 20, 256, ".png", arrayOf(
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/"
            ), "© OpenStreetMap contributors, © CARTO"
        )
        mapPreview.setTileSource(cartoVoyager)
        mapPreview.setMultiTouchControls(false)
        
        val point = GeoPoint(latitude, longitude)
        // Zoom 16.0 — balanced neighborhood context and landmark visibility
        mapPreview.controller.setZoom(16.0)
        mapPreview.controller.setCenter(point)
        
        val marker = Marker(mapPreview)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location)
        marker.icon?.setTint(ContextCompat.getColor(this, R.color.blue))
        
        mapPreview.overlays.clear()
        mapPreview.overlays.add(marker)
        mapPreview.invalidate()
    }

    private fun setupSpinners() {
        propertyType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, PROPERTY_TYPE_CATEGORIES))
    }

    private fun setupRoomTypeChips() {
        val types = listOf("full_house" to "Full House", "living_room" to "Living Room", "bedroom" to "Bedroom", "kitchen" to "Kitchen", "toilet" to "Toilet", "compound" to "Compound")
        types.forEach { (type, name) ->
            roomTypeChipGroup.addView(Chip(this).apply {
                text = name
                isCheckable = true
                isChecked = type == currentRoomType
                setOnClickListener { currentRoomType = type }
            })
        }
    }

    private fun setupAmenitiesChips() {
        val commonAmenities = listOf(
            "WiFi", "Electricity", "Water", "Bed", "Table", "Security", "Gym", "CCTV", "Laundry", "Study Area"
        )
        commonAmenities.forEach { name ->
            val chip = Chip(this).apply {
                text = name
                isCheckable = true
                setTextColor(ContextCompat.getColor(this@AddPropertyActivity, R.color.text_primary))
                setChipBackgroundColorResource(R.color.surface)
                setChipStrokeColorResource(R.color.border_light)
                chipStrokeWidth = 1f
                rippleColor = ContextCompat.getColorStateList(this@AddPropertyActivity, R.color.blue_light_transparent)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(R.color.blue)
                        setTextColor(ContextCompat.getColor(this@AddPropertyActivity, R.color.white))
                    } else {
                        setChipBackgroundColorResource(R.color.surface)
                        setTextColor(ContextCompat.getColor(this@AddPropertyActivity, R.color.text_primary))
                    }
                }
            }
            amenitiesChipGroup.addView(chip)
        }
    }

    private fun setupRules() {
        rulesAdapter = PropertyRulesEditAdapter(propertyRules) { }
        rulesRecyclerView.layoutManager = LinearLayoutManager(this)
        rulesRecyclerView.adapter = rulesAdapter

        btnAddRule.setOnClickListener {
            if (propertyRules.size >= 7) {
                Toast.makeText(this, "Maximum 7 rules allowed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddRuleBottomSheet()
        }

        updateRulesUI()
    }

    private fun showAddRuleBottomSheet() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_rule, null)
        val etNewRule = bottomSheetView.findViewById<TextInputEditText>(R.id.etNewRule)
        val btnSaveRule = bottomSheetView.findViewById<MaterialButton>(R.id.btnSaveRule)
        val btnCancelRule = bottomSheetView.findViewById<MaterialButton>(R.id.btnCancelRule)

        val bottomSheet = MaterialAlertDialogBuilder(this)
            .setView(bottomSheetView)
            .show()

        btnSaveRule.setOnClickListener {
            val rule = etNewRule.text.toString().trim()
            if (rule.isEmpty()) {
                Toast.makeText(this@AddPropertyActivity, "Enter a rule", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (propertyRules.contains(rule)) {
                Toast.makeText(this@AddPropertyActivity, "This rule already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            rulesAdapter.addRule(rule)
            propertyRules.add(rule)
            updateRulesUI()
            bottomSheet.dismiss()
            Toast.makeText(this@AddPropertyActivity, "Rule added", Toast.LENGTH_SHORT).show()
        }

        btnCancelRule.setOnClickListener {
            bottomSheet.dismiss()
        }
    }

    private fun updateRulesUI() {
        if (propertyRules.isEmpty()) {
            emptyRulesText.visibility = View.VISIBLE
            rulesRecyclerView.visibility = View.GONE
        } else {
            emptyRulesText.visibility = View.GONE
            rulesRecyclerView.visibility = View.VISIBLE
        }

        btnAddRule.isEnabled = propertyRules.size < 7
        btnAddRule.alpha = if (propertyRules.size < 7) 1f else 0.5f
    }

    private fun setupImageUpload() {
        imagesAdapter = PropertyImageAdapter { pos -> removeImage(pos) }
        findViewById<RecyclerView>(R.id.imagesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@AddPropertyActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imagesAdapter
        }
        findViewById<Button>(R.id.addImageButton).setOnClickListener { openImagePicker() }
    }

    private fun handleImageSelection(data: Intent?) {
        val uris = mutableListOf<Uri>()
        if (data?.clipData != null) {
            for (i in 0 until (data.clipData?.itemCount ?: 0)) data.clipData?.getItemAt(i)?.uri?.let { uris.add(it) }
        } else data?.data?.let { uris.add(it) }

        roomImages[currentRoomType]?.addAll(uris)
        updateImagesUI()
    }

    private fun compressImage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val maxDimension = 640
            val width = originalBitmap.width
            val height = originalBitmap.height

            val (newWidth, newHeight) = if (width > height) {
                if (width > maxDimension) {
                    val ratio = maxDimension.toFloat() / width
                    (maxDimension to (height * ratio).toInt())
                } else (width to height)
            } else {
                if (height > maxDimension) {
                    val ratio = maxDimension.toFloat() / height
                    ((width * ratio).toInt() to maxDimension)
                } else (width to height)
            }

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            val outputStream = java.io.ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.DEFAULT)}"
        } catch (e: Exception) {
            Log.e("AddProperty", "Error compressing image: ${e.message}")
            null
        }
    }

    private fun removeImage(pos: Int) {
        var count = 0
        for ((type, uris) in roomImages) {
            if (pos < count + uris.size) {
                uris.removeAt(pos - count)
                break
            }
            count += uris.size
        }
        updateImagesUI()
    }

    private fun updateImagesUI() {
        val allImages = mutableListOf<Pair<Any, String>>()
        roomImages.forEach { (type, uris) ->
            val prettyType = type.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            uris.forEach { uri ->
                allImages.add(uri to prettyType)
            }
        }
        imagesAdapter.updateImages(allImages)
        findViewById<TextView>(R.id.imageCounter).text = "${roomImages.values.flatten().size}/8 images"
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }, "Select Images"))
    }

    private fun prefillForm(property: Property) {
        propertyName.setText(property.title)
        location.setText(property.location)
        price.setText(property.priceValue.toString())
        deposit.setText(property.deposit.toString())
        description.setText(property.description)
        totalRooms.setText(property.totalRooms.toString())

        val tIndex = PROPERTY_TYPE_CATEGORIES.indexOf(property.propertyType)
        if (tIndex != -1) propertyType.setText(PROPERTY_TYPE_CATEGORIES[tIndex], false)

        for (i in 0 until amenitiesChipGroup.childCount) {
            val chip = amenitiesChipGroup.getChildAt(i) as? Chip
            if (chip != null && property.amenities.contains(chip.text.toString())) {
                chip.isChecked = true
            }
        }

        // Load property rules when editing
        propertyRules.clear()
        propertyRules.addAll(property.propertyRules)
        rulesAdapter.notifyDataSetChanged()
        updateRulesUI()

        etRoomPrefix.setText(property.roomPrefix)
        this.latitude = property.latitude
        this.longitude = property.longitude
        updateMapPreview()

        // Load existing images if any
        if (property.imageUrls.isNotEmpty()) {
            val existingImages = property.imageUrls.map { it to "Existing" }
            imagesAdapter.updateImages(existingImages)
            findViewById<TextView>(R.id.imageCounter).text = "${property.imageUrls.size}/8 images"
        }
    }

    private fun requestLiveLocation() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        findViewById<MaterialButton>(R.id.btnLiveLocation).isEnabled = false
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            findViewById<MaterialButton>(R.id.btnLiveLocation).isEnabled = true
            if (loc != null) {
                this.latitude = loc.latitude
                this.longitude = loc.longitude
                updateMapPreview()
                Toast.makeText(this, "Live location captured: ${loc.latitude}, ${loc.longitude}", Toast.LENGTH_SHORT).show()
                
                // Try to reverse geocode for the address field
                scope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = android.location.Geocoder(this@AddPropertyActivity, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        if (addresses?.isNotEmpty() == true) {
                            withContext(Dispatchers.Main) {
                                location.setText(addresses[0].getAddressLine(0))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AddProperty", "Geocoding failed", e)
                    }
                }
            } else {
                Toast.makeText(this, "Unable to get current location. Ensure GPS is on.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            findViewById<MaterialButton>(R.id.btnLiveLocation).isEnabled = true
            toastError(it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestLiveLocation()
        }
    }

    private fun openMapPicker() {
        val intent = Intent(this, MapLocationPickerActivity::class.java)
        mapPickerLauncher.launch(intent)
    }

    private fun checkCaretakerStatusAndProceed() {
        val user = auth.currentUser ?: return
        statusCheckProgressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                statusCheckProgressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
                
                val status = doc.getString("verificationStatus") ?: "PENDING"
                if (status == "APPROVED") {
                    submitProperty()
                } else {
                    showGatingDialog(status)
                }
            }
            .addOnFailureListener {
                statusCheckProgressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Connection error. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showGatingDialog(status: String) {
        val title = if (status == "REJECTED") "Verification Rejected" else "Approval Pending"
        val message = if (status == "REJECTED") {
            "Your identity verification was rejected. Please update your profile with correct documents to continue."
        } else {
            "Your account is currently undergoing verification. You will be able to add properties once an administrator approves your ID/Passport."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun submitProperty() {
        val name = propertyName.text.toString().trim()
        val loc = location.text.toString().trim()
        val prc = price.text.toString().trim()
        val dep = deposit.text.toString().trim()

        if (name.isEmpty()) { propertyName.error = "Required"; return }
        if (loc.isEmpty()) { location.error = "Required"; return }
        if (prc.isEmpty()) { price.error = "Required"; return }
        if (dep.isEmpty()) { deposit.error = "Required"; return }

        val totalImages = roomImages.values.flatten().size
        if (totalImages < 4) {
            Toast.makeText(this, "Please upload at least 4 photos", Toast.LENGTH_LONG).show()
            return
        }
        if (totalImages > 8) {
            Toast.makeText(this, "Maximum of 8 photos allowed for stability", Toast.LENGTH_LONG).show()
            return
        }

        val id = editingProperty?.id?.ifEmpty { null } ?: UUID.randomUUID().toString()
        statusCheckProgressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false

        scope.launch {
            try {
                val base64Images = withContext(Dispatchers.IO) { convertImages() }
                val data = prepareData(id, base64Images)

                Log.d("AddProperty", "Saving property with data: $data")

                db.collection("properties").document(id).set(data, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        HybridPropertyManager.saveLocalProperty(this@AddPropertyActivity, data)
                        statusCheckProgressBar.visibility = View.GONE
                        val msg = if (editingProperty != null) "Changes Saved Successfully!" else "Published Successfully!"
                        Toast.makeText(this@AddPropertyActivity, msg, Toast.LENGTH_LONG).show()
                        Log.d("AddProperty", "Property saved successfully with ID: $id")
                        finish()
                    }
                    .addOnFailureListener { e ->
                        statusCheckProgressBar.visibility = View.GONE
                        btnSubmit.isEnabled = true
                        val errorMsg = if (e.message?.contains("too large") == true)
                            "Error: Too many high-res photos. Please remove some." else "Failed: ${e.message}"
                        Toast.makeText(this@AddPropertyActivity, errorMsg, Toast.LENGTH_LONG).show()
                        Log.e("AddProperty", "Failed to save property: ${e.message}", e)
                    }
            } catch (e: Exception) {
                statusCheckProgressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
                Toast.makeText(this@AddPropertyActivity, "Error processing images: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun convertImages(): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        roomImages.forEach { (type, uris) ->
            result[type] = uris.mapNotNull { uri ->
                compressImage(uri)
            }
        }
        return result
    }

    private fun prepareData(id: String, images: Map<String, List<String>>): Map<String, Any> {
        val totalRoomsValue = totalRooms.text.toString().toIntOrNull() ?: 0
        val priceValue = price.text.toString().toDoubleOrNull() ?: 0.0
        val propertyTypeValue = propertyType.text.toString()

        val data = mutableMapOf<String, Any>(
            "propertyId" to id,
            "id" to id,
            "caretakerId" to auth.currentUser!!.uid,
            "ownerId" to auth.currentUser!!.uid,
            "title" to propertyName.text.toString(),
            "propertyName" to propertyName.text.toString(),
            "description" to description.text.toString(),
            "type" to propertyTypeValue,
            "propertyType" to propertyTypeValue,
            "category" to propertyTypeValue,
            "price" to priceValue,
            "deposit" to (deposit.text.toString().toDoubleOrNull() ?: 0.0),
            "location" to location.text.toString(),
            "totalRooms" to totalRoomsValue,
            "availableRooms" to (editingProperty?.availableRooms ?: totalRoomsValue),
            "roomPrefix" to etRoomPrefix.text.toString(),
            "roomStatuses" to (editingProperty?.roomStatuses ?: generateRoomStatuses(totalRoomsValue, etRoomPrefix.text.toString())),
            "amenities" to getSelectedAmenities(),
            "propertyRules" to propertyRules,
            "status" to (editingProperty?.status ?: "Active"),
            "isFeatured" to (editingProperty?.isFeatured ?: false),
            "available" to (editingProperty?.available ?: true),
            "isDeleted" to (editingProperty?.isDeleted ?: false),
            "isArchived" to (editingProperty?.isArchived ?: false),
            "createdAt" to (editingProperty?.createdAt?.let { com.google.firebase.Timestamp(it) } ?: Timestamp.now()),
            "updatedAt" to Timestamp.now(),
            "bedroom" to totalRoomsValue,
            "bedrooms" to totalRoomsValue,
            "baths" to 1,
            "bathrooms" to 1,
            "rating" to (editingProperty?.rating ?: 0.0),
            "reviews" to (editingProperty?.reviews ?: 0),
            "totalBookings" to (editingProperty?.totalBookings ?: 0),
            "totalRevenue" to (editingProperty?.totalRevenue ?: 0.0),
            "viewCount" to (editingProperty?.viewCount ?: 0),
            "likeCount" to (editingProperty?.likeCount ?: 0),
            "duration" to "per month",
            "latitude" to latitude,
            "longitude" to longitude
        )

        val allNewImages = images.values.flatten()
        if (allNewImages.isNotEmpty()) {
            data["imageUrl"] = allNewImages.first()
            data["imageUrls"] = allNewImages
            data["roomImages"] = images
            data["firebaseImages"] = allNewImages
        }

        return data
    }

    private fun getSelectedAmenities(): List<String> {
        val selected = mutableListOf<String>()
        for (i in 0 until amenitiesChipGroup.childCount) {
            val chip = amenitiesChipGroup.getChildAt(i) as Chip
            if (chip.isChecked) selected.add(chip.text.toString())
        }
        return selected
    }

    private fun generateRoomStatuses(total: Int, prefix: String): Map<String, String> {
        val statuses = mutableMapOf<String, String>()
        val cleanPrefix = prefix.trim()
        
        // Split by comma for potential individual naming
        val parts = cleanPrefix.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (parts.size == total) {
            // Caretaker provided exactly enough names (e.g. "A10, AB10")
            parts.forEach { statuses[it] = "Available" }
        } else if (total == 1) {
            // Single room, use the prefix exactly as provided
            statuses[cleanPrefix] = "Available"
        } else {
            // Bulk generation case
            for (i in 1..total) {
                statuses["$cleanPrefix$i"] = "Available"
            }
        }
        return statuses
    }
}