package com.markflow.app.cv

import android.graphics.Bitmap
import com.markflow.app.util.BitmapUtils
import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects duplicate pages by comparing perceptual hashes, image similarity, and OCR texts.
 * Prevents the same page from being scanned twice using a multi-stage check.
 */
@Singleton
class DuplicateDetector @Inject constructor() {

    private val pageHashes = mutableMapOf<Long, String>() // pageId -> hash
    private val pageGrayscales = mutableMapOf<Long, DoubleArray>() // pageId -> 32x32 grayscale array
    private val pageOcrTexts = mutableMapOf<Long, String>() // pageId -> ocr text

    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val matchingPageId: Long?,
        val confidence: Double
    )

    /**
     * Check if a page is a duplicate of any previously scanned page in the current copy.
     */
    fun checkDuplicate(
        bitmap: Bitmap,
        ocrText: String,
        excludePageId: Long? = null
    ): DuplicateCheckResult {
        val hash = BitmapUtils.generatePerceptualHash(bitmap)
        val size = 32
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        small.recycle()
        val currentGrays = getGrayscales(pixels)

        var highestConfidence = 0.0
        var bestMatchPageId: Long? = null

        for ((pageId, existingHash) in pageHashes) {
            if (pageId == excludePageId) continue

            // Stage 1: pHash distance
            val distance = BitmapUtils.hammingDistance(hash, existingHash)
            val hashSimilarity = (1.0 - (distance / 64.0)).coerceIn(0.0, 1.0)

            // Stage 2: Image Similarity Score (Pearson Correlation)
            val existingGrays = pageGrayscales[pageId]
            val imageSimilarity = if (existingGrays != null) {
                computePearsonCorrelation(currentGrays, existingGrays)
            } else {
                0.0
            }

            // Stage 3: OCR Text Similarity
            val existingOcr = pageOcrTexts[pageId] ?: ""
            val ocrSimilarity = calculateOcrSimilarity(ocrText, existingOcr)

            // Combined confidence score
            val combined = 0.3 * hashSimilarity + 0.4 * imageSimilarity + 0.3 * ocrSimilarity

            if (combined > highestConfidence) {
                highestConfidence = combined
                bestMatchPageId = pageId
            }
        }

        val isDuplicate = highestConfidence >= 0.75

        return DuplicateCheckResult(isDuplicate, bestMatchPageId, highestConfidence)
    }

    /**
     * Register a page's visual features and OCR text after capture.
     */
    fun registerPage(pageId: Long, bitmap: Bitmap, ocrText: String) {
        val hash = BitmapUtils.generatePerceptualHash(bitmap)
        pageHashes[pageId] = hash
        
        val size = 32
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        small.recycle()
        pageGrayscales[pageId] = getGrayscales(pixels)
        pageOcrTexts[pageId] = ocrText
    }

    private fun getGrayscales(pixels: IntArray): DoubleArray {
        val grays = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            grays[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }
        return grays
    }

    private fun computePearsonCorrelation(grays1: DoubleArray, grays2: DoubleArray): Double {
        val avg1 = grays1.average()
        val avg2 = grays2.average()

        var num = 0.0
        var den1 = 0.0
        var den2 = 0.0
        for (i in grays1.indices) {
            val diff1 = grays1[i] - avg1
            val diff2 = grays2[i] - avg2
            num += diff1 * diff2
            den1 += diff1 * diff1
            den2 += diff2 * diff2
        }
        if (den1 == 0.0 || den2 == 0.0) return 0.0
        val r = num / (Math.sqrt(den1) * Math.sqrt(den2))
        return ((r + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun calculateOcrSimilarity(text1: String, text2: String): Double {
        val words1 = text1.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val words2 = text2.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Clear all cached features (when starting a new copy scan).
     */
    fun reset() {
        pageHashes.clear()
        pageGrayscales.clear()
        pageOcrTexts.clear()
    }

    /**
     * Remove a specific page from the cache.
     */
    fun removePage(pageId: Long) {
        pageHashes.remove(pageId)
        pageGrayscales.remove(pageId)
        pageOcrTexts.remove(pageId)
    }
}
