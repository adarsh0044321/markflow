package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a batch scanning session. A session can contain multiple copies,
 * enabling teachers to scan an entire class of answer sheets in one sitting.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Human-readable session name, e.g. "Math Unit Test - Class 10A" */
    val name: String,

    /** Number of copies scanned in this session */
    val copyCount: Int = 0,

    /** Aggregate total marks across all copies */
    val totalMarksSum: Double = 0.0,

    /** Average marks per copy */
    val averageMarks: Double = 0.0,

    /** Highest score in the session */
    val highestMarks: Double = 0.0,

    /** Lowest score in the session */
    val lowestMarks: Double = 0.0,

    /** Pass percentage (configurable threshold) */
    val passPercentage: Double = 0.0,

    /** Maximum possible marks for this exam (configurable) */
    val maxMarks: Double = 100.0,

    /** Pass threshold percentage */
    val passThreshold: Double = 33.0,

    /** Session status: active, completed, archived */
    val status: String = "active",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
