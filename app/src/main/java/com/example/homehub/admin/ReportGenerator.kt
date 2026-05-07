package com.example.homehub.admin

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.example.homehub.billing.Booking
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportGenerator {
    
    /**
     * Generates a PDF earnings report for a caretaker.
     * Uses Android's native PdfDocument API to avoid external dependencies.
     */
    fun generateCaretakerEarningsReport(
        context: Context,
        caretakerName: String,
        bookings: List<Booking>,
        totalEarnings: Double
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (in points)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val titlePaint = Paint()
        
        // Header
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        titlePaint.textSize = 24f
        titlePaint.color = Color.parseColor("#1565C0") // Primary Blue
        canvas.drawText("HomeHub: Earnings Report", 50f, 60f, titlePaint)
        
        paint.textSize = 12f
        paint.color = Color.BLACK
        canvas.drawText("Caretaker: $caretakerName", 50f, 90f, paint)
        canvas.drawText("Generated On: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}", 50f, 110f, paint)
        
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("Total Summary Earnings: KSh ${String.format("%,.0f", totalEarnings)}", 50f, 140f, paint)
        
        // Draw a separator line
        paint.color = Color.LTGRAY
        canvas.drawLine(50f, 160f, 545f, 160f, paint)
        
        // Table Header
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        var yPos = 185f
        canvas.drawText("Date", 50f, yPos, paint)
        canvas.drawText("Property", 130f, yPos, paint)
        canvas.drawText("Student", 330f, yPos, paint)
        canvas.drawText("Amount", 480f, yPos, paint)
        
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 10f
        canvas.drawLine(50f, yPos, 545f, yPos, paint)
        yPos += 20f
        
        // Rows
        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        for (booking in bookings) {
            if (yPos > 800f) break // Simple paging overflow protection for now
            
            canvas.drawText(dateFormat.format(booking.bookingDate), 50f, yPos, paint)
            
            // Handle long property names
            val propName = if (booking.propertyName.length > 25) 
                booking.propertyName.substring(0, 22) + "..." else booking.propertyName
            canvas.drawText(propName, 130f, yPos, paint)
            
            val studName = if (booking.studentName.length > 20)
                booking.studentName.substring(0, 17) + "..." else booking.studentName
            canvas.drawText(studName, 330f, yPos, paint)
            
            canvas.drawText("${String.format("%,.0f", booking.amount)}", 480f, yPos, paint)
            
            yPos += 20f
        }
        
        // Footer
        paint.textSize = 10f
        paint.color = Color.GRAY
        canvas.drawText("End of Report", 260f, 820f, paint)
        
        pdfDocument.finishPage(page)
        
        // Save using current time for uniqueness
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, "Caretaker_Earnings_${System.currentTimeMillis()}.pdf")
        
        return try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            Log.e("ReportGenerator", "Error saving PDF: ${e.message}")
            pdfDocument.close()
            null
        }
    }
}
