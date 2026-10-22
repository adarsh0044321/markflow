package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity): Long

    @Update
    suspend fun update(page: PageEntity)

    @Delete
    suspend fun delete(page: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun observePage(pageId: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE copyId = :copyId ORDER BY pageNumber ASC")
    fun getPagesByCopy(copyId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE copyId = :copyId ORDER BY pageNumber ASC")
    suspend fun getPagesByCopySync(copyId: Long): List<PageEntity>

    @Query("SELECT COUNT(*) FROM pages WHERE copyId = :copyId")
    suspend fun getPageCount(copyId: Long): Int

    @Query("SELECT COALESCE(MAX(pageNumber), 0) FROM pages WHERE copyId = :copyId")
    suspend fun getMaxPageNumber(copyId: Long): Int

    /** Check for duplicate pages using perceptual hash. Returns pages with similar hashes. */
    @Query("SELECT * FROM pages WHERE copyId = :copyId AND pageHash = :hash AND id != :excludePageId")
    suspend fun findDuplicatePages(copyId: Long, hash: String, excludePageId: Long = 0): List<PageEntity>

    @Query("SELECT * FROM pages WHERE copyId = :copyId AND isDuplicate = 1")
    fun getDuplicatePages(copyId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE copyId = :copyId AND hasUncheckedAnswers = 1")
    fun getPagesWithUncheckedAnswers(copyId: Long): Flow<List<PageEntity>>

    @Query("UPDATE pages SET status = :status, processedAt = :processedAt WHERE id = :pageId")
    suspend fun updatePageStatus(pageId: Long, status: String, processedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE pages SET 
            pageTotal = COALESCE((SELECT SUM(value) FROM marks WHERE pageId = :pageId AND status IN ('confirmed', 'edited') AND regionType = 'awarded_mark'), 0),
            markCount = (SELECT COUNT(*) FROM marks WHERE pageId = :pageId AND status IN ('confirmed', 'edited', 'needs_review') AND regionType = 'awarded_mark')
        WHERE id = :pageId
    """)
    suspend fun recalculatePageStats(pageId: Long)
}
