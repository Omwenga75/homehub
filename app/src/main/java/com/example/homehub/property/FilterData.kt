package com.example.homehub.property

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilterData(
    var minPrice: Double = 0.0,
    var maxPrice: Double = 70000.0,
    var location: String = "",
    var propertyTypes: Set<String> = emptySet(),
    var bedroomCounts: Set<Int> = emptySet(),
    var amenities: Set<String> = emptySet()
) : Parcelable {
    val minPriceDisplay: String get() = "KSh $minPrice"
    val maxPriceDisplay: String get() = "KSh $maxPrice"
    fun getPriceList(): List<Float> = listOf(minPrice.toFloat(), maxPrice.toFloat())

    fun hasActiveFilters(): Boolean {
        return minPrice > 0.0 || 
               maxPrice < 70000.0 || 
               location.isNotEmpty() || 
               propertyTypes.isNotEmpty() || 
               bedroomCounts.isNotEmpty() || 
               amenities.isNotEmpty()
    }

    fun clear() {
        minPrice = 0.0
        maxPrice = 70000.0
        location = ""
        propertyTypes = emptySet()
        bedroomCounts = emptySet()
        amenities = emptySet()
    }
}
