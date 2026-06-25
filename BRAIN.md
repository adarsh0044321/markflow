# Project Overview

## Project Name

MarkFlow

## Purpose

**MarkFlow** is a professional-grade, offline-first Android application designed for teachers and examiners to digitize, evaluate, annotate, and report on paper answer sheets directly on their mobile device or tablet. It incorporates native computer vision (OpenCV) for document scanning/alignment, on-device OCR (ML Kit) and handwriting digit validation (TensorFlow Lite), and a Jetpack Compose floating canvas for digital red ink annotations.

## Current Status

Production-ready. Version 2.0.0 has been released, featuring the annotation toolbox, sequential step/final marks dialogs, explicit settings validation, and background camera resource release on export.

## Tech Stack

*   **Frontend**: Jetpack Compose (Material 3), Compose Navigation, Hilt (Dependency Injection), Kotlin Coroutines & Flow.
*   **Database**: Room SQLite Database (Offline-first architecture).
*   **Native & CV Layer**: OpenCV SDK (Android Port), C++ JNI.
*   **Machine Learning**: Google ML Kit (Text Recognition OCR), TensorFlow Lite interpreter (`digit_recognizer.tflite`).
*   **Reporting**: iText PDF (v5.x), OpenCSV.
*   **Languages**: Kotlin (JDK 17), JNI C++.
*   **Build Tools**: Gradle (Kotlin DSL), Android SDK (target SDK 35, min SDK 29), CMake (for C++ JNI compilation).
*   **Bundled Tools**: JDK 17 (`jdk-dist/`), Gradle wrapper (`gradle/wrapper/`).

---

# Architecture

## High-Level Structure

```
              [Camera Preview / Sensor Guide]
                             ↓
              [Contour Identification (OpenCV)]
                             ↓
               [Perspective Warp / Deskew]
                             ↓
            [HSV Red Ink Masking & Segmentation]
                             ↓
           [OCR Text Recognition (Google ML Kit)]
                             ↓
          [Digit Verification (TensorFlow Lite)]
                             ↓
             [SQLite Storage Layer (Room DB)]
                             ↓
         [Floating Annotation / Evaluation Workspace]
                             ↓
          [Background Report Generator (iText)]
```

*   **UI Layer**: Jetpack Compose views observing ViewModels that expose MVI state via Kotlin Flows.
*   **Service/Core Layer**: OpenCV processes the camera frame buffer, filters red ink contours, and warps perspective. ML Kit and TFLite analyze the cropped patches to recognize digits.
*   **Data Layer**: Clean repository interface separating UI code from raw SQLite Room database operations.

---

# Directory Map

```
app/src/main/java/com/markflow/app/
├── MainActivity.kt               # App entry point, nav-host config
├── MarkFlowApp.kt                # Application subclass, Hilt setup
├── cv/                           # Computer vision engines (OpenCV JNI wrapper)
│   ├── ContourAnalyzer.kt        # Auto-boundary detection & tilt guides
│   ├── DuplicateDetector.kt      # Hash checks to avoid scan double-saves
│   ├── ImageProcessor.kt         # Deskewing, thresholding, shadow cleanup
│   ├── PageChangeDetector.kt     # Detects page flips in real time
│   ├── RedInkFilter.kt           # HSV masking for isolating teacher ink
│   └── UncheckedAnswerDetector.kt# Flags pages containing ticks with no mark entries
├── data/                         # Data access layer
│   ├── local/
│   │   ├── dao/                  # Session, Copy, Page, Mark, Issue, Question DAOs
│   │   ├── entity/               # Room schema entities
│   │   └── MarkFlowDatabase.kt   # Room database abstraction (version 3)
│   └── repository/               # Repository implementations
├── di/                           # Hilt DI Modules (Database, Repository, ML modules)
├── domain/                       # Core domain models
├── ml/                           # On-device AI/ML pipeline
│   ├── ConfidenceCalculator.kt   # OCR vs CV vs AI confidence scoring
│   ├── DigitRecognizer.kt        # TFLite handwriting recognition interpreter
│   ├── MarkVerifier.kt           # Smart range validator (0.5 increments, bounds check)
│   └── OcrProcessor.kt           # ML Kit wrapper with confusion matrix corrections
├── ui/                           # UI Screens & ViewModels
│   ├── components/               # Shared custom composables (Stamps, sliders, alerts)
│   ├── history/                  # Evaluation session history logs
│   ├── home/                     # Class folder explorer & statistics dashboard
│   ├── pageview/                 # Scoring board and annotation editor
│   ├── scan/                     # Spirit level camera scanner
│   ├── settings/                 # App rules config (Max marks, margins, thresholds)
│   └── summary/                  # Review screens, overall grades, and finalization
└── util/                         # Utility classes (Bitmap converters, PDF builder)
```

