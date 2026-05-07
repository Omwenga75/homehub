package com.example.homehub.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent cache for Dashboard statistics to enable instant UI loading.
 * Stores a snapshot of the last known platform metrics on disk.
 */
class DashboardCache(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("DashboardStatsCache", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOTAL_USERS = "total_users"
        private const val KEY_TOTAL_STUDENTS = "total_students"
        private const val KEY_TOTAL_CARETAKERS = "total_caretakers"
        private const val KEY_TOTAL_SUPPLIERS = "total_suppliers"
        private const val KEY_TOTAL_PROPERTIES = "total_properties"
        private const val KEY_VACANT_PROPERTIES = "vacant_properties"
        private const val KEY_TOTAL_BOOKINGS = "total_bookings"
        private const val KEY_LAST_UPDATE_TIME = "last_update_ts"
        private const val KEY_TOTAL_REVENUE = "total_revenue"
    }

    /**
     * Data class to represent the full dashboard snapshot
     */
    data class StatsSnapshot(
        val totalUsers: Int = 0,
        val students: Int = 0,
        val caretakers: Int = 0,
        val suppliers: Int = 0,
        val properties: Int = 0,
        val vacant: Int = 0,
        val bookings: Int = 0,
        val totalRevenue: Double = 0.0,
        val lastUpdate: Long = 0L
    )

    fun saveSnapshot(stats: StatsSnapshot) {
        prefs.edit().apply {
            putInt(KEY_TOTAL_USERS, stats.totalUsers)
            putInt(KEY_TOTAL_STUDENTS, stats.students)
            putInt(KEY_TOTAL_CARETAKERS, stats.caretakers)
            putInt(KEY_TOTAL_SUPPLIERS, stats.suppliers)
            putInt(KEY_TOTAL_PROPERTIES, stats.properties)
            putInt(KEY_VACANT_PROPERTIES, stats.vacant)
            putInt(KEY_TOTAL_BOOKINGS, stats.bookings)
            putLong(KEY_TOTAL_REVENUE, java.lang.Double.doubleToRawLongBits(stats.totalRevenue))
            putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getSnapshot(): StatsSnapshot {
        return StatsSnapshot(
            totalUsers = prefs.getInt(KEY_TOTAL_USERS, 0),
            students = prefs.getInt(KEY_TOTAL_STUDENTS, 0),
            caretakers = prefs.getInt(KEY_TOTAL_CARETAKERS, 0),
            suppliers = prefs.getInt(KEY_TOTAL_SUPPLIERS, 0),
            properties = prefs.getInt(KEY_TOTAL_PROPERTIES, 0),
            vacant = prefs.getInt(KEY_VACANT_PROPERTIES, 0),
            bookings = prefs.getInt(KEY_TOTAL_BOOKINGS, 0),
            totalRevenue = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_TOTAL_REVENUE, 0L)),
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
