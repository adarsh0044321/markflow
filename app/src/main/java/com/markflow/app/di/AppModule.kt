package com.markflow.app.di

import android.content.Context
import androidx.room.Room
import com.markflow.app.data.local.MarkFlowDatabase
import com.markflow.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module providing database, DAOs, and core services.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MarkFlowDatabase {
        return Room.databaseBuilder(
            context,
            MarkFlowDatabase::class.java,
            MarkFlowDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(database: MarkFlowDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideCopyDao(database: MarkFlowDatabase): CopyDao = database.copyDao()

    @Provides
    fun providePageDao(database: MarkFlowDatabase): PageDao = database.pageDao()

    @Provides
    fun provideMarkDao(database: MarkFlowDatabase): MarkDao = database.markDao()

    @Provides
    fun provideIssueDao(database: MarkFlowDatabase): IssueDao = database.issueDao()

    @Provides
    fun provideQuestionMarkDao(database: MarkFlowDatabase): QuestionMarkDao = database.questionMarkDao()

    @Provides
    fun provideAuditTrailDao(database: MarkFlowDatabase): AuditTrailDao = database.auditTrailDao()
}
