package com.markflow.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.markflow.app.data.local.dao.*
import com.markflow.app.data.local.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface ExportProgressListener {
    fun onProgress(currentPage: Int, totalPages: Int, progress: Float, estimatedTimeSeconds: Int): Boolean
}

@Singleton
class ReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copyDao: CopyDao,
    private val pageDao: PageDao,
    private val markDao: MarkDao,
    private val issueDao: IssueDao,
    private val sessionDao: SessionDao,
    private val auditTrailDao: AuditTrailDao
) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

    private fun addHeaderCell(table: PdfPTable, text: String, font: Font) {
        val cell = PdfPCell(Phrase(text, font)).apply {
            backgroundColor = BaseColor(0, 102, 204)
            horizontalAlignment = Element.ALIGN_CENTER
            setPadding(5f)
        }
        table.addCell(cell)
    }

    suspend fun generateCopyReport(
        copyId: Long,
        progressListener: ExportProgressListener? = null
    ): File = withContext(Dispatchers.IO) {
        val copy = copyDao.getCopyById(copyId) ?: throw IllegalArgumentException("Copy not found")
        val pages = pageDao.getPagesByCopySync(copyId).sortedBy { it.pageNumber }
        val marks = markDao.getMarksByCopySync(copyId)
        val issues = issueDao.getIssuesByCopySync(copyId)
        val auditLogs = auditTrailDao.getAuditTrailForCopy(copyId).first()
        val session = sessionDao.getSessionById(copy.sessionId)

        val reportsDir = FileUtils.getReportsDir(context)
        val fileName = FileUtils.generateReportFileName("copy_${copy.copyNumber}", "pdf")
        val reportFile = File(reportsDir, fileName)

        // 1. Process page images in parallel with Semaphore to limit concurrency and RAM footprint
        val totalPages = pages.size
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)

        val imageBytesDeferred = coroutineScope {
            pages.map { page ->
                async(Dispatchers.Default) {
                    if (!isActive) return@async null
                    semaphore.withPermit {
                        if (!isActive) return@withPermit null
                        val rawFile = File(page.imagePath)
                        var byteData: ByteArray? = null
                        if (rawFile.exists()) {
                            val options = BitmapFactory.Options().apply {
                                inMutable = true
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val rawBitmap = BitmapFactory.decodeFile(page.imagePath, options)
                            if (rawBitmap != null) {
                                val canvas = Canvas(rawBitmap)
                                val paint = Paint().apply {
                                    color = Color.RED
                                    style = Paint.Style.STROKE
                                    strokeWidth = 5f
                                }
                                val textPaint = Paint().apply {
                                    color = Color.RED
                                    textSize = 28f
                                    style = Paint.Style.FILL
                                    strokeWidth = 2f
                                }

                                val pageMarks = marks.filter { it.pageId == page.id }
                                pageMarks.forEach { mark ->
                                    if (mark.status != "rejected" && mark.status != "ignored") {
                                        val rect = Rect(
                                            mark.boundingBoxX,
                                            mark.boundingBoxY,
                                            mark.boundingBoxX + mark.boundingBoxWidth,
                                            mark.boundingBoxY + mark.boundingBoxHeight
                                        )
                                        canvas.drawRect(rect, paint)
                                        canvas.drawText(
                                            mark.displayValue,
                                            mark.boundingBoxX.toFloat(),
                                            (mark.boundingBoxY - 10).toFloat(),
                                            textPaint
                                        )
                                    }
                                }

                                val outStream = java.io.ByteArrayOutputStream()
                                rawBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                                byteData = outStream.toByteArray()
                                rawBitmap.recycle()
                            }
                        }

                        // Increment progress
                        val completed = completedCount.incrementAndGet()
                        val pct = completed.toFloat() / totalPages
                        val elapsed = System.currentTimeMillis() - startTime
                        val avgTimePerPage = elapsed / completed.toDouble()
                        val estRemainingSeconds = ((totalPages - completed) * avgTimePerPage / 1000.0).toInt()

                        val shouldContinue = progressListener?.onProgress(
                            currentPage = completed,
                            totalPages = totalPages,
                            progress = pct,
                            estimatedTimeSeconds = estRemainingSeconds
                        ) ?: true

                        if (!shouldContinue) {
                            throw kotlinx.coroutines.CancellationException("Export cancelled by user")
                        }

                        byteData
                    }
                }
            }
        }

        // Wait for all image processes to complete
        val imageBytesList = imageBytesDeferred.map { it.await() }

        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(reportFile))
        document.open()

        // Fonts
        val titleFont = Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, BaseColor(0, 102, 204))
        val subtitleFont = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL, BaseColor.GRAY)
        val sectionFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor(0, 102, 204))
        val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
        val cellFont = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL, BaseColor.BLACK)
        val boldCellFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.BLACK)
        val alertFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor(204, 0, 0))
        val passFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor(46, 125, 50))
        val failFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor(198, 40, 40))

        val maxMarks = session?.maxMarks ?: 100.0
        val passThreshold = session?.passThreshold ?: 33.0
        val passThresholdScore = maxMarks * (passThreshold / 100.0)
        val isPassed = copy.calculatedTotal >= passThresholdScore
        val percentage = if (maxMarks > 0) (copy.calculatedTotal / maxMarks) * 100 else 0.0

        // ── Cover Sheet Header Banner ──
        val headerBanner = PdfPTable(1).apply {
            widthPercentage = 100f
            spacingAfter = 20f
        }
        headerBanner.addCell(PdfPCell(Phrase("MARKFLOW AUTOMATED EVALUATION SYSTEM", Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor.WHITE))).apply {
            backgroundColor = BaseColor(27, 122, 61) // MarkFlowGreen
            horizontalAlignment = Element.ALIGN_CENTER
            setPadding(10f)
            border = PdfPCell.NO_BORDER
        })
        document.add(headerBanner)

        val titlePara = Paragraph("Student Evaluation Report", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 5f
        }
        document.add(titlePara)
        val datePara = Paragraph("Generated on: ${dateFormat.format(Date())}", subtitleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 30f
        }
        document.add(datePara)

        // 1. Student Information
        document.add(Paragraph("Student Details", sectionFont).apply { spacingAfter = 8f })
        val studentTable = PdfPTable(4).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2.5f, 2.5f, 2.5f, 2.5f))
            spacingAfter = 20f
        }

        studentTable.addCell(PdfPCell(Phrase("Student Name:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        studentTable.addCell(PdfPCell(Phrase(copy.studentName ?: "N/A", cellFont)).apply { setPadding(8f) })
        studentTable.addCell(PdfPCell(Phrase("Roll Number:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        studentTable.addCell(PdfPCell(Phrase(copy.rollNumber ?: "N/A", cellFont)).apply { setPadding(8f) })

        studentTable.addCell(PdfPCell(Phrase("Class - Section:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        studentTable.addCell(PdfPCell(Phrase("${copy.className ?: "N/A"} - ${copy.section ?: "N/A"}", cellFont)).apply { setPadding(8f) })
        studentTable.addCell(PdfPCell(Phrase("Registration No:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        studentTable.addCell(PdfPCell(Phrase(copy.registrationNumber ?: "N/A", cellFont)).apply { setPadding(8f) })

        document.add(studentTable)

        // 2. Evaluation Summary
        document.add(Paragraph("Evaluation Summary", sectionFont).apply { spacingAfter = 8f })
        val summaryTable = PdfPTable(4).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2.5f, 2.5f, 2.5f, 2.5f))
            spacingAfter = 30f
        }

        summaryTable.addCell(PdfPCell(Phrase("Total Score:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        summaryTable.addCell(PdfPCell(Phrase("${copy.calculatedTotal} / $maxMarks", boldCellFont)).apply { setPadding(8f) })
        summaryTable.addCell(PdfPCell(Phrase("Percentage / Grade:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        summaryTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.1f%%", percentage), cellFont)).apply { setPadding(8f) })

        summaryTable.addCell(PdfPCell(Phrase("Result Status:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        val statusCellText = if (isPassed) "PASSED" else "FAILED"
        val statusFont = if (isPassed) passFont else failFont
        summaryTable.addCell(PdfPCell(Phrase(statusCellText, statusFont)).apply { setPadding(8f) })
        summaryTable.addCell(PdfPCell(Phrase("Confidence / Pages:", boldCellFont)).apply { setPadding(8f); backgroundColor = BaseColor(245, 245, 245) })
        summaryTable.addCell(PdfPCell(Phrase("${(copy.overallConfidence * 100).toInt()}% (${copy.pageCount} pgs)", cellFont)).apply { setPadding(8f) })

        document.add(summaryTable)

        // 3. Evaluator Sign-off & Comments
        document.add(Paragraph("Teacher Notes & Discrepancies", sectionFont).apply { spacingAfter = 8f })
        val discrepancyText = if (copy.writtenTotal != null && copy.calculatedTotal != copy.writtenTotal) {
            "DISCREPANCY: Teacher written total (${copy.writtenTotal}) does not match calculated total (${copy.calculatedTotal})."
        } else {
            "OCR and written totals match successfully or no discrepancies flagged."
        }
        val noteTable = PdfPTable(1).apply {
            widthPercentage = 100f
            spacingAfter = 30f
        }
        noteTable.addCell(PdfPCell(Phrase(discrepancyText, if (copy.writtenTotal != null && copy.calculatedTotal != copy.writtenTotal) alertFont else cellFont)).apply {
            setPadding(10f)
            backgroundColor = if (copy.writtenTotal != null && copy.calculatedTotal != copy.writtenTotal) BaseColor(255, 235, 235) else BaseColor(250, 250, 250)
        })
        document.add(noteTable)

        // Signature area
        document.add(Paragraph("Evaluator Sign-off", sectionFont).apply { spacingAfter = 15f })
        val sigTable = PdfPTable(2).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(5f, 5f))
        }
        sigTable.addCell(PdfPCell(Paragraph("\n\n_____________________________________\nEvaluator Signature (Teacher)", boldCellFont)).apply {
            border = PdfPCell.NO_BORDER
            horizontalAlignment = Element.ALIGN_CENTER
        })
        sigTable.addCell(PdfPCell(Paragraph("\n\n_____________________________________\nAdministrator Signature", boldCellFont)).apply {
            border = PdfPCell.NO_BORDER
            horizontalAlignment = Element.ALIGN_CENTER
        })
        document.add(sigTable)

        // Add page break to separate Cover Sheet from details
        document.newPage()

        // 3. Issue Summary
        if (issues.isNotEmpty()) {
            document.add(Paragraph("Detected Issues", sectionFont).apply { spacingAfter = 8f })
            val issueTable = PdfPTable(3).apply {
                widthPercentage = 100f
                setWidths(floatArrayOf(1.5f, 5.5f, 3f))
            }
            addHeaderCell(issueTable, "Severity", headerFont)
            addHeaderCell(issueTable, "Description", headerFont)
            addHeaderCell(issueTable, "Status", headerFont)

            issues.forEach { issue ->
                val severityColor = if (issue.severity == "error") BaseColor(255, 204, 204) else BaseColor(255, 255, 204)
                issueTable.addCell(PdfPCell(Phrase(issue.severity.uppercase(Locale.US), alertFont)).apply {
                    backgroundColor = severityColor
                    setPadding(5f)
                    horizontalAlignment = Element.ALIGN_CENTER
                })
                issueTable.addCell(PdfPCell(Phrase(issue.description, cellFont)).apply { setPadding(5f) })
                issueTable.addCell(PdfPCell(Phrase(if (issue.isResolved) "Resolved" else "Pending Review", cellFont)).apply { setPadding(5f) })
            }
            document.add(issueTable)
            document.add(Paragraph(" "))
        }

        // 4. Page Breakdown
        document.add(Paragraph("Page-wise Breakdown", sectionFont).apply { spacingAfter = 8f })
        val breakdownTable = PdfPTable(4).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2f, 3f, 3f, 2f))
        }
        addHeaderCell(breakdownTable, "Page Number", headerFont)
        addHeaderCell(breakdownTable, "Marks Sum", headerFont)
        addHeaderCell(breakdownTable, "Mark Count", headerFont)
        addHeaderCell(breakdownTable, "Quality", headerFont)

        pages.forEach { page ->
            breakdownTable.addCell(PdfPCell(Phrase("Page ${page.pageNumber}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            breakdownTable.addCell(PdfPCell(Phrase("${page.pageTotal}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            breakdownTable.addCell(PdfPCell(Phrase("${page.markCount}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            breakdownTable.addCell(PdfPCell(Phrase("${page.scanQualityRating} (${page.scanQualityScore}%)", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
        }
        document.add(breakdownTable)
        document.add(Paragraph(" "))

        // 5. Embedded Scanned Pages with Annotations and Evidence
        document.add(Paragraph("Scanned Documents & Evidence", sectionFont).apply { spacingAfter = 8f })
        pages.forEachIndexed { index, page ->
            if (!this@withContext.isActive) {
                throw kotlinx.coroutines.CancellationException("Export cancelled by user")
            }
            document.newPage()
            document.add(Paragraph("Page ${page.pageNumber}", sectionFont).apply { spacingAfter = 6f })

            // Embed annotated page image in PDF
            val imageBytes = imageBytesList[index]
            if (imageBytes != null) {
                val img = Image.getInstance(imageBytes)
                img.scaleToFit(500f, 650f)
                img.alignment = Element.ALIGN_CENTER
                document.add(img)
            }

            // Embedded evidence crops
            val pageMarks = marks.filter { it.pageId == page.id }
            val marksWithEvidence = pageMarks.filter { !it.evidenceImagePath.isNullOrEmpty() && File(it.evidenceImagePath).exists() }
            if (marksWithEvidence.isNotEmpty()) {
                document.add(Paragraph("Evidence Crops (Detected Marks)", boldCellFont).apply { spacingBefore = 10f; spacingAfter = 5f })
                val evidenceTable = PdfPTable(4).apply {
                    widthPercentage = 100f
                    setWidths(floatArrayOf(2f, 2f, 3f, 3f))
                }
                addHeaderCell(evidenceTable, "Mark Index", headerFont)
                addHeaderCell(evidenceTable, "Evidence Crop", headerFont)
                addHeaderCell(evidenceTable, "Detected Value", headerFont)
                addHeaderCell(evidenceTable, "Teacher Action", headerFont)

                marksWithEvidence.forEachIndexed { idx, mark ->
                    evidenceTable.addCell(PdfPCell(Phrase("Mark #${idx + 1}", cellFont)).apply { setPadding(5f); verticalAlignment = Element.ALIGN_MIDDLE; horizontalAlignment = Element.ALIGN_CENTER })

                    // Add evidence crop image
                    val evidenceFile = File(mark.evidenceImagePath!!)
                    val evImg = Image.getInstance(evidenceFile.absolutePath)
                    evImg.scaleToFit(80f, 40f)
                    val imgCell = PdfPCell(evImg).apply {
                        setPadding(4f)
                        horizontalAlignment = Element.ALIGN_CENTER
                        verticalAlignment = Element.ALIGN_MIDDLE
                    }
                    evidenceTable.addCell(imgCell)

                    evidenceTable.addCell(PdfPCell(Phrase("${mark.displayValue} (Conf: ${(mark.confidence * 100).toInt()}%)", cellFont)).apply { setPadding(5f); verticalAlignment = Element.ALIGN_MIDDLE; horizontalAlignment = Element.ALIGN_CENTER })
                    evidenceTable.addCell(PdfPCell(Phrase(mark.status.uppercase(Locale.US), cellFont)).apply { setPadding(5f); verticalAlignment = Element.ALIGN_MIDDLE; horizontalAlignment = Element.ALIGN_CENTER })
                }
                document.add(evidenceTable)
            }
        }

        // 6. Audit Trail and Ending Notes
        document.newPage()
        document.add(Paragraph("Audit Trail & Action Logs", sectionFont).apply { spacingAfter = 8f })
        val auditTable = PdfPTable(3).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(3f, 2f, 5f))
        }
        addHeaderCell(auditTable, "Timestamp", headerFont)
        addHeaderCell(auditTable, "Action", headerFont)
        addHeaderCell(auditTable, "Description", headerFont)

        auditLogs.take(30).forEach { log ->
            auditTable.addCell(PdfPCell(Phrase(dateFormat.format(Date(log.timestamp)), cellFont)).apply { setPadding(5f) })
            auditTable.addCell(PdfPCell(Phrase(log.action.uppercase(Locale.US), boldCellFont)).apply { setPadding(5f) })
            auditTable.addCell(PdfPCell(Phrase(log.userAction ?: "No description", cellFont)).apply { setPadding(5f) })
        }
        document.add(auditTable)

        document.add(Paragraph(" "))
        document.add(Paragraph("Teacher Notes & Discrepancies", sectionFont).apply { spacingAfter = 8f })
        val totalDiscrepancy = copy.calculatedTotal - (copy.writtenTotal ?: copy.calculatedTotal)
        val notePara = Paragraph().apply {
            add(Chunk("Discrepancy Detected: ", boldCellFont))
            if (totalDiscrepancy != 0.0) {
                add(Chunk("$totalDiscrepancy marks discrepancy between OCR total and written total.\n", alertFont))
            } else {
                add(Chunk("None. Totals are matched successfully.\n", cellFont))
            }
            add(Chunk("\nVerified Status: ", boldCellFont))
            add(Chunk(if (copy.isVerified) "VERIFIED AND SIGNED OFF\n" else "PENDING SIGNOFF\n", cellFont))
        }
        document.add(notePara)

        document.close()
        return@withContext reportFile
    }

    suspend fun generateBatchPdfReport(sessionId: Long): File = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId) ?: throw IllegalArgumentException("Session not found")
        val copies = copyDao.getCopiesBySession(sessionId).first().sortedBy { it.copyNumber }

        val reportsDir = FileUtils.getReportsDir(context)
        val fileName = FileUtils.generateReportFileName("session_${session.id}_batch", "pdf")
        val reportFile = File(reportsDir, fileName)

        val document = Document(PageSize.A4.rotate(), 36f, 36f, 36f, 36f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(reportFile))
        document.open()

        val titleFont = Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, BaseColor.DARK_GRAY)
        val sectionFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor(0, 102, 204))
        val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
        val cellFont = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)
        val boldFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.BLACK)

        // Title
        document.add(Paragraph("MarkFlow Class Evaluation Summary Report", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 5f
        })
        document.add(Paragraph("Session: ${session.name} | Generated on: ${dateFormat.format(Date())}", cellFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 15f
        })

        // 1. Session Statistics
        document.add(Paragraph("Class Statistics", sectionFont).apply { spacingAfter = 8f })
        val statsTable = PdfPTable(5).apply { widthPercentage = 100f }
        addHeaderCell(statsTable, "Evaluated Copies", headerFont)
        addHeaderCell(statsTable, "Class Average", headerFont)
        addHeaderCell(statsTable, "Highest Score", headerFont)
        addHeaderCell(statsTable, "Lowest Score", headerFont)
        addHeaderCell(statsTable, "Pass Rate", headerFont)

        val passCount = copies.count { it.calculatedTotal >= session.passThreshold }
        val passPercentage = if (copies.isNotEmpty()) (passCount.toDouble() / copies.size) * 100 else 0.0

        statsTable.addCell(PdfPCell(Phrase("${copies.size}", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.2f", session.averageMarks), boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase("${session.highestMarks}", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase("${session.lowestMarks}", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.1f%%", passPercentage), boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })

        document.add(statsTable)
        document.add(Paragraph(" "))

        // 2. Copies List Table
        document.add(Paragraph("Evaluated Student Copies", sectionFont).apply { spacingAfter = 8f })
        val passStatusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, BaseColor(46, 125, 50))
        val failStatusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, BaseColor(198, 40, 40))

        // 2. Copies List Table
        document.add(Paragraph("Evaluated Student Copies", sectionFont).apply { spacingAfter = 8f })
        val copiesTable = PdfPTable(8).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(0.8f, 2.5f, 1.8f, 1.2f, 1.5f, 1.5f, 1.5f, 2.2f))
        }
        addHeaderCell(copiesTable, "#", headerFont)
        addHeaderCell(copiesTable, "Student Name", headerFont)
        addHeaderCell(copiesTable, "Roll Number", headerFont)
        addHeaderCell(copiesTable, "Pages", headerFont)
        addHeaderCell(copiesTable, "Total Marks", headerFont)
        addHeaderCell(copiesTable, "Result", headerFont)
        addHeaderCell(copiesTable, "Confidence", headerFont)
        addHeaderCell(copiesTable, "Evaluation Date", headerFont)

        copies.forEachIndexed { idx, copy ->
            val isPassed = copy.calculatedTotal >= session.maxMarks * (session.passThreshold / 100.0)
            val statusText = if (isPassed) "PASS" else "FAIL"
            val statusFont = if (isPassed) passStatusFont else failStatusFont

            copiesTable.addCell(PdfPCell(Phrase("${idx + 1}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase(copy.studentName ?: "N/A", cellFont)).apply { setPadding(5f) })
            copiesTable.addCell(PdfPCell(Phrase(copy.rollNumber ?: "N/A", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase("${copy.pageCount}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase("${copy.calculatedTotal}", boldFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase(statusText, statusFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase("${(copy.overallConfidence * 100).toInt()}%", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            copiesTable.addCell(PdfPCell(Phrase(dateFormat.format(Date(copy.createdAt)), cellFont)).apply { setPadding(5f) })
        }
        document.add(copiesTable)

        document.close()
        return@withContext reportFile
    }

    suspend fun generateBatchExcelReport(sessionId: Long): File = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId) ?: throw IllegalArgumentException("Session not found")
        val copies = copyDao.getCopiesBySession(sessionId).first().sortedBy { it.copyNumber }

        val reportsDir = FileUtils.getReportsDir(context)
        val fileName = FileUtils.generateReportFileName("session_${session.id}_batch", "xlsx")
        val reportFile = File(reportsDir, fileName)

        val workbook = XSSFWorkbook()

        // Styles
        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.getIndex()
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(headerFont)
        }
        val boldFont = workbook.createFont().apply { bold = true }
        val boldStyle = workbook.createCellStyle().apply { setFont(boldFont) }

        // Sheet 1: Summary Statistics
        val statsSheet = workbook.createSheet("Class Statistics")
        var rowNum = 0

        // Headers
        var row = statsSheet.createRow(rowNum++)
        row.createCell(0).apply { setCellValue("Metric"); setCellStyle(headerStyle) }
        row.createCell(1).apply { setCellValue("Value"); setCellStyle(headerStyle) }

        val passCount = copies.count { it.calculatedTotal >= session.passThreshold }
        val passPercentage = if (copies.isNotEmpty()) (passCount.toDouble() / copies.size) * 100 else 0.0

        val statsMap = linkedMapOf(
            "Session Name" to session.name,
            "Total Copies Evaluated" to copies.size,
            "Class Average Marks" to session.averageMarks,
            "Highest Marks" to session.highestMarks,
            "Lowest Marks" to session.lowestMarks,
            "Pass Threshold" to session.passThreshold,
            "Pass Rate" to String.format(Locale.US, "%.1f%%", passPercentage),
            "Generation Date" to dateFormat.format(Date())
        )

        statsMap.forEach { (metric, value) ->
            val statRow = statsSheet.createRow(rowNum++)
            statRow.createCell(0).setCellValue(metric)
            when (value) {
                is Number -> statRow.createCell(1).setCellValue(value.toDouble())
                else -> statRow.createCell(1).setCellValue(value.toString())
            }
        }
        statsSheet.autoSizeColumn(0)
        statsSheet.autoSizeColumn(1)

        // Sheet 2: Copies List
        val listSheet = workbook.createSheet("Student Evaluations")
        var listRowNum = 0

        // Headers
        val headers = arrayOf("Index", "Student Name", "Roll Number", "Class", "Section", "Page Count", "Final Marks", "Confidence Score", "Issues Count", "Evaluation Date")
        val headerRow = listSheet.createRow(listRowNum++)
        headers.forEachIndexed { idx, title ->
            headerRow.createCell(idx).apply {
                setCellValue(title)
                setCellStyle(headerStyle)
            }
        }

        copies.forEachIndexed { index, copy ->
            val dataRow = listSheet.createRow(listRowNum++)
            dataRow.createCell(0).setCellValue((index + 1).toDouble())
            dataRow.createCell(1).setCellValue(copy.studentName ?: "N/A")
            dataRow.createCell(2).setCellValue(copy.rollNumber ?: "N/A")
            dataRow.createCell(3).setCellValue(copy.className ?: "N/A")
            dataRow.createCell(4).setCellValue(copy.section ?: "N/A")
            dataRow.createCell(5).setCellValue(copy.pageCount.toDouble())
            dataRow.createCell(6).setCellValue(copy.calculatedTotal)
            dataRow.createCell(7).setCellValue(copy.overallConfidence)
            dataRow.createCell(8).setCellValue(copy.issueCount.toDouble())
            dataRow.createCell(9).setCellValue(dateFormat.format(Date(copy.createdAt)))
        }

        for (i in headers.indices) {
            listSheet.autoSizeColumn(i)
        }

        val out = FileOutputStream(reportFile)
        workbook.write(out)
        out.close()
        workbook.close()

        return@withContext reportFile
    }

    suspend fun generateAllClassesReport(): File = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAllSessions().first()
        val allCopies = copyDao.getAllCopies().first()

        val reportsDir = FileUtils.getReportsDir(context)
        val fileName = FileUtils.generateReportFileName("all_classes_summary", "pdf")
        val reportFile = File(reportsDir, fileName)

        val document = Document(PageSize.A4.rotate(), 36f, 36f, 36f, 36f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(reportFile))
        document.open()

        val titleFont = Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, BaseColor.DARK_GRAY)
        val sectionFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, BaseColor(0, 102, 204))
        val headerFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.WHITE)
        val cellFont = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.BLACK)
        val boldFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.BLACK)
        val passStatusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, BaseColor(46, 125, 50))
        val failStatusFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, BaseColor(198, 40, 40))

        // Title
        document.add(Paragraph("MarkFlow All-Classes Summary Report", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 5f
        })
        document.add(Paragraph("Generated on: ${dateFormat.format(Date())}", cellFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 15f
        })

        // 1. Overall Statistics
        document.add(Paragraph("Overview Statistics", sectionFont).apply { spacingAfter = 8f })
        val statsTable = PdfPTable(5).apply { widthPercentage = 100f }
        addHeaderCell(statsTable, "Total Classes/Folders", headerFont)
        addHeaderCell(statsTable, "Total Students Scanned", headerFont)
        addHeaderCell(statsTable, "Total Students Passed", headerFont)
        addHeaderCell(statsTable, "Total Students Failed", headerFont)
        addHeaderCell(statsTable, "Overall Pass Rate", headerFont)

        var totalPassed = 0
        var totalFailed = 0
        allCopies.forEach { copy ->
            val session = sessions.find { it.id == copy.sessionId }
            val maxMarks = session?.maxMarks ?: 100.0
            val passThreshold = session?.passThreshold ?: 33.0
            val passThresholdScore = maxMarks * (passThreshold / 100.0)
            if (copy.calculatedTotal >= passThresholdScore) {
                totalPassed++
            } else {
                totalFailed++
            }
        }
        val totalStudents = allCopies.size
        val overallPassRate = if (totalStudents > 0) (totalPassed.toDouble() / totalStudents) * 100 else 0.0

        statsTable.addCell(PdfPCell(Phrase("${sessions.size}", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase("$totalStudents", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase("$totalPassed", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase("$totalFailed", boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })
        statsTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.1f%%", overallPassRate), boldFont)).apply { setPadding(6f); horizontalAlignment = Element.ALIGN_CENTER })

        document.add(statsTable)
        document.add(Paragraph(" "))

        // 2. Classes Breakdown
        document.add(Paragraph("Class-wise Breakdown", sectionFont).apply { spacingAfter = 8f })
        val classesTable = PdfPTable(6).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(3f, 1.5f, 1.5f, 1.5f, 1.5f, 2f))
        }
        addHeaderCell(classesTable, "Class/Folder Name", headerFont)
        addHeaderCell(classesTable, "Total Copies", headerFont)
        addHeaderCell(classesTable, "Class Average", headerFont)
        addHeaderCell(classesTable, "Highest Score", headerFont)
        addHeaderCell(classesTable, "Lowest Score", headerFont)
        addHeaderCell(classesTable, "Pass Rate", headerFont)

        sessions.forEach { session ->
            val sessionCopies = allCopies.filter { it.sessionId == session.id }
            val sessionPassCount = sessionCopies.count { it.calculatedTotal >= session.maxMarks * (session.passThreshold / 100.0) }
            val sessionPassRate = if (sessionCopies.isNotEmpty()) (sessionPassCount.toDouble() / sessionCopies.size) * 100 else 0.0

            classesTable.addCell(PdfPCell(Phrase(session.name, cellFont)).apply { setPadding(5f) })
            classesTable.addCell(PdfPCell(Phrase("${sessionCopies.size}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            classesTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.2f", session.averageMarks), cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            classesTable.addCell(PdfPCell(Phrase("${session.highestMarks}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            classesTable.addCell(PdfPCell(Phrase("${session.lowestMarks}", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            classesTable.addCell(PdfPCell(Phrase(String.format(Locale.US, "%.1f%%", sessionPassRate), cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
        }
        document.add(classesTable)
        document.add(Paragraph(" "))

        // 3. Full Student Name List & Marks
        document.add(Paragraph("All Students Evaluation Directory", sectionFont).apply { spacingAfter = 8f })
        val studentTable = PdfPTable(6).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2.5f, 3f, 2f, 1.5f, 1.5f, 1.5f))
        }
        addHeaderCell(studentTable, "Class/Folder", headerFont)
        addHeaderCell(studentTable, "Student Name", headerFont)
        addHeaderCell(studentTable, "Roll Number", headerFont)
        addHeaderCell(studentTable, "Total Marks", headerFont)
        addHeaderCell(studentTable, "Result Status", headerFont)
        addHeaderCell(studentTable, "Confidence", headerFont)

        allCopies.forEach { copy ->
            val session = sessions.find { it.id == copy.sessionId }
            val maxMarks = session?.maxMarks ?: 100.0
            val passThreshold = session?.passThreshold ?: 33.0
            val passThresholdScore = maxMarks * (passThreshold / 100.0)
            val isPassed = copy.calculatedTotal >= passThresholdScore
            val statusText = if (isPassed) "PASS" else "FAIL"
            val statusFont = if (isPassed) passStatusFont else failStatusFont

            studentTable.addCell(PdfPCell(Phrase(session?.name ?: "N/A", cellFont)).apply { setPadding(5f) })
            studentTable.addCell(PdfPCell(Phrase(copy.studentName ?: "N/A", cellFont)).apply { setPadding(5f) })
            studentTable.addCell(PdfPCell(Phrase(copy.rollNumber ?: "N/A", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            studentTable.addCell(PdfPCell(Phrase("${copy.calculatedTotal}", boldFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            studentTable.addCell(PdfPCell(Phrase(statusText, statusFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
            studentTable.addCell(PdfPCell(Phrase("${(copy.overallConfidence * 100).toInt()}%", cellFont)).apply { setPadding(5f); horizontalAlignment = Element.ALIGN_CENTER })
        }
        document.add(studentTable)

        document.close()
        return@withContext reportFile
    }
}
