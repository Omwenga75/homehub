package com.example.homehub.property

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.example.homehub.R
import com.example.homehub.utils.StatusEntry
import java.util.*

@Parcelize
data class Property(
    var id: String = "",
    val imageRes: Int = R.drawable.ic_house_placeholder,
    val title: String = "",
    val location: String = "",
    var price: String = "",
    val bedroom: Int = 0,
    val baths: Int = 0,
    val area: String = "",
    val rating: Double = 0.0,
    val reviews: Int = 0,
    var isFavorite: Boolean = false,
    val description: String = "",
    val features: List<String> = emptyList(),
    val ownerName: String = "Caretaker",
    val ownerType: String = "Property Owner",
    val ownerImageRes: Int = R.drawable.ic_profile,
    val images: List<Int> = emptyList(),
    val userReviews: List<Review> = emptyList(),
    val caretakerId: String = "",
    val caretakerName: String = "",
    val propertyName: String = "",
    val propertyType: String = "",
    val houseType: String = "",
    val firebaseImages: List<String> = emptyList(),
    val createdAt: Date = Date(),
    var status: String = "Active",
    val localImagePaths: List<String> = emptyList(),
    val priceValue: Double = 0.0,
    val duration: String = "per month",
    val category: String = "",
    val isFeatured: Boolean = false,
    val isCheap: Boolean = false,
    val isBudget: Boolean = false,
    val roomImages: Map<String, List<String>> = emptyMap(),
    var imageUrls: List<String> = emptyList(),
    val isSynced: Boolean = true,
    val caretakerVerified: Boolean = false,
    val caretakerFullName: String = "",
    val caretakerCountry: String = "",
    val caretakerPhoneNumber: String = "",
    val caretakerMonthsExperience: Int = 0,
    val hostVerified: Boolean = false,
    val hostFullName: String = "",
    val hostCountry: String = "",
    val hostPhoneNumber: String = "",
    val hostMonthsExperience: Int = 0,
    val totalBookings: Int = 0,
    val totalRevenue: Double = 0.0,
    var available: Boolean = true,
    val updatedAt: Date = Date(),
    val amenities: List<String> = emptyList(),
    val bathrooms: Int = 1,
    val bedrooms: Int = 1,
    val caretakerProfilePicture: String = "",
    val propertySize: Double = 0.0,
    val yearBuilt: Int = 0,
    val lastMaintenance: Date = Date(),
    val nextMaintenance: Date = Date(),
    val insuranceExpiry: Date = Date(),
    val propertyTax: Double = 0.0,
    val managementFee: Double = 0.0,
    val tenantName: String = "",
    val tenantPhone: String = "",
    val leaseStart: Date = Date(),
    val leaseEnd: Date = Date(),
    val monthlyRent: Double = 0.0,
    val securityDeposit: Double = 0.0,
    val utilitiesIncluded: Boolean = false,
    val parkingSpaces: Int = 0,
    val petFriendly: Boolean = false,
    val smokingAllowed: Boolean = false,
    val furnished: Boolean = false,
    var isDeleted: Boolean = false,
    val deletedAt: Date? = null,
    val deletedBy: String = "",
    val deletionReason: String = "",
    val isArchived: Boolean = false,
    val archivedAt: Date? = null,
    val archiveReason: String = "",
    val statusHistory: List<StatusEntry> = emptyList(),
    val lastStatusChange: Date? = null,
    val statusChangeReason: String = "",
    val bookedAt: Date? = null,
    val bookedBy: String = "",
    val occupiedAt: Date? = null,
    var reactivatedAt: Date? = null,
    var inactiveAt: Date? = null,
    var inactiveBy: String = "",
    var adminAction: Boolean = false,
    val adminActionAt: Date? = null,
    val restoredAt: Date? = null,
    var isPlot: Boolean = false,
    var totalRooms: Int = 0,
    var availableRooms: Int = 0,
    var roomPrefix: String = "RM",
    var roomStatuses: Map<String, String> = emptyMap(),
    var roomTypes: List<RoomType> = emptyList(),
    val imageUrl: String = "",
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val deposit: Double = 0.0,
    var isLiked: Boolean = false,
    val propertyRules: List<String> = emptyList()
) : Parcelable {

    val displayTitle: String
        get() = propertyName.ifBlank { title.ifBlank { "HomeHub Property" } }

    val caretakerDisplayName: String
        get() = caretakerFullName.ifBlank { caretakerName.ifBlank { ownerName.ifBlank { "Caretaker" } } }

    fun getRentalPeriod(): String = duration.ifBlank { "per month" }

    val type: String
        get() = propertyType

    // CRITICAL FIX: This method must exist and return true for valid properties
    fun getEffectiveAvailableRooms(): Int {
        if (roomStatuses.isNotEmpty()) {
            return roomStatuses.count { it.value.equals("Available", ignoreCase = true) }
        }
        if (roomTypes.isNotEmpty()) {
            return roomTypes.sumOf { it.availableQuantity }
        }
        return availableRooms
    }

    fun shouldShowOnDashboard(): Boolean {
        if (isDeleted || isArchived) return false
        if (status.equals("Inactive", ignoreCase = true)) return false

        if (totalRooms > 0) {
            return getEffectiveAvailableRooms() > 0
        }

        val isExplicitlyInactive = status.equals("Booked", ignoreCase = true) ||
                                   status.equals("Rented", ignoreCase = true)

        return !isExplicitlyInactive && available
    }

    fun isAvailable(): Boolean = shouldShowOnDashboard()

    fun isSuspendedByAdmin(): Boolean = adminAction && status.lowercase() == "inactive"

    fun isActuallyRented(): Boolean = status.equals("Rented", ignoreCase = true)

    fun getFirstImagePath(): String? {
        if (imageUrl.isNotBlank()) return imageUrl
        if (firebaseImages.isNotEmpty()) return firebaseImages[0]
        if (imageUrls.isNotEmpty()) return imageUrls[0]
        val allRoomImgs = roomImages.values.flatten()
        if (allRoomImgs.isNotEmpty()) return allRoomImgs[0]
        if (localImagePaths.isNotEmpty()) return localImagePaths[0]
        return null
    }

    val allImages: List<String>
        get() {
            val list = mutableListOf<String>()
            if (imageUrl.isNotBlank()) list.add(imageUrl)
            list.addAll(firebaseImages)
            list.addAll(imageUrls)
            list.addAll(roomImages.values.flatten())
            return list.distinct()
        }

    fun getFormattedLocation(): String = if (location.isNotBlank()) "📍 $location" else "📍 Unknown"

    fun getFormattedPriceForKenya(): String {
        val amount = if (priceValue > 0) priceValue else monthlyRent
        return if (amount > 0) "KSh ${String.format("%,.0f", amount)}" else price.ifBlank { "Price TBD" }
    }

    fun getRatingText(): String = if (rating > 0) String.format("%.1f", rating) else "New"

    fun getStatusBadgeText(): String = when (status.lowercase()) {
        "active" -> {
            val booked = getBookedRoomsCount()
            if (booked > 0) "Active ($booked Booked)" else "Active"
        }
        "booked" -> getBookedRoomsDisplay()
        "rented" -> "Rented"
        "inactive" -> if (adminAction) "Suspended" else "Inactive"
        else -> status.replaceFirstChar { it.uppercase() }
    }

    fun getStatusBadgeColorRes(): Int = when (status.lowercase()) {
        "active" -> R.drawable.bg_status_active
        "booked", "rented" -> R.drawable.bg_status_booked
        "inactive" -> R.drawable.bg_status_inactive
        else -> R.drawable.bg_status_inactive
    }

    fun getFormattedPrice(): String {
        val amount = if (priceValue > 0) priceValue else monthlyRent
        return if (amount > 0) "KSh ${String.format("%,.0f", amount)}" else price.ifBlank { "Price TBD" }
    }

    fun hasWifi(): Boolean {
        val all = (amenities + features).map { it.lowercase() }
        return all.any { it.contains("wifi") || it.contains("wi-fi") || it.contains("internet") }
    }

    fun hasWater(): Boolean {
        val all = (amenities + features).map { it.lowercase() }
        return all.any { it.contains("water") }
    }

    fun getRoomCountDisplay(): String {
        return when {
            totalRooms > 0 -> "$availableRooms Rooms"
            bedrooms > 0 -> "$bedrooms Bed"
            bedroom > 0 -> "$bedroom Bed"
            else -> "—"
        }
    }

    fun getBookedRoomsCount(): Int {
        if (roomStatuses.isNotEmpty()) {
            return roomStatuses.count { it.value.equals("Booked", ignoreCase = true) || it.value.equals("Occupied", ignoreCase = true) }
        }
        if (totalRooms > 0) {
            val booked = totalRooms - getEffectiveAvailableRooms()
            return booked.coerceAtLeast(0)
        }
        return 0
    }

    fun getBookedRoomsDisplay(): String {
        val count = getBookedRoomsCount()
        return when (count) {
            0 -> "0 Rooms Booked"
            1 -> "1 Room Booked"
            else -> "$count Rooms Booked"
        }
    }

    fun getAvailableRoomsDisplay(): String {
        val count = getEffectiveAvailableRooms().coerceAtLeast(0)
        return when (count) {
            0 -> "0 Rooms"
            1 -> "1 Room"
            else -> "$count Rooms"
        }
    }

    fun getAllAmenities(): List<String> {
        val combined = mutableListOf<String>()
        combined.addAll(amenities)
        combined.addAll(features)
        return combined.distinct()
    }

    /**
     * Returns a numerically sorted list of available room numbers (e.g. RM1, RM2, RM10)
     */
    fun getAvailableSortedRoomNumbers(): List<String> {
        return roomStatuses
            .filter { it.value.equals("Available", ignoreCase = true) }
            .keys
            .sortedWith(compareBy { 
                it.substringAfter(roomPrefix).toIntOrNull() ?: it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 
            })
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromDocument(data: Map<String, Any>): Property {
            fun dateFrom(value: Any?): Date = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> Date()
            }
            fun dateOrNull(value: Any?): Date? = when (value) {
                is com.google.firebase.Timestamp -> value.toDate()
                is Date -> value
                is Long -> Date(value)
                else -> null
            }
            fun doubleFrom(value: Any?, fallback: Double = 0.0): Double = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: fallback
                else -> fallback
            }
            fun intFrom(value: Any?, fallback: Int = 0): Int = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: fallback
                else -> fallback
            }

            val statusHistoryRaw = data["statusHistory"] as? List<Map<String, Any>> ?: emptyList()
            val statusHistory = statusHistoryRaw.map { com.example.homehub.utils.StatusEntry.fromMap(it) }

            val roomImages = (data["roomImages"] as? Map<String, List<String>>) ?: emptyMap()

            val propertyId = data["id"] as? String ?: data["propertyId"] as? String ?: ""
            val statusValue = data["status"] as? String ?: "Active"
            val availableValue = data["available"] as? Boolean ?: true
            val isDeletedValue = data["isDeleted"] as? Boolean ?: false
            val isArchivedValue = data["isArchived"] as? Boolean ?: false

            // Try to get priceValue from multiple fields with fallbacks
            var pValue = doubleFrom(data["priceValue"])
            if (pValue <= 0) pValue = doubleFrom(data["price"])
            if (pValue <= 0) pValue = doubleFrom(data["monthlyRent"])

            return Property(
                id = propertyId,
                title = data["title"] as? String ?: "",
                propertyName = data["propertyName"] as? String ?: "",
                location = data["location"] as? String ?: "",
                price = data["price"] as? String ?: "",
                priceValue = pValue,
                description = data["description"] as? String ?: "",
                propertyType = data["propertyType"] as? String ?: data["type"] as? String ?: "",
                houseType = data["houseType"] as? String ?: "",
                category = data["category"] as? String ?: "",
                bedroom = intFrom(data["bedroom"]),
                bedrooms = intFrom(data["bedrooms"], 1),
                baths = intFrom(data["baths"]),
                bathrooms = intFrom(data["bathrooms"], 1),
                area = data["area"] as? String ?: "",
                rating = doubleFrom(data["rating"]),
                reviews = intFrom(data["reviews"]),
                caretakerId = data["caretakerId"] as? String ?: "",
                caretakerName = data["caretakerName"] as? String ?: "",
                caretakerFullName = data["caretakerFullName"] as? String ?: "",
                caretakerVerified = data["caretakerVerified"] as? Boolean ?: false,
                caretakerProfilePicture = data["caretakerProfilePicture"] as? String ?: "",
                ownerName = data["ownerName"] as? String ?: "Caretaker",
                ownerType = data["ownerType"] as? String ?: "Property Owner",
                firebaseImages = data["firebaseImages"] as? List<String> ?: emptyList(),
                imageUrls = data["imageUrls"] as? List<String> ?: emptyList(),
                roomImages = roomImages,
                features = data["features"] as? List<String> ?: emptyList(),
                amenities = data["amenities"] as? List<String> ?: emptyList(),
                status = statusValue,
                available = availableValue,
                adminAction = data["adminAction"] as? Boolean ?: false,
                isDeleted = isDeletedValue,
                isArchived = isArchivedValue,
                isFeatured = data["isFeatured"] as? Boolean ?: false,
                isCheap = data["isCheap"] as? Boolean ?: false,
                isBudget = data["isBudget"] as? Boolean ?: false,
                isSynced = data["isSynced"] as? Boolean ?: true,
                furnished = data["furnished"] as? Boolean ?: false,
                petFriendly = data["petFriendly"] as? Boolean ?: false,
                smokingAllowed = data["smokingAllowed"] as? Boolean ?: false,
                utilitiesIncluded = data["utilitiesIncluded"] as? Boolean ?: false,
                parkingSpaces = intFrom(data["parkingSpaces"]),
                propertySize = doubleFrom(data["propertySize"]),
                yearBuilt = intFrom(data["yearBuilt"]),
                monthlyRent = doubleFrom(data["monthlyRent"]),
                securityDeposit = doubleFrom(data["securityDeposit"]),
                propertyTax = doubleFrom(data["propertyTax"]),
                managementFee = doubleFrom(data["managementFee"]),
                totalBookings = intFrom(data["totalBookings"]),
                totalRevenue = doubleFrom(data["totalRevenue"]),
                totalRooms = intFrom(data["totalRooms"]),
                availableRooms = intFrom(data["availableRooms"]),
                tenantName = data["tenantName"] as? String ?: "",
                tenantPhone = data["tenantPhone"] as? String ?: "",
                inactiveBy = data["inactiveBy"] as? String ?: "",
                deletedBy = data["deletedBy"] as? String ?: "",
                deletionReason = data["deletionReason"] as? String ?: "",
                archiveReason = data["archiveReason"] as? String ?: "",
                statusChangeReason = data["statusChangeReason"] as? String ?: "",
                bookedBy = data["bookedBy"] as? String ?: "",
                roomPrefix = data["roomPrefix"] as? String ?: "RM",
                roomStatuses = data["roomStatuses"] as? Map<String, String> ?: emptyMap(),
                isPlot = data["isPlot"] as? Boolean ?: false,
                hostFullName = data["hostFullName"] as? String ?: "",
                hostCountry = data["hostCountry"] as? String ?: "",
                hostPhoneNumber = data["hostPhoneNumber"] as? String ?: "",
                hostMonthsExperience = intFrom(data["hostMonthsExperience"]),
                hostVerified = data["hostVerified"] as? Boolean ?: false,
                duration = data["duration"] as? String ?: "per month",
                statusHistory = statusHistory,
                createdAt = dateFrom(data["createdAt"]),
                updatedAt = dateFrom(data["updatedAt"]),
                inactiveAt = dateOrNull(data["inactiveAt"]),
                reactivatedAt = dateOrNull(data["reactivatedAt"]),
                deletedAt = dateOrNull(data["deletedAt"]),
                archivedAt = dateOrNull(data["archivedAt"]),
                bookedAt = dateOrNull(data["bookedAt"]),
                occupiedAt = dateOrNull(data["occupiedAt"]),
                leaseStart = dateFrom(data["leaseStart"]),
                leaseEnd = dateFrom(data["leaseEnd"]),
                lastMaintenance = dateFrom(data["lastMaintenance"]),
                nextMaintenance = dateFrom(data["nextMaintenance"]),
                insuranceExpiry = dateFrom(data["insuranceExpiry"]),
                lastStatusChange = dateOrNull(data["lastStatusChange"]),
                adminActionAt = dateOrNull(data["adminActionAt"]),
                restoredAt = dateOrNull(data["restoredAt"]),
                imageUrl = data["imageUrl"] as? String ?: "",
                viewCount = intFrom(data["viewCount"], intFrom(data["views"])),
                likeCount = intFrom(data["likeCount"], intFrom(data["likes"])),
                latitude = doubleFrom(data["latitude"]),
                longitude = doubleFrom(data["longitude"]),
                deposit = (data["deposit"] as? Number)?.toDouble() ?: 0.0
            )
        }
    }
}