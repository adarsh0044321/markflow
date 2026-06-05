package com.markflow.app.util

/**
 * Application-wide constants for MarkFlow.
 */
object Constants {

    // ── Confidence Thresholds ──
    /** Marks above this confidence are auto-confirmed */
    const val CONFIDENCE_AUTO_CONFIRM = 0.85
    /** Marks below this go to "needs review" */
    const val CONFIDENCE_REVIEW_THRESHOLD = 0.50
    /** Marks below this are flagged as low confidence issues */
    const val CONFIDENCE_LOW_THRESHOLD = 0.30

    // ── Page Change Detection ──
    /** Minimum similarity change to detect a page turn (0-1, lower = more sensitive) */
    const val PAGE_CHANGE_THRESHOLD = 0.65
    /** Number of stable frames required before auto-capture */
    const val STABILITY_FRAME_COUNT = 15
    /** Minimum time between captures (milliseconds) to prevent rapid-fire */
    const val MIN_CAPTURE_INTERVAL_MS = 2000L
    /** Frame analysis interval (milliseconds) — don't analyze every frame */
    const val FRAME_ANALYSIS_INTERVAL_MS = 200L

    // ── Red Ink Detection (HSV ranges) ──
    /** Hue range for red ink detection (two ranges because red wraps around 0/180) */
    const val RED_HUE_LOW_1 = 0.0
    const val RED_HUE_HIGH_1 = 30.0
    const val RED_HUE_LOW_2 = 135.0
    const val RED_HUE_HIGH_2 = 180.0
    /** Minimum saturation for red ink */
    const val RED_SAT_MIN = 60.0
    /** Minimum value (brightness) for red ink */
    const val RED_VAL_MIN = 50.0

    // ── Contour Filtering ──
    /** Minimum contour area to be considered a mark candidate (pixels squared) */
    const val MIN_CONTOUR_AREA = 15.0
    /** Maximum contour area to be considered a mark (avoids large blobs) */
    const val MAX_CONTOUR_AREA = 50000.0
    /** Minimum aspect ratio for a mark bounding box */
    const val MIN_MARK_ASPECT_RATIO = 0.2
    /** Maximum aspect ratio for a mark bounding box */
    const val MAX_MARK_ASPECT_RATIO = 5.0

    // ── Image Processing ──
    /** Target width for processed page images */
    const val PROCESSED_IMAGE_WIDTH = 1200
    /** JPEG quality for saved images */
    const val IMAGE_QUALITY = 90
    /** Thumbnail size for list displays */
    const val THUMBNAIL_SIZE = 300
    /** Evidence crop padding around bounding box (pixels) */
    const val EVIDENCE_CROP_PADDING = 20

    // ── Duplicate Detection ──
    /** Hamming distance threshold for duplicate pages (lower = stricter) */
    const val DUPLICATE_HASH_THRESHOLD = 8

    // ── TF Lite ──
    /** Input image size for digit recognizer model */
    const val DIGIT_MODEL_INPUT_SIZE = 28
    /** Model file name in assets */
    const val DIGIT_MODEL_FILENAME = "digit_recognizer.tflite"

    // ── File Storage ──
    const val PAGES_DIR = "pages"
    const val EVIDENCE_DIR = "evidence"
    const val THUMBNAILS_DIR = "thumbnails"
    const val REPORTS_DIR = "reports"

    // ── Export ──
    const val PDF_EXPORT_PREFIX = "markflow_report"
    const val EXCEL_EXPORT_PREFIX = "markflow_data"
    const val CSV_EXPORT_PREFIX = "markflow_export"

    // ── UI ──
    const val MARK_FEED_MAX_ITEMS = 50
    const val ANIMATION_DURATION_MS = 300
    const val SCAN_OVERLAY_ALPHA = 0.7f
}
