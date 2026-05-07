package com.example.homehub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object LocalImageManager {

    fun loadImageFromInternalStorage(context: Context, imagePath: String): Bitmap? {
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                // Try as relative path in app's internal storage
                val internalFile = File(context.filesDir, imagePath)
                if (internalFile.exists()) {
                    BitmapFactory.decodeFile(internalFile.absolutePath)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveImageToInternalStorage(context: Context, bitmap: Bitmap, fileName: String): String? {
        return try {
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteImage(context: Context, imagePath: String): Boolean {
        return try {
            File(imagePath).delete()
        } catch (e: Exception) {
            false
        }
    }
}
