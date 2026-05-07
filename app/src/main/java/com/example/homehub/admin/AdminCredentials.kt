package com.example.homehub.admin

object AdminCredentials {
    const val ADMIN_EMAIL = "admin@homehub.com"
    private const val ADMIN_PASSWORD = "!Admin@12" // In a real app, this should be handled securely

    data class AdminResult(
        val isValid: Boolean,
        val adminName: String? = null,
        val errorMessage: String? = null
    )

    fun isValidAdmin(context: android.content.Context, email: String, pass: String, deviceId: String): AdminResult {
        return if (email.equals(ADMIN_EMAIL, ignoreCase = true) && pass == ADMIN_PASSWORD) {
            AdminResult(true, "System Admin")
        } else {
            AdminResult(false, errorMessage = "Invalid admin credentials")
        }
    }
}
