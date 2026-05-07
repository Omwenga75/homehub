package com.example.homehub.property

data class Category(
    val id: String,
    val name: String,
    val iconRes: Int,
    var count: Int = 0
)
