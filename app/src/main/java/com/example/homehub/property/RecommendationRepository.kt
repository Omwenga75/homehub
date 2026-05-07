package com.example.homehub.property

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RecommendationRepository {
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // DEPRECATED: Use InteractionManager.logView(property) instead
    // fun logPropertyView(property: Property) { ... }

    fun getRecommendations(properties: List<Property>): List<Property> {
        return properties.sortedWith(compareByDescending<Property> { it.isFeatured }
            .thenByDescending { it.rating }
            .thenByDescending { it.reviews }).take(10)
    }
}
