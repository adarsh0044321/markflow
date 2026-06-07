# MarkFlow 📄🖋️

MarkFlow is a professional-grade, document-processing-first Android application designed for evaluated answer sheet digitization, verification, grading, and reporting. 

Unlike generic camera scanner apps, MarkFlow acts as an end-to-end evaluation assistant. It uses native computer vision (OpenCV) to deskew pages, real-time device sensors to guide alignment, on-device machine learning (ML Kit & TensorFlow Lite) to verify digits, and interactive touch annotation to let teachers draw red ink checks directly onto digital sheets.

---

## 📖 Table of Contents
1. [🌟 Key Features & Capabilities](#-key-features--capabilities)
2. [⚙️ How It Works (Core Pipelines)](#️-how-it-works-core-pipelines)
3. [📁 Folder Structure & Package Mapping](#-folder-structure--package-mapping)
4. [🛠️ Technology Stack](#️-technology-stack)
5. [🏗️ Building and Installing](#️-building-and-installing)
6. [👩‍💻 Developer Guide: How to Extend & Update the Code](#-developer-guide-how-to-extend--update-the-code)
   - [A. Adding or Modifying Database Tables (Room)](#a-adding-or-modifying-database-tables-room)
   - [B. Modifying the Scanning / OpenCV Image Filter Pipeline](#b-modifying-the-scanning--opencv-image-filter-pipeline)
   - [C. Updating ML Models & Digit Recognition](#c-updating-ml-models--digit-recognition)
   - [D. Changing UI Screens & State Flow](#d-changing-ui-screens--state-flow)
   - [E. Updating Dependencies or Gradle Version](#e-updating-dependencies-or-gradle-version)
   - [F. Modifying ProGuard / R8 Rules](#f-modifying-proguard--r8-rules)
7. [🤖 CI/CD Release Automation](#-cicd-release-automation)

---

## 🌟 Key Features & Capabilities

### 1. High-Fidelity Scanning Pipeline
* **Warp Perspective (Deskewing)**: Detects document outer edges (contours) in real-time, calculates the bounding quadrangle corners, and performs an OpenCV perspective warp to flatten the page.
* **Shadow and Crease Removal**: Uses adaptive threshold binarization to extract dark text and red inks while erasing backgrounds, shadows, desk surfaces, and creases.
* **Stable Auto-Capture**: Once a document boundary is detected and holds steady, the frame is auto-captured. It checks for duplicates to prevent capturing the same page twice.

### 2. Physical Alignment Guide (Digital Spirit Level)
* **Real-time Accelerometer Sensor**: Uses `Sensor.TYPE_ACCELEROMETER` to measure tilt angles relative to a flat plane.
* **Visual Overlay**: Renders an interactive circular target and bubble overlay. Auto-capturing is restricted if the tilt exceeds `10°` to prevent perspective distortion.

### 3. Flash & Light Sensor Automation
* **Light Sensor**: Uses `Sensor.TYPE_LIGHT` to read ambient light. If it drops below `15 lux`, the camera torch is automatically turned on to ensure high-quality, shadowless scans.
* **Torch Controller**: Toggle between manual `ON`, `OFF`, and sensor-driven `AUTO` lighting.

### 4. Direct Page Annotation
* **Red Ink Canvas**: Enables a drawing canvas directly overlaying the scanned sheet image.
* **Pixel Coordinate Mapping**: Translates Compose Canvas gesture coordinates into exact pixel positions on the underlying high-resolution bitmap.
* **In-place Writing**: Merges red ink strokes into the raw file on disk. Custom cache-busting ensures that Coil reloads the updated image instantly.

### 5. Cohort Statistics & Dashboards
* **Aggregated Session Analytics**: Dynamic dropdown selections allow view switches between specific batches/sessions or full cohort statistics.
* **Grade Bands**: Computes student counts in standard grading ranges and displays a color-coded stacked progress chart.

### 6. PDF Exports with Cover Pages
* **Cover Page Generation**: The first page contains evaluation session records, class averages, passing ratios, student parameters, and evaluator sign-off sheets.
* **Page Breaks**: Seamlessly flows from the cover sheets to high-resolution evidence crops of the scanned pages.

---

## ⚙️ How It Works (Core Pipelines)

### A. The Capture and Binarization Flow
1. **Sensor Verification**: Accelerometer and light sensors configure torch toggles and target overlays.
2. **Contour Extraction**: Frames are sent to `ContourAnalyzer.kt`. It performs grayscale reduction, Gaussian blur, Canny edge detection, and isolates the largest quadrilateral contour.
3. **Perspective Warp**: Bounding corners are mapped to a rectangular target shape. `Imgproc.getPerspectiveTransform` and `Imgproc.warpPerspective` output a flat document image.
4. **Adaptive Cleaning**: `ImageProcessor.kt` strips backgrounds using local adaptive thresholding filters, keeping dark and red ink channels clear.

### B. The Digit Verification Flow
1. **OCR Detection**: `OcrProcessor.kt` uses Google ML Kit to parse characters, boundary locations, and text strings on the document.
2. **Neural Network Check**: Extracted bounding boxes of parsed digits are forwarded to `DigitRecognizer.kt`, which executes on-device inference against the custom `digit_recognizer.tflite` model.
3. **Confidence Scoring**: Outputs verification flags to flag low-confidence reads for human review.

### C. The Touch Drawing Coordinate Translation
When drawing on Compose's `Canvas`:
* The preview image is scaled and centered inside a layout box.
* The translation formula calculates raw image pixels based on canvas aspect ratios:
  $$\text{Pixel}_X = \frac{\text{Touch}_X - \text{Padding}_X}{\text{Scale}_X}$$
  $$\text{Pixel}_Y = \frac{\text{Touch}_Y - \text{Padding}_Y}{\text{Scale}_Y}$$
* Red paint strokes are written directly onto the original file using an Android `Canvas` overlaying the mutable `Bitmap` before saving.

---

## 📁 Folder Structure & Package Mapping

```
app/src/main/java/com/markflow/app/
│
├── cv/                      # OpenCV native image processing modules
│   ├── ImageProcessor.kt    # Contrast, thresholding, and shadow cleanups
│   ├── ContourAnalyzer.kt   # Boundary edge tracking & warp transforms
│   ├── PageChangeDetector.kt# Frame stability detector
│   └── RedInkFilter.kt      # Filters to isolate checking markings
│
├── data/                    # Storage and API endpoints
│   ├── local/               # Database implementation (Room)
│   │   ├── dao/             # Data Access Objects (Copy, Session, Page, Marks)
│   │   ├── entity/          # SQLite Schema Entity models
│   │   └── MarkFlowDatabase.kt # Room database definition
│   └── repository/          # Repositories (bridges VM to storage)
│
├── domain/                  # Pure Business Logic
│   └── model/               # Session models and core domain enums
│
├── ml/                      # Machine Learning models
│   ├── DigitRecognizer.kt   # TFLite digit recognizer wrapper
│   └── OcrProcessor.kt      # ML Kit OCR processor
│
├── ui/                      # Jetpack Compose Screens and ViewModels
│   ├── home/                # Launch dashboards
│   ├── scan/                # Document preview, spirit level, and torch guides
│   ├── pageview/            # Page viewer and red ink drawing annotation canvas
│   ├── statistics/          # Cohort selection, grade bands, and sorting list
│   ├── review/              # Unchecked answers and OCR verification reviews
│   └── reports/             # PDF coversheet and data exports
│
└── util/                    # Helper packages
    ├── FileUtils.kt         # Direct disk IO operations
    ├── BitmapUtils.kt       # Scaling, translation, and image rotations
    └── ReportGenerator.kt   # iText PDF cover and content generator
```

---

## 🛠️ Technology Stack
* **Language**: Kotlin (JDK 17)
* **Design Pattern**: MVVM with Repository Pattern
* **UI**: Jetpack Compose (Material 3)
* **Dependency Injection**: Hilt (Dagger)
* **Local Database**: Room DB (SQLite)
* **Camera Platform**: CameraX
* **Native Processing**: OpenCV Android SDK
* **Deep Learning**: Google ML Kit OCR & TensorFlow Lite (TFLite)
* **Exporter APIs**: iText PDF (v5.x), Apache POI (Excel generation), OpenCSV

---

## 🏗️ Building and Installing

### 1. Requirements
* Android Studio (Koala/Ladybug or newer)
* Android SDK (API 29 to 35)
* Native C++ build tools (CMake & NDK configured in Android Studio)

### 2. Gradle Build Commands
Run from the project root:

```bash
# Build Debug APK (Ideal for immediate sideload testing)
./gradlew assembleDebug

# Build Unsigned Release APK (Optimized and minified production build)
./gradlew assembleRelease
```
APKs will be written to:
* **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
* **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 👩‍💻 Developer Guide: How to Extend & Update the Code

If you are a developer looking to modify, extend, or update MarkFlow, follow these instructions:

### A. Adding or Modifying Database Tables (Room)
The database definitions reside in `com.markflow.app.data.local/`.
1. **Modify/Create Entity**: Update or add a new file in the `entity/` folder (e.g. `CopyEntity.kt`). Mark it with `@Entity(tableName = "your_table")`.
2. **Register in Database**: Open [MarkFlowDatabase.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/data/local/MarkFlowDatabase.kt), add your entity class to the `@Database(entities = [...])` array, and increment the `version` number.
3. **Write Schema Migrations**:
   * If you add database tables or columns, you must write a migration path in `MarkFlowDatabase.kt` (e.g., `val MIGRATION_3_4 = object : Migration(3, 4) { ... }`).
   * Register the migration using `.addMigrations(MIGRATION_3_4)` in the database builder inside [AppModule.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/di/AppModule.kt).
   * Verify schemas: Room automatically exports schema JSONs to `app/schemas/`. Running `./gradlew compileDebugKotlin` updates these files. Commit these JSON schemas to git.

### B. Modifying the Scanning / OpenCV Image Filter Pipeline
* **Warp and Edge Detection**: Edit [ContourAnalyzer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ContourAnalyzer.kt). You can adjust Canny thresholds, Gaussian blur kernel sizes, or the minimum document contour area filters.
* **Cleaning and Enhancements**: Edit [ImageProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/cv/ImageProcessor.kt). To adjust threshold limits, background erasure formulas, or color parameters, edit `binarizeDocument()` or `removeShadows()`.

### C. Modifying ML Models & Digit Recognition
1. **Model File**: If you train a better digit recognizer model, overwrite the file at `app/src/main/assets/ml/digit_recognizer.tflite`.
2. **Input/Output Dimensions**: If your model changes target size (e.g., from `28x28` grayscale to `64x64`), edit [DigitRecognizer.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/DigitRecognizer.kt).
3. **OCR Boundaries**: To change bounding box extraction logic or ML Kit text configurations, update [OcrProcessor.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ml/OcrProcessor.kt).

### D. Changing UI Screens & State Flow
* **Composables**: Screens are split cleanly by domain under the `ui/` package. For example, to adjust drawing tools, modify [PageViewScreen.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/src/main/java/com/markflow/app/ui/pageview/PageViewScreen.kt).
* **ViewModels**: State is fetched and exposed reactively from repositories. ViewModels use Hilt injection (`@HiltViewModel`). If you add parameters to a ViewModel constructor, verify that a corresponding binding is present in `AppModule.kt`.
* **Routing**: To add screens or alter parameters passed between them, modify [NavGraph.kt](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/ui/navigation/NavGraph.kt).

### E. Updating Dependencies or Gradle Version
* **Dependency Versions**: MarkFlow uses a version catalog. To update libraries (e.g., CameraX, Compose, Room), modify the version catalog file: [libs.versions.toml](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/gradle/libs.versions.toml).
* **Adding Libraries**: Put version declarations under `[versions]`, dependency coordinates under `[libraries]`, and link them in the target module's `build.gradle.kts` using `libs.name.alias`.

### F. Modifying ProGuard / R8 Rules
Minification issues occur when R8 attempts to strip or optimize external dependencies that reference missing classes.
* If you add a library and the release build fails, inspect the R8 compiler logs.
* Open [proguard-rules.pro](file:///c:/Users/JAISINGH/.gemini/antigravity-ide/scratch/MarkFlow/app/proguard-rules.pro) and append `-dontwarn <missing-class-package>.**` or `-keep class <class-name> { *; }` to handle the failure cleanly.

---

## 🤖 CI/CD Release Automation

The project includes a robust build automation script in `.github/workflows/build-release.yml`.

Every time you push a commit or merge a branch into **`main`**, GitHub Actions:
1. **Pre-build check**: Validates dependencies, resources, and room schemas.
2. **Compiles Code**: Launches Gradle to assemble both debug and minified release APKs.
3. **Triggers Release**: Generates a GitHub Release tagged `v1.0.<actions_run_number>`, drafts the release notes containing all features, and uploads both APKs.
