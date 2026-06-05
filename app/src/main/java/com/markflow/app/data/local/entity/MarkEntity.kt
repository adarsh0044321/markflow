package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single detected mark on a page. Each mark goes through a
 * three-stage verification pipeline (CV → OCR → AI) and stores results
 * from each stage along with the final confidence score.
 */
@Entity(
    tableName = "marks",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["copyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId"), Index("copyId")]
)
data class MarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Reference to the page this mark was found on */
    val pageId: Long,

    /** Reference to the parent copy (denormalized for query efficiency) */
    val copyId: Long,

    /** The final numeric value of the mark (e.g., 7.0, 4.5) */
    val value: Double,

    /** Human-readable display value preserving original format ("7/10", "4.5", "8") */
    val displayValue: String,

    /** Combined confidence score from all verification stages (0.0 to 1.0) */
    val confidence: Double,

    /**
     * Mark status:
     * - confirmed: passed all verification stages
     * - needs_review: low confidence, awaiting teacher input
     * - edited: teacher manually corrected the value
     * - rejected: teacher marked as false detection
     * - ignored: teacher chose to skip
     */
    val status: String = "confirmed",

    // ── Bounding Box (on the full page image) ──
    val boundingBoxX: Int,
    val boundingBoxY: Int,
    val boundingBoxWidth: Int,
    val boundingBoxHeight: Int,

    // ── Evidence ──
    /** File path to the cropped evidence image of this mark */
    val evidenceImagePath: String? = null,

    // ── Three-Stage Verification Results ──

    /** Stage 1: Value detected by computer vision (contour analysis) */
    val cvDetectedValue: Double? = null,
    /** Stage 1 confidence */
    val cvConfidence: Double? = null,

    /** Stage 2: Value detected by ML Kit OCR */
    val ocrDetectedValue: Double? = null,
    /** Stage 2 confidence */
    val ocrConfidence: Double? = null,

    /** Stage 3: Value detected by TF Lite digit recognizer */
    val aiDetectedValue: Double? = null,
    /** Stage 3 confidence */
    val aiConfidence: Double? = null,

    // ── Flags ──
    /** Whether the mark appears to be overwritten/corrected */
    val isOverwritten: Boolean = false,

    /** Whether this is a fraction mark (e.g., 7/10) */
    val isFraction: Boolean = false,

    /** Denominator if fraction (e.g., 10 in "7/10") */
    val fractionDenominator: Double? = null,

    /** Whether this mark was auto-confirmed (high confidence) or needs review */
    val isAutoConfirmed: Boolean = true,

    /** Classification of the detected mark region */
    val regionType: String = "awarded_mark",

    /** Whether this mark was manually added by the teacher */
    val isManual: Boolean = false,

    /** The reason why this mark was detected (e.g. Red Ink, Circled, OCR Match, etc.) */
    val detectionReason: String = "",

    /** Comma-separated alternative OCR interpretations */
    val candidates: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
