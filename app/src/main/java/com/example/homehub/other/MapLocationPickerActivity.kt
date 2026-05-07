package com.example.homehub.other

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.homehub.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*

class MapLocationPickerActivity : AppCompatActivity(), MapEventsReceiver {

    private lateinit var mapView: MapView
    private lateinit var selectedLocationText: TextView
    private lateinit var confirmButton: com.google.android.material.button.MaterialButton
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var useCurrentLocationBtn: com.google.android.material.button.MaterialButton
    private lateinit var clearSelectionBtn: com.google.android.material.button.MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private var selectedMarker: Marker? = null
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    companion object {
        const val EXTRA_SELECTED_ADDRESS = "selected_address"
        const val LOCATION_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_location_picker)

        window.statusBarColor = ContextCompat.getColor(this, R.color.themeColor)

        // Initialize OSMDroid with proper configuration
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@MapLocationPickerActivity, getSharedPreferences("osm", MODE_PRIVATE))
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        initializeViews()
        setupMap()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        selectedLocationText = findViewById(R.id.selectedLocationText)
        confirmButton = findViewById(R.id.confirmButton)
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        useCurrentLocationBtn = findViewById(R.id.useCurrentLocationBtn)
        clearSelectionBtn = findViewById(R.id.clearSelectionBtn)
        loadingProgress = findViewById(R.id.loadingProgress)

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        confirmButton.setOnClickListener {
            confirmSelection()
        }

        useCurrentLocationBtn.setOnClickListener {
            useCurrentLocation()
        }

        clearSelectionBtn.setOnClickListener {
            clearSelection()
        }

        searchButton.setOnClickListener {
            performSearch()
        }

        // Set initial Nairobi coordinates as default
        selectedLatitude = -1.286389
        selectedLongitude = 36.817223
    }

    private fun setupMap() {
        // Use a better tile source for detailed maps
        val cartoVoyager = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoVoyager",
            1, 20, 256, ".png", arrayOf(
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/"
            ), "© OpenStreetMap contributors, © CARTO"
        )
        mapView.setTileSource(cartoVoyager)

        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 6.0
        mapView.maxZoomLevel = 19.0

        // Set initial view to Nairobi, Kenya with better zoom
        val nairobi = GeoPoint(-1.286389, 36.817223)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(nairobi)

        // Add compass overlay
        val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        // Add scale bar
        val scaleBarOverlay = ScaleBarOverlay(mapView)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(
            resources.displayMetrics.widthPixels / 2,
            20
        )
        mapView.overlays.add(scaleBarOverlay)

        // Add my location overlay if permission is granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            myLocationOverlay.enableMyLocation()
            mapView.overlays.add(myLocationOverlay)
        }

        // Add click listener
        val mapEventsOverlay = MapEventsOverlay(this)
        mapView.overlays.add(mapEventsOverlay)

        // Add initial marker for Nairobi
        updateMarker(nairobi)
        reverseGeocode(nairobi)
    }

    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        p?.let { point ->
            updateMarker(point)
            reverseGeocode(point)
        }
        return true
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        return true
    }

    private fun updateMarker(point: GeoPoint, label: String? = null) {
        selectedMarker?.let { marker ->
            mapView.overlays.remove(marker)
        }

        selectedMarker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = label ?: "Selected Location"
            snippet = "Tap to confirm this location"
        }
        mapView.overlays.add(selectedMarker)
        selectedMarker?.showInfoWindow()
        mapView.invalidate()

        selectedLatitude = point.latitude
        selectedLongitude = point.longitude

        // Zoom 16.0 — best balance of neighborhood context and landmark visibility
        mapView.controller.animateTo(point, 16.0, 1000L)
    }

    private fun reverseGeocode(point: GeoPoint) {
        showLoading(true)

        Thread {
            try {
                val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                runOnUiThread {
                    showLoading(false)
                    if (addresses?.isNotEmpty() == true) {
                        val address = addresses[0]
                        val addressText = buildCleanAddressText(address)
                        selectedLocationText.text = addressText
                    } else {
                        selectedLocationText.text = "📍 Location selected (${point.latitude}, ${point.longitude})"
                    }
                    confirmButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    selectedLocationText.text = "📍 Location selected (${point.latitude}, ${point.longitude})"
                    confirmButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun buildCleanAddressText(address: android.location.Address): String {
        val addressParts = mutableListOf<String>()

        // 1. Thoroughfare (Street Name) - Often most specific
        if (!address.thoroughfare.isNullOrEmpty() && !address.thoroughfare.matches(Regex(".*[0-9]{4,}.*"))) {
            addressParts.add(address.thoroughfare)
        }

        // 2. Sub-locality (Neighborhood/Area) - CRITICAL for "Neighborhood"
        if (!address.subLocality.isNullOrEmpty() && !addressParts.contains(address.subLocality)) {
            addressParts.add(address.subLocality)
        }

        // 3. Feature Name (Building/Landmark) - Only if it's not a coordinate/plus code
        if (!address.featureName.isNullOrEmpty() && 
            !address.featureName.matches(Regex(".*\\+.*")) && 
            !addressParts.contains(address.featureName) &&
            address.featureName != address.locality) {
            addressParts.add(address.featureName)
        }

        // 4. Locality (City/Town)
        if (!address.locality.isNullOrEmpty() && !addressParts.contains(address.locality)) {
            addressParts.add(address.locality)
        }

        // 5. Admin Area (County) - Added if distinct from locality
        if (!address.adminArea.isNullOrEmpty()) {
            val cleanAdmin = address.adminArea.replace(" County", "", ignoreCase = true)
            if (!addressParts.contains(cleanAdmin) && !addressParts.contains(address.locality)) {
                addressParts.add("$cleanAdmin County")
            }
        }

        // If we have parts, join them cleanly
        if (addressParts.isNotEmpty()) {
            return "📍 ${addressParts.take(3).joinToString(", ")}"
        }

        // Final Fallback: try to build from getAddressLine
        return try {
            val addressLine = address.getAddressLine(0) ?: "Selected Location"
            cleanAddressLine(addressLine)
        } catch (e: Exception) {
            "📍 Selected Location"
        }
    }

    private fun cleanAddressLine(addressLine: String): String {
        var cleaned = addressLine

        // Remove coordinate codes like "8fq69+dbr"
        cleaned = cleaned.replace(Regex("[a-zA-Z0-9]{4,10}\\+[a-zA-Z0-9]{2,4}"), "").trim()

        // Remove extra commas and clean up
        cleaned = cleaned.replace(Regex(",+"), ",")
        cleaned = cleaned.replace(Regex("^[,\\s]+"), "")
        cleaned = cleaned.replace(Regex("[,\\s]+$"), "")

        // Remove standalone Kenya if it's at the end and we have other content
        if (cleaned.endsWith(", Kenya") && cleaned.length > 7) {
            cleaned = cleaned.substring(0, cleaned.length - 7).trim()
            cleaned = cleaned.replace(Regex(",+$"), "")
        }

        // Remove duplicate entries (like "Meru, Meru" becomes "Meru")
        val parts = cleaned.split(",").map { it.trim() }
        val uniqueParts = mutableListOf<String>()
        for (part in parts) {
            if (part.isNotEmpty() && !uniqueParts.any { it.equals(part, ignoreCase = true) }) {
                uniqueParts.add(part)
            }
        }

        cleaned = uniqueParts.joinToString(", ")

        return if (cleaned.isNotEmpty()) "📍 $cleaned" else "📍 Selected Location"
    }

    private fun performSearch() {
        val query = searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            showToast("Please enter a location to search in Kenya")
            return
        }

        showLoading(true)

        Thread {
            try {
                // Search specifically in Kenya by adding country
                val kenyaQuery = if (!query.contains("Kenya", true)) "$query, Kenya" else query
                val addresses = geocoder.getFromLocationName(kenyaQuery, 5)

                runOnUiThread {
                    showLoading(false)
                    if (addresses?.isNotEmpty() == true) {
                        val address = addresses[0]
                        val point = GeoPoint(address.latitude, address.longitude)

                        // Use the search query as the marker label so landmarks show on the pin
                        val cleanLocationName = getCleanLocationName(address, query)
                        updateMarker(point, cleanLocationName)
                        reverseGeocode(point)

                        searchEditText.setText(cleanLocationName)

                        // Zoom 16.0 — balanced neighborhood + landmark visibility
                        mapView.controller.animateTo(point, 16.0, 1000L)
                    } else {
                        showToast("No locations found for '$query' in Kenya")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    showToast("Search failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun getCleanLocationName(address: android.location.Address, originalQuery: String): String {
        // Try to return the most specific clean name
        return when {
            address.featureName != null &&
                    address.featureName != address.locality &&
                    !address.featureName.matches(Regex(".*[0-9].*")) -> {
                address.featureName
            }
            address.locality != null -> {
                address.locality
            }
            address.adminArea != null -> {
                address.adminArea.replace(" County", "").trim()
            }
            else -> originalQuery
        }
    }

    private fun useCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showLoading(true)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                showLoading(false)
                location?.let {
                    val point = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.animateTo(point)
                    updateMarker(point)
                    reverseGeocode(point)
                } ?: run {
                    showToast("Unable to get current location. Please try again.")
                }
            }.addOnFailureListener { e ->
                showLoading(false)
                showToast("Error getting location: ${e.message}")
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                useCurrentLocation()
            } else {
                showToast("Location permission denied")
            }
        }
    }

    private fun clearSelection() {
        selectedMarker?.let { marker ->
            mapView.overlays.remove(marker)
            mapView.invalidate()
        }
        selectedMarker = null
        selectedLocationText.text = "Tap on map to select location"
        searchEditText.text.clear()
        confirmButton.isEnabled = false
        showToast("Selection cleared")
    }

    private fun confirmSelection() {
        if (selectedLocationText.text.toString() == "Tap on map to select location") {
            showToast("Please select a location first")
            return
        }

        val locationName = selectedLocationText.text.toString().replace("📍 ", "")
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_ADDRESS, locationName)
            putExtra("latitude", selectedLatitude)
            putExtra("longitude", selectedLongitude)
        }
        setResult(RESULT_OK, resultIntent)

        // Show intelligent confirmation message
        val confirmationMessage = when {
            searchEditText.text.isNotEmpty() -> "✅ $locationName confirmed"
            else -> "✅ Location confirmed: $locationName"
        }
        showToast(confirmationMessage)
        finish()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
        confirmButton.isEnabled = !show && selectedMarker != null
        searchButton.isEnabled = !show
        useCurrentLocationBtn.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
