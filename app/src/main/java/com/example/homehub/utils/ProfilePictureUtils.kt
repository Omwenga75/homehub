package com.example.homehub.utils

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.example.homehub.R

object ProfilePictureUtils {

    fun getInitials(name: String): String {
        if (name.isBlank()) return "H"
        val parts = name.trim().split(" ")
        return if (parts.size >= 2) {
            "${parts[0][0]}${parts[1][0]}".uppercase()
        } else {
            "${name[0]}".uppercase()
        }
    }

    private val profileColors = listOf(
        "#1976D2", "#388E3C", "#F4511E", "#7B1FA2", "#0097A7",
        "#C2185B", "#E64A19", "#00796B", "#689F38", "#AFB42B",
        "#0D9488", "#0284C7", "#4F46E5", "#7C3AED", "#DB2777"
    )

    fun getColorHexForName(name: String): String {
        if (name.equals("SA", ignoreCase = true) || name.equals("Super Admin", ignoreCase = true)) {
            return "#000080" // Deep navy blue
        }
        if (name.isBlank()) return profileColors[0]
        val colorIndex = Math.abs(name.hashCode()) % profileColors.size
        return profileColors[colorIndex]
    }

    fun generateProfilePicture(name: String, size: Int, context: Context): Bitmap {
        val initials = getInitials(name)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint()
        paint.color = Color.parseColor(getColorHexForName(name))
        paint.isAntiAlias = true
        
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = size / 2.5f
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        
        val xPos = size / 2f
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        
        canvas.drawText(initials, xPos, yPos, textPaint)
        
        return bitmap
    }

    fun getProfilePicture(userId: String): String = ""

    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        if (base64String.isEmpty()) return null
        return try {
            val cleanBase64 = if (base64String.contains(",")) {
                base64String.substringAfter(",")
            } else {
                base64String
            }
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
