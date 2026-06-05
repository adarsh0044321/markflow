package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks teacher review actions and AI detections for evaluation integrity.
 */
@Entity(
    tableName = "audit_trail",
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
data class AuditTrailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val copyId: Long,
    val markId: Long? = null,
    val action: String, // Detected, Corrected, Approved, Ignored, Deleted
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double,
    val userAction: String? = null
)
