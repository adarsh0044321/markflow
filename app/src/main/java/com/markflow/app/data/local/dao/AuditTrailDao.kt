package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.AuditTrailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditTrailDao {

    @Query("SELECT * FROM audit_trail WHERE copyId = :copyId ORDER BY timestamp DESC")
    fun getAuditTrailForCopy(copyId: Long): Flow<List<AuditTrailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditTrail(audit: AuditTrailEntity): Long

    @Query("DELETE FROM audit_trail WHERE copyId = :copyId")
    suspend fun deleteAuditTrailForCopy(copyId: Long)
}
