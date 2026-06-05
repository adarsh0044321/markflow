package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.MarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mark: MarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(marks: List<MarkEntity>): List<Long>

    @Update
    suspend fun update(mark: MarkEntity)

    @Delete
    suspend fun delete(mark: MarkEntity)

    @Query("SELECT * FROM marks WHERE id = :markId")
    suspend fun getMarkById(markId: Long): MarkEntity?

    @Query("SELECT * FROM marks WHERE id = :markId")
    fun observeMark(markId: Long): Flow<MarkEntity?>

    @Query("SELECT * FROM marks WHERE copyId = :copyId ORDER BY createdAt ASC")
    fun getMarksByCopy(copyId: Long): Flow<List<MarkEntity>>

    @Query("SELECT * FROM marks WHERE copyId = :copyId ORDER BY createdAt ASC")
    suspend fun getMarksByCopySync(copyId: Long): List<MarkEntity>

    @Query("SELECT * FROM marks WHERE pageId = :pageId ORDER BY boundingBoxY ASC, boundingBoxX ASC")
    fun getMarksByPage(pageId: Long): Flow<List<MarkEntity>>

    @Query("SELECT * FROM marks WHERE pageId = :pageId ORDER BY boundingBoxY ASC, boundingBoxX ASC")
    suspend fun getMarksByPageSync(pageId: Long): List<MarkEntity>

    /** Get all marks needing teacher review */
    @Query("SELECT * FROM marks WHERE copyId = :copyId AND status = 'needs_review' ORDER BY createdAt ASC")
    fun getMarksNeedingReview(copyId: Long): Flow<List<MarkEntity>>

    @Query("SELECT COUNT(*) FROM marks WHERE copyId = :copyId AND status = 'needs_review'")
    fun getReviewCount(copyId: Long): Flow<Int>

    /** Get confirmed + edited marks for total calculation */
    @Query("SELECT * FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited') AND regionType = 'awarded_mark' ORDER BY createdAt ASC")
    fun getConfirmedMarks(copyId: Long): Flow<List<MarkEntity>>

    /** Calculate running total for a copy */
    @Query("SELECT COALESCE(SUM(value), 0) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited') AND regionType = 'awarded_mark'")
    fun getRunningTotal(copyId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(value), 0) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited') AND regionType = 'awarded_mark'")
    suspend fun getRunningTotalSync(copyId: Long): Double

    /** Get average confidence for a copy */
    @Query("SELECT COALESCE(AVG(confidence), 0) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited') AND regionType = 'awarded_mark'")
    suspend fun getAverageConfidence(copyId: Long): Double

    /** Update mark coordinates (for moving a mark) */
    @Query("UPDATE marks SET boundingBoxX = :x, boundingBoxY = :y, updatedAt = :timestamp WHERE id = :markId")
    suspend fun updateMarkCoordinates(markId: Long, x: Int, y: Int, timestamp: Long = System.currentTimeMillis())

    /** Count marks by status */
    @Query("SELECT COUNT(*) FROM marks WHERE copyId = :copyId AND status = :status")
    suspend fun getMarkCountByStatus(copyId: Long, status: String): Int

    /** Get marks with low confidence */
    @Query("SELECT * FROM marks WHERE copyId = :copyId AND confidence < :threshold ORDER BY confidence ASC")
    fun getLowConfidenceMarks(copyId: Long, threshold: Double = 0.7): Flow<List<MarkEntity>>

    /** Get overwritten marks */
    @Query("SELECT * FROM marks WHERE copyId = :copyId AND isOverwritten = 1")
    fun getOverwrittenMarks(copyId: Long): Flow<List<MarkEntity>>

    /** Update mark status (for teacher review actions) */
    @Query("UPDATE marks SET status = :status, updatedAt = :timestamp WHERE id = :markId")
    suspend fun updateMarkStatus(markId: Long, status: String, timestamp: Long = System.currentTimeMillis())

    /** Update mark value (for teacher edits) */
    @Query("UPDATE marks SET value = :value, displayValue = :displayValue, status = 'edited', updatedAt = :timestamp WHERE id = :markId")
    suspend fun updateMarkValue(markId: Long, value: Double, displayValue: String, timestamp: Long = System.currentTimeMillis())

    /** Get all marks (global) for statistics */
    @Query("SELECT COUNT(*) FROM marks WHERE status IN ('confirmed', 'edited')")
    fun getTotalConfirmedMarkCount(): Flow<Int>
}
