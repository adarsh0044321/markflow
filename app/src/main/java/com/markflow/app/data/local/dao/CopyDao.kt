package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.CopyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CopyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(copy: CopyEntity): Long

    @Update
    suspend fun update(copy: CopyEntity)

    @Delete
    suspend fun delete(copy: CopyEntity)

    @Query("SELECT * FROM copies WHERE id = :copyId")
    suspend fun getCopyById(copyId: Long): CopyEntity?

    @Query("SELECT * FROM copies WHERE id = :copyId")
    fun observeCopy(copyId: Long): Flow<CopyEntity?>

    @Query("SELECT * FROM copies WHERE sessionId = :sessionId AND pageCount > 0 ORDER BY copyNumber ASC")
    fun getCopiesBySession(sessionId: Long): Flow<List<CopyEntity>>

    @Query("SELECT * FROM copies WHERE pageCount > 0 ORDER BY createdAt DESC")
    fun getAllCopies(): Flow<List<CopyEntity>>

    @Query("SELECT * FROM copies WHERE pageCount > 0 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentCopies(limit: Int): Flow<List<CopyEntity>>

    @Query("SELECT * FROM copies WHERE status = :status AND pageCount > 0 ORDER BY createdAt DESC")
    fun getCopiesByStatus(status: String): Flow<List<CopyEntity>>

    @Query("SELECT COUNT(*) FROM copies WHERE pageCount > 0")
    fun getTotalCopyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM copies WHERE sessionId = :sessionId AND pageCount > 0")
    suspend fun getCopyCountForSession(sessionId: Long): Int

    @Query("SELECT COALESCE(MAX(copyNumber), 0) FROM copies WHERE sessionId = :sessionId")
    suspend fun getMaxCopyNumber(sessionId: Long): Int

    @Query("SELECT COALESCE(SUM(pageCount), 0) FROM copies")
    fun getTotalPageCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(markCount), 0) FROM copies")
    fun getTotalMarkCount(): Flow<Int>

    // Aggregate statistics for dashboard
    @Query("SELECT COALESCE(AVG(calculatedTotal), 0) FROM copies WHERE sessionId = :sessionId AND pageCount > 0")
    suspend fun getAverageMarks(sessionId: Long): Double

    @Query("SELECT COALESCE(MAX(calculatedTotal), 0) FROM copies WHERE sessionId = :sessionId AND pageCount > 0")
    suspend fun getHighestMarks(sessionId: Long): Double

    @Query("SELECT COALESCE(MIN(calculatedTotal), 0) FROM copies WHERE sessionId = :sessionId AND pageCount > 0")
    suspend fun getLowestMarks(sessionId: Long): Double

    @Query("""
        UPDATE copies SET 
            calculatedTotal = COALESCE((SELECT SUM(value) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited')), 0),
            markCount = (SELECT COUNT(*) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited', 'needs_review')),
            pageCount = (SELECT COUNT(*) FROM pages WHERE copyId = :copyId),
            overallConfidence = COALESCE((SELECT AVG(confidence) FROM marks WHERE copyId = :copyId AND status IN ('confirmed', 'edited')), 0),
            reviewCount = (SELECT COUNT(*) FROM marks WHERE copyId = :copyId AND status = 'needs_review'),
            updatedAt = :timestamp
        WHERE id = :copyId
    """)
    suspend fun recalculateCopyStats(copyId: Long, timestamp: Long = System.currentTimeMillis())

    // Date-range filtered queries for statistics
    @Query("SELECT * FROM copies WHERE createdAt >= :startTime AND createdAt <= :endTime AND pageCount > 0 ORDER BY createdAt DESC")
    fun getCopiesInDateRange(startTime: Long, endTime: Long): Flow<List<CopyEntity>>

    @Query("SELECT COUNT(*) FROM copies WHERE createdAt >= :startTime AND pageCount > 0")
    fun getCopyCountSince(startTime: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(pageCount), 0) FROM copies WHERE createdAt >= :startTime AND pageCount > 0")
    fun getPageCountSince(startTime: Long): Flow<Int>
}
