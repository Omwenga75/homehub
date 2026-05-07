package com.example.homehub.utils

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class StatusEntry(
    val status: String = "",
    val reason: String = "",
    val changedBy: String = "",
    val changedAt: Date = Date()
) : Parcelable {

    companion object {
        fun fromMap(map: Map<String, Any>): StatusEntry {
            return try {
                val changedAt = when (val timestamp = map["changedAt"]) {
                    is Timestamp -> timestamp.toDate()
                    is Date -> timestamp
                    is Long -> Date(timestamp)
                    else -> Date()
                }

                StatusEntry(
                    status = map["status"] as? String ?: "",
                    reason = map["reason"] as? String ?: "",
                    changedBy = map["changedBy"] as? String ?: "",
                    changedAt = changedAt
                )
            } catch (e: Exception) {
                StatusEntry()
            }
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "status" to status,
            "reason" to reason,
            "changedBy" to changedBy,
            "changedAt" to changedAt
        )
    }
}
