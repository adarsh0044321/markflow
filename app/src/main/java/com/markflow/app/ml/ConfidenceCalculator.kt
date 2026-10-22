package com.markflow.app.ml

import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates combined confidence scores from multiple verification stages.
 * Uses weighted averaging with agreement bonuses.
 */
@Singleton
class ConfidenceCalculator @Inject constructor() {

    data class ConfidenceResult(
        val finalConfidence: Double,
        val finalValue: Double,
        val finalDisplayValue: String,
        val isAutoConfirmed: Boolean,
        val needsReview: Boolean,
        val agreementLevel: AgreementLevel
    )

    enum class AgreementLevel {
        /** All stages agree on the value */
        FULL_AGREEMENT,
        /** At least 2 of 3 stages agree */
        MAJORITY_AGREEMENT,
        /** Stages disagree on the value */
        DISAGREEMENT,
        /** Only one stage produced a result */
        SINGLE_SOURCE
    }

    /**
     * Calculate combined confidence from all verification stages.
     *
     * @param cvValue Value from Stage 1 (contour analysis)
     * @param cvConfidence Confidence from Stage 1
     * @param ocrValue Value from Stage 2 (ML Kit OCR)
     * @param ocrConfidence Confidence from Stage 2
     * @param aiValue Value from Stage 3 (TF Lite)
     * @param aiConfidence Confidence from Stage 3
     * @param displayValue The display format string
     */
    fun calculateCombinedConfidence(
        cvValue: Double?,
        cvConfidence: Double?,
        ocrValue: Double?,
        ocrConfidence: Double?,
        aiValue: Double?,
        aiConfidence: Double?,
        displayValue: String,
        isFraction: Boolean = false
    ): ConfidenceResult {
        // If it's a fraction and OCR successfully parsed a value, it is highly reliable.
        if (isFraction && ocrValue != null && ocrConfidence != null) {
            // Check if TFLite (AI) agrees with the numerator of the fraction.
            // (e.g. OCR value is 7.0 for "7/10", and AI value is also 7.0)
            val aiMatchesNumerator = aiValue != null && Math.abs(aiValue - ocrValue) < 1e-9
            
            if (aiMatchesNumerator) {
                // Strong consensus: AI verified the numerator, OCR verified the format
                val finalConf = (ocrConfidence + 0.15).coerceIn(0.0, 1.0)
                return ConfidenceResult(
                    finalConfidence = finalConf,
                    finalValue = ocrValue,
                    finalDisplayValue = displayValue,
                    isAutoConfirmed = finalConf >= Constants.CONFIDENCE_AUTO_CONFIRM,
                    needsReview = finalConf < Constants.CONFIDENCE_REVIEW_THRESHOLD,
                    agreementLevel = AgreementLevel.FULL_AGREEMENT
                )
            } else {
                // If they disagree but OCR has high confidence in the fraction, trust OCR without penalty
                return ConfidenceResult(
                    finalConfidence = ocrConfidence,
                    finalValue = ocrValue,
                    finalDisplayValue = displayValue,
                    isAutoConfirmed = ocrConfidence >= Constants.CONFIDENCE_AUTO_CONFIRM,
                    needsReview = ocrConfidence < Constants.CONFIDENCE_REVIEW_THRESHOLD,
                    agreementLevel = AgreementLevel.SINGLE_SOURCE
                )
            }
        }

        val sources = mutableListOf<Pair<Double, Double>>() // value, confidence pairs

        if (cvValue != null && cvConfidence != null) sources.add(cvValue to cvConfidence)
        if (ocrValue != null && ocrConfidence != null) sources.add(ocrValue to ocrConfidence)
        if (aiValue != null && aiConfidence != null) sources.add(aiValue to aiConfidence)

        if (sources.isEmpty()) {
            return ConfidenceResult(
                finalConfidence = 0.0,
                finalValue = 0.0,
                finalDisplayValue = displayValue,
                isAutoConfirmed = false,
                needsReview = true,
                agreementLevel = AgreementLevel.SINGLE_SOURCE
            )
        }

        if (sources.size == 1) {
            val (value, confidence) = sources[0]
            return ConfidenceResult(
                finalConfidence = confidence * 0.85, // Slight penalty for single source
                finalValue = value,
                finalDisplayValue = displayValue,
                isAutoConfirmed = confidence * 0.85 >= Constants.CONFIDENCE_AUTO_CONFIRM,
                needsReview = confidence * 0.85 < Constants.CONFIDENCE_REVIEW_THRESHOLD,
                agreementLevel = AgreementLevel.SINGLE_SOURCE
            )
        }

        // Check agreement between sources
        val values = sources.map { it.first }
        val allAgree = values.all { it == values[0] }
        val majorityValue = findMajorityValue(values)

        val agreementLevel: AgreementLevel
        val finalValue: Double
        var confidenceBonus = 0.0

        if (allAgree) {
            agreementLevel = AgreementLevel.FULL_AGREEMENT
            finalValue = values[0]
            confidenceBonus = 0.15  // Strong bonus for full agreement
        } else if (majorityValue != null) {
            agreementLevel = AgreementLevel.MAJORITY_AGREEMENT
            finalValue = majorityValue
            confidenceBonus = 0.05  // Small bonus for majority
        } else {
            agreementLevel = AgreementLevel.DISAGREEMENT
            // Use the value from the highest-confidence source
            finalValue = sources.maxByOrNull { it.second }?.first ?: 0.0
            confidenceBonus = -0.15  // Penalty for disagreement
        }

        // Weighted average confidence
        val totalWeight = sources.sumOf { it.second }
        val weightedConfidence = if (totalWeight > 0) {
            sources.sumOf { (_, conf) -> conf * conf } / totalWeight // Weighted by confidence
        } else 0.0

        val finalConfidence = (weightedConfidence + confidenceBonus).coerceIn(0.0, 1.0)

        return ConfidenceResult(
            finalConfidence = finalConfidence,
            finalValue = finalValue,
            finalDisplayValue = displayValue,
            isAutoConfirmed = finalConfidence >= Constants.CONFIDENCE_AUTO_CONFIRM,
            needsReview = finalConfidence < Constants.CONFIDENCE_REVIEW_THRESHOLD,
            agreementLevel = agreementLevel
        )
    }

    /**
     * Find the value that appears most frequently.
     * Returns null if no value has a majority.
     */
    private fun findMajorityValue(values: List<Double>): Double? {
        val counts = values.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: return null
        return if (maxCount > values.size / 2.0) {
            counts.entries.first { it.value == maxCount }.key
        } else null
    }
}
