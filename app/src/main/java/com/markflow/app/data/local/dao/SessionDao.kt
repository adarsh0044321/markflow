package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY createdAt DESC")
    fun getSessionsByStatus(status: String): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    fun getTotalSessionCount(): Flow<Int>

    @Query("""
        UPDATE sessions SET 
            copyCount = (SELECT COUNT(*) FROM copies WHERE sessionId = :sessionId AND pageCount > 0),
            totalMarksSum = COALESCE((SELECT SUM(calculatedTotal) FROM copies WHERE sessionId = :sessionId AND pageCount > 0), 0),
            averageMarks = COALESCE((SELECT AVG(calculatedTotal) FROM copies WHERE sessionId = :sessionId AND pageCount > 0), 0),
            highestMarks = COALESCE((SELECT MAX(calculatedTotal) FROM copies WHERE sessionId = :sessionId AND pageCount > 0), 0),
            lowestMarks = COALESCE((SELECT MIN(calculatedTotal) FROM copies WHERE sessionId = :sessionId AND pageCount > 0), 0),
            updatedAt = :timestamp
        WHERE id = :sessionId
    """)
    suspend fun recalculateSessionStats(sessionId: Long, timestamp: Long = System.currentTimeMillis())
}
