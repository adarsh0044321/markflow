package com.markflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents marks associated with individual questions on a page.
 */
@Entity(
    tableName = "question_marks",
    foreignKeys = [
        ForeignKey(
            entity = CopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["copyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("copyId"), Index("pageId")]
)
data class QuestionMarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val copyId: Long,
    val pageId: Long,
    val questionNumber: Int,
    val marksAwarded: Double,
    val pageNumber: Int,
    val confidence: Double,
    val createdAt: Long = System.currentTimeMillis()
)
