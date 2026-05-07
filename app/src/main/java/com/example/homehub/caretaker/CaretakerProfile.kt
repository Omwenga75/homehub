package com.example.homehub.caretaker

import com.example.homehub.property.Property
import com.example.homehub.property.Review

data class CaretakerProfile(
    val id: String,
    val name: String,
    val profilePicture: String = "",
    val isVerified: Boolean = false,
    val caretakerType: String = "Professional Caretaker",
    val location: String = "",
    val rating: Double = 0.0,
    val reviews: Int = 0,
    val caretakerSince: String = "",
    val propertiesCount: Int = 0,
    val responseRate: Int = 0,
    val responseTime: String = "",
    val about: String = "",
    val languages: String = "",
    val phoneNumber: String = "",
    val totalEarnings: String = "Sh 0",
    val livesIn: String = "Not available",
    val properties: List<Property> = emptyList(),
    val caretakerReviews: List<Review> = emptyList()
)
