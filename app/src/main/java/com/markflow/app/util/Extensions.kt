package com.markflow.app.util

import com.markflow.app.data.local.entity.*
import com.markflow.app.domain.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Extension functions for entity-to-domain mapping, formatting, and convenience.
 */

// ── Entity → Domain Mappers ──

fun SessionEntity.toDomain() = ScanSession(
    id = id,
    name = name,
    copyCount = copyCount,
    totalMarksSum = totalMarksSum,
    averageMarks = averageMarks,
    highestMarks = highestMarks,
    lowestMarks = lowestMarks,
    passPercentage = passPercentage,
    maxMarks = maxMarks,
    passThreshold = passThreshold,
    status = SessionStatus.valueOf(status.uppercase()),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ScanSession.toEntity() = SessionEntity(
    id = id,
    name = name,
    copyCount = copyCount,
    totalMarksSum = totalMarksSum,
    averageMarks = averageMarks,
    highestMarks = highestMarks,
    lowestMarks = lowestMarks,
    passPercentage = passPercentage,
    maxMarks = maxMarks,
    passThreshold = passThreshold,
    status = status.name.lowercase(),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CopyEntity.toDomain() = Copy(
    id = id,
    sessionId = sessionId,
    copyNumber = copyNumber,
    calculatedTotal = calculatedTotal,
    writtenTotal = writtenTotal,
    pageCount = pageCount,
    markCount = markCount,
    overallConfidence = overallConfidence,
    status = CopyStatus.valueOf(status.uppercase()),
    hasIssues = hasIssues,
    issueCount = issueCount,
    reviewCount = reviewCount,
    isVerified = isVerified,
    studentIdentifier = studentIdentifier,
    studentName = studentName,
    rollNumber = rollNumber,
    registrationNumber = registrationNumber,
    className = className,
    section = section,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Copy.toEntity() = CopyEntity(
    id = id,
    sessionId = sessionId,
    copyNumber = copyNumber,
    calculatedTotal = calculatedTotal,
    writtenTotal = writtenTotal,
    pageCount = pageCount,
    markCount = markCount,
    overallConfidence = overallConfidence,
    status = status.name.lowercase(),
    hasIssues = hasIssues,
    issueCount = issueCount,
    reviewCount = reviewCount,
    isVerified = isVerified,
    studentIdentifier = studentIdentifier,
    studentName = studentName,
    rollNumber = rollNumber,
    registrationNumber = registrationNumber,
    className = className,
    section = section,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PageEntity.toDomain() = Page(
    id = id,
    copyId = copyId,
    pageNumber = pageNumber,
    imagePath = imagePath,
    thumbnailPath = thumbnailPath,
    pageHash = pageHash,
    pageTotal = pageTotal,
    markCount = markCount,
    isDuplicate = isDuplicate,
    hasUncheckedAnswers = hasUncheckedAnswers,
    uncheckedAnswerCount = uncheckedAnswerCount,
    status = PageStatus.valueOf(status.uppercase()),
    scanQualityScore = scanQualityScore,
    scanQualityRating = scanQualityRating,
    ocrText = ocrText,
    capturedAt = capturedAt,
    processedAt = processedAt
)

fun Page.toEntity() = PageEntity(
    id = id,
    copyId = copyId,
    pageNumber = pageNumber,
    imagePath = imagePath,
    thumbnailPath = thumbnailPath,
    pageHash = pageHash,
    pageTotal = pageTotal,
    markCount = markCount,
    isDuplicate = isDuplicate,
    hasUncheckedAnswers = hasUncheckedAnswers,
    uncheckedAnswerCount = uncheckedAnswerCount,
    status = status.name.lowercase(),
    scanQualityScore = scanQualityScore,
    scanQualityRating = scanQualityRating,
    ocrText = ocrText,
    capturedAt = capturedAt,
    processedAt = processedAt
)

fun MarkEntity.toDomain() = DetectedMark(
    id = id,
    pageId = pageId,
    copyId = copyId,
    value = value,
    displayValue = displayValue,
    confidence = confidence,
    status = MarkStatus.valueOf(status.uppercase()),
    boundingBox = BoundingBox(boundingBoxX, boundingBoxY, boundingBoxWidth, boundingBoxHeight),
    evidenceImagePath = evidenceImagePath,
    cvResult = if (cvDetectedValue != null) VerificationResult(cvDetectedValue, cvConfidence) else null,
    ocrResult = if (ocrDetectedValue != null) VerificationResult(ocrDetectedValue, ocrConfidence) else null,
    aiResult = if (aiDetectedValue != null) VerificationResult(aiDetectedValue, aiConfidence) else null,
    isOverwritten = isOverwritten,
    isFraction = isFraction,
    fractionDenominator = fractionDenominator,
    isAutoConfirmed = isAutoConfirmed,
    regionType = regionType,
    isManual = isManual,
    detectionReason = detectionReason,
    candidates = if (candidates.isEmpty()) emptyList() else candidates.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    createdAt = createdAt
)

fun DetectedMark.toEntity() = MarkEntity(
    id = id,
    pageId = pageId,
    copyId = copyId,
    value = value,
    displayValue = displayValue,
    confidence = confidence,
    status = status.name.lowercase(),
    boundingBoxX = boundingBox.x,
    boundingBoxY = boundingBox.y,
    boundingBoxWidth = boundingBox.width,
    boundingBoxHeight = boundingBox.height,
    evidenceImagePath = evidenceImagePath,
    cvDetectedValue = cvResult?.detectedValue,
    cvConfidence = cvResult?.confidence,
    ocrDetectedValue = ocrResult?.detectedValue,
    ocrConfidence = ocrResult?.confidence,
    aiDetectedValue = aiResult?.detectedValue,
    aiConfidence = aiResult?.confidence,
    isOverwritten = isOverwritten,
    isFraction = isFraction,
    fractionDenominator = fractionDenominator,
    isAutoConfirmed = isAutoConfirmed,
    regionType = regionType,
    isManual = isManual,
    detectionReason = detectionReason,
    candidates = candidates.joinToString(","),
    createdAt = createdAt
)

fun IssueEntity.toDomain() = Issue(
    id = id,
    copyId = copyId,
    pageId = pageId,
    markId = markId,
    type = IssueType.valueOf(type.uppercase()),
    description = description,
    severity = IssueSeverity.valueOf(severity.uppercase()),
    isResolved = isResolved,
    resolutionNote = resolutionNote,
    metadata = metadata,
    createdAt = createdAt
)

fun Issue.toEntity() = IssueEntity(
    id = id,
    copyId = copyId,
    pageId = pageId,
    markId = markId,
    type = type.name.lowercase(),
    description = description,
    severity = severity.name.lowercase(),
    isResolved = isResolved,
    resolutionNote = resolutionNote,
    metadata = metadata,
    createdAt = createdAt
)

// ── Formatting Extensions ──

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> toFormattedDate()
    }
}

fun Double.toPercentageString(): String = "${(this * 100).toInt()}%"

fun Double.toMarksString(): String {
    return if (this == this.toLong().toDouble()) {
        this.toLong().toString()
    } else {
        String.format("%.1f", this)
    }
}

fun Int.toFormattedCount(): String {
    return when {
        this < 1000 -> this.toString()
        this < 1_000_000 -> String.format("%.1fK", this / 1000.0)
        else -> String.format("%.1fM", this / 1_000_000.0)
    }
}

fun QuestionMarkEntity.toDomain() = QuestionMark(
    id = id,
    copyId = copyId,
    pageId = pageId,
    questionNumber = questionNumber,
    marksAwarded = marksAwarded,
    pageNumber = pageNumber,
    confidence = confidence,
    createdAt = createdAt
)

fun QuestionMark.toEntity() = QuestionMarkEntity(
    id = id,
    copyId = copyId,
    pageId = pageId,
    questionNumber = questionNumber,
    marksAwarded = marksAwarded,
    pageNumber = pageNumber,
    confidence = confidence,
    createdAt = createdAt
)

fun AuditTrailEntity.toDomain() = AuditLog(
    id = id,
    copyId = copyId,
    markId = markId,
    action = action,
    timestamp = timestamp,
    confidence = confidence,
    userAction = userAction
)

fun AuditLog.toEntity() = AuditTrailEntity(
    id = id,
    copyId = copyId,
    markId = markId,
    action = action,
    timestamp = timestamp,
    confidence = confidence,
    userAction = userAction
)
