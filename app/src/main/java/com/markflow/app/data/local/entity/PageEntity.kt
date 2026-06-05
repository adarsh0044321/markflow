package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single scanned page of an answer copy.
 * Stores the full page image path and a perceptual hash for duplicate detection.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = CopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["copyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("copyId"), Index("pageHash")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Reference to the parent copy */
    val copyId: Long,

    /** Sequential page number within the copy */
    val pageNumber: Int,

    /** Absolute file path to the full page image */
    val imagePath: String,

    /** Path to a smaller thumbnail for list displays */
    val thumbnailPath: String? = null,

    /** Perceptual hash of the page image for duplicate detection */
    val pageHash: String = "",

    /** Total marks detected on this specific page */
    val pageTotal: Double = 0.0,

    /** Number of marks detected on this page */
    val markCount: Int = 0,

    /** Whether this page was flagged as a potential duplicate */
    val isDuplicate: Boolean = false,

    /** Whether unchecked answers were detected on this page */
    val hasUncheckedAnswers: Boolean = false,

    /** Number of unchecked answer regions */
    val uncheckedAnswerCount: Int = 0,

    /** Processing status: captured, processing, processed, error */
    val status: String = "captured",

    val scanQualityScore: Int = 100,
    val scanQualityRating: String = "Excellent",
    val ocrText: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)
