package com.example.homehub.utils

import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*

object ImageWatermarkUtils {
    fun addWatermark(bitmap: Bitmap, latitude: Double?, longitude: Double?): Bitmap {
        val result = bitmap.copy(bitmap.config, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = (bitmap.height / 30).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStamp = sdf.format(Date())
        val locationText = if (latitude != null && longitude != null) {
            "GPS: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
        } else {
            "GPS: N/A"
        }

        val x = 20f
        val y = bitmap.height - 40f
        
        canvas.drawText(timeStamp, x, y - paint.textSize - 10f, paint)
        canvas.drawText(locationText, x, y, paint)
        
        return result
    }
}
