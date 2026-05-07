package com.example.homehub.utils

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

object BackButtonHandler {
    fun handleBackPress(activity: AppCompatActivity): Boolean {
        // Return true if handled, false to allow default back press
        return false 
    }
}
