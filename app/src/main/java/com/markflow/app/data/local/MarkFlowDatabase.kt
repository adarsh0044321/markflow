package com.markflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.markflow.app.data.local.dao.*
import com.markflow.app.data.local.entity.*

/**
 * Main Room database for MarkFlow.
 * Stores all scanning sessions, copies, pages, marks, and issues locally.
 */
@Database(
    entities = [
        SessionEntity::class,
        CopyEntity::class,
        PageEntity::class,
        MarkEntity::class,
        IssueEntity::class,
        QuestionMarkEntity::class,
        AuditTrailEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MarkFlowDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun copyDao(): CopyDao
    abstract fun pageDao(): PageDao
    abstract fun markDao(): MarkDao
    abstract fun issueDao(): IssueDao
    abstract fun questionMarkDao(): QuestionMarkDao
    abstract fun auditTrailDao(): AuditTrailDao

    companion object {
        const val DATABASE_NAME = "markflow_database"
    }
}
