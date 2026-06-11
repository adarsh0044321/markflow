package com.markflow.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wrapper around Google ML Kit Text Recognition.
 * Extracts text from cropped mark images and parses numeric values.
 */
@Singleton
class OcrProcessor @Inject constructor() {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class OcrResult(
        val rawText: String,
        val numericValue: Double?,
        val displayValue: String,
        val confidence: Double,
        val isFraction: Boolean = false,
        val numerator: Double? = null,
        val denominator: Double? = null
    )

    /**
     * Perform OCR on a cropped mark image and parse the numeric value.
     *
     * Handles formats:
     * - Simple integers: "5", "10"
     * - Decimals: "4.5", "7.5"
     * - Fractions: "7/10", "3/5"
     * - Marks with ticks: "✓ 5" → 5
     *
     * @param bitmap Cropped image of the mark region
     * @return OcrResult with parsed value and confidence
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val rawText = result.text.trim()
                    val parsed = parseMarkValue(rawText)
                    val confidence = calculateOcrConfidence(result, rawText)

                    continuation.resume(
                        OcrResult(
                            rawText = rawText,
                            numericValue = parsed.first,
                            displayValue = parsed.second,
                            confidence = confidence,
                            isFraction = parsed.third,
                            numerator = parsed.fourth,
                            denominator = parsed.fifth
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resume(
                        OcrResult(
                            rawText = "",
                            numericValue = null,
                            displayValue = "",
                            confidence = 0.0
                        )
                    )
                }
        }
    }

    private fun mapOcrConfusions(text: String): String {
        var mapped = text.trim()
        if (mapped.isEmpty()) return mapped

        // Common substitutions for single characters
        if (mapped.length == 1) {
            when (mapped) {
                "S", "s" -> return "5"
                "O", "o" -> return "0"
                "I", "l", "|", "i" -> return "1"
                "Z", "z" -> return "2"
                "B" -> return "8"
                "g", "q" -> return "9"
                "e", "E" -> return "3"
                "a", "A", "H", "h" -> return "4"
                "F", "f" -> return "7"
            }
        }

        // For multi-character strings, check if all characters are valid digits, slashes, dots,
        // whitespace, or common confusion characters. If so, map all of them.
        val isAllConfusionChars = mapped.all { 
            it.isDigit() || it == '/' || it == '.' || it.isWhitespace() || it in "oOiIl|iszZBgqbTeEaAHhfF" 
        }
        if (isAllConfusionChars) {
            val sb = StringBuilder()
            for (char in mapped) {
                when (char) {
                    'o', 'O' -> sb.append('0')
                    'i', 'I', 'l', '|', 't', 'T' -> sb.append('1')
                    'z', 'Z' -> sb.append('2')
                    'e', 'E' -> sb.append('3')
                    'a', 'A', 'H', 'h' -> sb.append('4')
                    's', 'S' -> sb.append('5')
                    'b' -> sb.append('6')
                    'f', 'F' -> sb.append('7')
                    'B' -> sb.append('8')
                    'g', 'q' -> sb.append('9')
                    else -> sb.append(char) // keeps whitespace, slashes, dots
                }
            }
            mapped = sb.toString()
        }

        return mapped
    }

    /**
     * Parse a raw OCR text string into a numeric mark value.
     * Returns: (value, displayString, isFraction, numerator, denominator)
     */
    private fun parseMarkValue(text: String): ParsedMark {
        var normalized = mapOcrConfusions(text)
        
        // 1. Replace commas/semicolons with dots
        normalized = normalized.replace(',', '.')
        normalized = normalized.replace(';', '.')
        
        // 2. Replace separators between two digits with dots (e.g. "4-5" -> "4.5")
        normalized = normalized.replace(Regex("(\\d)\\s*[-_·•*:;]\\s*(\\d)"), "$1.$2")
        
        // 3. Replace a single space between two digits with a dot (e.g. "4 5" -> "4.5")
        normalized = normalized.replace(Regex("(\\d)\\s+(\\d)"), "$1.$2")
        
        var cleaned = normalized.replace("[^0-9./]".toRegex(), "").trim()

        // Strip leading/trailing slashes if it's not a fraction (e.g. "/5" -> "5" or "5/" -> "5")
        if (cleaned.startsWith("/") && !cleaned.substring(1).contains("/")) {
            cleaned = cleaned.removePrefix("/")
        }
        if (cleaned.endsWith("/") && !cleaned.substring(0, cleaned.length - 1).contains("/")) {
            cleaned = cleaned.removeSuffix("/")
        }

        if (cleaned.isEmpty()) return ParsedMark(null, text, false, null, null)

        // Check for fraction format: "7/10", "3/5"
        val fractionPattern = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""")
        val fractionMatch = fractionPattern.find(cleaned)
        if (fractionMatch != null) {
            val numerator = fractionMatch.groupValues[1].toDoubleOrNull()
            val denominator = fractionMatch.groupValues[2].toDoubleOrNull()
            if (numerator != null && denominator != null && denominator > 0) {
                return ParsedMark(
                    numerator, // Use numerator as the mark value
                    "${numerator.toCleanString()}/${denominator.toCleanString()}",
                    true,
                    numerator,
                    denominator
                )
            }
        }

        // Check for decimal: "4.5", "7.5"
        val decimalPattern = Regex("""(\d+\.\d+)""")
        val decimalMatch = decimalPattern.find(cleaned)
        if (decimalMatch != null) {
            val value = decimalMatch.groupValues[1].toDoubleOrNull()
            if (value != null) {
                return ParsedMark(value, value.toCleanString(), false, null, null)
            }
        }

        // Simple integer: "5", "10" (with contextual validation for decimals read as integer)
        val intPattern = Regex("""^(\d+)$""")
        val intMatch = intPattern.find(cleaned)
        if (intMatch != null) {
            val rawVal = intMatch.groupValues[1]
            val value = rawVal.toDoubleOrNull()
            if (value != null) {
                // If it's a two digit integer ending in 5 (like 25, 35, 45, 55, 75, 85, 95) and > 20.0, it's very likely a decimal (e.g., 2.5)
                // We exclude values <= 20.0 (like 15.0) to prevent corrupting valid marks of 15 points.
                if (rawVal.length == 2 && rawVal.endsWith("5") && value > 20.0) {
                    val correctedValue = value / 10.0
                    return ParsedMark(correctedValue, correctedValue.toCleanString(), false, null, null)
                }
                if (value <= 100) { // Sanity check: marks shouldn't exceed 100
                    return ParsedMark(value, value.toInt().toString(), false, null, null)
                }
            }
        }

        return ParsedMark(null, text, false, null, null)
    }

    private data class ParsedMark(
        val first: Double?,
        val second: String,
        val third: Boolean,
        val fourth: Double?,
        val fifth: Double?
    )

    /**
     * Calculate OCR confidence based on ML Kit's internal confidence metrics.
     */
    private fun calculateOcrConfidence(
        result: com.google.mlkit.vision.text.Text,
        rawText: String
    ): Double {
        if (rawText.isEmpty()) return 0.0

        // ML Kit doesn't expose per-character confidence directly,
        // so we use heuristics based on recognized text quality
        var confidence = 0.7  // Base confidence for successful recognition

        // Bonus for clean numeric text
        val cleaned = rawText.replace("[^0-9./]".toRegex(), "")
        if (cleaned.length == rawText.length) confidence += 0.15

        // Bonus for reasonable mark values (1-100)
        val value = cleaned.toDoubleOrNull()
        if (value != null && value in 0.0..100.0) confidence += 0.1

        // Penalty for very long text (likely picked up noise)
        if (rawText.length > 5) confidence -= 0.1 * (rawText.length - 5)

        // Penalty for multiple text blocks (might be reading surrounding text)
        if (result.textBlocks.size > 1) confidence -= 0.15

        return confidence.coerceIn(0.0, 1.0)
    }

    private fun Double.toCleanString(): String {
        return if (this == this.toLong().toDouble()) {
            this.toLong().toString()
        } else {
            this.toString()
        }
    }

    data class StudentDetails(
        val name: String? = null,
        val rollNumber: String? = null,
        val registrationNumber: String? = null,
        val className: String? = null,
        val section: String? = null
    )

    /**
     * Extracts student details from the first page of an answer sheet.
     */
    suspend fun extractStudentDetails(bitmap: Bitmap): StudentDetails {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text
                    val details = parseStudentDetails(text)
                    continuation.resume(details)
                }
                .addOnFailureListener {
                    continuation.resume(StudentDetails())
                }
        }
    }

    private fun parseStudentDetails(text: String): StudentDetails {
        val lines = text.split("\n")
        var name: String? = null
        var rollNumber: String? = null
        var registrationNumber: String? = null
        var className: String? = null
        var section: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            // Name: Rahul Kumar
            val nameRegex = Regex("(?i)name\\s*:\\s*(.*)")
            nameRegex.find(trimmed)?.let { match ->
                name = match.groupValues[1].trim()
            }

            // Roll No: 24CS017
            val rollRegex = Regex("(?i)roll(?:\\s*no)?\\s*:\\s*(.*)")
            rollRegex.find(trimmed)?.let { match ->
                rollNumber = match.groupValues[1].trim()
            }

            // Registration No: 2026A1234
            val regRegex = Regex("(?i)reg(?:istration)?(?:\\s*no)?\\s*:\\s*(.*)")
            regRegex.find(trimmed)?.let { match ->
                registrationNumber = match.groupValues[1].trim()
            }

            // Class: XII-A
            val classRegex = Regex("(?i)class\\s*:\\s*(.*)")
            classRegex.find(trimmed)?.let { match ->
                val rawClass = match.groupValues[1].trim()
                // Check if section is combined, e.g. "XII-A" or "XII A"
                val classSecMatch = Regex("(.+?)(?:\\s*-\\s*|\\s+)([A-Z])$").find(rawClass)
                if (classSecMatch != null) {
                    className = classSecMatch.groupValues[1].trim()
                    section = classSecMatch.groupValues[2].trim()
                } else {
                    className = rawClass
                }
            }

            // Section: A
            val secRegex = Regex("(?i)section\\s*:\\s*(.*)")
            secRegex.find(trimmed)?.let { match ->
                section = match.groupValues[1].trim()
            }
        }

        return StudentDetails(name, rollNumber, registrationNumber, className, section)
    }

    /**
     * Perform OCR on the entire page bitmap and return the full text.
     */
    suspend fun recognizeFullPageText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                continuation.resume(result.text)
            }
            .addOnFailureListener {
                continuation.resume("")
            }
    }

    /**
     * Extracts a page number from the full text of a page using regex heuristics.
     */
    fun extractPageNumber(text: String): Int? {
        val lines = text.split("\n")
        
        // Pattern 1: Page X or Pg X or P. X
        val pagePattern = Regex("(?i)\\b(?:page|pg\\.?|p\\.?)\\s*(\\d+)")
        for (line in lines) {
            pagePattern.find(line.trim())?.let { match ->
                val num = match.groupValues[1].toIntOrNull()
                if (num != null && num in 1..20) return num
            }
        }

        // Pattern 2: Single number at the top or bottom of the page
        if (lines.isNotEmpty()) {
            val firstLine = lines.first().trim()
            if (firstLine.matches(Regex("\\d+"))) {
                val num = firstLine.toIntOrNull()
                if (num != null && num in 1..20) return num
            }
            val lastLine = lines.last().trim()
            if (lastLine.matches(Regex("\\d+"))) {
                val num = lastLine.toIntOrNull()
                if (num != null && num in 1..20) return num
            }
        }
        return null
    }

    data class OcrValidationResult(
        val isValid: Boolean,
        val totalCharacters: Int,
        val totalWords: Int,
        val combinedText: String
    )

    suspend fun runOcrValidationPasses(
        bitmap: Bitmap,
        imageProcessor: com.markflow.app.cv.ImageProcessor
    ): OcrValidationResult {
        val texts = mutableListOf<String>()
        
        // Pass 1: Standard full-page text
        val txt1 = recognizeFullPageText(bitmap)
        texts.add(txt1)
        if (hasMeaningfulText(txt1)) return createOcrValidationResult(texts)
        
        // Pass 2: High Contrast binarization
        val hc = imageProcessor.convertToHighContrast(bitmap)
        val txt2 = recognizeFullPageText(hc)
        hc.recycle()
        texts.add(txt2)
        if (hasMeaningfulText(txt2)) return createOcrValidationResult(texts)
        
        // Pass 3: Illumination Normalization
        val norm = imageProcessor.normalizeIllumination(bitmap)
        val txt3 = recognizeFullPageText(norm)
        norm.recycle()
        texts.add(txt3)
        if (hasMeaningfulText(txt3)) return createOcrValidationResult(texts)

        // Pass 4: Readability Enhanced (Handwriting-oriented)
        val enhanced = imageProcessor.enhanceDocumentReadability(bitmap)
        val txt4 = recognizeFullPageText(enhanced)
        enhanced.recycle()
        texts.add(txt4)
        if (hasMeaningfulText(txt4)) return createOcrValidationResult(texts)

        // Pass 5: Edge Enhanced
        val edge = imageProcessor.enhanceEdges(bitmap)
        val txt5 = recognizeFullPageText(edge)
        edge.recycle()
        texts.add(txt5)
        
        return createOcrValidationResult(texts)
    }

    private fun hasMeaningfulText(text: String): Boolean {
        val cleaned = text.replace(Regex("[^a-zA-Z0-9]"), "").trim()
        return cleaned.length >= 5
    }

    private fun createOcrValidationResult(texts: List<String>): OcrValidationResult {
        val combined = texts.joinToString("\n").trim()
        val cleaned = combined.replace(Regex("[^a-zA-Z0-9\\s]"), "")
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val chars = cleaned.replace(Regex("\\s"), "")
        
        val totalChars = chars.length
        val totalWords = words.size
        // Valid if at least 5 alphanumeric characters or 2 words are found
        val isValid = totalChars >= 5 || totalWords >= 2
        
        return OcrValidationResult(
            isValid = isValid,
            totalCharacters = totalChars,
            totalWords = totalWords,
            combinedText = combined
        )
    }

    /**
     * Release ML Kit resources.
     */
    fun close() {
        recognizer.close()
    }
}
