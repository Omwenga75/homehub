package com.example.homehub.property

import com.google.firebase.Timestamp

data class UserInteraction(
    val userId: String = "",
    val type: String = "",
    val houseId: String = "",
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Timestamp = Timestamp.now()
)
