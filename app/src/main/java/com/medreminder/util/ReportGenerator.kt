package com.medreminder.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.medreminder.domain.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportGenerator {

    fun generateAdherenceReport(
        context: Context,
        medications: List<Medication>,
        stats: AdherenceStats,
        doseLogs: List<DoseLog>,
        periodLabel: String = "Last 7 days"
    ): File? {
        return try {
            val document = PdfDocument()
            var pageNum = 1
            var yPos = 60f

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#1A1A1A")
            }
            val headerPaint = Paint().apply {
                textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#4A90D9")
            }
            val bodyPaint = Paint().apply {
                textSize = 12f; color = android.graphics.Color.parseColor("#333333")
            }
            val mutedPaint = Paint().apply {
                textSize = 11f; color = android.graphics.Color.parseColor("#888888")
            }
            val linePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#E0E0E0"); strokeWidth = 1f
            }

            // Title
            canvas.drawText("Medication Adherence Report", 40f, yPos, titlePaint)
            yPos += 24f
            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            canvas.drawText("Generated: ${dateFormat.format(Date())} | Period: $periodLabel", 40f, yPos, mutedPaint)
            yPos += 30f
            canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
            yPos += 20f

            // Summary
            canvas.drawText("Summary", 40f, yPos, headerPaint)
            yPos += 22f
            canvas.drawText("Adherence Rate: ${stats.adherenceRate.toInt()}%", 40f, yPos, bodyPaint)
            yPos += 18f
            canvas.drawText("Total Doses: ${stats.totalDoses}", 40f, yPos, bodyPaint)
            yPos += 18f
            canvas.drawText("Taken: ${stats.takenDoses} | Missed: ${stats.missedDoses} | Skipped: ${stats.skippedDoses}", 40f, yPos, bodyPaint)
            yPos += 18f
            canvas.drawText("Current Streak: ${stats.currentStreak} days", 40f, yPos, bodyPaint)
            yPos += 30f

            canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
            yPos += 20f

            // Medications list
            canvas.drawText("Active Medications", 40f, yPos, headerPaint)
            yPos += 22f

            medications.filter { it.isActive }.forEach { med ->
                if (yPos > 760) {
                    document.finishPage(page)
                    pageNum++
                    val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = document.startPage(newPageInfo)
                    canvas = page.canvas
                    yPos = 60f
                }

                val boldBody = Paint(bodyPaint).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("${med.name} - ${med.dosage} ${med.dosageUnit}", 50f, yPos, boldBody)
                yPos += 16f
                val schedStr = med.schedules.joinToString(", ") { it.timeFormatted }
                canvas.drawText("Schedule: $schedStr | Form: ${med.form.displayName}", 60f, yPos, mutedPaint)
                yPos += 14f
                if (med.instructions.isNotBlank()) {
                    canvas.drawText("Instructions: ${med.instructions}", 60f, yPos, mutedPaint)
                    yPos += 14f
                }
                if (med.currentStock > 0) {
                    canvas.drawText("Stock: ${med.currentStock} remaining", 60f, yPos, mutedPaint)
                    yPos += 14f
                }
                yPos += 8f
            }

            yPos += 10f
            canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
            yPos += 20f

            // Recent dose log
            canvas.drawText("Recent Dose Log", 40f, yPos, headerPaint)
            yPos += 22f

            val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            doseLogs.take(30).forEach { log ->
                if (yPos > 770) {
                    document.finishPage(page)
                    pageNum++
                    val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = document.startPage(newPageInfo)
                    canvas = page.canvas
                    yPos = 60f
                }

                val statusSymbol = when (log.status) {
                    DoseStatus.TAKEN -> "✓"
                    DoseStatus.MISSED -> "✗"
                    DoseStatus.SKIPPED -> "–"
                    DoseStatus.SNOOZED -> "⏰"
                    DoseStatus.PENDING -> "○"
                }
                val statusColor = when (log.status) {
                    DoseStatus.TAKEN -> android.graphics.Color.parseColor("#2ECC71")
                    DoseStatus.MISSED -> android.graphics.Color.parseColor("#E74C3C")
                    else -> android.graphics.Color.parseColor("#F39C12")
                }
                val statusPaint = Paint(bodyPaint).apply { color = statusColor }

                canvas.drawText(statusSymbol, 50f, yPos, statusPaint)
                canvas.drawText(
                    "${log.medicationName} ${log.medicationDosage}",
                    70f, yPos, bodyPaint
                )
                canvas.drawText(
                    "${timeFormat.format(Date(log.scheduledTime))} - ${log.status.displayName}",
                    350f, yPos, mutedPaint
                )
                yPos += 16f
            }

            // Footer
            yPos = 820f
            canvas.drawText(
                "Report generated by MedReminder - Privacy-first medication management",
                40f, yPos, mutedPaint
            )

            document.finishPage(page)

            // Save to cache
            val reportDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "MedReminder_Report_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.pdf"
            val file = File(reportDir, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareReport(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Medication Adherence Report")
                putExtra(Intent.EXTRA_TEXT, "Here is my medication adherence report from MedReminder.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share report via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share report", Toast.LENGTH_SHORT).show()
        }
    }
}
