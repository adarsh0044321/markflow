package com.markflow.app.data.repository

import com.markflow.app.data.local.dao.*
import com.markflow.app.domain.model.*
import com.markflow.app.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.sqrt
import com.markflow.app.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing copy, page, and session data.
 * Provides reactive queries for UI consumption.
 */
@Singleton
class CopyRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val copyDao: CopyDao,
    private val pageDao: PageDao,
    private val markDao: MarkDao,
    private val issueDao: IssueDao,
    private val questionMarkDao: QuestionMarkDao,
    private val auditTrailDao: AuditTrailDao,
    private val settingsRepository: SettingsRepository
) {

    // ── Sessions ──

    fun getAllSessions(): Flow<List<ScanSession>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    suspend fun getSession(sessionId: Long): ScanSession? =
        sessionDao.getSessionById(sessionId)?.toDomain()

    fun observeSession(sessionId: Long): Flow<ScanSession?> =
        sessionDao.observeSession(sessionId).map { it?.toDomain() }

    suspend fun updateSession(session: ScanSession) =
        sessionDao.update(session.toEntity())

    suspend fun deleteSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.delete(session)
    }

    // ── Copies ──

    fun getAllCopies(): Flow<List<Copy>> =
        copyDao.getAllCopies().map { list -> list.map { it.toDomain() } }

    fun getRecentCopies(limit: Int = 20): Flow<List<Copy>> =
        copyDao.getRecentCopies(limit).map { list -> list.map { it.toDomain() } }

    fun getCopiesBySession(sessionId: Long): Flow<List<Copy>> =
        copyDao.getCopiesBySession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getCopy(copyId: Long): Copy? =
        copyDao.getCopyById(copyId)?.toDomain()

    fun observeCopy(copyId: Long): Flow<Copy?> =
        copyDao.observeCopy(copyId).map { it?.toDomain() }

    suspend fun deleteCopy(copyId: Long) {
        val copy = copyDao.getCopyById(copyId) ?: return
        copyDao.delete(copy)
    }

    // ── Pages ──

    fun getPagesByCopy(copyId: Long): Flow<List<Page>> =
        pageDao.getPagesByCopy(copyId).map { list -> list.map { it.toDomain() } }

    suspend fun getPage(pageId: Long): Page? =
        pageDao.getPageById(pageId)?.toDomain()

    fun observePage(pageId: Long): Flow<Page?> =
        pageDao.observePage(pageId).map { it?.toDomain() }

    // ── Marks ──

    fun getMarksByCopy(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getMarksByCopy(copyId).map { list -> list.map { it.toDomain() } }

    fun getMarksByPage(pageId: Long): Flow<List<DetectedMark>> =
        markDao.getMarksByPage(pageId).map { list -> list.map { it.toDomain() } }

    fun getConfirmedMarks(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getConfirmedMarks(copyId).map { list -> list.map { it.toDomain() } }

    fun getMarksNeedingReview(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getMarksNeedingReview(copyId).map { list -> list.map { it.toDomain() } }

    fun getLowConfidenceMarks(copyId: Long): Flow<List<DetectedMark>> =
        markDao.getLowConfidenceMarks(copyId).map { list -> list.map { it.toDomain() } }

    suspend fun getMark(markId: Long): DetectedMark? =
        markDao.getMarkById(markId)?.toDomain()

    // ── Issues ──

    fun getIssuesByCopy(copyId: Long): Flow<List<Issue>> =
        issueDao.getIssuesByCopy(copyId).map { list -> list.map { it.toDomain() } }

    fun getUnresolvedIssues(copyId: Long): Flow<List<Issue>> =
        issueDao.getUnresolvedIssues(copyId).map { list -> list.map { it.toDomain() } }

    fun getUnresolvedIssueCount(copyId: Long): Flow<Int> =
        issueDao.getUnresolvedIssueCount(copyId)

    suspend fun resolveIssue(issueId: Long, note: String? = null) =
        issueDao.resolveIssue(issueId, note)

    // ── Dashboard Statistics ──

    fun getDashboardStats(): Flow<DashboardStats> {
        val totalPages = copyDao.getTotalPageCount()
        val totalMarks = copyDao.getTotalMarkCount()
        val allCopies = copyDao.getAllCopies()
        val maxMarksFlow = settingsRepository.maxMarksFlow
        val passThresholdFlow = settingsRepository.passThresholdFlow

        return combine(allCopies, totalPages, totalMarks, maxMarksFlow, passThresholdFlow) { copies, pages, marks, maxMarksStr, passThresholdStr ->
            if (copies.isEmpty()) {
                return@combine DashboardStats(
                    totalCopiesScanned = 0,
                    totalPagesScanned = pages,
                    totalMarksDetected = marks
                )
            }

            val totalCopiesCount = copies.size
            val marksList = copies.map { it.calculatedTotal }.sorted()
            val average = marksList.average()
            val highest = marksList.lastOrNull() ?: 0.0
            val lowest = marksList.firstOrNull() ?: 0.0
            
            // Parse max marks and pass threshold dynamically
            val maxMarks = maxMarksStr.toDoubleOrNull() ?: 100.0
            val passPercent = (passThresholdStr.toDoubleOrNull() ?: 33.0) / 100.0
            val passScore = maxMarks * passPercent
            
            // Pass rate
            val passCount = copies.count { it.calculatedTotal >= passScore }
            val passRate = (passCount.toDouble() / totalCopiesCount) * 100.0

            // Median calculation
            val median = if (totalCopiesCount % 2 == 0) {
                (marksList[totalCopiesCount / 2 - 1] + marksList[totalCopiesCount / 2]) / 2.0
            } else {
                marksList[totalCopiesCount / 2]
            }

            // Standard Deviation calculation
            val variance = marksList.map { (it - average) * (it - average) }.sum() / totalCopiesCount
            val stdDev = sqrt(variance)

            DashboardStats(
                totalCopiesScanned = totalCopiesCount,
                totalPagesScanned = pages,
                totalMarksDetected = marks,
                averageMarks = average,
                highestMarks = highest,
                lowestMarks = lowest,
                passPercentage = passRate,
                medianMarks = median,
                standardDeviation = stdDev
            )
        }
    }

    fun getCopiesInDateRange(startTime: Long, endTime: Long): Flow<List<Copy>> =
        copyDao.getCopiesInDateRange(startTime, endTime).map { list -> list.map { it.toDomain() } }

    fun getCopyCountSince(startTime: Long): Flow<Int> =
        copyDao.getCopyCountSince(startTime)

    fun getPageCountSince(startTime: Long): Flow<Int> =
        copyDao.getPageCountSince(startTime)

    // ── Question Marks ──

    fun getQuestionMarksForCopy(copyId: Long): Flow<List<QuestionMark>> =
        questionMarkDao.getQuestionMarksForCopy(copyId).map { list -> list.map { it.toDomain() } }

    suspend fun saveQuestionMark(questionMark: QuestionMark) =
        questionMarkDao.insertQuestionMark(questionMark.toEntity())

    suspend fun saveQuestionMarks(questionMarks: List<QuestionMark>) =
        questionMarkDao.insertQuestionMarks(questionMarks.map { it.toEntity() })

    // ── Audit Logs ──

    fun getAuditTrailForCopy(copyId: Long): Flow<List<AuditLog>> =
        auditTrailDao.getAuditTrailForCopy(copyId).map { list -> list.map { it.toDomain() } }

    suspend fun saveAuditLog(audit: AuditLog) =
        auditTrailDao.insertAuditTrail(audit.toEntity())

    suspend fun updateCopyStudentDetails(
        copyId: Long,
        name: String?,
        roll: String?,
        reg: String?,
        className: String?,
        sec: String?
    ) {
        val copy = copyDao.getCopyById(copyId) ?: return
        val updated = copy.copy(
            studentName = name,
            rollNumber = roll,
            registrationNumber = reg,
            className = className,
            section = sec,
            updatedAt = System.currentTimeMillis()
        )
        copyDao.update(updated)
    }

    suspend fun recalculateSessionStats(sessionId: Long) {
        sessionDao.recalculateSessionStats(sessionId)
    }
}
