package com.example.homehub.caretaker

data class CaretakerRequest(
    var id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val status: String = "pending",
    val requestedAt: Long = 0,
    val message: String = ""
)
