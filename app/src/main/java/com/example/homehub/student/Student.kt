package com.example.homehub.student

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.homehub.utils.UsernameFormatter
import com.example.homehub.utils.ProfilePictureUtils

data class Student(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val status: String = "Active", // Default to Active
    val course: String = "", // Student's course
    val userType: String = "Student", // Default to Student
    val joinDate: Timestamp? = null,
    val lastLogin: Timestamp? = null,
    val bookings: Int = 0,
    val rating: Double = 0.0,
    val isVerified: Boolean = false,
    val profileInitials: String = "",
    val profileColor: String = "",
    val signupMethod: String = "",
    val createdAt: Timestamp? = null,
    val emailVerified: Boolean = false,
    val hasCustomProfile: Boolean = false,
    val fcmToken: String = "",

    // Host-specific fields
    val propertiesCount: Int = 0,
    val isHostVerified: Boolean = false,
    val hostProperties: List<String> = emptyList(),

    // Location tracking fields
    val lastKnownLatitude: Double = 0.0,
    val lastKnownLongitude: Double = 0.0,
    val lastLocationUpdate: Timestamp? = null,
    val locationAccuracy: Float = 0f,
    val locationProvider: String = "",
    val locationEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val address: String = "",
    val city: String = "",
    val country: String = "",
    val registrationNumber: String = "",
    val profileImageUrl: String = ""
) {
    // Get display name - fallback to email if username is empty
    fun getDisplayName(): String {
        return if (username.isNotEmpty() && username != "Unknown") {
            username
        } else if (email.isNotEmpty()) {
            "Student"
        } else {
            "Student"
        }
    }

    // Get student role/type for display - Only Student or Caretaker
    fun getStudentTypeDisplay(): String {
        return getDisplayStudentType()
    }

    // Get status for display
    fun getStatusDisplay(): String {
        return when (status.lowercase()) {
            "active" -> "Active"
            "suspended" -> "Suspended"
            "inactive", "disabled" -> "Inactive"
            else -> "Active"
        }
    }

    // Get initials for profile
    fun getProfileInitialsText(): String {
        return if (profileInitials.isNotEmpty()) {
            profileInitials
        } else {
            ProfilePictureUtils.getInitials(getDisplayName())
        }
    }

    // Get profile color
    fun getProfileColorHex(): String {
        return if (profileColor.isNotEmpty()) {
            profileColor
        } else {
            ProfilePictureUtils.getColorHexForName(getDisplayName())
        }
    }

    // Helper function to format date
    fun getFormattedJoinDate(): String {
        return if (joinDate != null) {
            "Joined: ${joinDate.toDate().formatDate()}"
        } else if (createdAt != null) {
            "Joined: ${createdAt.toDate().formatDate()}"
        } else {
            "Joined: N/A"
        }
    }

    // Helper function to format last login (for Las seen)
    fun getFormattedLastLogin(): String {
        return if (lastLogin != null) {
            val now = System.currentTimeMillis()
            val lastLoginTime = lastLogin.toDate().time
            val diff = now - lastLoginTime
            val hours = diff / (1000 * 60 * 60)
            val minutes = diff / (1000 * 60)
            val days = diff / (1000 * 60 * 60 * 24)

            when {
                days > 30 -> "Last seen: long ago"
                days > 0 -> "Last seen: ${days.toInt()} days ago"
                hours > 0 -> "Last seen: ${hours.toInt()} hours ago"
                minutes > 0 -> "Last seen: ${minutes.toInt()} minutes ago"
                else -> "Last seen: just now"
            }
        } else {
            "Last seen: Never"
        }
    }

    // Check if student is admin
    fun isAdmin(): Boolean {
        return userType.lowercase() == "admin"
    }

    // Check if student is active
    fun isActive(): Boolean {
        return status.lowercase() == "active"
    }

    // Get formatted rating
    fun getFormattedRating(): String {
        return if (rating > 0) {
            String.format("%.1f", rating)
        } else {
            "0.0"
        }
    }

    // Get formatted phone number
    fun getFormattedPhone(): String {
        return if (phone.isNotEmpty()) {
            phone
        } else {
            "Not provided"
        }
    }

    // Get display student type (only Student or Caretaker)
    fun getDisplayStudentType(): String {
        return when {
            // If student is explicitly marked as landlord/host
            userType.equals("landlord", ignoreCase = true) -> "Caretaker"
            userType.equals("host", ignoreCase = true) -> "Caretaker"
            userType.equals("caretaker", ignoreCase = true) -> "Caretaker"

            // If student has host/caretaker verification
            isHostVerified -> "Caretaker"

            // If student has properties
            propertiesCount > 0 -> "Caretaker"

            // If student has host/caretaker properties list
            hostProperties.isNotEmpty() -> "Caretaker"

            // Default to Student for regular students
            else -> "Student"
        }
    }

    // Get formatted location with enhanced information
    fun getFormattedLocation(): String {
        return when {
            // If we have address details
            address.isNotEmpty() && city.isNotEmpty() -> "$address, $city"
            address.isNotEmpty() -> address
            city.isNotEmpty() -> city
            location.isNotEmpty() -> location
            else -> "Location not set"
        }
    }

    // Get GPS location string
    fun getGPSLocation(): String {
        return if (lastKnownLatitude != 0.0 && lastKnownLongitude != 0.0) {
            String.format("Lat: %.6f, Long: %.6f", lastKnownLatitude, lastKnownLongitude)
        } else {
            "GPS location not available"
        }
    }

    // Get full address with GPS
    fun getFullAddress(): String {
        val baseLocation = getFormattedLocation()
        val gpsLocation = getGPSLocation()

        return when {
            baseLocation != "Location not set" && gpsLocation != "GPS location not available" ->
                "$baseLocation ($gpsLocation)"
            baseLocation != "Location not set" -> baseLocation
            else -> gpsLocation
        }
    }

    // Check if location is recent (within last 15 minutes)
    fun isLocationRecent(): Boolean {
        return lastLocationUpdate?.let {
            val now = System.currentTimeMillis()
            val updateTime = it.toDate().time
            (now - updateTime) < (15 * 60 * 1000) // 15 minutes
        } ?: false
    }

    // Check if student has location tracking enabled
    fun hasLocationTracking(): Boolean {
        return locationEnabled && locationPermissionGranted
    }

    // Get location status for display
    fun getLocationStatus(): String {
        return when {
            hasLocationTracking() && isLocationRecent() -> "\uD83D\uDCCD Live Location"
            hasLocationTracking() && lastKnownLatitude != 0.0 -> "\uD83D\uDCCD Last Known Location"
            locationEnabled -> "\uD83D\uDCCD Location Enabled (No data)"
            else -> "\uD83D\uDCCD Location Disabled"
        }
    }

    // Get accuracy information
    fun getLocationAccuracyInfo(): String {
        return if (locationAccuracy > 0) {
            String.format("Accuracy: %.1f meters", locationAccuracy)
        } else {
            "Accuracy: Unknown"
        }
    }

    // Get time since last location update
    fun getTimeSinceLastLocation(): String {
        return lastLocationUpdate?.let {
            val now = System.currentTimeMillis()
            val updateTime = it.toDate().time
            val diffMinutes = (now - updateTime) / (1000 * 60)

            when {
                diffMinutes < 1 -> "Just now"
                diffMinutes < 60 -> "${diffMinutes.toInt()} min ago"
                diffMinutes < 1440 -> "${(diffMinutes / 60).toInt()} hours ago"
                else -> "${(diffMinutes / 1440).toInt()} days ago"
            }
        } ?: "Never updated"
    }

    // Get location provider info
    fun getLocationProviderInfo(): String {
        return when (locationProvider.lowercase()) {
            "gps" -> "GPS (High accuracy)"
            "network" -> "Network (Medium accuracy)"
            "passive" -> "Passive (Low accuracy)"
            "" -> "Not specified"
            else -> locationProvider
        }
    }

    // Create Google Maps URL
    fun getGoogleMapsUrl(): String {
        return if (lastKnownLatitude != 0.0 && lastKnownLongitude != 0.0) {
            "https://www.google.com/maps?q=$lastKnownLatitude,$lastKnownLongitude"
        } else {
            ""
        }
    }

    // Create intent for opening in Google Maps
    fun getGoogleMapsIntent(): String {
        return if (lastKnownLatitude != 0.0 && lastKnownLongitude != 0.0) {
            "geo:$lastKnownLatitude,$lastKnownLongitude?q=$lastKnownLatitude,$lastKnownLongitude"
        } else {
            ""
        }
    }
}

// Extension function to format Date
fun java.util.Date.formatDate(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(this)
}

// Extension for formatting location date
fun java.util.Date.formatLocationTime(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(this)
}
