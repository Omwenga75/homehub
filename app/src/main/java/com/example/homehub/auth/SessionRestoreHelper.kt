package com.example.homehub.auth

import android.content.Context

object SessionRestoreHelper {
    private const val PREFS_NAME = "session_restore_prefs"
    private const val KEY_RESTORE_COUNT = "restore_count"

    fun resetRestoreCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RESTORE_COUNT, 0).apply()
    }

    fun restoreSession() {
        // Implementation for session restoration
    }
}
