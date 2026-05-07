package com.example.homehub.utils

import android.content.Context
import android.view.View
import android.widget.Toast

fun View.hide() { visibility = View.GONE }
fun View.show() { visibility = View.VISIBLE }

/**
 * Shows a user-friendly toast for a given Throwable.
 */
fun Context.toastError(e: Throwable?) {
    Toast.makeText(this, ErrorMapper.map(e), Toast.LENGTH_SHORT).show()
}

/**
 * Shows a user-friendly toast for a raw error message.
 */
fun Context.toastError(message: String?) {
    Toast.makeText(this, ErrorMapper.map(message), Toast.LENGTH_SHORT).show()
}
