package com.example.homehub.other

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.homehub.databinding.ActivityLocationDetailsBinding
import kotlinx.parcelize.Parcelize

class LocationDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationDetailsBinding

    companion object {
        const val EXTRA_LOCATION_DETAILS = "location_details"
        const val EXTRA_LOCATION_ADDRESS = "location_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener {
            saveLocationDetails()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun saveLocationDetails() {
        val county = binding.countyEditText.text.toString().trim()
        val town = binding.townEditText.text.toString().trim()
        val street = binding.streetEditText.text.toString().trim()
        val landmark1 = binding.landmark1EditText.text.toString().trim()
        val landmark2 = binding.landmark2EditText.text.toString().trim()
        val mainRoad = binding.mainRoadEditText.text.toString().trim()

        // Validate required fields
        if (county.isEmpty() || town.isEmpty() || street.isEmpty()) {
            Toast.makeText(this, "Please fill in county, town, and street", Toast.LENGTH_SHORT).show()
            return
        }

        // Create location details object
        val locationDetails = LocationDetails(
            county = county,
            town = town,
            street = street,
            landmark1 = landmark1,
            landmark2 = landmark2,
            mainRoad = mainRoad
        )

        // Create formatted address
        val address = buildAddressString(locationDetails)

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOCATION_DETAILS, locationDetails)
            putExtra(EXTRA_LOCATION_ADDRESS, address)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun buildAddressString(locationDetails: LocationDetails): String {
        return buildString {
            append(locationDetails.street)
            if (locationDetails.mainRoad.isNotEmpty()) {
                append(", ${locationDetails.mainRoad}")
            }
            append(", ${locationDetails.town}")
            append(", ${locationDetails.county}")
            if (locationDetails.landmark1.isNotEmpty()) {
                append(" (Near ${locationDetails.landmark1}")
                if (locationDetails.landmark2.isNotEmpty()) {
                    append(" & ${locationDetails.landmark2}")
                }
                append(")")
            }
        }
    }
}

// Data class for location details - Made Parcelable
@Parcelize
data class LocationDetails(
    val county: String = "",
    val town: String = "",
    val street: String = "",
    val landmark1: String = "",
    val landmark2: String = "",
    val mainRoad: String = ""
) : Parcelable