---

# Features

## Completed Features

### Smart Document Scanning & Deskewing
*   **Implementation**: [ContourAnalyzer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ContourAnalyzer.kt), [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt) using OpenCV `warpPerspective` and Canny edge detection.
*   **Notes**: Accelerometer restricts capture if tilt exceeds 10° to prevent perspective deformation.

### On-Device Mark Verification (OCR & ML)
*   **Implementation**: [OcrProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/OcrProcessor.kt), [DigitRecognizer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/DigitRecognizer.kt), [MarkVerifier.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/MarkVerifier.kt).
*   **Notes**: Combines Google ML Kit with a custom TFLite digit recognizer to segment and cross-validate markings with handwriting-aware confidence scoring.

### Floating Annotation Canvas
*   **Implementation**: Jetpack Compose graphics on canvas overlaid on top of paper bitmap.
*   **Notes**: Includes 9 stamp/pen types (Tick, Cross, Circle, Pen, etc.) and exports annotations back to the base bitmap at high resolution.

### Concurrent Report Compilation
*   **Implementation**: [ReportGenerator.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/util/ReportGenerator.kt) using a background thread pool and Semaphore(3) concurrency limiting.
*   **Notes**: Shuts down the camera engine (`CameraX`) and recycles ML frames immediately when finalization begins to avoid CPU/memory resource exhaustion.

---

# Technical Decisions

## Bundling JDK 17
*   **Date**: 2026-06-23
*   **Decision**: Bundle JDK 17 within the project under `jdk-dist/` and configure wrapper scripts to enforce it.
*   **Alternatives Considered**: Rely on standard system JDK installations.
*   **Reasoning**: Prevents compiler crashes on developer systems using newer incompatible versions of Java (e.g., JDK 26) and guarantees reproducibility.
*   **Consequences**: Increases repository size (adds jdk archive files) but drastically improves developer setup reliability.

## Morphological Erosion in HSV Red Ink Processing
*   **Date**: 2026-06-12
*   **Decision**: Standardize on a morphological closing (dilation followed by a 3x3 kernel erosion) instead of dilation-only inside [RedInkFilter.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/RedInkFilter.kt).
*   **Reasoning**: Dilation-only bloated red lines, causing isolated background speckles to expand and confuse OCR models. Erosion shrinks strokes back to original dimensions while keeping gaps closed.

## Enforcing Locale.US on Formatting Extensions
*   **Date**: 2026-06-14
*   **Decision**: Enforce `Locale.US` in all Double/Float string formatting and file size utilities.
*   **Reasoning**: Prevents parsing errors on devices configured in European or Latin-American locales where commas (`,`) are used as decimal separators, breaking Room DB serialization.

---

# Change Log & Commit History

A record of architectural changes, optimizations, and hotfixes implemented:

*   **`docs: add BRAIN.md persistent memory file`** (2026-06-25)
    *   Initial creation of the project brain state file.
*   **`fix: update Gradle wrapper scripts to automatically use bundled JDK 17`** (2026-06-23)
    *   Updated `gradlew` and `gradlew.bat` to detect the bundled JDK 17 path and automatically export it to `JAVA_HOME`.
*   **`feat: evaluation pipeline improvements and theme redesign`** (2026-06-21)
    *   M3 UI components migration and canvas redraw enhancements.
*   **`fix: include lowercase t in confusion mapping check for OCR values`** & **`fix: map single character T and t confusions to 1 in OCR mapping`** (2026-06-14)
    *   Updated [OcrProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/OcrProcessor.kt) confusion mappings. ML Kit commonly reads handwriting digits `1` or `/` as characters `t` or `T`.
*   **`fix: format floating point strings in OCR processor to prevent float noise`** (2026-06-14)
    *   Formats OCR inputs to strip IEEE floating point precision noise (e.g. `2.00000003` to `2.0`).
*   **`fix: constrain page/copy stats recalculation to awarded marks region type`** (2026-06-14)
    *   Optimizes database triggers by avoiding recalculating aggregates on non-mark region modifications.
*   **`fix: return copy in scaleBitmap to avoid sharing reference and crash on recycle`** (2026-06-14)
    *   Fixed a critical Canvas rendering crash inside [BitmapUtils.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/util/BitmapUtils.kt) where sharing original bitmap references led to attempts to draw on a recycled bitmap.
