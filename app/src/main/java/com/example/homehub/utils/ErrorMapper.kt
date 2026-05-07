package com.example.homehub.utils

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {

    /**
     * Maps a Throwable to a user-friendly error message.
     */
    fun map(e: Throwable?): String {
        if (e == null) return "Unknown error occurred"

        return when (e) {
            is UnknownHostException, is ConnectException -> 
                "Unable to connect. Please check your internet connection."
            
            is SocketTimeoutException -> 
                "Connection timed out. Please try again."

            else -> map(e.message)
        }
    }

    /**
     * Maps a raw error string (e.g. from Firebase) to a user-friendly message.
     */
    fun map(message: String?): String {
        if (message == null || message.isBlank()) return "Something went wrong. Please try again."

        val msg = message.lowercase()

        return when {
            // Firebase Auth specific
            msg.contains("invalid-email") || msg.contains("invalid email") -> 
                "Invalid email format. Please check and try again."
            
            msg.contains("user-not-found") || msg.contains("no user record") -> 
                "Account not found. Please sign up first."
            
            msg.contains("wrong-password") || msg.contains("invalid credentials") -> 
                "Incorrect email or password."
            
            msg.contains("email-already-in-use") || msg.contains("email already exists") -> 
                "Email is already registered. Try logging in instead."
            
            msg.contains("weak-password") -> 
                "Password is too weak. Please use a stronger one."
            
            msg.contains("user-disabled") -> 
                "This account has been disabled. Contact support."

            // Firebase Firestore / Permissions
            msg.contains("permission-denied") -> 
                "Access denied. You don't have permission for this action."
            
            msg.contains("unavailable") -> 
                "Service temporarily unavailable. Please try later."

            // Network / Connectivity (String fallbacks)
            msg.contains("network error") || msg.contains("timeout") -> 
                "Network failure. Please check your data connection."

            // Location / GPS
            msg.contains("location") || msg.contains("gps") -> 
                "Location error. Please ensure GPS is enabled."

            // Default fallback
            else -> "Processing failed. Please try again."
        }
    }
}
