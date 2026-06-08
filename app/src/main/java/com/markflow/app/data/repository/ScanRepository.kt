package com.markflow.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.markflow.app.cv.*
import com.markflow.app.data.local.dao.*
import com.markflow.app.data.local.entity.*
import com.markflow.app.domain.model.*
import com.markflow.app.ml.MarkVerifier
import com.markflow.app.ml.OcrProcessor
import com.markflow.app.util.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing active scanning operations: page capture, processing,
 * mark detection, evidence saving, and running total updates.
 */
@Singleton
class ScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copyDao: CopyDao,
    private val pageDao: PageDao,
    private val markDao: MarkDao,
    private val issueDao: IssueDao,
    private val sessionDao: SessionDao,
    private val markVerifier: MarkVerifier,
    private val imageProcessor: ImageProcessor,
    private val duplicateDetector: DuplicateDetector,
    private val uncheckedAnswerDetector: UncheckedAnswerDetector,
    private val pageChangeDetector: PageChangeDetector,
    private val questionMarkDao: QuestionMarkDao,
    private val auditTrailDao: AuditTrailDao,
    private val ocrProcessor: OcrProcessor,
    private val redInkFilter: RedInkFilter
) {

    /**
     * Create a new scanning session.
     */
    suspend fun createSession(name: String, maxMarks: Double = 100.0, passThreshold: Double = 33.0): Long {
        val session = SessionEntity(
            name = name,
            maxMarks = maxMarks,
            passThreshold = passThreshold
        )
        return sessionDao.insert(session)
    }

    /**
     * Create a new copy within a session.
     */
    suspend fun createCopy(sessionId: Long): Long {
        val copyNumber = copyDao.getMaxCopyNumber(sessionId) + 1
        val copy = CopyEntity(
            sessionId = sessionId,
            copyNumber = copyNumber
        )
        return copyDao.insert(copy)
    }

    /**
     * Process and save a captured page image.
     * Runs the full detection pipeline in background.
     *
     * @param bitmap Raw camera capture
     * @param copyId Parent copy ID
     * @return The page ID and processing results
     */
    suspend fun processCapture(
        bitmap: Bitmap,
        copyId: Long,
        corners: ImageProcessor.CornerPoints? = null,
        isPreProcessed: Boolean = false
    ): PageProcessingResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // 1. Process image (warp perspective if corners are provided or automatically detected with high confidence)
        val processed = if (isPreProcessed) {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        } else {
            val finalCorners = corners ?: imageProcessor.detectPaperCorners(bitmap)
            if (finalCorners.isHighConfidence || corners != null) {
                imageProcessor.cropAndWarpPerspective(bitmap, finalCorners)
            } else {
                imageProcessor.resizeImageOnly(bitmap)
            }
        }

        val pageNumber = pageDao.getMaxPageNumber(copyId) + 1

        // 2. Perform page classification & validation
        val pixelAnalysis = imageProcessor.analyzePagePixels(processed)
        
        var isPageValid = true
        var isBlank = false
        var isRedInkOnly = false
        var isLowConfidence = false
        var reason = pixelAnalysis.classificationReason
        
        var ocrCharCount = 0
        var ocrWordCount = 0
        var textRegionCount = 0
        var ocrText = ""

        // Run standard OCR first to calculate standard counts
        ocrText = ocrProcessor.recognizeFullPageText(processed)
        ocrCharCount = ocrText.replace(Regex("\\s"), "").length
        ocrWordCount = ocrText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        textRegionCount = ocrText.count { it == '\n' } + 1

        val isStandardOcrPassed = ocrCharCount >= 5 || ocrWordCount >= 2

        if (pixelAnalysis.isLikelyBlank || pixelAnalysis.isLikelyRedInkOnly) {
            // Run multi-pass OCR validation layer before rejecting
            val ocrValResult = ocrProcessor.runOcrValidationPasses(processed, imageProcessor)
            val combinedOcrCharCount = maxOf(ocrCharCount, ocrValResult.totalCharacters)
            val combinedOcrWordCount = maxOf(ocrWordCount, ocrValResult.totalWords)
            val hasOcrContent = ocrValResult.isValid || combinedOcrCharCount >= 5 || combinedOcrWordCount >= 2
            
            // Check if page has visual structure despite low OCR (diagrams, math calculations)
            val hasVisualStructure = pixelAnalysis.edgeDensity >= 0.15 || 
                                     pixelAnalysis.inkCoveragePct >= 0.4 || 
                                     pixelAnalysis.handwritingDensity >= 2.0 ||
                                     pixelAnalysis.connectedComponents >= 3 ||
                                     pixelAnalysis.structureScore >= 5.0

            if (hasOcrContent || hasVisualStructure) {
                // Found content, override reject
                isPageValid = true
                isBlank = false
                isRedInkOnly = false
                isLowConfidence = true
                reason = if (hasOcrContent) {
                    "Text found by OCR validation layer ($combinedOcrCharCount chars). Review recommended."
                } else {
                    "Visual structure detected (Edge: ${"%.2f".format(pixelAnalysis.edgeDensity)}%, Density: ${"%.2f".format(pixelAnalysis.handwritingDensity)}%). Review recommended."
                }
                
                // Update text fields if validation passes had better OCR
                if (ocrValResult.combinedText.length > ocrText.length) {
                    ocrText = ocrValResult.combinedText
                    ocrCharCount = combinedOcrCharCount
                    ocrWordCount = combinedOcrWordCount
                    textRegionCount = ocrText.count { it == '\n' } + 1
                }
            } else {
                // Truly invalid/blank page
                isPageValid = false
                isBlank = pixelAnalysis.isLikelyBlank
                isRedInkOnly = pixelAnalysis.isLikelyRedInkOnly
            }
        } else {
            // If the blank score is somewhat high or standard OCR is low, label as low confidence review recommended
            if (pixelAnalysis.blankScore > 60.0 || !isStandardOcrPassed) {
                isLowConfidence = true
                reason = "Low Content Density (Blank Score: ${pixelAnalysis.blankScore.toInt()}). Review recommended."
            }
        }

        // Write developer diagnostics log
        android.util.Log.d("MarkFlowDiagnostics", """
            === PAGE VALIDATION DIAGNOSTICS ===
            Page Number: $pageNumber
            IsValid: $isPageValid
            IsBlank: $isBlank
            IsRedInkOnly: $isRedInkOnly
            IsLowConfidence: $isLowConfidence
            Blank Score: ${pixelAnalysis.blankScore}
            Edge Density: ${pixelAnalysis.edgeDensity}%
            Ink Coverage: ${pixelAnalysis.inkCoveragePct}%
            Red Ink coverage: ${pixelAnalysis.redInkPct}%
            Blue Ink coverage: ${pixelAnalysis.blueInkPct}%
            Black Ink coverage: ${pixelAnalysis.blackInkPct}%
            Handwriting Density: ${pixelAnalysis.handwritingDensity}%
            OCR Char Count: $ocrCharCount
            OCR Word Count: $ocrWordCount
            Text Regions: $textRegionCount
            Classification Reason: $reason
            ==================================
        """.trimIndent())

        if (!isPageValid) {
            val errMsg = if (isRedInkOnly) "Red Ink Page Detected: Page contains only teacher annotations/corrections and no student handwriting."
                         else "Blank Page Detected: Page is blank, dark, or contains insufficient answer content."
            throw IllegalArgumentException(errMsg)
        }

        // Cache parameters to local variables for database insertion below
        val finalIsLowConfidence = isLowConfidence
        val finalLowConfidenceReason = reason

        // 3. Check for duplicate
        val duplicateResult = duplicateDetector.checkDuplicate(processed, ocrText)

        // Calculate page scan quality (Feature 13)
        val qualityResult = imageProcessor.calculateScanQuality(processed)

        // 4. Check for page skip (Feature 7)
        val parsedPageNum = ocrProcessor.extractPageNumber(ocrText)
        val expectedPageNumber = pageNumber
        val isSequenceSkipped = parsedPageNum != null && parsedPageNum > expectedPageNumber

        // If first page, extract student details (Feature 1)
        if (pageNumber == 1) {
            val studentDetails = ocrProcessor.extractStudentDetails(processed)
            val currentCopy = copyDao.getCopyById(copyId)
            if (currentCopy != null) {
                copyDao.update(
                    currentCopy.copy(
                        studentName = studentDetails.name,
                        rollNumber = studentDetails.rollNumber,
                        registrationNumber = studentDetails.registrationNumber,
                        className = studentDetails.className,
                        section = studentDetails.section,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // 5. Generate page hash and save image
        val pageHash = BitmapUtils.generatePerceptualHash(processed)
        val fileName = FileUtils.generatePageFileName(copyId, pageNumber)
        val pagesDir = FileUtils.getPagesDir(context, copyId)
        val pageFile = java.io.File(pagesDir, fileName)
        BitmapUtils.saveBitmap(processed, pageFile)

        // 6. Create thumbnail
        val thumbnail = BitmapUtils.createThumbnail(processed)
        val thumbDir = FileUtils.getThumbnailsDir(context, copyId)
        val thumbFile = java.io.File(thumbDir, "thumb_$fileName")
        BitmapUtils.saveBitmap(thumbnail, thumbFile)
        thumbnail.recycle()

        // 7. Save page to database
        val pageEntity = PageEntity(
            copyId = copyId,
            pageNumber = pageNumber,
            imagePath = pageFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            pageHash = pageHash,
            status = "processing",
            scanQualityScore = qualityResult.score,
            scanQualityRating = qualityResult.rating,
            ocrText = ocrText,
            isDuplicate = duplicateResult.isDuplicate
        )
        val pageId = pageDao.insert(pageEntity)

        // 8. Register for duplicate detection
        duplicateDetector.registerPage(pageId, processed, ocrText)

        // 7. Run mark verification pipeline
        val evidenceDir = FileUtils.getEvidenceDir(context, copyId)
        val verificationResult = markVerifier.verifyPage(
            pageImage = processed,
            pageId = pageId,
            copyId = copyId,
            evidenceSaver = { cropBitmap, markIndex ->
                val evidenceName = FileUtils.generateEvidenceFileName(copyId, pageNumber, markIndex)
                val evidenceFile = java.io.File(evidenceDir, evidenceName)
                BitmapUtils.saveBitmap(cropBitmap, evidenceFile)
                evidenceFile.absolutePath
            }
        )

        // 8. Save detected marks
        val markEntities = verificationResult.marks.map { it.toEntity() }
        val insertedIds = markDao.insertAll(markEntities)

        // Feature 2: Map detected marks to question numbers sequentially
        val startingQNum = questionMarkDao.getMaxQuestionNumber(copyId)
        val questionMarks = verificationResult.marks.mapIndexed { idx, mark ->
            QuestionMarkEntity(
                copyId = copyId,
                pageId = pageId,
                questionNumber = startingQNum + idx + 1,
                marksAwarded = mark.value,
                pageNumber = pageNumber,
                confidence = mark.confidence
            )
        }
        questionMarkDao.insertQuestionMarks(questionMarks)

        // Feature 14: Save detection audit trail
        verificationResult.marks.forEachIndexed { idx, mark ->
            val dbMarkId = insertedIds.getOrNull(idx) ?: 0L
            auditTrailDao.insertAuditTrail(
                AuditTrailEntity(
                    copyId = copyId,
                    markId = dbMarkId,
                    action = "detected",
                    confidence = mark.confidence,
                    userAction = "AI Detection: ${mark.displayValue} marks on page $pageNumber"
                )
            )
        }

        // 9. Detect unchecked answers
        val redMask = redInkFilter.detectRedInk(processed).mask
        val uncheckedAnswers = uncheckedAnswerDetector.detectUncheckedAnswers(processed, redMask)
        redMask.recycle()

        // 10. Create issues for unchecked answers
        val issues = mutableListOf<IssueEntity>()
        if (uncheckedAnswers.isNotEmpty()) {
            for (region in uncheckedAnswers) {
                issues.add(
                    IssueEntity(
                        copyId = copyId,
                        pageId = pageId,
                        type = "unchecked_answer",
                        description = "Possible unchecked answer on page $pageNumber",
                        severity = "warning"
                    )
                )
            }
            issueDao.insertAll(issues)
        }

        // 11. Create issues for overwritten marks
        verificationResult.marks.filter { it.isOverwritten }.forEach { mark ->
            issueDao.insert(
                IssueEntity(
                    copyId = copyId,
                    pageId = pageId,
                    markId = mark.id,
                    type = "overwritten_mark",
                    description = "Mark '${mark.displayValue}' appears overwritten on page $pageNumber",
                    severity = "warning"
                )
            )
        }

        // 12. Create issues for low confidence marks
        verificationResult.marks.filter { it.confidence < Constants.CONFIDENCE_REVIEW_THRESHOLD }.forEach { mark ->
            issueDao.insert(
                IssueEntity(
                    copyId = copyId,
                    pageId = pageId,
                    markId = mark.id,
                    type = "low_confidence",
                    description = "Low confidence detection: '${mark.displayValue}' (${(mark.confidence * 100).toInt()}%)",
                    severity = "info"
                )
            )
        }

        // Create issue for page low confidence classification
        if (finalIsLowConfidence) {
            issueDao.insert(
                IssueEntity(
                    copyId = copyId,
                    pageId = pageId,
                    type = "low_confidence",
                    description = finalLowConfidenceReason,
                    severity = "warning"
                )
            )
        }

        // 13. Update page stats
        val pageTotal = verificationResult.marks
            .filter { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.NEEDS_REVIEW }
            .sumOf { it.value }

        pageDao.update(
            pageEntity.copy(
                id = pageId,
                status = "processed",
                pageTotal = pageTotal,
                markCount = verificationResult.totalMarksFound,
                hasUncheckedAnswers = uncheckedAnswers.isNotEmpty(),
                uncheckedAnswerCount = uncheckedAnswers.size,
                scanQualityScore = qualityResult.score,
                scanQualityRating = qualityResult.rating,
                processedAt = System.currentTimeMillis()
            )
        )
        pageDao.recalculatePageStats(pageId)

        // 14. Recalculate copy stats
        copyDao.recalculateCopyStats(copyId)

        processed.recycle()

        val processingTime = System.currentTimeMillis() - startTime
        return@withContext PageProcessingResult(
            pageId = pageId,
            isDuplicate = duplicateResult.isDuplicate,
            duplicateOfPageId = duplicateResult.matchingPageId,
            duplicateConfidence = duplicateResult.confidence,
            isSequenceSkipped = isSequenceSkipped,
            detectedPageNumber = parsedPageNum,
            expectedPageNumber = expectedPageNumber,
            ocrText = ocrText,
            marksDetected = verificationResult.totalMarksFound,
            pageTotal = pageTotal,
            marks = verificationResult.marks,
            uncheckedAnswerCount = uncheckedAnswers.size,
            scanQualityScore = qualityResult.score,
            scanQualityRating = qualityResult.rating,
            processingTimeMs = processingTime
        )
    }

    /**
     * Finish scanning a copy. Checks for totaling errors and updates status.
     * Returns true if saved, false if deleted (empty copy with 0 pages).
     */
    suspend fun finishCopy(copyId: Long): Boolean {
        val copy = copyDao.getCopyById(copyId) ?: return false
        val pageCount = pageDao.getPageCount(copyId)

        if (pageCount == 0) {
            copyDao.delete(copy)
            sessionDao.recalculateSessionStats(copy.sessionId)
            return false
        }

        val calculatedTotal = markDao.getRunningTotalSync(copyId)

        // Check if a written total was detected and compare
        if (copy.writtenTotal != null && copy.writtenTotal != calculatedTotal) {
            issueDao.insert(
                IssueEntity(
                    copyId = copyId,
                    type = "totaling_error",
                    description = "Calculated total ($calculatedTotal) differs from written total (${copy.writtenTotal}). Difference: ${calculatedTotal - copy.writtenTotal}",
                    severity = "error",
                    metadata = """{"calculated": $calculatedTotal, "written": ${copy.writtenTotal}, "difference": ${calculatedTotal - copy.writtenTotal}}"""
                )
            )
        }

        // Update copy status
        val issueCount = issueDao.getIssuesByCopySync(copyId).count { !it.isResolved }
        val reviewCount = markDao.getMarkCountByStatus(copyId, "needs_review")

        copyDao.update(
            copy.copy(
                calculatedTotal = calculatedTotal,
                status = "completed",
                hasIssues = issueCount > 0,
                issueCount = issueCount,
                reviewCount = reviewCount,
                overallConfidence = markDao.getAverageConfidence(copyId),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Update session stats
        sessionDao.recalculateSessionStats(copy.sessionId)
        return true
    }

    /**
     * Get the running total for a copy (reactive).
     */
    fun getRunningTotal(copyId: Long): Flow<Double> = markDao.getRunningTotal(copyId)

    /**
     * Get all marks for a copy (reactive).
     */
    fun getMarksByCopy(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getMarksByCopy(copyId).map { entities -> entities.map { it.toDomain() } }

    /**
     * Get marks needing review.
     */
    fun getMarksNeedingReview(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getMarksNeedingReview(copyId).map { entities -> entities.map { it.toDomain() } }

    /**
     * Approve a mark (teacher verification).
     */
    suspend fun approveMark(markId: Long) {
        markDao.updateMarkStatus(markId, "confirmed")
        val mark = markDao.getMarkById(markId)
        if (mark != null) {
            pageDao.recalculatePageStats(mark.pageId)
            copyDao.recalculateCopyStats(mark.copyId)
            auditTrailDao.insertAuditTrail(
                AuditTrailEntity(
                    copyId = mark.copyId,
                    markId = mark.id,
                    action = "approved",
                    confidence = mark.confidence,
                    userAction = "Teacher Approved: ${mark.displayValue} marks"
                )
            )
        }
    }

    /**
     * Edit a mark value.
     */
    suspend fun editMark(markId: Long, newValue: Double, displayValue: String) {
        markDao.updateMarkValue(markId, newValue, displayValue)
        val mark = markDao.getMarkById(markId)
        if (mark != null) {
            pageDao.recalculatePageStats(mark.pageId)
            copyDao.recalculateCopyStats(mark.copyId)
            auditTrailDao.insertAuditTrail(
                AuditTrailEntity(
                    copyId = mark.copyId,
                    markId = mark.id,
                    action = "corrected",
                    confidence = 1.0,
                    userAction = "Teacher Corrected: ${mark.value} -> $newValue (display: $displayValue)"
                )
            )
        }
    }

    /**
     * Reject a mark (false detection).
     */
    suspend fun rejectMark(markId: Long) {
        markDao.updateMarkStatus(markId, "rejected")
        val mark = markDao.getMarkById(markId)
        if (mark != null) {
            pageDao.recalculatePageStats(mark.pageId)
            copyDao.recalculateCopyStats(mark.copyId)
            auditTrailDao.insertAuditTrail(
                AuditTrailEntity(
                    copyId = mark.copyId,
                    markId = mark.id,
                    action = "deleted",
                    confidence = 0.0,
                    userAction = "Teacher Rejected: ${mark.displayValue} marks"
                )
            )
        }
    }

    /**
     * Ignore a mark.
     */
    suspend fun ignoreMark(markId: Long) {
        markDao.updateMarkStatus(markId, "ignored")
        val mark = markDao.getMarkById(markId)
        if (mark != null) {
            pageDao.recalculatePageStats(mark.pageId)
            copyDao.recalculateCopyStats(mark.copyId)
            auditTrailDao.insertAuditTrail(
                AuditTrailEntity(
                    copyId = mark.copyId,
                    markId = mark.id,
                    action = "ignored",
                    confidence = 0.0,
                    userAction = "Teacher Ignored: ${mark.displayValue} marks"
                )
            )
        }
    }

    /**
     * Reset page change detector for new copy.
     */
    fun resetPageDetection() {
        pageChangeDetector.reset()
        duplicateDetector.reset()
    }

    /**
     * Add a manual mark (Feature 3).
     */
    suspend fun addManualMark(
        pageId: Long,
        copyId: Long,
        value: Double,
        displayValue: String,
        x: Int,
        y: Int,
        width: Int = 100,
        height: Int = 60
    ): Long = withContext(Dispatchers.IO) {
        val markEntity = MarkEntity(
            pageId = pageId,
            copyId = copyId,
            value = value,
            displayValue = displayValue,
            confidence = 1.0,
            status = "confirmed",
            boundingBoxX = x,
            boundingBoxY = y,
            boundingBoxWidth = width,
            boundingBoxHeight = height,
            isAutoConfirmed = true,
            regionType = "awarded_mark",
            isManual = true
        )
        val markId = markDao.insert(markEntity)
        pageDao.recalculatePageStats(pageId)
        copyDao.recalculateCopyStats(copyId)
        copyDao.getCopyById(copyId)?.let { sessionDao.recalculateSessionStats(it.sessionId) }
        
        auditTrailDao.insertAuditTrail(
            AuditTrailEntity(
                copyId = copyId,
                markId = markId,
                action = "added",
                confidence = 1.0,
                userAction = "Teacher Added Manual Mark: $displayValue marks"
            )
        )
        markId
    }

    /**
     * Move an existing mark (Feature 4).
     */
    suspend fun moveMark(markId: Long, x: Int, y: Int) = withContext(Dispatchers.IO) {
        val mark = markDao.getMarkById(markId) ?: return@withContext
        markDao.updateMarkCoordinates(markId, x, y)
        auditTrailDao.insertAuditTrail(
            AuditTrailEntity(
                copyId = mark.copyId,
                markId = markId,
                action = "moved",
                confidence = mark.confidence,
                userAction = "Teacher Moved Mark: ${mark.displayValue} to ($x, $y)"
            )
        )
    }

    /**
     * Delete/Reject a mark (Feature 4).
     */
    suspend fun deleteMark(markId: Long) = withContext(Dispatchers.IO) {
        val mark = markDao.getMarkById(markId) ?: return@withContext
        markDao.delete(mark)
        pageDao.recalculatePageStats(mark.pageId)
        copyDao.recalculateCopyStats(mark.copyId)
        copyDao.getCopyById(mark.copyId)?.let { sessionDao.recalculateSessionStats(it.sessionId) }
        
        auditTrailDao.insertAuditTrail(
            AuditTrailEntity(
                copyId = mark.copyId,
                markId = markId,
                action = "deleted",
                confidence = 0.0,
                userAction = "Teacher Deleted Mark: ${mark.displayValue} marks"
            )
        )
    }

    /**
     * Delete a single page and clean up all associated data and files.
     */
    suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val copyId = page.copyId

        // 1. Delete page files from storage
        try {
            java.io.File(page.imagePath).delete()
            page.thumbnailPath?.let { java.io.File(it).delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Delete related issues
        issueDao.deleteIssuesByPage(pageId)

        // 3. Delete page from database (marks will cascade delete)
        pageDao.delete(page)

        // 4. Update page numbers and question mark mappings for remaining pages
        val remainingPages = pageDao.getPagesByCopySync(copyId).sortedBy { it.pageNumber }
        remainingPages.forEachIndexed { index, p ->
            val updatedPage = p.copy(pageNumber = index + 1)
            pageDao.update(updatedPage)
            questionMarkDao.updatePageNumberForPage(p.id, index + 1)
        }

        // 5. Update parent copy or delete if empty
        val pageCount = pageDao.getPageCount(copyId)
        if (pageCount == 0) {
            val copy = copyDao.getCopyById(copyId)
            if (copy != null) {
                copyDao.delete(copy)
                sessionDao.recalculateSessionStats(copy.sessionId)
            }
        } else {
            copyDao.recalculateCopyStats(copyId)
            copyDao.getCopyById(copyId)?.let { sessionDao.recalculateSessionStats(it.sessionId) }
        }
    }

    /**
     * Merges a duplicate page's marks into another page (Feature 6).
     */
    suspend fun mergeDuplicatePage(fromPageId: Long, toPageId: Long) = withContext(Dispatchers.IO) {
        val fromPage = pageDao.getPageById(fromPageId) ?: return@withContext
        val toPage = pageDao.getPageById(toPageId) ?: return@withContext

        val marks = markDao.getMarksByPageSync(fromPageId)
        marks.forEach { mark ->
            markDao.update(mark.copy(pageId = toPageId))
        }

        pageDao.delete(fromPage)
        pageDao.recalculatePageStats(toPageId)
        copyDao.recalculateCopyStats(toPage.copyId)
        copyDao.getCopyById(toPage.copyId)?.let { sessionDao.recalculateSessionStats(it.sessionId) }
    }

    /**
     * Adjust copy running total manually (Feature 14).
     */
    suspend fun adjustCopyTotal(copyId: Long, newTotal: Double, bonus: Double, penalty: Double, reason: String) = withContext(Dispatchers.IO) {
        val copy = copyDao.getCopyById(copyId) ?: return@withContext
        copyDao.update(
            copy.copy(
                writtenTotal = newTotal,
                updatedAt = System.currentTimeMillis()
            )
        )
        
        auditTrailDao.insertAuditTrail(
            AuditTrailEntity(
                copyId = copyId,
                action = "adjusted_total",
                confidence = 1.0,
                userAction = "Teacher Adjusted Total: $newTotal (Bonus: +$bonus, Penalty: -$penalty). Reason: $reason"
            )
        )
    }

    /**
     * Set a copy's verified status (Feature 13).
     */
    suspend fun setCopyVerified(copyId: Long, isVerified: Boolean) = withContext(Dispatchers.IO) {
        val copy = copyDao.getCopyById(copyId) ?: return@withContext
        copyDao.update(
            copy.copy(
                isVerified = isVerified,
                status = if (isVerified) "reviewed" else "completed",
                updatedAt = System.currentTimeMillis()
            )
        )
        
        auditTrailDao.insertAuditTrail(
            AuditTrailEntity(
                copyId = copyId,
                action = if (isVerified) "approved" else "unapproved",
                confidence = 1.0,
                userAction = if (isVerified) "Teacher Approved Evaluation" else "Teacher Revoked Evaluation Approval"
            )
        )
    }

    data class PageProcessingResult(
        val pageId: Long,
        val isDuplicate: Boolean,
        val duplicateOfPageId: Long?,
        val duplicateConfidence: Double = 0.0,
        val isSequenceSkipped: Boolean = false,
        val detectedPageNumber: Int? = null,
        val expectedPageNumber: Int = 1,
        val ocrText: String = "",
        val marksDetected: Int,
        val pageTotal: Double = 0.0,
        val marks: List<DetectedMark> = emptyList(),
        val uncheckedAnswerCount: Int = 0,
        val scanQualityScore: Int = 100,
        val scanQualityRating: String = "Excellent",
        val processingTimeMs: Long = 0
    )
}
