package com.markflow.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.markflow.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TensorFlow Lite digit recognizer for mark verification.
 * Uses a trained model to classify individual digit images.
 * Acts as Stage 3 of the dual verification pipeline.
 *
 * The model expects 28x28 grayscale images (MNIST-style input)
 * and outputs probabilities for digits 0-9.
 */
@Singleton
class DigitRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isInitialized = false

    data class DigitResult(
        val digit: Int,
        val confidence: Double,
        val allProbabilities: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DigitResult) return false
            return digit == other.digit && confidence == other.confidence
        }

        override fun hashCode(): Int {
            return 31 * digit + confidence.hashCode()
        }
    }

    /**
     * Initialize the TF Lite interpreter with the bundled model.
     * Call this once at application startup.
     */
    fun initialize() {
        if (isInitialized) return
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
            // App can still function without TF Lite (falls back to OCR only)
        }
    }

    /**
     * Recognize a digit from a cropped image.
     *
     * @param bitmap The cropped image containing a single digit
     * @return DigitResult with the recognized digit and confidence
     */
    fun recognizeDigit(bitmap: Bitmap): DigitResult? {
        if (!isInitialized || interpreter == null) return null

        try {
            // Preprocess: convert to 28x28 grayscale, normalize to 0-1
            val inputBuffer = preprocessImage(bitmap)

            // Output: 10 probabilities (digits 0-9)
            val output = Array(1) { FloatArray(10) }

            interpreter?.run(inputBuffer, output)

            val probabilities = output[0]
            val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
            val maxProb = probabilities[maxIdx]

            return DigitResult(
                digit = maxIdx,
                confidence = maxProb.toDouble(),
                allProbabilities = probabilities
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Recognize a multi-digit number from a cropped image.
     * Attempts to segment individual digits and recognize each one.
     *
     * @param bitmap The cropped image potentially containing multiple digits
     * @return The recognized numeric value and confidence, or null
     */
    fun recognizeNumber(bitmap: Bitmap): Pair<Double?, Double>? {
        if (!isInitialized) return null

        // For simple single-digit marks, use direct recognition
        val singleResult = recognizeDigit(bitmap)
        if (singleResult != null && singleResult.confidence > 0.5) {
            return singleResult.digit.toDouble() to singleResult.confidence
        }

        // For multi-digit: try to segment and recognize each digit
        val digits = segmentDigits(bitmap)
        if (digits.isEmpty()) return null

        val results = digits.mapNotNull { recognizeDigit(it) }
        if (results.isEmpty()) return null

        // Combine digits into a number
        var number = 0.0
        for (result in results) {
            number = number * 10 + result.digit
        }

        val avgConfidence = results.map { it.confidence }.average()
        return number to avgConfidence
    }

    private fun getRedness(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // If it's a binary mask (white on black background)
        if (r == 255 && g == 255 && b == 255) {
            return 1.0f
        }
        
        // Compute redness: how much red exceeds the green/blue channels
        val redness = r - maxOf(g, b)
        return if (redness > 15) {
            // Map redness range [15, 240] to [0.3, 1.0]
            0.3f + ((redness - 15).toFloat() / 225.0f).coerceIn(0.0f, 1.0f) * 0.7f
        } else {
            0.0f
        }
    }

    /**
     * Preprocess image for TF Lite model input.
     * Converts to 28x28 grayscale and normalizes pixel values to 0-1.
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = Constants.DIGIT_MODEL_INPUT_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val gray = getRedness(pixel)
            buffer.putFloat(gray)
        }

        buffer.rewind()
        scaled.recycle()
        return buffer
    }

    /**
     * Simple digit segmentation using vertical projection.
     * Splits a multi-digit image into individual digit bitmaps.
     */
    private fun segmentDigits(bitmap: Bitmap): List<Bitmap> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Vertical projection: count red ink pixels per column using getRedness helper
        val projection = IntArray(width)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = pixels[y * width + x]
                if (getRedness(pixel) > 0.15f) {
                    projection[x]++
                }
            }
        }

        // Find digit boundaries (gaps in projection)
        val segments = mutableListOf<Pair<Int, Int>>() // start, end columns
        var inDigit = false
        var segStart = 0

        val threshold = maxOf(1.0, height * 0.03)
        for (x in 0 until width) {
            if (projection[x] >= threshold && !inDigit) {
                segStart = x
                inDigit = true
            } else if (projection[x] < threshold && inDigit) {
                segments.add(segStart to x)
                inDigit = false
            }
        }
        if (inDigit) segments.add(segStart to width)

        // Crop each segment
        return segments.mapNotNull { (start, end) ->
            val segWidth = end - start
            if (segWidth < 3) return@mapNotNull null
            Bitmap.createBitmap(bitmap, start, 0, segWidth, height)
        }
    }

    /**
     * Load the TF Lite model file from assets.
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("ml/${Constants.DIGIT_MODEL_FILENAME}")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Release TF Lite resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}
