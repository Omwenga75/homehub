package com.example.homehub.utils

import com.example.homehub.R
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class SenderType { SYSTEM, USER, ADMIN, CARETAKER, APPLICATION, BOOKING, PAYMENT }
enum class NotificationType { WELCOME, APPLICATION_SUBMITTED, NEW_CARETAKER_APPLICATION, CARETAKER_APPLICATION_APPROVED, CARETAKER_APPLICATION_REJECTED, CARETAKER_DASHBOARD_ACCESS, GENERAL, SYSTEM_ALERT, BOOKING_CONFIRMED, PAYMENT_RECEIVED, VERIFICATION_REQUIRED, WISHLIST_UPDATE, BOOKING_CANCELLED, NEW_BOOKING, NEW_MESSAGE, PROFILE_UPDATE, BOOKING_CREATED, PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_PENDING, MAINTENANCE_ALERT, SECURITY_ALERT }
enum class Priority { URGENT, HIGH, NORMAL, LOW }
enum class NotificationAccess { PRIVATE, ROLE_BASED, PUBLIC, ADMIN_ONLY, CARETAKER_ONLY, WATER_SUPPLIER_ONLY, SYSTEM }
enum class UserRole { USER, CARETAKER, ADMIN, WATER_SUPPLIER, GUEST }

data class NotificationModel(
    val id: String = "",
    val userId: String = "",
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.GENERAL,
    val notificationHash: String = "",
    val senderId: String = "system",
    val senderType: SenderType = SenderType.SYSTEM,
    val relatedId: String = "",
    val relatedType: String = "",
    val isRead: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp? = null,
    val actionUrl: String = "",
    val priority: Priority = Priority.NORMAL,
    val icon: Int = R.drawable.notifications,
    val accessLevel: NotificationAccess = NotificationAccess.PRIVATE,
    val allowedRoles: List<UserRole> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
