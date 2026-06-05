package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single answer copy (one student's answer sheet).
 * Each copy belongs to a session and contains multiple pages.
 */
@Entity(
    tableName = "copies",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class CopyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Reference to the parent session */
    val sessionId: Long,

    /** Sequential copy number within the session (Copy #1, #2, ...) */
    val copyNumber: Int,

    /** AI-calculated total marks from all detected marks */
    val calculatedTotal: Double = 0.0,

    /** Teacher-written total detected on the sheet (for comparison) */
    val writtenTotal: Double? = null,

    /** Number of pages scanned for this copy */
    val pageCount: Int = 0,

    /** Number of marks detected across all pages */
    val markCount: Int = 0,

    /** Overall confidence score (weighted average of all mark confidences) */
    val overallConfidence: Double = 0.0,

    /** Copy status: scanning, processing, completed, reviewed */
    val status: String = "scanning",

    /** Whether any issues were flagged (totaling error, unchecked answers, etc.) */
    val hasIssues: Boolean = false,

    /** Number of issues detected */
    val issueCount: Int = 0,

    /** Number of marks needing teacher review */
    val reviewCount: Int = 0,

    /** Whether the teacher has fully verified this copy */
    val isVerified: Boolean = false,

    /** Optional student name or roll number */
    val studentIdentifier: String? = null,

    val studentName: String? = null,
    val rollNumber: String? = null,
    val registrationNumber: String? = null,
    val className: String? = null,
    val section: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
