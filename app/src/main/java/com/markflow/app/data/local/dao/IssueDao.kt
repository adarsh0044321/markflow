package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(issue: IssueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(issues: List<IssueEntity>): List<Long>

    @Update
    suspend fun update(issue: IssueEntity)

    @Delete
    suspend fun delete(issue: IssueEntity)

    @Query("SELECT * FROM issues WHERE copyId = :copyId ORDER BY createdAt DESC")
    fun getIssuesByCopy(copyId: Long): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE copyId = :copyId ORDER BY createdAt DESC")
    suspend fun getIssuesByCopySync(copyId: Long): List<IssueEntity>

    @Query("SELECT * FROM issues WHERE copyId = :copyId AND isResolved = 0 ORDER BY severity DESC, createdAt DESC")
    fun getUnresolvedIssues(copyId: Long): Flow<List<IssueEntity>>

    @Query("SELECT COUNT(*) FROM issues WHERE copyId = :copyId AND isResolved = 0")
    fun getUnresolvedIssueCount(copyId: Long): Flow<Int>

    @Query("SELECT * FROM issues WHERE copyId = :copyId AND type = :type")
    suspend fun getIssuesByType(copyId: Long, type: String): List<IssueEntity>

    @Query("UPDATE issues SET isResolved = 1, resolutionNote = :note WHERE id = :issueId")
    suspend fun resolveIssue(issueId: Long, note: String? = null)

    @Query("DELETE FROM issues WHERE pageId = :pageId")
    suspend fun deleteIssuesByPage(pageId: Long)
}
