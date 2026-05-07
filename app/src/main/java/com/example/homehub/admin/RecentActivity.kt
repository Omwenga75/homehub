package com.example.homehub.admin

import com.google.firebase.Timestamp
import java.util.*

data class RecentActivity(
    val id: String? = null,
    val title: String,
    val description: String,
    val user: String,
    val userId: String? = null,
    val activityType: ActivityType,
    val timestamp: Long,
    var isRead: Boolean = false,
    val relatedId: String? = null,
    val relatedType: String? = null,
    val priority: ActivityPriority = ActivityPriority.NORMAL,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun fromDocument(doc: Map<String, Any>, id: String? = null): RecentActivity {
            return RecentActivity(
                id = id ?: doc["id"] as? String,
                title = doc["title"] as? String ?: "Activity",
                description = doc["description"] as? String ?: "",
                user = doc["user"] as? String ?: doc["userName"] as? String ?: "System",
                userId = doc["userId"] as? String,
                activityType = try {
                    val raw = doc["activityType"] as? String
                        ?: when ((doc["type"] as? String)?.uppercase()) {
                            "VERIFICATION" -> "NEW_USER_SIGNUP"
                            "VERIFICATION_SUCCESS" -> "CARETAKER_VERIFIED"
                            "VERIFICATION_REJECTED" -> "CARETAKER_UNVERIFIED"
                            "BOOKING" -> "NEW_BOOKING"
                            "PROPERTY" -> "NEW_PROPERTY"
                            else -> "SYSTEM_ALERT"
                        }
                    ActivityType.valueOf(raw)
                } catch (e: Exception) {
                    ActivityType.SYSTEM_ALERT
                },
                timestamp = when (val ts = doc["timestamp"]) {
                    is com.google.firebase.Timestamp -> ts.toDate().time
                    is Long -> ts
                    is java.util.Date -> ts.time
                    else -> System.currentTimeMillis()
                },
                isRead = doc["isRead"] as? Boolean ?: false,
                relatedId = doc["relatedId"] as? String,
                relatedType = doc["relatedType"] as? String,
                priority = try {
                    ActivityPriority.valueOf(doc["priority"] as? String ?: "NORMAL")
                } catch (e: Exception) {
                    ActivityPriority.NORMAL
                },
                metadata = doc["metadata"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}

enum class ActivityType { NEW_CARETAKER_APPLICATION, CARETAKER_APPLICATION_APPROVED, CARETAKER_APPLICATION_REJECTED, NEW_PROPERTY_LISTED, PROPERTY_APPROVED, PROPERTY_REJECTED, PROPERTY_SUSPENDED, CARETAKER_SUSPENDED, CARETAKER_REACTIVATED, CARETAKER_VERIFIED, CARETAKER_UNVERIFIED, NEW_USER_SIGNUP, USER_BANNED, USER_REACTIVATED, NEW_BOOKING, BOOKING_CONFIRMED, BOOKING_CANCELLED, PAYMENT_RECEIVED, NEW_PROPERTY, SYSTEM_ALERT }
enum class ActivityPriority { URGENT, HIGH, NORMAL, LOW }
