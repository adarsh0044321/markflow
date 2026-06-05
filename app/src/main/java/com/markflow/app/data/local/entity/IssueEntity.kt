package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores issue flags detected during scanning — totaling errors,
 * unchecked answers, overwritten marks, missing scores, etc.
 */
@Entity(
    tableName = "issues",
    foreignKeys = [
        ForeignKey(
            entity = CopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["copyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("copyId")]
)
data class IssueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val copyId: Long,

    /** Optional page reference */
    val pageId: Long? = null,

    /** Optional mark reference */
    val markId: Long? = null,

    /**
     * Issue type:
     * - totaling_error: mismatch between calculated and written total
     * - unchecked_answer: handwritten content with no mark nearby
     * - overwritten_mark: mark appears to have been corrected
     * - missing_score: attempted question with no score
     * - duplicate_page: page appears to be a duplicate
     * - low_confidence: mark detection confidence below threshold
     */
    val type: String,

    /** Human-readable description */
    val description: String,

    /** Severity: info, warning, error */
    val severity: String = "warning",

    /** Whether the teacher has resolved this issue */
    val isResolved: Boolean = false,

    /** Teacher's resolution note */
    val resolutionNote: String? = null,

    /** Additional data (JSON string with issue-specific details) */
    val metadata: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
