package com.example.homehub.utils

import android.util.Log
import com.example.homehub.property.Property
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global Data Cache Manager for instant dashboard loading across all user types.
 * Provides persistent caching that survives activity lifecycle changes.
 */
object GlobalDataCache {

    private const val TAG = "GlobalDataCache"
    private const val CACHE_DURATION = 1800000L // 30 minutes
    private const val MAX_CACHE_SIZE = 1000

    // Core data caches
    private var propertiesCache = ConcurrentHashMap<String, Property>()
    private val usersCache = ConcurrentHashMap<String, Map<String, Any>>()
    private val bookingsCache = ConcurrentHashMap<String, Map<String, Any>>()
    private val reviewsCache = ConcurrentHashMap<String, Map<String, Any>>()

    // Metadata
    private var propertiesLastUpdate = 0L
    private var usersLastUpdate = 0L
    private var bookingsLastUpdate = 0L
    private var reviewsLastUpdate = 0L

    // Loading states
    private val isLoadingProperties = AtomicBoolean(false)
    private val isLoadingUsers = AtomicBoolean(false)
    private val isLoadingBookings = AtomicBoolean(false)
    private val isLoadingReviews = AtomicBoolean(false)

    // Background preload job
    private var preloadJob: Job? = null

    /**
     * Initialize and start background preloading
     */
    fun initialize() {
        if (isLoadingUsers.get() || isLoadingProperties.get()) return
        preloadJob?.cancel()
        preloadJob = CoroutineScope(Dispatchers.IO).launch {
            preloadAllData()
        }
    }

    /**
     * Get cached properties - returns immediately if available
     */
    fun getProperties(): List<Property> {
        return propertiesCache.values.toList()
    }

    /**
     * Get cached users
     */
    fun getUsers(): List<Map<String, Any>> {
        return usersCache.values.toList()
    }

    /**
     * Get cached bookings
     */
    fun getBookings(): List<Map<String, Any>> {
        return bookingsCache.values.toList()
    }

    /**
     * Get cached reviews
     */
    fun getReviews(): List<Map<String, Any>> {
        return reviewsCache.values.toList()
    }

    /**
     * Incrementally update or add a user to the cache
     */
    fun updateUserCache(uid: String, updates: Map<String, Any>) {
        val existing = usersCache[uid]?.toMutableMap() ?: mutableMapOf<String, Any>("uid" to uid)
        existing.putAll(updates)
        usersCache[uid] = existing
        Log.d(TAG, "Updated user $uid in cache")
    }

    /**
     * Check if properties cache is fresh
     */
    fun isPropertiesCacheFresh(): Boolean {
        return propertiesCache.isNotEmpty() &&
               (System.currentTimeMillis() - propertiesLastUpdate) < CACHE_DURATION
    }

    /**
     * Check if users cache is fresh
     */
    fun isUsersCacheFresh(): Boolean {
        return usersCache.isNotEmpty() &&
               (System.currentTimeMillis() - usersLastUpdate) < CACHE_DURATION
    }

    /**
     * Check if bookings cache is fresh
     */
    fun isBookingsCacheFresh(): Boolean {
        return bookingsCache.isNotEmpty() &&
               (System.currentTimeMillis() - bookingsLastUpdate) < CACHE_DURATION
    }

    /**
     * Check if reviews cache is fresh
     */
    fun isReviewsCacheFresh(): Boolean {
        return reviewsCache.isNotEmpty() &&
               (System.currentTimeMillis() - reviewsLastUpdate) < CACHE_DURATION
    }

