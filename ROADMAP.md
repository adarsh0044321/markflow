# ROADMAP.md

Purpose: Defines the future direction of the project.

---

# Vision

Create the most reliable, offline-first exam evaluation and grading digitizer for teachers, enabling fast, precise red-ink annotation and dynamic aggregate statistics directly on-device with zero network latency.

---

# Current Milestone

Milestone:
v3.0.0

Goal:
Implement local database encryption and initial designs for secure cloud synchronization.

Status:
In Progress

Completion:
10%

---

# Upcoming Milestones

## Milestone: v3.1.0

Target Date:
2027-06-01

Objectives:

* Real-time local encryption utilizing SQLCipher integration for Room database.
* End-to-end encrypted backup of evaluation sheets and cohort metrics.
* Initial prototypes of a secure web teacher dashboard for analytics sync.

Success Criteria:

* Room database is encrypted at rest with zero performance degradation in scanning.
* Data sync uploads occur seamlessly in the background when connectivity becomes active.

Risks:

* Key management security on Android Keystore across different API levels.
* Network overhead and sync latency for large multi-page PDF binaries.

---

## Milestone: v4.0.0

Objectives:

* Advanced machine learning statistics identifying common question failure patterns.
* OCR templates for customizable exam forms.

Dependencies:

* TFLite GPU delegate optimization.
* OpenCV contour matching for pre-defined box templates.

---

# Feature Backlog

## High Priority

### Batch Scanning Mode

Description:
A camera capture queue that saves page images instantly and delegates deskewing/OCR to a background coroutine pool, reducing time between page flips.

Expected Impact:
High

Estimated Complexity:
High

Dependencies:
Coroutines, CameraX ImageAnalysis.

---

## Medium Priority

### Excel/CSV Bulk Export Charts

Description:
Automatically generate summary charts (pie charts, distributions) inside exported spreadsheet report packages.

Expected Impact:
Medium

Estimated Complexity:
Medium

Dependencies:
ReportGenerator, OpenCSV.

---

## Low Priority

### Custom Annotation Ink Colors

Description:
Allows examiners to pick colors other than red (e.g. green or blue) for annotations.

Expected Impact:
Low

Estimated Complexity:
Low

Dependencies:
PageViewScreen, Compose Canvas.

---

# Technical Debt

## Frame Analyzer Downsampling on API < 30

Description:
Android 10 devices experience frame drop during real-time contour tracking. The ImageProxy resolution needs to be downsampled dynamically.

Reason:
CameraX analyzer pipeline processes full-resolution buffers on older GPUs.

Impact:
Performance

Priority:
Medium

---

# Research Items

## Room Local Encryption

Questions:

* Does SQLCipher library introduce library conflicts with Hilt/Room on target SDK 35?
* What is the binary size impact of adding SQLCipher dependency?

Potential Approaches:

* Approach A: Integrate `androidx.sqlite.db.SupportSQLiteDatabase` custom open helper.
* Approach B: Rely on Android EncryptedFile/EncryptedSharedPreferences for directory paths.

---

# Stretch Goals

* Dark mode manual override toggle.
* Real-time automated grading suggestions for MCQ regions.

---

# Recently Completed

Move completed roadmap items here.

## 2026-06-23

Completed:

* Lock Gradle wrapper tasks to target JDK 17 to avoid Java 26 environment startup crashes.

Notes:
Always test scripts against non-default configurations (such as standard environment PATH overrides).

## 2026-06-21

Completed:

* Release v2.0.0: Red Ink Annotation Toolbox, Step and Final Marks Entry dialogs, camera shutdown on finalization.

Notes:
Bitmap memory pools must be strictly managed to prevent GC OOM exceptions during PDF compilation.
