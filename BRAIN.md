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
*   **Implementation**: `ContourAnalyzer.kt`, `ImageProcessor.kt` using OpenCV `warpPerspective` and Canny edge detection.
*   **Notes**: Accelerometer restricts capture if tilt exceeds 10° to prevent perspective deformation.

### On-Device Mark Verification (OCR & ML)
*   **Implementation**: `OcrProcessor.kt`, `DigitRecognizer.kt`, `MarkVerifier.kt`.
*   **Notes**: Combines Google ML Kit with a custom TFLite digit recognizer to segment and cross-validate markings with handwriting-aware confidence scoring.

### Floating Annotation Canvas
*   **Implementation**: Jetpack Compose graphics on canvas overlaid on top of paper bitmap.
*   **Notes**: Includes 9 stamp/pen types (Tick, Cross, Circle, Pen, etc.) and exports annotations back to the base bitmap at high resolution.

### Concurrent Report Compilation
*   **Implementation**: `ReportGenerator.kt` using a background thread pool and Semaphore(3) concurrency limiting.
*   **Notes**: Shuts down the camera engine (`CameraX`) and recycles ML frames immediately when finalization begins to avoid CPU/memory resource exhaustion.

---

## In Progress Features

### Cloud Synchronization (v3.0.0)
*   **Status**: Initial research phase.
*   **Remaining work**: E2E encryption library implementation, database sync adapter.

---

# Change Log

## 2026-06-23

### Modified
*   `gradlew`, `gradlew.bat`

### Reason
*   Resolved a startup failure on host machines running default Java 26. Fixed by checking if the bundled `jdk-dist/jdk-17.0.2` exists and automatically overriding the local `JAVA_HOME` to match it.

## 2026-06-21

### Added
*   Redesigned UI theme utilizing Material 3 components.
*   Evaluation pipeline optimizations for Compose canvas drawing.

## 2026-06-14

### Modified
*   `OcrProcessor.kt`, `MarkVerifier.kt`

### Reason
*   Implemented confusion matrix corrections mapping character matches (e.g., lowercase `t` and uppercase `T` OCR readings) to digit `1` value.
*   Constrained page stats recalculations to only run on awarded mark regions to prevent processing overhead.

---

# Bug Fixes

## Gradle Wrapper incompatible Java 26 startup error

### Problem
*   Executing `./gradlew` threw `java.lang.IllegalArgumentException: 26.0.1` and aborted.

### Root Cause
*   The system-default JDK version (26.0.1) is unsupported by older Gradle/Kotlin plugins. While the daemon is configured to use the bundled JDK 17, the wrapper client starts up using whatever `java` is configured on the environment path.

### Fix
*   Inserted automatic directory existence checks in `gradlew` and `gradlew.bat` to override `JAVA_HOME` to the bundled `jdk-dist/jdk-17.0.2` before executing.

### Files Modified
*   `gradlew`
*   `gradlew.bat`

### Prevention
*   Always ensure wrapper scripts prefer project-bundled runtimes if available instead of blindly relying on the parent host path.

---

# Known Issues

## Android 10 CameraX Frame Analysis Lag

*   **Description**: Low-end Android 10 devices experience frame drop during real-time edge tracking.
*   **Impact**: Scanning takes longer; auto-capture delay is increased.
*   **Potential Solution**: Downsample the analyzer image proxy dimensions in `ScanViewModel` on API level < 30.
*   **Priority**: Medium

---

# Technical Decisions

## Bundling JDK 17
*   **Date**: 2026-06-23
*   **Decision**: Bundle JDK 17 within the project under `jdk-dist/` and configure wrapper scripts to enforce it.
*   **Alternatives Considered**: Rely on standard system JDK installations.
*   **Reasoning**: Prevents compiler crashes on developer systems using newer incompatible versions of Java (e.g., JDK 26) and guarantees reproducibility.
*   **Consequences**: Increases repository size (adds jdk archive files) but drastically improves developer setup reliability.

---

# Agent Notes

> [!IMPORTANT]
> *   Do not remove `org.gradle.java.home=jdk-dist/jdk-17.0.2` from `gradle.properties`.
> *   Always run Gradle tasks using `./gradlew` rather than global system `gradle` installs.
> *   Ensure Camera resources are explicitly closed upon composing view removal; failing to do so causes severe memory leaks.

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

# Dependency Notes

*   **Package**: `OpenCV SDK (Android Port)`
    *   **Purpose**: Perspective warping, edge contouring, image threshold cleaning.
    *   **Do Not Replace Because**: JNI binding allows fast, native-level pixel manipulation.

*   **Package**: `Google ML Kit (Text Recognition)`
    *   **Purpose**: High-confidence text segmentation OCR.
    *   **Do Not Replace Because**: Bundled directly into the Android system, minimizing APK overhead.

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

# AI Context Summary

1.  **What the project does**: Offline mobile scanning and auto-grading of student papers using OpenCV (CV) and TFLite/ML Kit (OCR).
2.  **Current architecture**: Jetpack Compose frontend calling Clean-Architecture Kotlin Repositories backed by Room SQLite, leveraging native JNI libraries.
3.  **Recent major changes**: Standardized local gradle wrappers to lock execution environment to JDK 17 to fix Java 26 compatibility crashes.
4.  **Active bugs**: Frame analysis lag on Android 10 devices.
5.  **Next priorities**: Implementation of batch-scanning and multi-page doc assemblies.

---

# Last Updated

Timestamp:
2026-06-25 13:55 UTC

Updated By:
AI Agent (Antigravity)

Summary:
Created BRAIN.md outlining project architecture, schemas, tech stack, and change log history including Gradle wrapper fixes.