*   **`fix: recalculate session stats on copy deletion in copy repository`** (2026-06-14)
    *   Purges cached cohort statistics correctly when student records are deleted.
*   **`fix: validate default question marks against max marks in settings`** (2026-06-14)
    *   Adds bounds validation to Settings configurations to block user error input.
*   **`fix: recycle intermediate sharpened bitmap in edge enhancement`** & **`fix: recycle intermediate bitmaps in readability enhancement`** (2026-06-14)
    *   Fixed JVM memory leaks inside [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt) by explicitly calling `.recycle()` on intermediate steps (`normalizeIllumination`, `sharpen`, `contrast`).
*   **`fix: close input streams and file channels in model loading`** (2026-06-14)
    *   Fixed file descriptor leak in [DigitRecognizer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/DigitRecognizer.kt) by wrapping AssetFileDescriptor reading in `.use { ... }`.
*   **`fix: guard against division by zero in report progress estimate`** (2026-06-14)
    *   Checks if average time-per-page is zero to avoid NaN division output during export logs.
*   **`fix: recycle intermediate mask bitmap in red ink filter`** (2026-06-14)
    *   Recycles temporary HSV binary mask canvas objects to clear native memory during camera frames.
*   **`fix: optimize pearson correlation computation in duplicate detection`** (2026-06-14)
    *   Pre-calculates current frame averages outside comparison iterations inside [DuplicateDetector.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/DuplicateDetector.kt).
*   **`fix: optimize unchecked answer detector loop performance`** (2026-06-14)
    *   Rewrote [UncheckedAnswerDetector.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/UncheckedAnswerDetector.kt) to access raw pixel integer arrays (`getPixels`) directly. Eliminated slower `getPixel` JNI jumps and redundant mask bitmaps allocations.
*   **`fix: calculate statistics grade distribution using dynamic percentages instead of absolute marks`** (2026-06-12)
    *   Grade distribution analytics now scale dynamically against the configuration session's `maxMarks` instead of absolute bounds (fixed bug where students on a 50-mark exam were marked F).

---

# Bug Fixes & Deep-Dive Root Causes

## Gradle Wrapper Incompatible Java 26 Startup Error
*   **Problem**: Running Gradle tasks threw `java.lang.IllegalArgumentException: 26.0.1` and failed.
*   **Root Cause**: The Kotlin compiler version utilized inside early Gradle scripts check the version of the running JVM, crashing on Java 26's version representation.
*   **Fix**: Configured wrapper scripts to check for the bundled JDK 17 folder and override `JAVA_HOME` if present.
*   **Files Modified**: `gradlew`, `gradlew.bat`

## Canvas Drawing recycled bitmap crash in scaleBitmap
*   **Problem**: App threw `java.lang.RuntimeException: Canvas: trying to use a recycled bitmap` and crashed.
*   **Root Cause**: Inside [BitmapUtils.scaleBitmap](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/util/BitmapUtils.kt), if the scaling ratio was `>= 1f` (image smaller than max dimensions), the script returned the original bitmap reference instead of a scaled one. The caller recycled the scaled bitmap after processing, inadvertently destroying the original image which was still bound to the UI.
*   **Fix**: Modified the exit condition to return a deep copy: `bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)`.
*   **Files Modified**: [BitmapUtils.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/util/BitmapUtils.kt)

## Memory OOM Crashes on Rapid scanning
*   **Problem**: After scanning 10-15 pages, the application ran out of JVM heap memory and crashed.
*   **Root Cause**: Intermediate operations in [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt) (`enhanceContrast`, `sharpen`, `normalizeIllumination`) allocate full-resolution bitmaps. These references were discarded but remained un-recycled, leaving their native backing pixel array memory allocated in heap.
*   **Fix**: Added strict `.recycle()` calls on intermediate bitmaps immediately after the successive transformation step consumed them.
*   **Files Modified**: [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt)

## Unchecked Answer Detector Performance Bottleneck
*   **Problem**: Analyzing a page took > 1.2 seconds, introducing lag.
*   **Root Cause**: The detector created a mask bitmap and evaluated pixels in two horizontal loop passes using the standard `getPixel(x, y)` method. Each call to `getPixel` transitions the call across JNI boundaries, creating massive loop overhead.
*   **Fix**: Extracted the bitmap pixels into flat `IntArray`s using `getPixels(...)` once, and ran brightness calculations and HSV range checking directly inside Kotlin arrays using bitwise shifts.
*   **Files Modified**: [UncheckedAnswerDetector.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/UncheckedAnswerDetector.kt)

