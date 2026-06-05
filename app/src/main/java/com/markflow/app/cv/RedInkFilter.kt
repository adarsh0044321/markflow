package com.markflow.app.cv

import android.graphics.Bitmap
import android.graphics.Color
import com.markflow.app.data.repository.SettingsRepository
import com.markflow.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filters red ink regions from answer sheet images using HSV color space analysis.
 * Red ink is used by teachers to write marks, ticks, and corrections.
 * This filter isolates those marks from the blue/black student handwriting and printed text.
 */
@Singleton
class RedInkFilter @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    private var sensitivity = 50.0

    init {
        CoroutineScope(Dispatchers.Default).launch {
            settingsRepository.markSensitivityFlow.collect {
                sensitivity = it.toDoubleOrNull() ?: 50.0
            }
        }
    }

    /**
     * Result of red ink filtering — contains the binary mask and detected red pixel positions.
     */
    data class RedInkResult(
        /** Binary mask where white = red ink, black = background */
        val mask: Bitmap,
        /** Total number of red pixels detected */
        val redPixelCount: Int,
        /** Ratio of red pixels to total pixels */
        val redRatio: Double
    )

    /**
     * Detect red ink regions in the given bitmap.
     *
     * Uses HSV color space to identify red ink marks:
     * - Red wraps around hue 0/360, so we check two ranges: [0-12] and [165-180]
     * - Minimum saturation filters out faded pink / white areas
     * - Minimum brightness filters out dark shadows
     *
     * @param bitmap The input image (RGB)
     * @return RedInkResult with the binary mask and statistics
     */
    fun detectRedInk(bitmap: Bitmap): RedInkResult {
        val width = bitmap.width
        val height = bitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        var redCount = 0
        val hsv = FloatArray(3)

        // Dynamically compute thresholds based on sensitivity (0 to 100 range)
        val baseSat = maxOf(10.0, 60.0 - (sensitivity * 0.5))
        val baseVal = maxOf(10.0, 50.0 - (sensitivity * 0.4))

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Convert RGB to HSV
            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]       // 0-360
            val sat = hsv[1] * 100 // 0-100
            val value = hsv[2] * 100 // 0-100

            // Scale hue to 0-180 range (OpenCV convention)
            val hueScaled = hue / 2.0

            val isRed = (
                (hueScaled in Constants.RED_HUE_LOW_1..Constants.RED_HUE_HIGH_1 ||
                 hueScaled in Constants.RED_HUE_LOW_2..Constants.RED_HUE_HIGH_2) &&
                sat >= baseSat &&
                value >= baseVal
            )

            if (isRed) {
                maskPixels[i] = Color.WHITE
                redCount++
            } else {
                maskPixels[i] = Color.BLACK
            }
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)

        // Apply simple morphological cleanup (dilate then erode to fill gaps)
        val cleanedMask = morphologicalClean(mask, width, height)

        return RedInkResult(
            mask = cleanedMask,
            redPixelCount = redCount,
            redRatio = redCount.toDouble() / (width * height)
        )
    }

    /**
     * Simple morphological cleanup — dilation followed by erosion.
     * Fills small gaps in detected marks and removes isolated noise pixels.
     */
    private fun morphologicalClean(mask: Bitmap, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        // Dilation (3x3 kernel) — expand white regions
        val dilated = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var isWhite = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (pixels[(y + dy) * width + (x + dx)] == Color.WHITE) {
                            isWhite = true
                            break
                        }
                    }
                    if (isWhite) break
                }
                dilated[y * width + x] = if (isWhite) Color.WHITE else Color.BLACK
            }
        }

        // Erosion (3x3 kernel) — shrink white regions back
        val eroded = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var allWhite = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dilated[(y + dy) * width + (x + dx)] != Color.WHITE) {
                            allWhite = false
                            break
                        }
                    }
                    if (!allWhite) break
                }
                eroded[y * width + x] = if (allWhite) Color.WHITE else Color.BLACK
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(eroded, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Enhanced red detection with adaptive thresholds.
     * Adjusts sensitivity based on the image's overall color distribution.
     */
    fun detectRedInkAdaptive(bitmap: Bitmap): RedInkResult {
        // First pass — analyze color distribution
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var totalSat = 0.0
        var totalVal = 0.0

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            Color.RGBToHSV(r, g, b, hsv)
            totalSat += hsv[1]
            totalVal += hsv[2]
        }

        val avgSat = totalSat / pixels.size
        val avgVal = totalVal / pixels.size

        // Dynamically compute thresholds based on sensitivity (0 to 100 range)
        val baseSat = maxOf(10.0, 60.0 - (sensitivity * 0.5))
        val baseVal = maxOf(10.0, 50.0 - (sensitivity * 0.4))

        // If the image is very washed out, lower saturation threshold
        // If very dark, lower brightness threshold
        val satThreshold = maxOf(10.0, if (avgSat < 0.3) baseSat * 0.6 else baseSat)
        val valThreshold = maxOf(10.0, if (avgVal < 0.4) baseVal * 0.6 else baseVal)

        return detectRedInkWithThresholds(bitmap, satThreshold, valThreshold)
    }

    private fun detectRedInkWithThresholds(bitmap: Bitmap, satMin: Double, valMin: Double): RedInkResult {
        val width = bitmap.width
        val height = bitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        var redCount = 0
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            Color.RGBToHSV(
                (pixel shr 16) and 0xFF,
                (pixel shr 8) and 0xFF,
                pixel and 0xFF,
                hsv
            )
            val hueScaled = hsv[0] / 2.0
            val sat = hsv[1] * 100
            val value = hsv[2] * 100

            val isRed = (
                (hueScaled in Constants.RED_HUE_LOW_1..Constants.RED_HUE_HIGH_1 ||
                 hueScaled in Constants.RED_HUE_LOW_2..Constants.RED_HUE_HIGH_2) &&
                sat >= satMin && value >= valMin
            )

            if (isRed) {
                maskPixels[i] = Color.WHITE
                redCount++
            } else {
                maskPixels[i] = Color.BLACK
            }
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        val cleanedMask = morphologicalClean(mask, width, height)

        return RedInkResult(
            mask = cleanedMask,
            redPixelCount = redCount,
            redRatio = redCount.toDouble() / (width * height)
        )
    }
}
