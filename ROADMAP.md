# MarkFlow Product Roadmap 🗺️

This document outlines the planned development path for **MarkFlow**. Milestones are divided into core pipeline improvements, workflow efficiency features, next-generation machine learning integration, and enterprise cloud dashboards.

---

## 📈 Milestone Overview

| Version | Focus Area | Key Initiatives | Target Timeline | Status |
| :--- | :--- | :--- | :--- | :--- |
| **v1.0.0** | Core Release | Perspective deskew, adaptive binarization, digit OCR, PDF reports | Q2 2026 | **Released** |
| **v1.1.0** | Precision & Scanner | Robust light-invariant edge tracking, higher OCR resolution matrices | Q3 2026 | *In Planning* |
| **v1.2.0** | Batch Evaluation | Folder scanning, multi-page document packages, quick grading templates | Q4 2026 | *In Planning* |
| **v2.0.0** | Intelligent OCR | Advanced handwriting recognition (HTR), automated sum validations | Q2 2027 | *Researching* |
| **v3.0.0** | Cloud Ecosystem | Real-time teacher dashboards, secure cloud sync, student analytics | Q4 2027 | *Future Vision* |

---

## 🚀 Detailed Milestones

### 🟢 v1.0.0 — Core Document Assistant (Current)
*   **Warp Perspective**: Automatic contour tracking and JNI OpenCV perspective correction.
*   **On-Device OCR & Verification**: Integrated Google ML Kit text blocks parsing combined with a local lightweight TensorFlow Lite digit validator.
*   **Sensor Alignment Guard**: Real-time accelerometer tilt visualizer enforcing scanner alignment under `10°` to prevent perspective warp artifacts.
*   **Local Caching**: Robust offline evaluation records stored in an Android Room database.
*   **Reporting**: Compact iText PDF coversheet and score evidence builder.

### 🟡 v1.1.0 — Scanner Precision & Reliability
*   **Adaptive Binarization Improvements**: Better shadow-erasing threshold coefficients to handle fluorescent and natural lighting variations.
*   **Enhanced Contour Math**: RANSAC-like line fitting for torn or dog-eared page boundary calculations.
*   **OCR Correction Loops**: Double-checking parsed scores against a user-defined max marks limit per question to catch OCR reading errors pre-save.

### 🔵 v1.2.0 — Multi-Page & Batch Evaluation
*   **Multi-Page Document Packs**: Ability to group multiple page scans under a single student copy rather than single-page evaluations.
*   **Batch Scanning Mode**: Rapid capture queue that saves images instantly and processes JNI OpenCV warps in a background coroutine pool.
*   **Excel/CSV Bulk Exports**: Bulk csv and xlsx exports for school database integrations.

### 🔴 v2.0.0 — Machine Learning & Intelligent Automation
*   **AI Handwriting Recognition (HTR)**: Transition from simple digit parsing to full word evaluation and handwritten comment transcripts.
*   **Automated Sum Verification**: Automatically calculate question sub-scores and verify if they mathematically equal the teacher's hand-written final sum.
*   **Model Expansion**: On-device quantization updates for the TFLite models to support sub-100ms inference times on budget devices.

### 🟣 v3.0.0 — Centralized Portal & Dashboard
*   **Secure Cloud Sync**: End-to-end encrypted backup of evaluation sheets and metrics.
*   **Web Teacher Dashboard**: A modern web-based console allowing evaluators to view cohort statistics from their desktops.
*   **Advanced Analytics & Cohort Spotlights**: Machine-learning insights identifying common question failure trends across large classes.

---

## 💬 Feedback & Contributions
If you would like to request a feature or collaborate on our machine-learning models, please open a GitHub Issue or join the discussions tab on our repository!
