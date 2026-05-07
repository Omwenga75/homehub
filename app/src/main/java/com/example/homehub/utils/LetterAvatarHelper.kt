package com.example.homehub.utils

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.example.homehub.other.Extensions.loadProfileImage
import java.util.*

object LetterAvatarHelper {

    private val colors = arrayOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#03A6F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#D32F2F",
        "#C2185B", "#7B1FA2", "#512DA8", "#303F9F", "#1976D2",
        "#0288D1"
    )

    fun getInitials(name: String): String {
        if (name.isBlank()) return "?"
        val parts = name.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) {
            (parts[0].take(1) + parts[1].take(1)).uppercase()
        } else {
            parts[0].take(1).uppercase()
        }
    }

    private fun getColorForLetter(letter: Char): Int {
        val upperLetter = letter.uppercaseChar()
        val index = if (upperLetter in 'A'..'Z') {
            upperLetter - 'A'
        } else {
            0
        }
        return Color.parseColor(colors[index % colors.size])
    }

    fun setLetterAvatar(imageView: ImageView, name: String, avatarUrl: String? = null, userId: String? = null) {
        if (!avatarUrl.isNullOrBlank()) {
            imageView.loadProfileImage(userId ?: "", avatarUrl)
            return
        }

        val initials = getInitials(name)
        val firstChar = if (name.isNotBlank()) name.trim()[0] else '?'
        val backgroundColor = getColorForLetter(firstChar)

        val size = 200 // Bitmap size
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Circle
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = backgroundColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = Color.WHITE
        textPaint.textSize = size / 2.5f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f)

        canvas.drawText(initials, xPos, yPos, textPaint)

        imageView.setImageBitmap(bitmap)
    }
}