    /**
     * Force refresh all data in background
     */
    fun refreshAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            preloadAllData()
        }
    }

    /**
     * Preload all data in background. If network is unavailable, we skip the refresh
     * but keep existing cache contents.
     */
    private suspend fun preloadAllData() {
        // Skip background refresh if strictly offline to save battery and avoid errors
        // Note: Firestore will still work with local cache if we are offline.
        // This check is mainly to avoid unnecessary IO suspension while the device is offline.
        if (System.currentTimeMillis() - propertiesLastUpdate < CACHE_DURATION) {
             Log.d(TAG, "Caches are fresh, skipping preload")
             return
        }

        try {
            // Load properties
            if (!isLoadingProperties.getAndSet(true)) {
                loadPropertiesData()
                isLoadingProperties.set(false)
            }

            // Load users
            if (!isLoadingUsers.getAndSet(true)) {
                loadUsersData()
                isLoadingUsers.set(false)
            }

            // Load bookings
            if (!isLoadingBookings.getAndSet(true)) {
                loadBookingsData()
                isLoadingBookings.set(false)
            }

            // Load reviews
            if (!isLoadingReviews.getAndSet(true)) {
                loadReviewsData()
                isLoadingReviews.set(false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error preloading data", e)
            resetLoadingFlags()
        }
    }

    private suspend fun loadPropertiesData() {
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("properties")
                .limit(MAX_CACHE_SIZE.toLong())
                .get()
                .await()

            val newProperties = ConcurrentHashMap<String, Property>()
            for (doc in snapshot.documents) {
                try {
                    val property = Property.fromDocument(doc.data ?: emptyMap())
                    property.id = doc.id
                    newProperties[doc.id] = property
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing property ${doc.id}", e)
                }
            }
            
            // Atomic update: swap reference to avoid "vanishing" states during iteration
            propertiesCache = newProperties
            
            propertiesLastUpdate = System.currentTimeMillis()
            Log.d(TAG, "Loaded ${propertiesCache.size} properties into cache")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading properties", e)
        }
    }

    private suspend fun loadUsersData() {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // 1. Load basic user profiles
            val userSnapshot = db.collection("users")
                .limit(MAX_CACHE_SIZE.toLong())
                .get()
                .await()

            val tempCache = ConcurrentHashMap<String, MutableMap<String, Any>>()
            
            for (doc in userSnapshot.documents) {
                val data = doc.data?.toMutableMap() ?: mutableMapOf<String, Any>()
                data["uid"] = doc.id
                tempCache[doc.id] = data
            }

            // 2. Load student names to enrich profiles
            try {
                val studentSnapshot = db.collection("students").get().await()
                for (doc in studentSnapshot.documents) {
                    val fullName = doc.getString("fullName") ?: doc.getString("name")
                    if (!fullName.isNullOrBlank()) {
                        val userData = tempCache.getOrPut(doc.id) { mutableMapOf<String, Any>("uid" to doc.id) }
                        userData["fullName"] = fullName
                        userData["isStudent"] = true
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error enriching student names", e) }

            // 3. Load caretaker names to enrich profiles
            try {
                val caretakerSnapshot = db.collection("caretakers").get().await()
                for (doc in caretakerSnapshot.documents) {
                    val fullName = doc.getString("fullName") 
                        ?: doc.getString("name")
                        ?: doc.getString("caretakerFullName")
                        ?: doc.getString("businessName")
                        ?: doc.getString("caretakerName")
                    
                    if (!fullName.isNullOrBlank()) {
                        val userData = tempCache.getOrPut(doc.id) { mutableMapOf<String, Any>("uid" to doc.id) }
                        userData["fullName"] = fullName
                        userData["isCaretaker"] = true
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error enriching caretaker names", e) }

            usersCache.clear()
            usersCache.putAll(tempCache)
            usersLastUpdate = System.currentTimeMillis()
            Log.d(TAG, "Loaded and enriched ${usersCache.size} users into cache with real names")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading users", e)
        }
    }

    private suspend fun loadBookingsData() {
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("bookings")
                .limit(MAX_CACHE_SIZE.toLong())
                .get()
                .await()

            bookingsCache.clear()
            for (doc in snapshot.documents) {
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                bookingsCache[doc.id] = data
            }
            bookingsLastUpdate = System.currentTimeMillis()
            Log.d(TAG, "Loaded ${bookingsCache.size} bookings into cache")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading bookings", e)
        }
    }

    private suspend fun loadReviewsData() {
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("reviews")
                .limit(MAX_CACHE_SIZE.toLong())
                .get()
                .await()

            reviewsCache.clear()
            for (doc in snapshot.documents) {
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                reviewsCache[doc.id] = data
            }
            reviewsLastUpdate = System.currentTimeMillis()
            Log.d(TAG, "Loaded ${reviewsCache.size} reviews into cache")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading reviews", e)
        }
    }

    private fun resetLoadingFlags() {
        isLoadingProperties.set(false)
        isLoadingUsers.set(false)
        isLoadingBookings.set(false)
        isLoadingReviews.set(false)
    }

    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        propertiesCache.clear()
        usersCache.clear()
        bookingsCache.clear()
        reviewsCache.clear()
        propertiesLastUpdate = 0L
        usersLastUpdate = 0L
        bookingsLastUpdate = 0L
        reviewsLastUpdate = 0L
        Log.d(TAG, "All caches cleared")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "properties" to propertiesCache.size,
            "users" to usersCache.size,
            "bookings" to bookingsCache.size,
            "reviews" to reviewsCache.size,
            "propertiesFresh" to isPropertiesCacheFresh(),
            "usersFresh" to isUsersCacheFresh(),
            "bookingsFresh" to isBookingsCacheFresh(),
            "reviewsFresh" to isReviewsCacheFresh()
        )
    }
}