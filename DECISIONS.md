# DECISIONS.md

Purpose: Stores architecture and engineering decisions.

---

# Decision History

## ADR-001

Decision: Locked Gradle wrapper tasks to target bundled JDK 17.

## ADR-002

Decision: Implemented offline-first architecture using Room DB and on-device TFLite/OpenCV.

## ADR-003

Decision: Integrated morphological erosion inside Red Ink segmentations.

## ADR-004

Decision: Enforced strict native bitmap recycling routines.

---

# Decision Template

## ADR-001: Enforcing Bundled JDK 17 for Build Environment

Date:
2026-06-23

Status:
Accepted

---

### Problem

Executing `./gradlew` commands on developer machines carrying newer JDK environments (e.g. Java 26) failed due to Kotlin compiler script parsing incompatibilities, interrupting compilation.

---

### Options Considered

#### Option A: Require developers to manually configure system path

Pros:

* No changes to project wrapper scripts.

Cons:

* Inconvenient, prone to error, and results in repetitive setup failure questions.

---

#### Option B: Bind wrappers to detect and fallback to bundled JDK 17

Pros:

* Zero-setup requirement.
* Assures target platform consistency.

Cons:

* Wrapper scripts carry custom directory detection logic.

---

### Decision

Option B. Override `JAVA_HOME` inside wrapper scripts to point to `jdk-dist/jdk-17.0.2` if it is present.

---

### Reasoning

Guarantees build reproducibility and developer onboarding comfort, completely eliminating environment mismatch exceptions out-of-the-box.

---

### Consequences

Positive:

* Unified JVM target environment for daemon and wrapper clients.

Negative:

* JDK zip archive overhead in repository structure.

---

### Related Files

* [gradlew](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/gradlew)
* [gradlew.bat](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/gradlew.bat)

---

## ADR-002: Offline-First Architecture via Room DB & On-device Inference

Date:
2026-06-12

Status:
Accepted

---

### Problem

Teachers evaluate exams in locations (exam halls, classrooms) with limited, unstable, or completely non-existent internet connectivity. Cloud-based grading APIs would fail frequently.

---

### Options Considered

#### Option A: Web-Service API Integration (Google Cloud Vision/TFLite Cloud)

Pros:

* Light client footprint.
* Easy ML model updates.

Cons:

* Complete dependency on network availability.
* High latency.

---

#### Option B: On-device SQLite Database (Room) & Local ML Inference (TFLite & ML Kit)

Pros:

* Absolute offline reliability.
* Zero network cost and latency.

Cons:

* Client-side processing load.
* Model file size overhead in APK.

---

### Decision

Option B. Integrate Local Room DB with custom embedded TensorFlow Lite models.

---

### Reasoning

Teacher productivity is the core product goal. Enabling real-time grading and image deskewing in remote offline rooms outweighs client file size constraints.

---

### Consequences

Positive:

* App works anywhere with sub-100ms grading response loops.

Negative:

* Increases app bundle size by ~15MB.

---

### Related Files

* [MarkFlowDatabase.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/data/local/MarkFlowDatabase.kt)
* [DigitRecognizer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/DigitRecognizer.kt)

---

## ADR-003: Morphological Closing in Red Ink Segmentation

Date:
2026-06-12

Status:
Accepted

---

### Problem

Image thresholding for red ink often leaves gaps in stroke paths or creates isolated noise dots. Standard dilation-only filters bloated strokes, merging ticks and crosses, which degraded the OCR classification rate.

---

### Options Considered

#### Option A: Dilation-Only Filter

Pros:

* Fast execution.

Cons:

* Bloats lines, creating bold shapes that standard digit classifiers fail to parse.

---

#### Option B: Dilation Followed by Erosion (Morphological Closing)

Pros:

* Closes minor stroke gaps while restoring lines back to their original width.
* Eliminates tiny noise speckles.

Cons:

* Requires a second pixel traversal pass.

---

### Decision

Option B. Implement morphological closing (3x3 kernel).

---

### Reasoning

A high classification rate is essential. Restoring original stroke width significantly increases ML Kit Text Recognition confidence scores.

---

### Consequences

Positive:

* Improved handwriting parsing accuracy.

Negative:

* Minor performance overhead on large images (resolved via direct IntArray access).

---

### Related Files

* [RedInkFilter.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/RedInkFilter.kt)

---

## ADR-004: Explicit Memory Bitmaps Recycling

Date:
2026-06-14

Status:
Accepted

---

### Problem

The JVM Garbage Collector handles java objects but does not reliably trigger immediate sweeps of native memory backing bitmap pixel arrays. This results in OutOfMemory (OOM) crashes during continuous document scanning.

---

### Options Considered

#### Option A: Allow standard Garbage Collector to clean bitmaps

Pros:

* Simpler, cleaner code structure.

Cons:

* Delayed native resource release, inducing OOM crashes.

---

#### Option B: Explicitly trigger `.recycle()` on intermediate Bitmaps

Pros:

* Reclaims native memory immediately.

Cons:

* Requires meticulous manual reference management to prevent usage-after-recycle exceptions.

---

### Decision

Option B. Explicitly recycle intermediate images in ImageProcessor and RedInkFilter pipelines.

---

### Reasoning

Stability during grading sessions is critical. Scanning dozens of papers must work without sudden OOM terminations.

---

### Consequences

Positive:

* Constant, low memory footprints.

Negative:

* Developer must make deep copies when returning bitmap outputs to prevent Canvas draw crashes.

---

### Related Files

* [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt)
* [BitmapUtils.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/util/BitmapUtils.kt)
