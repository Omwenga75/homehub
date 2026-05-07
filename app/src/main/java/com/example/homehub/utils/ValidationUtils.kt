package com.example.homehub.utils


data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object ValidationUtils {
    fun isValidEmail(email: String): ValidationResult {
        return if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Please enter a valid email address")
        }
    }

    fun isValidPhone(phone: String): ValidationResult {
        return if (phone.length >= 10) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Phone number must be at least 10 digits")
        }
    }

    fun isValidName(name: String): ValidationResult {
        return if (name.trim().length < 3) {
            ValidationResult(false, "Name is too short")
        } else if (!name.trim().contains(" ")) {
            ValidationResult(false, "Please enter your full official name")
        } else {
            ValidationResult(true)
        }
    }

    fun formatName(name: String): String {
        return name.trim().split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /** Standardizes phone number for Firestore (e.g., handles Kenyan 07xx -> +2547xx) */
    fun formatPhoneForFirestore(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("254") -> "+$digits"
            digits.startsWith("0") && digits.length == 10 -> "+254${digits.substring(1)}"
            digits.length == 9 -> "+254$digits"
            else -> if (digits.startsWith("+")) digits else "+$digits"
        }
    }

    fun isValidId(id: String, role: String): ValidationResult {
        val trimmed = id.trim()
        val isNumeric = trimmed.all { it.isDigit() }
        
        return when (role.uppercase()) {
            "STUDENT" -> {
                if (trimmed.length < 5) {
                    ValidationResult(false, "Registration Number must be at least 5 characters")
                } else {
                    ValidationResult(true)
                }
            }
            "CARETAKER", "WATER_SUPPLIER" -> {
                if (trimmed.length != 8) {
                    ValidationResult(false, "National ID must be exactly 8 digits")
                } else if (!isNumeric) {
                    ValidationResult(false, "National ID must contain numbers only")
                } else {
                    ValidationResult(true)
                }
            }
            else -> {
                if (trimmed.length < 5) {
                    ValidationResult(false, "ID Number is too short")
                } else {
                    ValidationResult(true)
                }
            }
        }
    }
}
