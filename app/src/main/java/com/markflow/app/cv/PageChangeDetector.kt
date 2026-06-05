package com.markflow.app.cv

import android.graphics.Bitmap
import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects page changes in the camera feed by comparing consecutive frames.
 * Uses structural similarity analysis to determine when a page has been
 * replaced and when the new page has stabilized (ready for capture).
 */
@Singleton
class PageChangeDetector @Inject constructor() {

    /** The previous frame (N-1) downscaled for stability checks */
    private var previousFrame: Bitmap? = null

    /** The last successfully captured page frame downscaled as reference */
    private var lastCapturedFrame: Bitmap? = null

    /** Timestamp of last capture */
    private var lastCaptureTime = 0L

    /** Number of consecutive stable frames */
    private var stableFrameCount = 0

    /**
     * State of the page change detection.
     */
    enum class PageState {
        /** No change detected, viewing current page */
        STABLE,
        /** Page is being turned (motion detected) */
        CHANGING,
        /** New page detected, waiting for stabilization */
        STABILIZING,
        /** New page is stable, ready for capture */
        READY_TO_CAPTURE
    }

    data class DetectionResult(
        val state: PageState,
        val similarity: Double,
        val stableFrames: Int,
        val shouldCapture: Boolean
    )

    /**
     * Process a new camera frame and determine the page state.
     *
     * @param frame Current camera frame (will be downscaled internally)
     * @return DetectionResult with current state and capture recommendation
     */
    fun processFrame(frame: Bitmap): DetectionResult {
        // Downscale for faster comparison
        val downscaled = Bitmap.createScaledBitmap(frame, 64, 64, true)

        val prev = previousFrame
        if (prev == null) {
            // First frame — initialize previous and last captured frames
            previousFrame = downscaled
            if (lastCapturedFrame == null) {
                lastCapturedFrame = Bitmap.createScaledBitmap(downscaled, 64, 64, true)
            }
            return DetectionResult(PageState.STABLE, 1.0, 0, false)
        }

        // 1. Calculate similarity between consecutive frames (stability check)
        val consecutiveSimilarity = calculateSimilarity(prev, downscaled)

        // Recycle the previous frame and set current as the new previous
        previousFrame?.recycle()
        previousFrame = downscaled

        val now = System.currentTimeMillis()
        val timeSinceLastCapture = now - lastCaptureTime

        // If consecutive similarity is low (< 0.95), device is moving/unstable
        if (consecutiveSimilarity < 0.95) {
            stableFrameCount = 0
            return DetectionResult(PageState.CHANGING, consecutiveSimilarity, 0, false)
        }

        // 2. If stable, check similarity against the last captured frame
        val ref = lastCapturedFrame
        if (ref == null) {
            // No reference page captured yet, treat this stable frame as a new page
            lastCapturedFrame = Bitmap.createScaledBitmap(downscaled, 64, 64, true)
            stableFrameCount = 0
            return DetectionResult(PageState.STABLE, 1.0, 0, false)
        }

        val referenceSimilarity = calculateSimilarity(ref, downscaled)

        // If reference similarity is low (< 0.82), a new page is present
        if (referenceSimilarity < 0.82) {
            stableFrameCount++
            return if (stableFrameCount >= 8 && timeSinceLastCapture >= Constants.MIN_CAPTURE_INTERVAL_MS) {
                DetectionResult(PageState.READY_TO_CAPTURE, referenceSimilarity, stableFrameCount, true)
            } else {
                DetectionResult(PageState.STABILIZING, referenceSimilarity, stableFrameCount, false)
            }
        } else {
            // Viewing the same page that was already captured
            stableFrameCount = 0
            return DetectionResult(PageState.STABLE, referenceSimilarity, 0, false)
        }
    }

    /**
     * Call this after a successful capture to update the last captured reference frame.
     */
    fun onPageCaptured(capturedFrame: Bitmap) {
        lastCapturedFrame?.recycle()
        lastCapturedFrame = Bitmap.createScaledBitmap(capturedFrame, 64, 64, true)
        
        previousFrame?.recycle()
        previousFrame = Bitmap.createScaledBitmap(capturedFrame, 64, 64, true)

        lastCaptureTime = System.currentTimeMillis()
        stableFrameCount = 0
    }

    /**
     * Reset the detector state (e.g., when starting a new scan).
     */
    fun reset() {
        previousFrame?.recycle()
        previousFrame = null
        
        lastCapturedFrame?.recycle()
        lastCapturedFrame = null
        
        lastCaptureTime = 0
        stableFrameCount = 0
    }

    /**
     * Calculate structural similarity between two frames.
     * Uses normalized cross-correlation of luminance values.
     *
     * @return Similarity score between 0 (completely different) and 1 (identical)
     */
    private fun calculateSimilarity(frame1: Bitmap, frame2: Bitmap): Double {
        val w = minOf(frame1.width, frame2.width)
        val h = minOf(frame1.height, frame2.height)
        val size = w * h

        val pixels1 = IntArray(size)
        val pixels2 = IntArray(size)
        frame1.getPixels(pixels1, 0, w, 0, 0, w, h)
        frame2.getPixels(pixels2, 0, w, 0, 0, w, h)

        var sum1 = 0.0
        var sum2 = 0.0
        var sumSq1 = 0.0
        var sumSq2 = 0.0
        var sumProduct = 0.0

        for (i in 0 until size) {
            val lum1 = luminance(pixels1[i])
            val lum2 = luminance(pixels2[i])
            sum1 += lum1
            sum2 += lum2
            sumSq1 += lum1 * lum1
            sumSq2 += lum2 * lum2
            sumProduct += lum1 * lum2
        }

        val mean1 = sum1 / size
        val mean2 = sum2 / size
        val var1 = sumSq1 / size - mean1 * mean1
        val var2 = sumSq2 / size - mean2 * mean2
        val covar = sumProduct / size - mean1 * mean2

        // SSIM-like formula
        val c1 = 6.5025  // (0.01 * 255)^2
        val c2 = 58.5225 // (0.03 * 255)^2

        val numerator = (2 * mean1 * mean2 + c1) * (2 * covar + c2)
        val denominator = (mean1 * mean1 + mean2 * mean2 + c1) * (var1 + var2 + c2)

        return if (denominator == 0.0) 1.0 else numerator / denominator
    }

    private fun luminance(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
