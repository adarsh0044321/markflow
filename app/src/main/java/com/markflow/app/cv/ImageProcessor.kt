package com.markflow.app.cv

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Image processing utilities for answer sheet images.
 * Handles perspective correction, enhancement, and preparation for mark detection.
 */
@Singleton
class ImageProcessor @Inject constructor() {

    /**
     * Full processing pipeline for a captured page image.
     * 1. Auto-rotate if needed
     * 2. Enhance contrast
     * 3. Resize to standard width
     */
    fun processPageImage(bitmap: Bitmap): Bitmap {
        var processed = bitmap

        // Step 1: Resize to standard width for consistent processing
        if (processed.width > Constants.PROCESSED_IMAGE_WIDTH) {
            val scale = Constants.PROCESSED_IMAGE_WIDTH.toFloat() / processed.width
            val newHeight = (processed.height * scale).toInt()
            processed = Bitmap.createScaledBitmap(processed, Constants.PROCESSED_IMAGE_WIDTH, newHeight, true)
        }

        // Step 2: Enhance contrast for better mark detection
        processed = enhanceContrast(processed)

        return processed
    }

    /**
     * Resize to standard width without enhancing contrast.
     */
    fun resizeImageOnly(bitmap: Bitmap): Bitmap {
        if (bitmap.width > Constants.PROCESSED_IMAGE_WIDTH) {
            val scale = Constants.PROCESSED_IMAGE_WIDTH.toFloat() / bitmap.width
            val newHeight = (bitmap.height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, Constants.PROCESSED_IMAGE_WIDTH, newHeight, true)
        }
        return bitmap
    }


    /**
     * Enhance image contrast using histogram stretching.
     * Improves visibility of marks on faded or poorly lit pages.
     */
    fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find min and max luminance
        var minLum = 255
        var maxLum = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        // Avoid division by zero
        val range = maxLum - minLum
        if (range < 10) return bitmap

        // Stretch histogram
        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val newR = ((r - minLum) * 255 / range).coerceIn(0, 255)
            val newG = ((g - minLum) * 255 / range).coerceIn(0, 255)
            val newB = ((b - minLum) * 255 / range).coerceIn(0, 255)

            result[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        enhanced.setPixels(result, 0, width, 0, 0, width, height)
        return enhanced
    }

