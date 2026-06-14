package com.markflow.app.cv

import android.graphics.Bitmap
import android.graphics.Color
import com.markflow.app.domain.model.BoundingBox
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects potentially unchecked answers on a page.
 * Analyzes handwritten content regions and checks if they have
 * corresponding red ink marks (ticks, scores) nearby.
 */
@Singleton
class UncheckedAnswerDetector @Inject constructor() {

    data class UncheckedRegion(
        val boundingBox: BoundingBox,
        val handwritingDensity: Double,
        val confidence: Double
    )

    /**
     * Detect handwriting regions that lack nearby red ink marks.
     *
     * Algorithm:
     * 1. Detect dark ink regions (student handwriting) — black/blue ink
     * 2. For each handwriting cluster, check if red ink exists nearby
     * 3. Flag clusters with writing but no marks
     *
     * @param originalBitmap The full page image
     * @param redMask The red ink binary mask
     * @return List of regions that may contain unchecked answers
     */
    fun detectUncheckedAnswers(
        originalBitmap: Bitmap,
        redMask: Bitmap
    ): List<UncheckedRegion> {
        val width = originalBitmap.width
        val height = originalBitmap.height

        // Read original pixels and red mask pixels into arrays
        val origPixels = IntArray(width * height)
        originalBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)

        val redPixels = IntArray(width * height)
        redMask.getPixels(redPixels, 0, width, 0, 0, width, height)

        val stripHeight = height / 10  // Divide page into ~10 answer regions
        val uncheckedRegions = mutableListOf<UncheckedRegion>()
        val hsv = FloatArray(3)

        for (stripIdx in 0 until 10) {
            val yStart = stripIdx * stripHeight
            val yEnd = minOf((stripIdx + 1) * stripHeight, height)

            // Count dark ink pixels in this strip
            var darkPixelCount = 0
            var redPixelCount = 0
            val stripPixelCount = (yEnd - yStart) * width

            for (y in yStart until yEnd) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val idx = rowOffset + x

                    // Check red ink from the red mask array
                    if (redPixels[idx] == Color.WHITE) {
                        redPixelCount++
                    }

                    // Check dark ink directly from original pixels
                    val pixel = origPixels[idx]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    Color.RGBToHSV(r, g, b, hsv)
                    val brightness = hsv[2]
                    val saturation = hsv[1]

                    // Dark ink: low brightness OR blue-ish ink (high saturation in blue range)
                    val isDark = brightness < 0.35
                    val isBlueInk = hsv[0] in 180f..260f && saturation > 0.3f && brightness < 0.6f

                    if (isDark || isBlueInk) {
                        darkPixelCount++
                    }
                }
            }

            val darkDensity = darkPixelCount.toDouble() / stripPixelCount
            val redDensity = redPixelCount.toDouble() / stripPixelCount

            // If there's significant handwriting but no red ink marks
            if (darkDensity > 0.02 && redDensity < 0.001) {
                uncheckedRegions.add(
                    UncheckedRegion(
                        boundingBox = BoundingBox(0, yStart, width, yEnd - yStart),
                        handwritingDensity = darkDensity,
                        confidence = calculateUncheckedConfidence(darkDensity, redDensity)
                    )
                )
            }
        }

        return uncheckedRegions
    }

    /**
     * Calculate confidence that a region is truly unchecked.
     * Higher dark density + lower red density = higher confidence.
     */
    private fun calculateUncheckedConfidence(darkDensity: Double, redDensity: Double): Double {
        val darkScore = minOf(darkDensity / 0.05, 1.0)  // Normalize to 0-1
        val redPenalty = minOf(redDensity / 0.002, 1.0)  // Penalty for any red ink
        return (darkScore * (1.0 - redPenalty)).coerceIn(0.0, 1.0)
    }
}
