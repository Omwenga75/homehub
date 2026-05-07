package com.example.homehub.auth

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun setLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }

    fun isRoleSelected(): Boolean = prefs.getBoolean(KEY_ROLE_SELECTED, false)

    fun setRoleSelected(selected: Boolean) {
        prefs.edit().putBoolean(KEY_ROLE_SELECTED, selected).apply()
    }

    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun saveLastScreen(screen: String) {
        prefs.edit().putString(KEY_LAST_SCREEN, screen).apply()
    }

    fun saveUserMode(isManagementMode: Boolean) {
        prefs.edit().putBoolean(KEY_USER_MODE, isManagementMode).apply()
    }

    fun setBasicVerificationCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_BASIC_VERIFICATION, completed).apply()
    }

    fun isBasicVerificationCompleted(): Boolean = prefs.getBoolean(KEY_BASIC_VERIFICATION, false)


    /**
     * Maps a role string to its corresponding Dashboard Activity class.
     */
    fun getDashboardClass(role: String?): Class<*> {
        return when (role?.lowercase()) {
            "admin" -> com.example.homehub.admin.AdminDashboardActivity::class.java
            "caretaker" -> com.example.homehub.caretaker.CaretakerDashboardActivity::class.java
            "water_supplier" -> com.example.homehub.supplier.WaterSupplierDashboardActivity::class.java
            "unassigned" -> com.example.homehub.auth.AccountVerificationActivity::class.java
            else -> com.example.homehub.student.StudentDashboardActivity::class.java
        }
    }

    /**
     * Maps a role string to its corresponding Profile Activity class.
     */
    fun getProfileClass(role: String?): Class<*> {
        return when (role?.lowercase()) {
            "admin" -> com.example.homehub.admin.AdminProfileActivity::class.java
            "caretaker" -> com.example.homehub.caretaker.CaretakerProfileActivity::class.java
            "water_supplier" -> com.example.homehub.supplier.WaterSupplierProfileActivity::class.java
            else -> com.example.homehub.student.StudentProfileActivity::class.java
        }
    }


    fun saveCachedUserProfile(name: String, initials: String, profileUrl: String?) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_INITIALS, initials)
            putString(KEY_USER_IMAGE, profileUrl)
            apply()
        }
    }

    fun updateCachedUserImageUrl(profileUrl: String?, lastUpdate: Long? = null) {
        val editor = prefs.edit()
        editor.putString(KEY_USER_IMAGE, profileUrl)
        lastUpdate?.let { editor.putLong(KEY_IMAGE_LAST_UPDATE, it) }
        editor.apply()
    }

    fun saveLastImageUpdate(timestamp: Long) {
        prefs.edit().putLong(KEY_IMAGE_LAST_UPDATE, timestamp).apply()
    }

    fun getLastImageUpdate(): Long = prefs.getLong(KEY_IMAGE_LAST_UPDATE, 0L)

    fun saveCachedUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun savePhoneNumber(phoneNumber: String) {
        prefs.edit().putString(KEY_PHONE_NUMBER, phoneNumber).apply()
    }

    fun getPhoneNumber(): String? {
        return prefs.getString(KEY_PHONE_NUMBER, null)
    }

    fun saveVerificationStatus(status: String) {
        prefs.edit().putString(KEY_VERIFICATION_STATUS, status).apply()
    }

    fun getVerificationStatus(): String? = prefs.getString(KEY_VERIFICATION_STATUS, null)

    private fun getPlaceholderName(userId: String): String {
        val shortId = if (userId.length >= 4) userId.substring(0, 4).uppercase() else "DEMO"
        return "User_$shortId"
    }

    fun getCachedUserName(userId: String? = null): String {
        val cached = prefs.getString(KEY_USER_NAME, null)
        if (!cached.isNullOrEmpty()) return cached
        return if (userId != null) getPlaceholderName(userId) else "System User"
    }

    fun getCachedUserInitials(): String? = prefs.getString(KEY_USER_INITIALS, null)
    fun getCachedUserImageUrl(): String? = prefs.getString(KEY_USER_IMAGE, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "isLoggedIn" // Unified with UserLoginActivity
        private const val KEY_ROLE_SELECTED = "roleSelected"
        private const val KEY_USER_ROLE = "role"
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_NAME = "cached_name"
        private const val KEY_USER_INITIALS = "cached_initials"
        private const val KEY_USER_IMAGE = "cached_image_url"
        private const val KEY_IMAGE_LAST_UPDATE = "cached_image_last_update"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_LAST_SCREEN = "last_screen"
        private const val KEY_USER_MODE = "user_mode_management"
        private const val KEY_BASIC_VERIFICATION = "basicVerificationCompleted"
        private const val KEY_VERIFICATION_STATUS = "verificationStatus"

        const val SCREEN_ADMIN_DASHBOARD = "admin_dashboard"
        const val SCREEN_CARETAKER_DASHBOARD = "caretaker_dashboard"
        const val SCREEN_WATER_SUPPLIER_DASHBOARD = "water_supplier_dashboard"
        const val SCREEN_STUDENT_DASHBOARD = "student_dashboard"
        const val SCREEN_USER_DASHBOARD = "student_dashboard" // Alias for backward compatibility
    }
}