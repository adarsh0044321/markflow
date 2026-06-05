package com.markflow.app.domain.model

/**
 * Domain model for a scanning session (batch of copies).
 */
data class ScanSession(
    val id: Long = 0,
    val name: String,
    val copyCount: Int = 0,
    val totalMarksSum: Double = 0.0,
    val averageMarks: Double = 0.0,
    val highestMarks: Double = 0.0,
    val lowestMarks: Double = 0.0,
    val passPercentage: Double = 0.0,
    val maxMarks: Double = 100.0,
    val passThreshold: Double = 33.0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SessionStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

/**
 * Domain model for a single answer copy.
 */
data class Copy(
    val id: Long = 0,
    val sessionId: Long,
    val copyNumber: Int,
    val calculatedTotal: Double = 0.0,
    val writtenTotal: Double? = null,
    val pageCount: Int = 0,
    val markCount: Int = 0,
    val overallConfidence: Double = 0.0,
    val status: CopyStatus = CopyStatus.SCANNING,
    val hasIssues: Boolean = false,
    val issueCount: Int = 0,
    val reviewCount: Int = 0,
    val isVerified: Boolean = false,
    val studentIdentifier: String? = null,
    val studentName: String? = null,
    val rollNumber: String? = null,
    val registrationNumber: String? = null,
    val className: String? = null,
    val section: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class CopyStatus {
    SCANNING, PROCESSING, COMPLETED, REVIEWED
}

/**
 * Domain model for a scanned page.
 */
data class Page(
    val id: Long = 0,
    val copyId: Long,
    val pageNumber: Int,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val pageHash: String = "",
    val pageTotal: Double = 0.0,
    val markCount: Int = 0,
    val isDuplicate: Boolean = false,
    val hasUncheckedAnswers: Boolean = false,
    val uncheckedAnswerCount: Int = 0,
    val status: PageStatus = PageStatus.CAPTURED,
    val scanQualityScore: Int = 100,
    val scanQualityRating: String = "Excellent",
    val ocrText: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

enum class PageStatus {
    CAPTURED, PROCESSING, PROCESSED, ERROR
}

/**
 * Domain model for a detected mark with full verification pipeline data.
 */
data class DetectedMark(
    val id: Long = 0,
    val pageId: Long,
    val copyId: Long,
    val value: Double,
    val displayValue: String,
    val confidence: Double,
    val status: MarkStatus = MarkStatus.CONFIRMED,
    val boundingBox: BoundingBox,
    val evidenceImagePath: String? = null,
    val cvResult: VerificationResult? = null,
    val ocrResult: VerificationResult? = null,
    val aiResult: VerificationResult? = null,
    val isOverwritten: Boolean = false,
    val isFraction: Boolean = false,
    val fractionDenominator: Double? = null,
    val isAutoConfirmed: Boolean = true,
    val regionType: String = "awarded_mark",
    val isManual: Boolean = false,
    val detectionReason: String = "",
    val candidates: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class MarkStatus {
    CONFIRMED, NEEDS_REVIEW, EDITED, REJECTED, IGNORED
}

data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class VerificationResult(
    val detectedValue: Double?,
    val confidence: Double?
)

/**
 * Domain model for a detected issue.
 */
data class Issue(
    val id: Long = 0,
    val copyId: Long,
    val pageId: Long? = null,
    val markId: Long? = null,
    val type: IssueType,
    val description: String,
    val severity: IssueSeverity = IssueSeverity.WARNING,
    val isResolved: Boolean = false,
    val resolutionNote: String? = null,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class IssueType {
    TOTALING_ERROR,
    UNCHECKED_ANSWER,
    OVERWRITTEN_MARK,
    MISSING_SCORE,
    DUPLICATE_PAGE,
    LOW_CONFIDENCE
}

enum class IssueSeverity {
    INFO, WARNING, ERROR
}

/**
 * Represents the current state of the scanning process.
 */
data class ScanState(
    val isScanning: Boolean = false,
    val currentCopyId: Long = 0,
    val currentPageNumber: Int = 0,
    val runningTotal: Double = 0.0,
    val marksDetected: Int = 0,
    val pagesScanned: Int = 0,
    val isPageStable: Boolean = false,
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val lastDetectedMarks: List<DetectedMark> = emptyList(),
    val recentMarkFeed: List<MarkFeedItem> = emptyList(),
    val statusMessage: String = ""
)

/**
 * Item in the live mark detection feed shown during scanning.
 */
data class MarkFeedItem(
    val value: Double,
    val displayValue: String,
    val pageNumber: Int,
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Statistics for the dashboard.
 */
data class DashboardStats(
    val totalCopiesScanned: Int = 0,
    val totalPagesScanned: Int = 0,
    val totalMarksDetected: Int = 0,
    val averageMarks: Double = 0.0,
    val highestMarks: Double = 0.0,
    val lowestMarks: Double = 0.0,
    val passPercentage: Double = 0.0,
    val medianMarks: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val copiesThisMonth: Int = 0,
    val pagesThisMonth: Int = 0
)

data class QuestionMark(
    val id: Long = 0,
    val copyId: Long,
    val pageId: Long,
    val questionNumber: Int,
    val marksAwarded: Double,
    val pageNumber: Int,
    val confidence: Double,
    val createdAt: Long = System.currentTimeMillis()
)

data class AuditLog(
    val id: Long = 0,
    val copyId: Long,
    val markId: Long? = null,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double,
    val userAction: String? = null
)

enum class FlashMode {
    OFF, ON, AUTO
}

