package com.example.homehub.utils

import android.content.Context
import android.util.Log
import com.example.homehub.property.Property
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages local caching of property data for a "hybrid" (offline-first) experience.
 * Used primarily by caretakers to see their listings even without a stable connection.
 */
object HybridPropertyManager {
    private const val PREFS_NAME = "HybridProperties"
    private const val KEY_PROPERTIES = "local_properties_list"
    private val gson = Gson()

    /**
     * Retrieves all locally cached properties as a list of data maps.
     */
    fun getLocalProperties(context: Context): List<Map<String, Any>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROPERTIES, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("HybridPropertyManager", "❌ Error parsing local properties: ${e.message}")
            emptyList()
        }
    }

    /**
     * Saves a single property to local storage, updating it if it already exists.
     */
    fun saveLocalProperty(context: Context, propertyData: Map<String, Any>) {
        val propertyId = propertyData["propertyId"] as? String ?: return
        val currentList = getLocalProperties(context).toMutableList()
        
        // Remove existing if present
        currentList.removeAll { it["propertyId"] == propertyId }
        
        // Add new
        currentList.add(propertyData)
        
        saveAll(context, currentList)
        Log.d("HybridPropertyManager", "✅ Saved property locally: $propertyId")
    }

    /**
     * Updates the status of a locally cached property.
     */
    fun updateLocalPropertyStatus(context: Context, propertyId: String, newStatus: String) {
        val currentList = getLocalProperties(context).toMutableList()
        val index = currentList.indexOfFirst { it["propertyId"] == propertyId }
        
        if (index != -1) {
            val updatedProperty = currentList[index].toMutableMap()
            updatedProperty["status"] = newStatus
            currentList[index] = updatedProperty
            saveAll(context, currentList)
            Log.d("HybridPropertyManager", "🔄 Updated local status for $propertyId to $newStatus")
        }
    }

    /**
     * Deletes a property from local storage.
     */
    fun deleteLocalProperty(context: Context, propertyId: String) {
        val currentList = getLocalProperties(context).toMutableList()
        val initialSize = currentList.size
        currentList.removeAll { it["propertyId"] == propertyId }
        
        if (currentList.size < initialSize) {
            saveAll(context, currentList)
            Log.d("HybridPropertyManager", "🗑️ Deleted local property: $propertyId")
        }
    }

    fun clearAllProperties(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d("HybridPropertyManager", "🧹 Cleared all local properties")
    }

    private fun saveAll(context: Context, list: List<Map<String, Any>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_PROPERTIES, json).apply()
    }
}
