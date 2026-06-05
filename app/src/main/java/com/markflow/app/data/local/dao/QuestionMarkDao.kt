package com.markflow.app.data.local.dao

import androidx.room.*
import com.markflow.app.data.local.entity.QuestionMarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionMarkDao {

    @Query("SELECT * FROM question_marks WHERE copyId = :copyId ORDER BY questionNumber ASC")
    fun getQuestionMarksForCopy(copyId: Long): Flow<List<QuestionMarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionMark(questionMark: QuestionMarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionMarks(questionMarks: List<QuestionMarkEntity>)

    @Query("DELETE FROM question_marks WHERE copyId = :copyId")
    suspend fun deleteQuestionMarksForCopy(copyId: Long)

    @Query("SELECT COALESCE(MAX(questionNumber), 0) FROM question_marks WHERE copyId = :copyId")
    suspend fun getMaxQuestionNumber(copyId: Long): Int

    @Query("UPDATE question_marks SET pageNumber = :pageNumber WHERE pageId = :pageId")
    suspend fun updatePageNumberForPage(pageId: Long, pageNumber: Int)
}