---

# Known Issues

## Android 10 CameraX Frame Analysis Lag
*   **Description**: Low-end Android 10 devices experience frame drop during real-time edge tracking.
*   **Impact**: Scanning takes longer; auto-capture delay is increased.
*   **Potential Solution**: Downsample the analyzer image proxy dimensions in `ScanViewModel` on API level < 30.
*   **Priority**: Medium

---

# Agent Notes & Safety Guidelines

Before modifying any code in MarkFlow, you MUST read and follow these safety guidelines:

> [!CAUTION]
> ### 1. Bitmap Lifecycle and Recycling Rules
> Bitmaps are native-backed allocations. In Android, you must explicitly call `recycle()` on any intermediate bitmap that is no longer needed.
> *   **Rule**: Never return an original reference from a utility function if it can be recycled later. Always copy it.
> *   **Rule**: Do not recycle a bitmap that is currently referenced by a Jetpack Compose image painter or drawing canvas.
> *   **Rule**: Ensure native masks created during HSV segmentation are recycled in `finally` blocks.

> [!WARNING]
> ### 2. Locale-Dependent String Serialization
> *   **Rule**: When parsing or writing floating-point numbers (such as scores) to database files or text interfaces, always enforce `Locale.US` (e.g., `String.format(Locale.US, "%.1f", value)`). Otherwise, systems running in locales that use commas (e.g., German or French) will produce formatting exceptions (e.g., `1,5` instead of `1.5`) when parsing decimal strings back into numbers.

> [!IMPORTANT]
> ### 3. Camera Session Life Cycle
> *   **Rule**: MarkFlow implements dynamic Camera shutdown. When generating reports or exiting scanning routes, you must invoke the shutdown hook on the camera executor to release frame analyzer binds, or else the app will suffer memory exhaustion and camera binding lockups.

---

# Development Workflow

Build Commands:
```powershell
./gradlew compileDebugSources
```

Test Commands:
```powershell
# Run unit tests
./gradlew testDebugUnitTest
```

Run/Install Commands:
```powershell
# Build and install debug APK on connected device
./gradlew installDebug
```

---

# Database Schema Summary

## Tables

1.  **sessions**: Class folders grouping evaluations. Holds statistics (average, median, high/low, pass rate).
2.  **copies**: Student answer papers linked to a session. Holds final total scores and identifier attributes (name, roll no).
3.  **pages**: Individual sheets linked to a copy. Caches image paths, page status, and OCR string blocks.
4.  **marks**: Detected mark entries. Stores coordinates (`boundingBox`), values, and verification details (OCR vs CV vs AI confidence).
5.  **issues**: Evaluation problems (e.g. low-confidence marks, unchecked answers) needing teacher audit.
6.  **question_marks**: Breakdown of scores per question number.
7.  **audit_trails**: Record of teacher overrides and edits for accountability.

---

# Dependency Notes

*   **Package**: `OpenCV SDK (Android Port)`
    *   **Purpose**: Perspective warping, edge contouring, image threshold cleaning.
    *   **Do Not Replace Because**: JNI binding allows fast, native-level pixel manipulation.

*   **Package**: `Google ML Kit (Text Recognition)`
    *   **Purpose**: High-confidence text segmentation OCR.
    *   **Do Not Replace Because**: Bundled directly into the Android system, minimizing APK overhead.

---

# AI Context Summary

1.  **What the project does**: Offline mobile scanning and auto-grading of student papers using OpenCV (CV) and TFLite/ML Kit (OCR).
2.  **Current architecture**: Jetpack Compose frontend calling Clean-Architecture Kotlin Repositories backed by Room SQLite, leveraging native JNI libraries.
3.  **Recent major changes**: Standardized local gradle wrappers to lock execution environment to JDK 17 to fix Java 26 compatibility crashes.
4.  **Active bugs**: Frame analysis lag on Android 10 devices.
5.  **Next priorities**: Implementation of batch-scanning and multi-page doc assemblies.

---

# Last Updated

Timestamp:
2026-06-25 14:02 UTC

Updated By:
AI Agent (Antigravity)

Summary:
Significantly expanded BRAIN.md with detailed descriptions of recent commits, bug deep-dives (bitmap recycling crashes, JNI pixel checks overhead, file channel leaks, morphological erosion, locale-US formatting, and statistics grading fixes), and warning guidelines for future developers.
