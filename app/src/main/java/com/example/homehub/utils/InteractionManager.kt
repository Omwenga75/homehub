package com.example.homehub.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Handles atomic likes and views for properties.
 * Enforces the "1 Account = 1 Like = 1 View" rule.
 */
object InteractionManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Toggles a like/favorite for a property.
     * If liking, also ensures a unique view is recorded if the user hasn't viewed it yet.
     */
    fun toggleLike(propertyId: String, isLiking: Boolean, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: run {
            onComplete(false)
            return
        }
        val userId = user.uid
        val likeDocId = "${userId}_$propertyId"
        val viewDocId = "${userId}_$propertyId"

        db.runTransaction { transaction ->
            val likeRef = db.collection("uniqueLikes").document(likeDocId)
            val viewRef = db.collection("uniqueViews").document(viewDocId)
            val propertyRef = db.collection("properties").document(propertyId)
            val userFavoriteRef = db.collection("users").document(userId).collection("favorites").document(propertyId)

            val likeExists = transaction.get(likeRef).exists()
            val viewExists = transaction.get(viewRef).exists()

            if (isLiking && !likeExists) {
                // ADD LIKE
                transaction.set(likeRef, mapOf(
                    "userId" to userId,
                    "propertyId" to propertyId,
                    "timestamp" to FieldValue.serverTimestamp()
                ))
                transaction.update(propertyRef, "likeCount", FieldValue.increment(1))
                
                // Sync with user favorites subcollection for dashboard queries
                transaction.set(userFavoriteRef, mapOf("addedAt" to FieldValue.serverTimestamp()))

                // ENSURE VIEW RECORDED: "1 Like = 1 View"
                if (!viewExists) {
                    transaction.set(viewRef, mapOf(
                        "userId" to userId,
                        "propertyId" to propertyId,
                        "timestamp" to FieldValue.serverTimestamp()
                    ))
                    transaction.update(propertyRef, "viewCount", FieldValue.increment(1))
                }
            } else if (!isLiking && likeExists) {
                // REMOVE LIKE
                transaction.delete(likeRef)
                transaction.update(propertyRef, "likeCount", FieldValue.increment(-1))
                transaction.delete(userFavoriteRef)
            }
        }.addOnSuccessListener { 
            onComplete(true) 
        }.addOnFailureListener { 
            onComplete(false) 
        }
    }

    /**
     * Logs a unique view for a property.
     * Uses a transaction to ensure atomicity: 1 Account = 1 View.
     */
    fun logView(property: com.example.homehub.property.Property) {
        val user = auth.currentUser ?: return
        val userId = user.uid
        val propertyId = property.id
        val viewDocId = "${userId}_$propertyId"
        
        val viewRef = db.collection("uniqueViews").document(viewDocId)
        val propertyRef = db.collection("properties").document(propertyId)

        db.runTransaction { transaction ->
            val viewDoc = transaction.get(viewRef)
            
            if (!viewDoc.exists()) {
                // First time this user views this specific property
                transaction.set(viewRef, mapOf(
                    "propertyId" to propertyId,
                    "userId" to userId,
                    "viewerId" to userId, // Compatibility with RecommendationRepo
                    "timestamp" to FieldValue.serverTimestamp(),
                    "category" to property.category,
                    "location" to property.location
                ))
                
                // Increment the counter only once
                transaction.update(propertyRef, "viewCount", FieldValue.increment(1))
            }
        }.addOnSuccessListener {
            // Log.d("InteractionManager", "Verified unique view for $propertyId")
        }.addOnFailureListener { e ->
            // Log.e("InteractionManager", "Failed to log unique view", e)
        }
    }
}
