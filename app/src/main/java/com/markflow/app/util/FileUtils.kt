package com.markflow.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File management utilities for organizing scanned pages, evidence crops, and exports.
 */
object FileUtils {

    /**
     * Get or create the base storage directory for MarkFlow data.
     */
    fun getAppStorageDir(context: Context): File {
        val dir = File(context.filesDir, "markflow_data")
        dir.mkdirs()
        return dir
    }

    /**
     * Get directory for page images of a specific copy.
     */
    fun getPagesDir(context: Context, copyId: Long): File {
        val dir = File(getAppStorageDir(context), "${Constants.PAGES_DIR}/$copyId")
        dir.mkdirs()
        return dir
    }

    /**
     * Get directory for evidence crop images of a specific copy.
     */
    fun getEvidenceDir(context: Context, copyId: Long): File {
        val dir = File(getAppStorageDir(context), "${Constants.EVIDENCE_DIR}/$copyId")
        dir.mkdirs()
        return dir
    }

    /**
     * Get directory for thumbnails.
     */
    fun getThumbnailsDir(context: Context, copyId: Long): File {
        val dir = File(getAppStorageDir(context), "${Constants.THUMBNAILS_DIR}/$copyId")
        dir.mkdirs()
        return dir
    }

    /**
     * Get directory for exported reports.
     */
    fun getReportsDir(context: Context): File {
        val dir = File(getAppStorageDir(context), Constants.REPORTS_DIR)
        dir.mkdirs()
        return dir
    }

    /**
     * Generate a unique filename for a page image.
     */
    fun generatePageFileName(copyId: Long, pageNumber: Int): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "page_${copyId}_${pageNumber}_$timestamp.jpg"
    }

    /**
     * Generate a unique filename for an evidence crop.
     */
    fun generateEvidenceFileName(copyId: Long, pageNumber: Int, markIndex: Int): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "evidence_${copyId}_p${pageNumber}_m${markIndex}_$timestamp.jpg"
    }

    /**
     * Generate a filename for a report export.
     */
    fun generateReportFileName(prefix: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$timestamp.$extension"
    }

    /**
     * Delete all files associated with a copy (pages, evidence, thumbnails).
     */
    fun deleteCopyFiles(context: Context, copyId: Long) {
        getPagesDir(context, copyId).deleteRecursively()
        getEvidenceDir(context, copyId).deleteRecursively()
        getThumbnailsDir(context, copyId).deleteRecursively()
    }

    /**
     * Get the total storage used by the app (in bytes).
     */
    fun getStorageUsed(context: Context): Long {
        return getAppStorageDir(context).walkTopDown().sumOf { it.length() }
    }

    /**
     * Format file size in human-readable format.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