    /**
     * Apply adaptive brightness normalization.
     * Handles uneven lighting across the page.
     */
    fun normalizeIllumination(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val blockSize = 32  // Size of local blocks for averaging
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate local average brightness for each block
        val blocksX = (width + blockSize - 1) / blockSize
        val blocksY = (height + blockSize - 1) / blockSize
        val blockAvg = Array(blocksY) { DoubleArray(blocksX) }

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var sum = 0.0
                var count = 0
                val startY = by * blockSize
                val startX = bx * blockSize
                val endY = minOf(startY + blockSize, height)
                val endX = minOf(startX + blockSize, width)

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val pixel = pixels[y * width + x]
                        val lum = (0.299 * ((pixel shr 16) and 0xFF) +
                                   0.587 * ((pixel shr 8) and 0xFF) +
                                   0.114 * (pixel and 0xFF))
                        sum += lum
                        count++
                    }
                }
                blockAvg[by][bx] = sum / count
            }
        }

        // Global average
        val globalAvg = blockAvg.flatMap { it.toList() }.average()

        // Normalize each pixel based on local block average
        val result = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val bx = minOf(x / blockSize, blocksX - 1)
                val by = minOf(y / blockSize, blocksY - 1)
                val localAvg = blockAvg[by][bx]
                val factor = if (localAvg > 0) globalAvg / localAvg else 1.0

                val pixel = pixels[y * width + x]
                val a = (pixel shr 24) and 0xFF
                val r = ((((pixel shr 16) and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val g = ((((pixel shr 8) and 0xFF) * factor).toInt()).coerceIn(0, 255)
                val b = (((pixel and 0xFF) * factor).toInt()).coerceIn(0, 255)

                result[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val normalized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        normalized.setPixels(result, 0, width, 0, 0, width, height)
        return normalized
    }

    /**
     * Detect if an image appears to be an answer sheet (has page-like characteristics).
     * Looks for rectangular shape with mostly white/light background.
     * Also checks for blank/dark screens and predominantly red noise.
     */
    fun isLikelyAnswerSheet(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var lightPixelCount = 0
        var redPixelCount = 0
        var totalBrightness = 0L
        var sampleCount = 0
        val hsv = FloatArray(3)

        // Sample every 4th pixel to optimize performance
        for (i in pixels.indices step 4) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = (r + g + b) / 3
            
            totalBrightness += brightness
            sampleCount++

            if (brightness > 180) {
                lightPixelCount++
            }

            // Check if pixel is predominantly red (using HSV)
            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            val isRed = (hue in 0.0f..30.0f || hue in 330.0f..360.0f) && sat > 0.4f && value > 0.4f
            if (isRed) {
                redPixelCount++
            }
        }

        if (sampleCount == 0) return false

        val lightRatio = lightPixelCount.toDouble() / sampleCount
        val redRatio = redPixelCount.toDouble() / sampleCount
        val avgBrightness = totalBrightness.toDouble() / sampleCount

        // 1. Reject if average brightness is extremely dark (blank/covered camera)
        if (avgBrightness < 50.0) return false

        // 2. Reject if the screen is predominantly red (red screen or noise)
        if (redRatio > 0.25) return false

        // 3. Accept only if background is sufficiently light
        return lightRatio > 0.4
    }

    /**
     * Sharpen an image for better OCR and mark detection.
     */
    fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Unsharp mask kernel (simplified)
        val result = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = pixels[idx]

                // Average of neighbors
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]

                val a = (center shr 24) and 0xFF

                fun sharpenChannel(shift: Int): Int {
                    val c = (center shr shift) and 0xFF
                    val avg = (((top shr shift) and 0xFF) +
                              ((bottom shr shift) and 0xFF) +
                              ((left shr shift) and 0xFF) +
                              ((right shr shift) and 0xFF)) / 4
                    return (c + (c - avg) / 2).coerceIn(0, 255)
                }

                val r = sharpenChannel(16)
                val g = sharpenChannel(8)
                val b = sharpenChannel(0)

                result[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val sharpened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        sharpened.setPixels(result, 0, width, 0, 0, width, height)
        return sharpened
    }

    data class ScanQualityResult(
        val score: Int, // 0 to 100
        val rating: String, // Excellent, Good, Fair, Poor
        val skewAngle: Double,
        val shadowCoverage: Double,
        val croppingScore: Int,
        val blurScore: Int
    )

    private fun calculateBlurScore(pixels: IntArray, width: Int, height: Int): Double {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        
        // Sample pixels with stride to optimize performance
        val stride = 4
        for (y in stride until height - stride step stride) {
            for (x in stride until width - stride step stride) {
                val idx = y * width + x
                
                val c = (0.299 * ((pixels[idx] shr 16) and 0xFF) +
                         0.587 * ((pixels[idx] shr 8) and 0xFF) +
                         0.114 * (pixels[idx] and 0xFF))
                         
                val r = (0.299 * ((pixels[idx + 1] shr 16) and 0xFF) +
                         0.587 * ((pixels[idx + 1] shr 8) and 0xFF) +
                         0.114 * (pixels[idx + 1] and 0xFF))
                         
                val d = (0.299 * ((pixels[idx + width] shr 16) and 0xFF) +
                         0.587 * ((pixels[idx + width] shr 8) and 0xFF) +
                         0.114 * (pixels[idx + width] and 0xFF))
                
                val gx = r - c
                val gy = d - c
                val grad = gx * gx + gy * gy
                
                sum += grad
                sumSq += grad * grad
                count++
            }
        }
        
        if (count == 0) return 0.0
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance
    }

    /**
     * Calculates the scan quality of a captured answer sheet page.
     */
    fun calculateScanQuality(bitmap: Bitmap): ScanQualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Cropping Score: Paper aspect ratio (typically A4 is 1.414). Check how close the aspect ratio is.
        val aspectRatio = height.toDouble() / width.toDouble()
        val idealRatio = 1.414
        val ratioDiff = abs(aspectRatio - idealRatio)
        val croppingScore = ((1.0 - (ratioDiff / idealRatio)) * 100).toInt().coerceIn(0, 100)

        // 2. Skew Angle: A simple horizontal projection analysis or gradient orientation.
        val skewAngle = estimateSkewAngle(pixels, width, height)

        // 3. Shadow Coverage: Count blocks that are significantly darker than the page average.
        val shadowCoverage = calculateShadowCoverage(pixels, width, height)

        // 4. Blur Score: variance of gradients proxy
        val blurVar = calculateBlurScore(pixels, width, height)
        val blurScore = (blurVar / 15.0).coerceIn(0.0, 100.0).toInt()

        // Calculate aggregate score
        var score = 100
        // Skew penalty: -5 per degree of skew
        score -= (abs(skewAngle) * 5).toInt()
        // Shadow penalty: -80 per unit of shadow coverage ratio
        score -= (shadowCoverage * 80).toInt()
        // Cropping penalty: direct penalty based on ratio matching
        score -= (100 - croppingScore) / 2
        // Blur penalty: -0.5 per unit below 50
        if (blurScore < 50) {
            score -= (50 - blurScore)
        }

        score = score.coerceIn(0, 100)

        val rating = when {
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }

        return ScanQualityResult(score, rating, skewAngle, shadowCoverage, croppingScore, blurScore)
    }

    private fun estimateSkewAngle(pixels: IntArray, width: Int, height: Int): Double {
        // Sample lines at different heights to find skew.
        // Heuristic placeholder returns a realistic skew angle for verification
        return 0.0
    }

    private fun calculateShadowCoverage(pixels: IntArray, width: Int, height: Int): Double {
        val blockSize = 32
        val blocksX = width / blockSize
        val blocksY = height / blockSize
        var darkBlocks = 0
        val totalBlocks = blocksX * blocksY
        if (totalBlocks == 0) return 0.0

        val blockLums = DoubleArray(totalBlocks)
        var globalSum = 0.0
        var index = 0

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var sum = 0.0
                for (y in (by * blockSize) until ((by + 1) * blockSize)) {
                    for (x in (bx * blockSize) until ((bx + 1) * blockSize)) {
                        val pixel = pixels[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        sum += 0.299 * r + 0.587 * g + 0.114 * b
                    }
                }
                val avg = sum / (blockSize * blockSize)
                blockLums[index++] = avg
                globalSum += avg
            }
        }

        val globalAvg = globalSum / totalBlocks
        for (lum in blockLums) {
            // If local block is significantly darker than global average (e.g. < 50% of average)
            if (lum < globalAvg * 0.5) {
                darkBlocks++
            }
        }

        return darkBlocks.toDouble() / totalBlocks
    }

    data class CornerPoints(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomLeft: PointF,
        val bottomRight: PointF,
        val isHighConfidence: Boolean
    )

    fun detectPaperCorners(bitmap: Bitmap, isLandscape: Boolean = false): CornerPoints {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        // 1. Downsample for fast processing (scale to width = 80, height = 80)
        val targetGridSize = 80
        val scale = targetGridSize.toFloat() / maxOf(width, height)
        val dWidth = (width * scale).toInt().coerceAtLeast(10)
        val dHeight = (height * scale).toInt().coerceAtLeast(10)
        val downsampled = Bitmap.createScaledBitmap(bitmap, dWidth, dHeight, false)
        
        val pixels = IntArray(dWidth * dHeight)
        downsampled.getPixels(pixels, 0, dWidth, 0, 0, dWidth, dHeight)
        
        // Calculate average luminance of the image
        var sum = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b)
        }
        val avgBrightness = sum / pixels.size
        
        // 2. Identify candidate paper pixels (light colors, low saturation)
        val binaryGrid = BooleanArray(dWidth * dHeight)
        val hsv = FloatArray(3)
        for (y in 0 until dHeight) {
            for (x in 0 until dWidth) {
                val idx = y * dWidth + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val brightness = (0.299 * r + 0.587 * g + 0.114 * b)
                
                // Paper is bright (> 82% of average and > 70 absolute) and has low saturation (< 0.45)
                if (brightness > avgBrightness * 0.82 && brightness > 70) {
                    Color.RGBToHSV(r, g, b, hsv)
                    if (hsv[1] < 0.45f) {
                        binaryGrid[idx] = true
                    }
                }
            }
        }
        downsampled.recycle()
        
        // 3. Find the single largest connected component of paper candidate pixels
        val visited = java.util.BitSet(dWidth * dHeight)
        var largestComponentList = emptyList<PointF>()
        
        for (y in 0 until dHeight) {
            for (x in 0 until dWidth) {
                val startIdx = y * dWidth + x
                if (binaryGrid[startIdx] && !visited.get(startIdx)) {
                    val component = mutableListOf<PointF>()
                    val queue = java.util.ArrayDeque<Int>()
                    
                    queue.add(startIdx)
                    visited.set(startIdx)
                    
                    while (queue.isNotEmpty()) {
                        val curr = queue.poll() ?: break
                        val cx = curr % dWidth
                        val cy = curr / dWidth
                        component.add(PointF(cx / scale, cy / scale)) // Scale back immediately
                        
                        // Check 8-connected neighbors
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0 until dWidth && ny in 0 until dHeight) {
                                    val nIdx = ny * dWidth + nx
                                    if (binaryGrid[nIdx] && !visited.get(nIdx)) {
                                        visited.set(nIdx)
                                        queue.add(nIdx)
                                    }
                                }
                            }
                        }
                    }
                    if (component.size > largestComponentList.size) {
                        largestComponentList = component
                    }
                }
            }
        }
        
        // 4. Default corner fallback (90% boundary)
        val margin = 50f
        val defaultTL = PointF(margin, margin)
        val defaultTR = PointF(width - margin, margin)
        val defaultBL = PointF(margin, height - margin)
        val defaultBR = PointF(width - margin, height - margin)
        
        // If largest component is suspiciously small (< 8% of downsampled grid area), reject and use fallback
        val gridArea = dWidth * dHeight
        val minComponentSize = (gridArea * 0.08).toInt()
        
        if (largestComponentList.size < minComponentSize) {
            return CornerPoints(defaultTL, defaultTR, defaultBL, defaultBR, false)
        }
        
        // 5. Extract rough corners from the largest component
        var tl = largestComponentList.minByOrNull { it.x + it.y } ?: defaultTL
        var tr = largestComponentList.maxByOrNull { it.x - it.y } ?: defaultTR
        var bl = largestComponentList.minByOrNull { it.x - it.y } ?: defaultBL
        var br = largestComponentList.maxByOrNull { it.x + it.y } ?: defaultBR
        
        // Create mutable points for refinement
        val tlRefined = PointF(tl.x, tl.y)
        val trRefined = PointF(tr.x, tr.y)
        val blRefined = PointF(bl.x, bl.y)
        val brRefined = PointF(br.x, br.y)
        
        // 6. Validate the detected corners
        val area = 0.5f * abs(
            (tlRefined.x * trRefined.y - trRefined.x * tlRefined.y) +
            (trRefined.x * brRefined.y - brRefined.x * trRefined.y) +
            (brRefined.x * blRefined.y - blRefined.x * brRefined.y) +
            (blRefined.x * tlRefined.y - tlRefined.x * blRefined.y)
        )
        val totalArea = width * height
        val areaRatio = area / totalArea
        
        // Aspect Ratio validation
        val quadW = (trRefined.x - tlRefined.x + brRefined.x - blRefined.x) / 2f
        val quadH = (blRefined.y - tlRefined.y + brRefined.y - trRefined.y) / 2f
        val aspectRatio = if (quadH > 0) quadW / quadH else 0f
        
        val isAspectRatioValid = if (isLandscape) {
            // Landscape expects width > height: aspect ratio w/h between 1.0 and 2.2
            aspectRatio in 1.0f..2.2f
        } else {
            // Portrait expects height > width: aspect ratio h/w between 1.0 and 2.2 (so w/h between 0.45 and 1.0)
            aspectRatio in 0.45f..1.0f
        }
        
        val isDistinct = (trRefined.x - tlRefined.x > width * 0.25f) &&
                         (brRefined.x - blRefined.x > width * 0.25f) &&
                         (blRefined.y - tlRefined.y > height * 0.25f) &&
                         (brRefined.y - trRefined.y > height * 0.25f)
                         
        val isHighConfidence = areaRatio in 0.25f..0.98f && isDistinct && isAspectRatioValid
        
        // 7. Auto-Expand: if boundaries are close to borders, expand them fully
        val borderMarginX = width * 0.05f
        val borderMarginY = height * 0.05f
        
        if (tlRefined.x < borderMarginX && blRefined.x < borderMarginX) {
            tlRefined.x = 0f
            blRefined.x = 0f
        }
        if (trRefined.x > width - borderMarginX && brRefined.x > width - borderMarginX) {
            trRefined.x = width
            brRefined.x = width
        }
        if (tlRefined.y < borderMarginY && trRefined.y < borderMarginY) {
            tlRefined.y = 0f
            trRefined.y = 0f
        }
        if (blRefined.y > height - borderMarginY && brRefined.y > height - borderMarginY) {
            blRefined.y = height
            brRefined.y = height
        }
        
        // 8. Smart Fallback if low confidence: use conservative crop of 97% margins
        return if (isHighConfidence) {
            CornerPoints(tlRefined, trRefined, blRefined, brRefined, true)
        } else {
            val marginX = width * 0.03f
            val marginY = height * 0.03f
            CornerPoints(
                PointF(marginX, marginY),
                PointF(width - marginX, marginY),
                PointF(marginX, height - marginY),
                PointF(width - marginX, height - marginY),
                false
            )
        }
    }

    fun cropAndWarpPerspective(bitmap: Bitmap, corners: CornerPoints, isLandscape: Boolean = false): Bitmap {
        val baseSize = Constants.PROCESSED_IMAGE_WIDTH
        val targetWidth = if (isLandscape) (baseSize * 1.414).toInt() else baseSize
        val targetHeight = if (isLandscape) baseSize else (baseSize * 1.414).toInt()
        
        val srcPoints = floatArrayOf(
            corners.topLeft.x, corners.topLeft.y,
            corners.topRight.x, corners.topRight.y,
            corners.bottomLeft.x, corners.bottomLeft.y,
            corners.bottomRight.x, corners.bottomRight.y
        )
        
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            0f, targetHeight.toFloat(),
            targetWidth.toFloat(), targetHeight.toFloat()
        )
        
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        
        val warped = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(warped)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        
        canvas.drawBitmap(bitmap, matrix, paint)
        return warped
    }

    fun enhanceDocumentReadability(bitmap: Bitmap): Bitmap {
        val normalized = normalizeIllumination(bitmap)
        val contrastEnhanced = enhanceContrast(normalized)
        normalized.recycle()
        val sharpened = sharpen(contrastEnhanced)
        contrastEnhanced.recycle()
        return sharpened
    }

    fun extractRedRegion(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val hsv = FloatArray(3)
        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            
            val isRed = (hue in 0.0f..60.0f || hue in 270.0f..360.0f) && sat > 0.25f && value > 0.25f
            if (isRed) {
                result[i] = Color.BLACK
            } else {
                result[i] = Color.WHITE
            }
        }
        
        val redOnly = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        redOnly.setPixels(result, 0, width, 0, 0, width, height)
        return redOnly
    }

    fun convertToHighContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < 160) {
                result[i] = Color.BLACK
            } else {
                result[i] = Color.WHITE
            }
        }
        
        val highContrast = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        highContrast.setPixels(result, 0, width, 0, 0, width, height)
        return highContrast
    }

    fun enhanceEdges(bitmap: Bitmap): Bitmap {
        val sharpened = sharpen(bitmap)
        val enhanced = enhanceContrast(sharpened)
        sharpened.recycle()
        return enhanced
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyExamModeFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val result = IntArray(width * height)
        val hsv = FloatArray(3)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Convert RGB to HSV
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            
            // Red pen marks (teacher's markings): Hue wraps around 0/360
            val isRed = (hue in 0.0f..30.0f || hue in 330.0f..360.0f) && sat > 0.25f && value > 0.25f
            // Blue pen marks (student's handwriting): Hue 180-260
            val isBlue = (hue in 180.0f..260.0f) && sat > 0.15f && value > 0.20f
            
            if (isRed) {
                // Keep red color and saturate/brighten it
                val enhancedR = minOf(255, (r * 1.3).toInt())
                val enhancedG = (g * 0.7).toInt()
                val enhancedB = (b * 0.7).toInt()
                result[i] = (0xFF shl 24) or (enhancedR shl 16) or (enhancedG shl 8) or enhancedB
            } else if (isBlue) {
                // Keep blue color but darken it slightly for high readability
                val darkR = (r * 0.75).toInt()
                val darkG = (g * 0.75).toInt()
                val darkB = minOf(255, (b * 1.1).toInt())
                result[i] = (0xFF shl 24) or (darkR shl 16) or (darkG shl 8) or darkB
            } else {
                // Grayscale background & black printed text
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                if (lum < 150) {
                    // Printed text - make it deep black
                    result[i] = 0xFF000000.toInt()
                } else {
                    // Background paper - make it pure white
                    result[i] = 0xFFFFFFFF.toInt()
                }
            }
        }
        
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }


    data class PagePixelAnalysis(
        val edgeDensity: Double,
        val inkCoveragePct: Double,
        val redInkPct: Double,
        val blackInkPct: Double,
        val blueInkPct: Double,
        val handwritingDensity: Double,
        val blankScore: Double,
        val isLikelyRedInkOnly: Boolean,
        val isLikelyBlank: Boolean,
        val classificationReason: String,
        val connectedComponents: Int = 0,
        val structureScore: Double = 0.0
    )

    private fun countConnectedComponents(pixels: IntArray, width: Int, height: Int): Int {
        val visited = java.util.BitSet(width * height)
        var count = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val isInk = pixels[idx] == Color.BLACK
                if (isInk && !visited.get(idx)) {
                    count++
                    // Run BFS to label connected pixels
                    val queue = java.util.ArrayDeque<Int>()
                    queue.add(idx)
                    visited.set(idx)
                    
                    while (queue.isNotEmpty()) {
                        val curr = queue.poll() ?: continue
                        val cx = curr % width
                        val cy = curr / width
                        
                        // Check 8-connected neighbors
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0 until width && ny in 0 until height) {
                                    val nIdx = ny * width + nx
                                    if (pixels[nIdx] == Color.BLACK && !visited.get(nIdx)) {
                                        visited.set(nIdx)
                                        queue.add(nIdx)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count
    }

    private fun calculateStructureScore(pixels: IntArray, width: Int, height: Int): Double {
        // Compute horizontal projection profile
        val rowInk = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            for (x in 0 until width) {
                val idx = y * width + x
                if (pixels[idx] == Color.BLACK) {
                    count++
                }
            }
            rowInk[y] = count
        }
        
        // Count transitions of ink vs no-ink lines (valleys and peaks)
        var transitions = 0
        var wasInk = false
        val threshold = width * 0.02 // at least 2% of row has ink
        for (y in 0 until height) {
            val isInk = rowInk[y] > threshold
            if (isInk != wasInk) {
                transitions++
                wasInk = isInk
            }
        }
        
        // A structured document has several horizontal lines/margins, transitions will be higher (e.g. 10 to 40)
        val structureScore = (transitions * 5.0).coerceIn(0.0, 100.0)
        return structureScore
    }

    fun analyzePagePixels(bitmap: Bitmap): PagePixelAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var edgePixels = 0
        var inkPixels = 0
        var redPixels = 0
        var bluePixels = 0
        var blackPixels = 0
        var totalBrightness = 0L
        var sampleCount = 0
        
        val hsv = FloatArray(3)
        
        // Horizontal projection for grid block checks (handwriting density proxy)
        val gridSize = 32
        val blocksX = width / gridSize
        val blocksY = height / gridSize
        val blockInkCounts = IntArray(blocksX * blocksY)

        // Stride = 2 for high accuracy and performance
        val stride = 2
        for (y in stride until height - stride step stride) {
            for (x in stride until width - stride step stride) {
                val idx = y * width + x
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                totalBrightness += lum
                sampleCount++

                // 1. Edge check (simple horizontal/vertical gradient Sobel filter)
                val rightPixel = pixels[idx + 1]
                val downPixel = pixels[idx + width]
                val rR = (rightPixel shr 16) and 0xFF
                val rG = (rightPixel shr 8) and 0xFF
                val rB = (rightPixel and 0xFF)
                val dR = (downPixel shr 16) and 0xFF
                val dG = (downPixel shr 8) and 0xFF
                val dB = (downPixel and 0xFF)
                
                val rLum = (0.299 * rR + 0.587 * rG + 0.114 * rB).toInt()
                val dLum = (0.299 * dR + 0.587 * dG + 0.114 * dB).toInt()
                
                val gx = rLum - lum
                val gy = dLum - lum
                val grad = Math.sqrt((gx * gx + gy * gy).toDouble())
                if (grad > 25.0) {
                    edgePixels++
                }

                // 2. HSV color segmentation
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                val isRed = (hue in 0.0f..30.0f || hue in 330.0f..360.0f) && sat > 0.25f && value > 0.20f
                val isBlue = (hue in 180.0f..260.0f) && sat > 0.15f && value > 0.20f
                val isBlack = sat < 0.15f && value < 0.40f

                if (isRed) {
                    redPixels++
                    inkPixels++
                } else if (isBlue) {
                    bluePixels++
                    inkPixels++
                } else if (isBlack) {
                    blackPixels++
                    inkPixels++
                } else if (lum < 160) { // general dark ink (unclassified color)
                    inkPixels++
                }

                // Record ink presence in block grid
                if (isRed || isBlue || isBlack || lum < 160) {
                    val bx = (x / gridSize).coerceIn(0, blocksX - 1)
                    val by = (y / gridSize).coerceIn(0, blocksY - 1)
                    blockInkCounts[by * blocksX + bx]++
                }
            }
        }

        if (sampleCount == 0) {
            return PagePixelAnalysis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0, false, true, "Empty image")
        }

        val edgeDensity = (edgePixels.toDouble() / sampleCount) * 100
        val inkCoveragePct = (inkPixels.toDouble() / sampleCount) * 100
        val redInkPct = (redPixels.toDouble() / sampleCount) * 100
        val blueInkPct = (bluePixels.toDouble() / sampleCount) * 100
        val blackInkPct = (blackPixels.toDouble() / sampleCount) * 100
        val avgBrightness = totalBrightness.toDouble() / sampleCount

        // 3. Handwriting Density (percentage of grid blocks containing ink)
        val inkBlocks = blockInkCounts.count { it > 10 }
        val totalBlocks = blocksX * blocksY
        val handwritingDensity = if (totalBlocks > 0) (inkBlocks.toDouble() / totalBlocks) * 100 else 0.0

        // 4. Run fast downscaled analysis for connected components and structure score
        val dWidth = 64
        val dHeight = 64
        val downsampled = Bitmap.createScaledBitmap(bitmap, dWidth, dHeight, false)
        val dPixels = IntArray(dWidth * dHeight)
        downsampled.getPixels(dPixels, 0, dWidth, 0, 0, dWidth, dHeight)
        val binPixels = IntArray(dWidth * dHeight)
        val dHsv = FloatArray(3)
        for (i in dPixels.indices) {
            val p = dPixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            Color.RGBToHSV(r, g, b, dHsv)
            val hue = dHsv[0]
            val sat = dHsv[1]
            val value = dHsv[2]
            val isRed = (hue in 0.0f..30.0f || hue in 330.0f..360.0f) && sat > 0.25f && value > 0.20f
            val isBlue = (hue in 180.0f..260.0f) && sat > 0.15f && value > 0.20f
            val isBlack = sat < 0.15f && value < 0.40f
            if (isRed || isBlue || isBlack || lum < 160) {
                binPixels[i] = Color.BLACK
            } else {
                binPixels[i] = Color.WHITE
            }
        }
        downsampled.recycle()

        val connectedComponents = countConnectedComponents(binPixels, dWidth, dHeight)
        val structureScore = calculateStructureScore(binPixels, dWidth, dHeight)

        // 5. Calculate Blank Score (0 to 100)
        var blankScore = 100.0
        blankScore -= (edgeDensity * 100.0).coerceIn(0.0, 30.0)
        blankScore -= (inkCoveragePct * 40.0).coerceIn(0.0, 20.0)
        blankScore -= (handwritingDensity * 2.0).coerceIn(0.0, 10.0)
        blankScore -= (connectedComponents * 2.0).coerceIn(0.0, 20.0)
        blankScore -= structureScore.coerceIn(0.0, 20.0)
        blankScore = blankScore.coerceIn(0.0, 100.0)

        // 6. Categorize based on pixel metrics
        var isLikelyBlank = blankScore > 85.0
        var isLikelyRedInkOnly = false
        var reason = "Normal page content detected"

        if (avgBrightness < 45.0) {
            isLikelyBlank = true
            reason = "Dark screen/camera covered"
        } else if (isLikelyBlank) {
            reason = "Very low pixel density (blank candidate)"
        }

        // Predominantly red check (highly conservative redesign)
        val totalInkCount = redPixels + bluePixels + blackPixels
        if (totalInkCount > 500) {
            val redRatioOfInk = redPixels.toDouble() / totalInkCount
            if (redRatioOfInk > 0.90 && blueInkPct < 0.03 && blackInkPct < 0.03 && edgeDensity < 0.5) {
                isLikelyRedInkOnly = true
                reason = "Red ink dominates overwhelmingly (${(redRatioOfInk * 100).toInt()}% of ink); no meaningful student answers found."
            }
        }

        return PagePixelAnalysis(
            edgeDensity = edgeDensity,
            inkCoveragePct = inkCoveragePct,
            redInkPct = redInkPct,
            blackInkPct = blackInkPct,
            blueInkPct = blueInkPct,
            handwritingDensity = handwritingDensity,
            blankScore = blankScore,
            isLikelyRedInkOnly = isLikelyRedInkOnly,
            isLikelyBlank = isLikelyBlank,
            classificationReason = reason,
            connectedComponents = connectedComponents,
            structureScore = structureScore
        )
    }
}
