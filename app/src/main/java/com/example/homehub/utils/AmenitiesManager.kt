package com.example.homehub.utils

import android.content.Context
import com.example.homehub.R
import com.example.homehub.property.Category
import com.example.homehub.property.Feature

object AmenitiesManager {
    fun getCategories(): List<Category> = emptyList()
    fun getFeatures(): List<Feature> = emptyList()
    
    fun getFeaturesForDisplay(amenities: List<String>, context: Context): List<Feature> {
        return amenities.map { name ->
            val lower = name.lowercase()
            val resId = when {
                lower.contains("wifi") -> R.drawable.ic_wifi
                lower.contains("water") -> R.drawable.ic_water
                lower.contains("park") -> R.drawable.ic_parking
                lower.contains("kitchen") -> R.drawable.ic_kitchen
                lower.contains("garden") -> R.drawable.ic_garden
                lower.contains("security") || lower.contains("cctv") -> R.drawable.ic_security
                lower.contains("gym") || lower.contains("fitness") -> R.drawable.ic_gym
                lower.contains("pool") -> R.drawable.ic_pool
                lower.contains("ac") || lower.contains("air condition") -> R.drawable.ic_ac
                lower.contains("balcony") -> R.drawable.ic_balcony
                lower.contains("pet") -> R.drawable.ic_pet_friendly
                lower.contains("bath") -> R.drawable.ic_bath
                lower.contains("bed") -> R.drawable.ic_bed
                lower.contains("elevator") || lower.contains("lift") -> R.drawable.ic_elevator
                lower.contains("electric") || lower.contains("power") -> R.drawable.ic_electricity
                else -> R.drawable.ic_default_amenity // Use a default icon or generic check
            }
            Feature(name, resId)
        }
    }
}
