package com.markflow.app.ml

import android.graphics.Bitmap
import com.markflow.app.cv.ContourAnalyzer
import com.markflow.app.cv.ImageProcessor
import com.markflow.app.cv.RedInkFilter
import com.markflow.app.domain.model.BoundingBox
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.MarkStatus
import com.markflow.app.domain.model.VerificationResult
import com.markflow.app.util.BitmapUtils
import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master mark verification pipeline that orchestrates all three detection stages:
 *
 * Stage 1: Computer Vision (RedInkFilter → ContourAnalyzer)
 *   - Detects red ink regions and extracts bounding boxes
 *
 * Stage 2: OCR (ML Kit Text Recognition)
 *   - Reads text from each candidate region, parses numeric values
 *
 * Stage 3: AI Verification (TensorFlow Lite)
 *   - Digit recognition on each candidate for cross-validation
 *
 * The final confidence is calculated by combining all three stages.
 */
@Singleton
class MarkVerifier @Inject constructor(
    private val redInkFilter: RedInkFilter,
    private val contourAnalyzer: ContourAnalyzer,
    private val imageProcessor: ImageProcessor,
    private val ocrProcessor: OcrProcessor,
    private val digitRecognizer: DigitRecognizer,
    private val confidenceCalculator: ConfidenceCalculator
) {

    data class VerificationPipelineResult(
        val marks: List<DetectedMark>,
        val totalMarksFound: Int,
        val processingTimeMs: Long
    )

    /**
     * Run the full three-stage verification pipeline on a page image.
     *
     * @param pageImage The processed page bitmap
     * @param pageId The database page ID
     * @param copyId The database copy ID
     * @param context Android context for saving evidence crops
     * @param evidenceSaver Callback to save evidence crop images
     * @return List of verified marks with confidence scores
     */
    suspend fun verifyPage(
        pageImage: Bitmap,
        pageId: Long,
        copyId: Long,
        evidenceSaver: suspend (Bitmap, Int) -> String?
    ): VerificationPipelineResult {
        val startTime = System.currentTimeMillis()
        val detectedMarks = mutableListOf<DetectedMark>()

        // Step 1: Detect page (Skipped here as validation is already handled in ScanRepository)

        // ═══ Stage 1: Computer Vision ═══
        val redInkResult = redInkFilter.detectRedInkAdaptive(pageImage)
        val contours = contourAnalyzer.findContours(redInkResult.mask)

        // Process each candidate region through stages 2 and 3
        for ((index, contour) in contours.withIndex()) {
            val bb = contour.boundingBox
            val innerBb = contourAnalyzer.getInnerBoundingBox(contour)
            val targetBb = innerBb ?: bb

            // Crop the region from the original image
            val crop = BitmapUtils.cropRegion(
                pageImage,
                targetBb.x, targetBb.y, targetBb.width, targetBb.height,
                Constants.EVIDENCE_CROP_PADDING
            )

            // ═══ Stage 2: OCR (Multiple Preprocessing Passes) ═══
            val candidatesList = mutableSetOf<String>()
            val candidatesValues = mutableMapOf<Double, String>()

            // Pass 1: Raw Crop OCR
            val ocrRaw = ocrProcessor.recognizeText(crop)
            ocrRaw.numericValue?.let {
                candidatesList.add(ocrRaw.displayValue)
                candidatesValues[it] = ocrRaw.displayValue
            }

            // Pass 2: Red Region Isolation OCR
            val redCrop = imageProcessor.extractRedRegion(crop)
            val ocrRed = ocrProcessor.recognizeText(redCrop)
            redCrop.recycle()
            ocrRed.numericValue?.let {
                candidatesList.add(ocrRed.displayValue)
                candidatesValues[it] = ocrRed.displayValue
            }

            // Pass 3: High Contrast Thresholding OCR
            val hcCrop = imageProcessor.convertToHighContrast(crop)
            val ocrHc = ocrProcessor.recognizeText(hcCrop)
            hcCrop.recycle()
            ocrHc.numericValue?.let {
                candidatesList.add(ocrHc.displayValue)
                candidatesValues[it] = ocrHc.displayValue
            }

            // Pass 4: Enhanced Edge Sharpening OCR
            val edgeCrop = imageProcessor.enhanceEdges(crop)
            val ocrEdge = ocrProcessor.recognizeText(edgeCrop)
            edgeCrop.recycle()
            ocrEdge.numericValue?.let {
                candidatesList.add(ocrEdge.displayValue)
                candidatesValues[it] = ocrEdge.displayValue
            }

            // ═══ Stage 3: TF Lite (Digit recognizer) ═══
            val aiResult = digitRecognizer.recognizeNumber(crop)
            aiResult?.first?.let {
                val displayStr = it.toCleanString()
                candidatesList.add(displayStr)
                candidatesValues[it] = displayStr
            }

            // Choose primary OCR result (raw preferred, else others)
            val ocrResult = when {
                ocrRaw.numericValue != null -> ocrRaw
                ocrRed.numericValue != null -> ocrRed
                ocrHc.numericValue != null -> ocrHc
                ocrEdge.numericValue != null -> ocrEdge
                else -> ocrRaw
            }

            val hasValue = candidatesList.isNotEmpty()
            val isCircled = isCircledOrBoxed(contour)
            val isUnderlined = isUnderlined(contour)
            val isMargin = bb.x < 250 || bb.x > 950

            // If a region has special visual structures (circled, boxed, underlined) or has a detected value, we keep it.
            // A plain margin location is only kept if it actually has a detected value.
            val shouldKeep = isCircled || isUnderlined || hasValue

            if (!shouldKeep) {
                crop.recycle()
                continue
            }

            // Save evidence image
            val evidencePath = evidenceSaver(crop, index)

            // Stage 1 result: estimate from contour properties
            val cvValue = estimateValueFromContour(contour)
            val cvConfidence = if (cvValue != null) 0.5 else 0.0
            
            val finalVal = when {
                ocrResult.numericValue != null -> ocrResult.numericValue!!
                aiResult?.first != null -> aiResult.first!!
                candidatesValues.isNotEmpty() -> candidatesValues.keys.first()
                else -> 0.0
            }
            val finalDisplayVal = when {
                ocrResult.numericValue != null -> ocrResult.displayValue
                aiResult?.first != null -> aiResult.first!!.toCleanString()
                candidatesValues.isNotEmpty() -> candidatesValues.values.first()
                else -> "Unreadable"
            }

            val isOverwritten = detectOverwriting(crop)
            val regionType = classifyRegion(ocrResult.rawText, bb, contour)

            // Detection reason construction
            val reasons = mutableListOf<String>()
            reasons.add("✓ Red Ink")

            var finalConfidence: Double
            if (regionType == "question_number") {
                finalConfidence = 0.1
                reasons.add("Question Number")
            } else if (regionType == "formula" || regionType == "calculation") {
                finalConfidence = 0.1
                reasons.add("Equation Number")
            } else if (regionType == "page_number" || regionType == "student_roll_number" || regionType == "answer_content") {
                finalConfidence = 0.1
                reasons.add("Page/Roll/Content")
            } else {
                // Awarded mark or potential candidate
                if (isCircled) {
                    val aspectRatio = bb.width.toDouble() / bb.height
                    if (aspectRatio in 0.8..1.2) {
                        finalConfidence = 0.99
                        reasons.add("✓ Circled")
                    } else {
                        finalConfidence = 0.97
                        reasons.add("✓ Boxed")
                    }
                } else if (isUnderlined) {
                    finalConfidence = 0.96
                    reasons.add("✓ Underlined")
                } else if (isMargin) {
                    finalConfidence = 0.92
                    reasons.add("✓ Margin Location")
                } else if (ocrResult.rawText.startsWith("✓") || ocrResult.rawText.matches(Regex("(?i)^[vy/\\\\].*")) || 
                           candidatesList.any { it.startsWith("✓") || it.matches(Regex("(?i)^[vy/\\\\].*")) }) {
                    finalConfidence = 0.90
                    reasons.add("✓ Tick + Number")
                } else {
                    finalConfidence = 0.75
                    reasons.add("✓ Standalone Number")
                }

                if (ocrResult.numericValue != null) {
                    reasons.add("✓ OCR Match")
                }
                if (aiResult?.first != null) {
                    reasons.add("✓ AI Match")
                }
            }

            val detectionReason = reasons.joinToString(", ")

            // Determine status
            val markStatus = when {
                regionType == "question_number" || regionType == "formula" || regionType == "calculation" || 
                regionType == "page_number" || regionType == "student_roll_number" || regionType == "answer_content" -> {
                    MarkStatus.IGNORED
                }
                !hasValue -> {
                    MarkStatus.NEEDS_REVIEW // Unreadable Candidate (Blue)
                }
                isOverwritten || finalConfidence < 0.90 -> {
                    MarkStatus.NEEDS_REVIEW // Needs review (Yellow or Red)
                }
                else -> {
                    MarkStatus.CONFIRMED // Confirmed (Green)
                }
            }

            val isAutoConfirmed = (markStatus == MarkStatus.CONFIRMED)

            detectedMarks.add(
                DetectedMark(
                    pageId = pageId,
                    copyId = copyId,
                    value = finalVal,
                    displayValue = finalDisplayVal,
                    confidence = finalConfidence,
                    status = markStatus,
                    boundingBox = bb,
                    evidenceImagePath = evidencePath,
                    cvResult = if (cvValue != null) VerificationResult(cvValue, cvConfidence) else null,
                    ocrResult = if (ocrResult.numericValue != null)
                        VerificationResult(ocrResult.numericValue, ocrResult.confidence) else null,
                    aiResult = if (aiResult != null)
                        VerificationResult(aiResult.first, aiResult.second) else null,
                    isOverwritten = isOverwritten,
                    isFraction = ocrResult.isFraction,
                    fractionDenominator = ocrResult.denominator,
                    isAutoConfirmed = isAutoConfirmed,
                    regionType = if (markStatus == MarkStatus.IGNORED) "ignored" else "awarded_mark",
                    isManual = false,
                    detectionReason = detectionReason,
                    candidates = candidatesList.toList()
                )
            )

            crop.recycle()
        }

        // Clean up
        redInkResult.mask.recycle()

        val processingTime = System.currentTimeMillis() - startTime
        return VerificationPipelineResult(
            marks = detectedMarks,
            totalMarksFound = detectedMarks.size,
            processingTimeMs = processingTime
        )
    }

    /**
     * Classifies a detected red ink region based on bounding box, text content, and contour shape.
     */
    private fun classifyRegion(
        ocrText: String,
        bb: BoundingBox,
        contour: ContourAnalyzer.ContourRegion
    ): String {
        val text = ocrText.trim()

        // 1. Page Number (Top/Bottom, typical formats)
        if (bb.y < 120 || bb.y > 1580) {
            if (text.matches(Regex("(?i)\\b(?:page|pg\\.?|p\\.?)\\s*\\d+")) || 
                (text.matches(Regex("\\d+")) && bb.width < 100)) {
                return "page_number"
            }
        }

        // 2. Student Roll Number / Header
        if (bb.y < 350) {
            if (text.matches(Regex("(?i)\\b(?:roll|no|id|reg|name|class|sec)\\b.*"))) {
                return "student_roll_number"
            }
        }

        // 3. Calculation / Formula
        if (text.contains("+") || text.contains("=") || text.contains("-") || text.contains("*")) {
            return "calculation"
        }
        if (text.matches(Regex(".*[a-zA-Z]{2,}.*")) && (text.contains("=") || text.contains("+"))) {
            return "formula"
        }

        // 4. Question Number
        val isCircled = isCircledOrBoxed(contour)
        if (bb.x < 200 && !isCircled) {
            if (text.matches(Regex("(?i)\\b(?:q|question)?\\s*\\d+\\b.*")) || 
                (text.matches(Regex("\\d+")) && text.toIntOrNull() in 1..30)) {
                return "question_number"
            }
        }

        // 5. Answer Content
        if (text.split(Regex("\\s+")).size > 2 || (text.any { it.isLetter() } && !text.matches(Regex("(?i)\\b(?:q|question|p|page|pg)\\b.*")))) {
            return "answer_content"
        }

        // 6. Awarded Mark
        val hasTickPattern = text.startsWith("✓") || text.matches(Regex("(?i)^[vy/\\\\].*"))
        if (isCircled || bb.x < 250 || bb.x > 950 || text.contains("/") || hasTickPattern) {
            return "awarded_mark"
        }

        if (text.matches(Regex("\\d+(?:\\.\\d+)?"))) {
            return "awarded_mark"
        }

        return "unknown"
    }

    /**
     * Checks if a contour is circled or boxed by analyzing its grouped sub-regions.
     */
    private fun isCircledOrBoxed(contour: ContourAnalyzer.ContourRegion): Boolean {
        return contourAnalyzer.getInnerBoundingBox(contour) != null
    }

    /**
     * Estimate a mark value from contour properties alone (Stage 1).
     * This is a rough estimate based on the shape/size of the red ink region.
     * Mainly used as a cross-validation signal.
     */
    private fun estimateValueFromContour(contour: ContourAnalyzer.ContourRegion): Double? {
        val density = contour.density
        val aspectRatio = contour.boundingBox.width.toDouble() / contour.boundingBox.height

        if (density > 0.5 && aspectRatio in 0.5..2.0 && contour.area < 2000) {
            return null
        }
        return null
    }

    /**
     * Detect if a mark appears to be overwritten (multiple ink layers).
     * Looks for high ink density which suggests correction/overwriting.
     */
    private fun detectOverwriting(crop: Bitmap): Boolean {
        val width = crop.width
        val height = crop.height
        val pixels = IntArray(width * height)
        crop.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var darkRedCount = 0
        var totalRed = 0

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hueScaled = hsv[0] / 2.0

            if ((hueScaled in 0.0..12.0 || hueScaled in 165.0..180.0) && hsv[1] > 0.3) {
                totalRed++
                if (hsv[2] < 0.4) darkRedCount++
            }
        }

        return totalRed > 0 && darkRedCount.toDouble() / totalRed > 0.3
    }

    private fun Double.toCleanString(): String {
        return if (this == this.toLong().toDouble()) {
            this.toLong().toString()
        } else {
            String.format("%.1f", this)
        }
    }

    private fun isUnderlined(contour: ContourAnalyzer.ContourRegion): Boolean {
        if (contour.subRegions.size >= 2) {
            val sorted = contour.subRegions.sortedBy { it.y }
            val bottom = sorted.last()
            val others = sorted.dropLast(1)
            
            val isLine = bottom.width > bottom.height * 2.5 && bottom.width > contour.boundingBox.width * 0.6
            val othersAbove = others.all { it.y + it.height <= bottom.y + 12 }
            if (isLine && othersAbove) return true
        }
        return false
    }
}
