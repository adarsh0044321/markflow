package com.markflow.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Utility functions for bitmap operations — scaling, rotation, hashing, cropping.
 */
object BitmapUtils {

    /**
     * Scale a bitmap to fit within the specified max dimensions while maintaining aspect ratio.
     */
    fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (ratio >= 1f) return bitmap
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Crop a region from a bitmap with padding.
     */
    fun cropRegion(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        padding: Int = Constants.EVIDENCE_CROP_PADDING
    ): Bitmap {
        val left = maxOf(0, x - padding)
        val top = maxOf(0, y - padding)
        val right = minOf(bitmap.width, x + width + padding)
        val bottom = minOf(bitmap.height, y + height + padding)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /**
     * Save a bitmap to a file.
     */
    fun saveBitmap(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = Constants.IMAGE_QUALITY
    ): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Load a bitmap from file with optional downsampling.
     */
    fun loadBitmap(file: File, maxWidth: Int = 0): Bitmap? {
        if (!file.exists()) return null
        return try {
            if (maxWidth > 0) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, maxWidth, maxWidth)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculate optimal sample size for bitmap downsampling.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Generate a perceptual hash (pHash) for duplicate detection.
     * Simple implementation using average hash algorithm.
     */
    fun generatePerceptualHash(bitmap: Bitmap): String {
        // Resize to 8x8 and convert to grayscale
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64)
        small.getPixels(pixels, 0, 8, 0, 0, 8, 8)

        // Calculate average luminance
        val grays = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b)
        }
        val average = grays.average()

        // Build hash: 1 if above average, 0 if below
        val hash = StringBuilder()
        grays.forEach { gray ->
            hash.append(if (gray >= average) "1" else "0")
        }

        // Convert binary string to hex
        val result = hash.toString().chunked(4).joinToString("") {
            it.toInt(2).toString(16)
        }

        small.recycle()
        return result
    }

    /**
     * Calculate Hamming distance between two perceptual hashes.
     */
    fun hammingDistance(hash1: String, hash2: String): Int {
        if (hash1.length != hash2.length) return Int.MAX_VALUE
        return hash1.zip(hash2).count { (a, b) -> a != b }
    }

    /**
     * Create a thumbnail bitmap.
     */
    fun createThumbnail(bitmap: Bitmap, size: Int = Constants.THUMBNAIL_SIZE): Bitmap {
        return scaleBitmap(bitmap, size, size)
    }

    /**
     * Rotate bitmap based on EXIF orientation.
     */
    fun rotateIfNeeded(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Convert bitmap to grayscale.
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        grayscale.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayscale
    }
}
