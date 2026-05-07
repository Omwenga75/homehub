package com.example.homehub.admin

import android.content.Context
import android.content.SharedPreferences

class AdminSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_ADMIN_LOGGED_IN = "is_admin_logged_in"
        private const val KEY_ADMIN_NAME = "admin_name"
        private const val KEY_ADMIN_ROLE = "admin_role"
        private const val KEY_ADMIN_DEPT = "admin_dept"
        private const val KEY_ADMIN_EMP_ID = "admin_emp_id"
        private const val KEY_ADMIN_BIO = "admin_bio"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_IMAGE_LAST_UPDATE = "admin_image_last_update"
    }

    fun isAdminLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_ADMIN_LOGGED_IN, false)

    fun createAdminSession(
        name: String,
        adminId: String = "HH-ADM-001",
        role: String = "Admin",
        permissions: String = "full_access"
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ADMIN_LOGGED_IN, true)
            putString(KEY_ADMIN_NAME, name)
            putString(KEY_ADMIN_EMP_ID, adminId)
            putString(KEY_ADMIN_ROLE, role)
            putString("admin_permissions", permissions)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getAdminName(): String = prefs.getString(KEY_ADMIN_NAME, "Admin") ?: "Admin"
    fun getAdminRole(): String = prefs.getString(KEY_ADMIN_ROLE, "Admin") ?: "Admin"
    fun getAdminDepartment(): String = prefs.getString(KEY_ADMIN_DEPT, "Management") ?: "Management"
    fun getAdminEmployeeId(): String = prefs.getString(KEY_ADMIN_EMP_ID, "HH-ADM-001") ?: "HH-ADM-001"
    fun getAdminBio(): String = prefs.getString(KEY_ADMIN_BIO, "HomeHub System Administrator") ?: "HomeHub System Administrator"
    fun getLoginTime(): Long = prefs.getLong(KEY_LOGIN_TIME, 0L)

    fun setAdminName(name: String) {
        prefs.edit().putString(KEY_ADMIN_NAME, name).apply()
    }

    fun setAdminRole(role: String) {
        prefs.edit().putString(KEY_ADMIN_ROLE, role).apply()
    }

    fun setAdminDepartment(dept: String) {
        prefs.edit().putString(KEY_ADMIN_DEPT, dept).apply()
    }

    fun setAdminEmployeeId(empId: String) {
        prefs.edit().putString(KEY_ADMIN_EMP_ID, empId).apply()
    }

    fun setAdminBio(bio: String) {
        prefs.edit().putString(KEY_ADMIN_BIO, bio).apply()
    }

    fun saveLastAdminImageUpdate(timestamp: Long) {
        prefs.edit().putLong(KEY_IMAGE_LAST_UPDATE, timestamp).apply()
    }

    fun getLastAdminImageUpdate(): Long = prefs.getLong(KEY_IMAGE_LAST_UPDATE, 0L)

    fun clearAdminSession() {
        prefs.edit().clear().apply()
    }
}
