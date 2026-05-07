package com.example.homehub.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.homehub.billing.Booking
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReceiptGenerator {
    private const val TAG = "ReceiptGenerator"

    /**
     * Generates a professional PDF receipt for an individual booking.
     */
    fun generateBookingReceipt(context: Context, booking: Booking) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#1A237E") // Deep Blue
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 12f
            color = Color.parseColor("#424242")
        }

        // --- Drawing ---
        var y = 60f

        // Header / Logo area
        canvas.drawText("HOMEHUB OFFICIAL RECEIPT", 40f, y, titlePaint)
        y += 40f

        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawRect(40f, y, 555f, y + 2f, paint)
        y += 30f

        // Booking Info
        canvas.drawText("Receipt No: ${booking.mpesaReceiptNumber.ifEmpty { "N/A" }}", 40f, y, headerPaint)
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(booking.bookingDate)
        canvas.drawText("Date: $dateStr", 350f, y, textPaint)
        y += 40f

        // Student Info
        canvas.drawText("BIll TO:", 40f, y, headerPaint)
        y += 20f
        canvas.drawText(booking.studentName, 40f, y, textPaint)
        y += 20f
        canvas.drawText(booking.studentPhone, 40f, y, textPaint)
        y += 40f

        // Property Info table header
        canvas.drawRect(40f, y, 555f, y + 30f, Paint().apply { color = Color.parseColor("#F5F5F5") })
        canvas.drawText("Description", 50f, y + 20f, headerPaint)
        canvas.drawText("Amount", 450f, y + 20f, headerPaint)
        y += 50f

        // Property details
        canvas.drawText(booking.propertyName, 50f, y, textPaint)
        canvas.drawText(booking.getFormattedAmount(), 450f, y, textPaint)
        y += 20f
        if (booking.roomNumber.isNotEmpty()) {
            canvas.drawText("Room No: ${booking.roomNumber}", 50f, y, Paint().apply { textSize = 10f; color = Color.GRAY })
            y += 20f
        }
        
        y += 40f
        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawRect(400f, y, 555f, y + 1f, paint)
        y += 30f

        // Total
        val totalPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        canvas.drawText("TOTAL PAID", 350f, y, totalPaint)
        canvas.drawText(booking.getFormattedAmount(), 450f, y, totalPaint)
        
        y += 100f
        val footerPaint = Paint().apply {
            textSize = 10f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("This is an electronically generated receipt. No signature required.", 297f, y, footerPaint)
        canvas.drawText("© 2026 HomeHub. All Rights Reserved.", 297f, y + 15f, footerPaint)

        pdfDocument.finishPage(page)

        // Save and Open
        saveAndOpenPdf(context, pdfDocument, "Receipt_${booking.mpesaReceiptNumber}")
    }

    /**
     * Generates a comprehensive summary report for an administrator.
     * Includes Platform Snapshot, Bar Charts, and Activity Ledger.
     */
    fun generateSummaryReport(
        context: Context,
        bookings: List<Booking>,
        reportTitle: String,
        platformStats: Map<String, Any> = emptyMap(),
        categoryDistribution: Map<String, Int> = emptyMap()
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#1A237E")
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#455A64")
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.GRAY }

        var y = 60f
        
        val itemHeaderName = if (reportTitle.contains("Water", ignoreCase = true)) "Service" else "Property"

        // --- PAGE 1: HEADER ---
        canvas.drawText("HomeHub Management Report", 40f, y, titlePaint)
        y += 25f
        canvas.drawText(reportTitle, 40f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = Color.parseColor("#666666") })
        y += 15f
        val dateGenerated = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $dateGenerated", 40f, y, secondaryPaint)
        y += 20f

        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawRect(40f, y, 555f, y + 1f, paint)
        y += 25f

        // --- PLATFORM SNAPSHOT CARDS ---
        if (platformStats.isNotEmpty()) {
            canvas.drawText("PLATFORM SNAPSHOT", 40f, y, subHeaderPaint)
            y += 18f
            val snapshotText = "Total Users: ${platformStats["totalUsers"] ?: "—"}   •   " +
                    "Properties: ${platformStats["totalProperties"] ?: "—"}   •   " +
                    "Occupancy: ${platformStats["occupancy"] ?: "—"}%"
            canvas.drawText(snapshotText, 40f, y, itemPaint)
            y += 30f
        }

        // --- SECTION: REVENUE CHART (Paid vs Pending) ---
        val paidRevenue = bookings.filter { it.paymentStatus == "completed" }.sumOf { it.amount }
        val pendingRevenue = bookings.filter { it.paymentStatus == "pending_deferred" || it.paymentStatus == "pending" }.sumOf { it.amount }
        val maxRevenue = maxOf(paidRevenue, pendingRevenue, 1.0)

        canvas.drawText("REVENUE OVERVIEW", 40f, y, subHeaderPaint)
        y += 18f

        val chartLeft = 40f
        val chartRight = 555f
        val chartW = (chartRight - chartLeft - 60f) / 2f    // width per bar group
        val barH = 90f  // max bar height
        val baseY = y + barH + 5f

        // Paid bar
        val paidBarH = ((paidRevenue / maxRevenue) * barH).toFloat().coerceAtLeast(4f)
        paint.color = Color.parseColor("#388E3C")
        canvas.drawRect(chartLeft + 30f, baseY - paidBarH, chartLeft + 30f + chartW - 20f, baseY, paint)
        // Paid label
        val paidLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.parseColor("#388E3C"); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("KSh ${String.format("%,.0f", paidRevenue)}", chartLeft + 30f, baseY - paidBarH - 5f, paidLabelPaint)
        canvas.drawText("PAID", chartLeft + 30f + (chartW - 20f) / 2f - 10f, baseY + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY })

        // Pending bar
        val xPending = chartLeft + 30f + chartW + 10f
        val pendingBarH = ((pendingRevenue / maxRevenue) * barH).toFloat().coerceAtLeast(4f)
        paint.color = Color.parseColor("#F57C00")
        canvas.drawRect(xPending, baseY - pendingBarH, xPending + chartW - 20f, baseY, paint)
        val pendingLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.parseColor("#F57C00"); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("KSh ${String.format("%,.0f", pendingRevenue)}", xPending, baseY - pendingBarH - 5f, pendingLabelPaint)
        canvas.drawText("PENDING", xPending + (chartW - 20f) / 2f - 17f, baseY + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY })

        // Baseline
        paint.color = Color.parseColor("#BDBDBD")
        paint.strokeWidth = 1.5f
        canvas.drawLine(chartLeft + 20f, baseY, chartRight - 20f, baseY, paint)

        y = baseY + 35f

        // --- SECTION: OCCUPANCY CHART (Occupied / Reserved / Vacant) ---
        val occupied = bookings.count { it.isCheckedIn && (it.status == "confirmed" || it.status == "active") }
        val reserved = bookings.count { !it.isCheckedIn && it.paymentStatus != "completed" }
        val totalProps = (platformStats["totalProperties"] as? Int) ?: (occupied + reserved + 1)
        val vacant = maxOf(0, totalProps - occupied - reserved)
        val maxOcc = maxOf(occupied, reserved, vacant, 1)

        canvas.drawText("OCCUPANCY OVERVIEW", 40f, y, subHeaderPaint)
        y += 18f

        val occBaseY = y + barH + 5f
        val barW = (chartRight - chartLeft - 100f) / 3f
        val colors = listOf("#1976D2", "#F57C00", "#43A047")
        val labels = listOf("OCCUPIED", "RESERVED", "VACANT")
        val values = listOf(occupied, reserved, vacant)

        values.forEachIndexed { i, value ->
            val bH = ((value.toDouble() / maxOcc) * barH).toFloat().coerceAtLeast(4f)
            val bX = chartLeft + 30f + i * (barW + 10f)
            paint.color = Color.parseColor(colors[i])
            canvas.drawRect(bX, occBaseY - bH, bX + barW - 10f, occBaseY, paint)
            // Value label above
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.parseColor(colors[i]); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            canvas.drawText("$value", bX + (barW - 10f) / 2f - 5f, occBaseY - bH - 5f, valPaint)
            // Name label below
            canvas.drawText(labels[i], bX, occBaseY + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY })
        }

        // Baseline
        paint.color = Color.parseColor("#BDBDBD")
        canvas.drawLine(chartLeft + 20f, occBaseY, chartRight - 20f, occBaseY, paint)

        y = occBaseY + 40f

        // --- CATEGORY DISTRIBUTION (text row if present) ---
        if (categoryDistribution.isNotEmpty()) {
            paint.color = Color.parseColor("#F5F5F5")
            canvas.drawRect(40f, y - 5f, 555f, y + 30f, paint)
            canvas.drawText("CATEGORY DISTRIBUTION:", 50f, y + 12f, subHeaderPaint)
            var xPos = 220f
            categoryDistribution.entries.take(4).forEach { entry ->
                canvas.drawText("${entry.key}: ${entry.value}", xPos, y + 12f, itemPaint)
                xPos += 90f
            }
            y += 45f
        }

        // --- ACTIVITY LEDGER TABLE ---
        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawRect(40f, y, 555f, y + 1f, paint)
        y += 20f

        canvas.drawText("ACTIVITY LEDGER", 40f, y, subHeaderPaint)
        y += 18f

        // Table header row
        paint.color = Color.parseColor("#EEEEEE")
        canvas.drawRect(40f, y, 555f, y + 25f, paint)
        y += 18f
        canvas.drawText("Date", 50f, y, headerPaint)
        canvas.drawText("Student", 140f, y, headerPaint)
        canvas.drawText(itemHeaderName, 295f, y, headerPaint)
        canvas.drawText("Status", 430f, y, headerPaint)
        canvas.drawText("Amount", 490f, y, headerPaint)
        y += 22f

        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        var total = 0.0

        bookings.forEach { booking ->
            if (y > 790) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                // Redraw header on new page
                paint.color = Color.parseColor("#EEEEEE")
                canvas.drawRect(40f, y, 555f, y + 25f, paint)
                y += 18f
                canvas.drawText("Date", 50f, y, headerPaint)
                canvas.drawText("Student", 140f, y, headerPaint)
                canvas.drawText(itemHeaderName, 295f, y, headerPaint)
                canvas.drawText("Status", 430f, y, headerPaint)
                canvas.drawText("Amount", 490f, y, headerPaint)
                y += 22f
            }

            val statusColor = when (booking.paymentStatus) {
                "completed" -> Color.parseColor("#388E3C")
                "pending_deferred" -> Color.parseColor("#F57C00")
                else -> Color.GRAY
            }
            val statusLabel = when (booking.paymentStatus) {
                "completed" -> "Paid"
                "pending_deferred" -> "Pending"
                else -> "Other"
            }

            canvas.drawText(sdf.format(booking.bookingDate), 50f, y, itemPaint)
            canvas.drawText(booking.studentName.take(14), 140f, y, itemPaint)
            canvas.drawText(booking.propertyName.take(20), 295f, y, itemPaint)
            canvas.drawText(statusLabel, 430f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = statusColor; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            canvas.drawText(booking.getFormattedAmount(), 490f, y, itemPaint)

            total += booking.amount
            y += 22f
        }

        y += 10f
        paint.color = Color.BLACK
        paint.strokeWidth = 1.5f
        canvas.drawLine(400f, y, 555f, y, paint)
        y += 20f
        canvas.drawText("PERIOD TOTAL:", 300f, y, headerPaint)
        canvas.drawText("KSh ${String.format("%,.0f", total)}", 490f, y, headerPaint)

        // Footer
        y += 40f
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.LTGRAY; textAlign = Paint.Align.CENTER }
        canvas.drawText("Generated by HomeHub • Electronically signed • No manual signature required", 297f, y, footerPaint)
        canvas.drawText("© 2026 HomeHub. All Rights Reserved.", 297f, y + 14f, footerPaint)

        pdfDocument.finishPage(page)
        saveAndOpenPdf(context, pdfDocument, "Report_${System.currentTimeMillis()}")
    }

    private fun saveAndOpenPdf(context: Context, pdfDocument: PdfDocument, fileName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$fileName.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            val uri = FileProvider.getUriForFile(context, "com.example.homehub.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            val chooser = Intent.createChooser(intent, "Open Receipt")
            context.startActivity(chooser)
            
            Toast.makeText(context, "Receipt saved to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF: ${e.message}")
            Toast.makeText(context, "Generation Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
        }
    }
}
