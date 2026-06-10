package com.markflow.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.cv.PageChangeDetector
import com.markflow.app.cv.ImageProcessor
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.*
import com.markflow.app.data.repository.SettingsRepository
import com.markflow.app.ml.DigitRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val copyRepository: CopyRepository,
    private val pageChangeDetector: PageChangeDetector,
    private val settingsRepository: SettingsRepository,
    private val imageProcessor: ImageProcessor,
    private val digitRecognizer: DigitRecognizer
) : ViewModel() {

    private var isAutoCaptureEnabled = true
    private val activeProcessingJobs = ConcurrentHashMap<String, Job>()
    private var lastCaptureTimestamp = 0L

    private val _isExportMode = MutableStateFlow(false)
    val isExportMode: StateFlow<Boolean> = _isExportMode.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.autoCaptureFlow.collect {
                isAutoCaptureEnabled = it
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            digitRecognizer.initialize()
        }
    }

    val answerSheetOrientation: StateFlow<String> = settingsRepository.answerSheetOrientationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "portrait"
        )

    fun toggleOrientation() {
        viewModelScope.launch {
            val newOrientation = if (answerSheetOrientation.value == "portrait") "landscape" else "portrait"
            settingsRepository.setAnswerSheetOrientation(newOrientation)
            addStatusMessage("Switched to $newOrientation mode")
        }
    }

    private var latestFrame: Bitmap? = null

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    enum class FilterMode {
        ORIGINAL,
        ENHANCED,
        EXAM_MODE
    }

    data class StagingPage(
        val id: String = UUID.randomUUID().toString(),
        val rawImagePath: String,
        val corners: ImageProcessor.CornerPoints,
        val rotationDegrees: Float = 0f,
        val filterMode: FilterMode = FilterMode.ENHANCED,
        val quality: ImageProcessor.ScanQualityResult,
        val isProcessed: Boolean = false,
        val isProcessing: Boolean = false,
        val pageProcessingResult: ScanRepository.PageProcessingResult? = null,
        val error: String? = null
    )

    data class DuplicateDialogData(
        val pageId: Long,
        val duplicateOfPageId: Long,
        val confidence: Double
    )

    data class MissingPageDialogData(
        val pageId: Long,
        val expectedPageNumber: Int,
        val detectedPageNumber: Int
    )

    data class CropState(
        val pageIndex: Int,
        val rawBitmap: Bitmap,
        val corners: ImageProcessor.CornerPoints
    )

    private val _capturedPages = MutableStateFlow<List<StagingPage>>(emptyList())
    val capturedPages: StateFlow<List<StagingPage>> = _capturedPages.asStateFlow()

    private val _liveCorners = MutableStateFlow<ImageProcessor.CornerPoints?>(null)
    val liveCorners: StateFlow<ImageProcessor.CornerPoints?> = _liveCorners.asStateFlow()

    private val _liveCornersSize = MutableStateFlow<android.util.Size?>(null)
    val liveCornersSize: StateFlow<android.util.Size?> = _liveCornersSize.asStateFlow()

    private val _duplicateDialog = MutableStateFlow<DuplicateDialogData?>(null)
    val duplicateDialog: StateFlow<DuplicateDialogData?> = _duplicateDialog.asStateFlow()

    private val _missingPageDialog = MutableStateFlow<MissingPageDialogData?>(null)
    val missingPageDialog: StateFlow<MissingPageDialogData?> = _missingPageDialog.asStateFlow()

    private val _cropState = MutableStateFlow<CropState?>(null)
    val cropState: StateFlow<CropState?> = _cropState.asStateFlow()

    private val _activeReviewPageIndex = MutableStateFlow<Int?>(null)
    val activeReviewPageIndex: StateFlow<Int?> = _activeReviewPageIndex.asStateFlow()

    private val _showFinalizeProgress = MutableStateFlow(false)
    val showFinalizeProgress: StateFlow<Boolean> = _showFinalizeProgress.asStateFlow()

    private val _finalizeProgressMessage = MutableStateFlow("")
    val finalizeProgressMessage: StateFlow<String> = _finalizeProgressMessage.asStateFlow()

    private val _detectedMarks = MutableStateFlow<List<DetectedMark>>(emptyList())
    val detectedMarks: StateFlow<List<DetectedMark>> = _detectedMarks.asStateFlow()

    private val _markFeed = MutableStateFlow<List<MarkFeedItem>>(emptyList())
    val markFeed: StateFlow<List<MarkFeedItem>> = _markFeed.asStateFlow()

    private val _runningTotal = MutableStateFlow(0.0)
    val runningTotal: StateFlow<Double> = _runningTotal.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _statusMessages = MutableStateFlow<List<String>>(emptyList())
    val statusMessages: StateFlow<List<String>> = _statusMessages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _pageDetectionState = MutableStateFlow(PageChangeDetector.PageState.STABLE)
    val pageDetectionState: StateFlow<PageChangeDetector.PageState> = _pageDetectionState.asStateFlow()

    private val _lastScanQuality = MutableStateFlow<String?>(null)
    val lastScanQuality: StateFlow<String?> = _lastScanQuality.asStateFlow()

    private val _tiltAngle = MutableStateFlow(0f)
    val tiltAngle: StateFlow<Float> = _tiltAngle.asStateFlow()

    fun updateTiltAngle(angle: Float) {
        _tiltAngle.value = angle
    }

    private var currentCopyId: Long = 0
    private var currentSessionId: Long = 0

    /**
     * Initialize the scan session.
     */
    fun initialize(sessionId: Long, copyId: Long) {
        currentSessionId = sessionId
        currentCopyId = copyId
        _isExportMode.value = false
        scanRepository.resetPageDetection()
        _lastScanQuality.value = null
        _capturedPages.value = emptyList()
        _activeReviewPageIndex.value = null
        _pageCount.value = 0
        _markFeed.value = emptyList()
        _liveCorners.value = null
        _liveCornersSize.value = null
        _duplicateDialog.value = null
        _missingPageDialog.value = null
        _cropState.value = null

        _scanState.value = ScanState(
            isScanning = true,
            currentCopyId = copyId
        )

        // Observe running total from database
        viewModelScope.launch {
            scanRepository.getRunningTotal(copyId).collect { total ->
                _runningTotal.value = total
            }
        }

        // Observe marks from database
        viewModelScope.launch {
            scanRepository.getMarksByCopy(copyId).collect { marks ->
                _detectedMarks.value = marks
            }
        }

        addStatusMessage("Scanner ready")
    }

    /**
     * Process a camera frame for page change detection.
     */
    fun processFrame(frame: Bitmap) {
        if (_isExportMode.value) {
            frame.recycle()
            return
        }
        try {
            // Cache the latest frame for manual capture
            synchronized(this) {
                latestFrame?.recycle()
                val config = frame.config ?: Bitmap.Config.ARGB_8888
                latestFrame = frame.copy(config, true)
            }

            // Check capture debounce (800ms)
            val now = System.currentTimeMillis()
            if (now - lastCaptureTimestamp < 800) return

            // Update live corners flow on the analysis thread (realtime)
            val corners = imageProcessor.detectPaperCorners(frame, answerSheetOrientation.value == "landscape")
            _liveCorners.value = corners
            _liveCornersSize.value = android.util.Size(frame.width, frame.height)

            val result = pageChangeDetector.processFrame(frame)

            when (result.state) {
                PageChangeDetector.PageState.CHANGING -> {
                    _pageDetectionState.value = result.state
                    _scanState.update { it.copy(
                        isPageStable = false,
                        isCapturing = false,
                        statusMessage = "Page turning..."
                    )}
                }
                PageChangeDetector.PageState.STABILIZING -> {
                    _pageDetectionState.value = result.state
                    _scanState.update { it.copy(
                        isPageStable = false,
                        statusMessage = "Stabilizing..."
                    )}
                }
                PageChangeDetector.PageState.READY_TO_CAPTURE -> {
                    if (isAutoCaptureEnabled && _tiltAngle.value <= 10.0f) {
                        if (corners.isHighConfidence) {
                            _pageDetectionState.value = result.state
                            // Make a copy of the frame for the background worker
                            val frameCopy = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
                            captureAndProcess(frameCopy)
                        } else {
                            // Keep stabilizing/waiting for alignment
                            _pageDetectionState.value = PageChangeDetector.PageState.STABILIZING
                            _scanState.update { it.copy(
                                isPageStable = false,
                                statusMessage = "Align sheet..."
                            )}
                        }
                    } else {
                        _pageDetectionState.value = result.state
                        if (_tiltAngle.value > 10.0f && isAutoCaptureEnabled) {
                            _scanState.update { it.copy(
                                isPageStable = false,
                                statusMessage = "Hold phone flat..."
                            )}
                        }
                    }
                }
                PageChangeDetector.PageState.STABLE -> {
                    _pageDetectionState.value = result.state
                    _scanState.update { it.copy(
                        isPageStable = true,
                        statusMessage = "Stable"
                    )}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            frame.recycle()
        }
    }

    fun manualCapture() {
        val frameToProcess = synchronized(this) {
            val frame = latestFrame
            if (frame != null && !frame.isRecycled) {
                val config = frame.config ?: Bitmap.Config.ARGB_8888
                frame.copy(config, true)
            } else {
                null
            }
        }
        if (frameToProcess != null) {
            captureAndProcess(frameToProcess)
        } else {
            addStatusMessage("Camera not ready")
        }
    }

    /**
     * Capture and process a page image. Saves raw captured frame to temp file,
     * detects corners, calculates quality, and pushes to review queue.
     */
    private fun captureAndProcess(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < 800) {
            // Debounce duplicate trigger within 800ms
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        lastCaptureTimestamp = now

        val pageId = UUID.randomUUID().toString()
        val rawPath = File(context.cacheDir, "raw_temp_$pageId.jpg").absolutePath

        // Create placeholder StagingPage
        val placeholder = StagingPage(
            id = pageId,
            rawImagePath = rawPath,
            corners = ImageProcessor.CornerPoints(
                PointF(0f, 0f), PointF(0f, 0f), PointF(0f, 0f), PointF(0f, 0f), false
            ),
            quality = ImageProcessor.ScanQualityResult(0, "Fair", 0.0, 0.0, 0, 0),
            isProcessing = true,
            isProcessed = false
        )

        // Add to queue immediately to update UI count and thumbnails
        _capturedPages.update { it + placeholder }
        val newPageCount = _capturedPages.value.size
        _pageCount.value = newPageCount

        addStatusMessage("Page $newPageCount captured")

        // Update page change detector reference immediately
        pageChangeDetector.onPageCaptured(bitmap)

        // Start background processing pipeline
        startPipelineForPage(pageId, bitmap, rawPath)
    }

    private fun startPipelineForPage(pageId: String, bitmap: Bitmap, rawPath: String) {
        // Cancel any existing job for this pageId
        activeProcessingJobs.remove(pageId)?.cancel()

        val job = viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Save raw frame to file in background
                withContext(Dispatchers.IO) {
                    FileOutputStream(File(rawPath)).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }

                // 2. Detect corners and run quality analysis on the bitmap
                val corners = imageProcessor.detectPaperCorners(bitmap, answerSheetOrientation.value == "landscape")
                val quality = imageProcessor.calculateScanQuality(bitmap)

                // Recycle the bitmap since we've saved it and run calculations
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }

                // 3. Update the StagingPage corners and quality in the list
                _capturedPages.update { list ->
                    list.map { p ->
                        if (p.id == pageId) p.copy(
                            corners = corners,
                            quality = quality
                        ) else p
                    }
                }

                // 4. Perspective warp using the detected corners
                val rawBitmap = BitmapFactory.decodeFile(rawPath)
                    ?: throw java.io.IOException("Failed to decode raw bitmap")

                var processed = imageProcessor.cropAndWarpPerspective(rawBitmap, corners, answerSheetOrientation.value == "landscape")

                // 5. Commit to repository/DB (runs Perspect Warp, OCR, DB insertion)
                val result = scanRepository.processCapture(processed, currentCopyId, corners = null, isPreProcessed = true)

                processed.recycle()
                rawBitmap.recycle()

                // 6. Update staging page state to completed
                _capturedPages.update { list ->
                    list.map { p ->
                        if (p.id == pageId) p.copy(
                            isProcessing = false,
                            isProcessed = true,
                            pageProcessingResult = result
                        ) else p
                    }
                }

                // 7. Trigger duplicate and sequence alert alerts if detected
                if (result.isDuplicate) {
                    _duplicateDialog.value = DuplicateDialogData(
                        pageId = result.pageId,
                        duplicateOfPageId = result.duplicateOfPageId ?: 0L,
                        confidence = result.duplicateConfidence
                    )
                    _duplicateDialog.first { it == null }
                }

                if (result.isSequenceSkipped) {
                    _missingPageDialog.value = MissingPageDialogData(
                        pageId = result.pageId,
                        expectedPageNumber = result.expectedPageNumber,
                        detectedPageNumber = result.detectedPageNumber ?: result.expectedPageNumber
                    )
                    _missingPageDialog.first { it == null }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                _capturedPages.update { list ->
                    list.map { p ->
                        if (p.id == pageId) p.copy(
                            isProcessing = false,
                            isProcessed = false,
                            error = e.message
                        ) else p
                    }
                }
            }
        }

        activeProcessingJobs[pageId] = job
        job.invokeOnCompletion {
            activeProcessingJobs.remove(pageId, job)
        }
    }

    private fun startBackgroundProcessingForPage(pageId: String) {
        val existingJob = activeProcessingJobs.remove(pageId)
        existingJob?.cancel()

        val job = viewModelScope.launch(Dispatchers.Default) {
            // 1. Get the page and mark it as processing
            var page = _capturedPages.value.find { it.id == pageId } ?: return@launch

            // Delete old database page first if it exists (re-processing)
            val oldPageId = page.pageProcessingResult?.pageId
            if (oldPageId != null) {
                scanRepository.deletePage(oldPageId)
            }

            _capturedPages.update { list ->
                list.map { p ->
                    if (p.id == pageId) p.copy(
                        isProcessing = true,
                        isProcessed = false,
                        pageProcessingResult = null,
                        error = null
                    ) else p
                }
            }

            try {
                // Refresh our local reference to page configurations
                page = _capturedPages.value.find { it.id == pageId } ?: return@launch

                // 2. Decode raw temp bitmap
                val rawBitmap = BitmapFactory.decodeFile(page.rawImagePath)
                    ?: throw java.io.IOException("Failed to decode raw bitmap")

                // 3. Perspective warp using staging corners
                var processed = imageProcessor.cropAndWarpPerspective(rawBitmap, page.corners, answerSheetOrientation.value == "landscape")

                // 4. Rotate bitmap if needed
                if (page.rotationDegrees != 0f) {
                    processed = imageProcessor.rotateBitmap(processed, page.rotationDegrees)
                }

                val filtered = when (page.filterMode) {
                    FilterMode.ORIGINAL -> processed
                    FilterMode.ENHANCED -> imageProcessor.enhanceDocumentReadability(processed)
                    FilterMode.EXAM_MODE -> imageProcessor.applyExamModeFilter(processed)
                }

                if (processed != filtered) {
                    processed.recycle()
                }

                // 6. Commit to repository/DB
                val result = scanRepository.processCapture(filtered, currentCopyId, corners = null, isPreProcessed = true)

                // Clean up bitmaps
                filtered.recycle()
                rawBitmap.recycle()

                // 7. Update staging page state to completed
                _capturedPages.update { list ->
                    list.map { p ->
                        if (p.id == pageId) p.copy(
                            isProcessing = false,
                            isProcessed = true,
                            pageProcessingResult = result
                        ) else p
                    }
                }

                // 8. Suspend / trigger alerts if duplicate or sequence skip detected
                if (result.isDuplicate) {
                    _duplicateDialog.value = DuplicateDialogData(
                        pageId = result.pageId,
                        duplicateOfPageId = result.duplicateOfPageId ?: 0L,
                        confidence = result.duplicateConfidence
                    )
                    _duplicateDialog.first { it == null }
                }

                if (result.isSequenceSkipped) {
                    _missingPageDialog.value = MissingPageDialogData(
                        pageId = result.pageId,
                        expectedPageNumber = result.expectedPageNumber,
                        detectedPageNumber = result.detectedPageNumber ?: result.expectedPageNumber
                    )
                    _missingPageDialog.first { it == null }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _capturedPages.update { list ->
                    list.map { p ->
                        if (p.id == pageId) p.copy(
                            isProcessing = false,
                            isProcessed = false,
                            error = e.message
                        ) else p
                    }
                }
            }
        }
        activeProcessingJobs[pageId] = job
        job.invokeOnCompletion {
            activeProcessingJobs.remove(pageId, job)
        }
    }


    private fun saveRawBitmapToTempFile(bitmap: Bitmap): String {
        val file = File(context.cacheDir, "raw_temp_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun rotateStagingPage(index: Int) {
        _capturedPages.update { list ->
            if (index in list.indices) {
                val page = list[index]
                val nextRot = (page.rotationDegrees + 90f) % 360f
                list.mapIndexed { idx, p -> if (idx == index) p.copy(rotationDegrees = nextRot) else p }
            } else list
        }
        val page = _capturedPages.value.getOrNull(index)
        if (page != null) {
            startBackgroundProcessingForPage(page.id)
        }
    }

    fun updateStagingPageCorners(index: Int, corners: ImageProcessor.CornerPoints) {
        _capturedPages.update { list ->
            if (index in list.indices) {
                val page = list[index]
                // Recalculate scan quality for new corners
                val rawBitmap = BitmapFactory.decodeFile(page.rawImagePath)
                val quality = if (rawBitmap != null) {
                    val warped = imageProcessor.cropAndWarpPerspective(rawBitmap, corners, answerSheetOrientation.value == "landscape")
                    val q = imageProcessor.calculateScanQuality(warped)
                    warped.recycle()
                    rawBitmap.recycle()
                    q
                } else {
                    page.quality
                }
                list.mapIndexed { idx, p -> if (idx == index) p.copy(corners = corners, quality = quality) else p }
            } else list
        }
        val page = _capturedPages.value.getOrNull(index)
        if (page != null) {
            startBackgroundProcessingForPage(page.id)
        }
    }

    fun updateStagingPageFilter(index: Int, filter: FilterMode) {
        _capturedPages.update { list ->
            if (index in list.indices) {
                list.mapIndexed { idx, p -> if (idx == index) p.copy(filterMode = filter) else p }
            } else list
        }
        val page = _capturedPages.value.getOrNull(index)
        if (page != null) {
            startBackgroundProcessingForPage(page.id)
        }
    }

    fun deleteStagingPage(index: Int) {
        val list = _capturedPages.value
        if (index in list.indices) {
            val page = list[index]
            // Cancel background processing job if still active
            activeProcessingJobs.remove(page.id)?.cancel()
            try {
                File(page.rawImagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Delete from database if already processed
            val pageId = page.pageProcessingResult?.pageId
            if (pageId != null) {
                viewModelScope.launch {
                    scanRepository.deletePage(pageId)
                }
            }

            _capturedPages.update { current ->
                val nextList = current.filterIndexed { idx, _ -> idx != index }
                _pageCount.value = nextList.size
                _scanState.update { it.copy(
                    currentPageNumber = nextList.size,
                    pagesScanned = nextList.size
                )}
                nextList
            }
            if (_activeReviewPageIndex.value == index) {
                _activeReviewPageIndex.value = if (list.size <= 1) null else minOf(index, list.size - 2)
            }
        }
    }

    fun retakeStagingPage(index: Int) {
        val list = _capturedPages.value
        if (index in list.indices) {
            deleteStagingPage(index)
            _activeReviewPageIndex.value = null
            addStatusMessage("Ready to retake page")
        }
    }

    fun selectReviewPage(index: Int?) {
        _activeReviewPageIndex.value = index
    }

    fun keepDuplicatePage() {
        _duplicateDialog.value = null
    }

    fun mergeDuplicatePage(pageId: Long, duplicateOfPageId: Long) {
        viewModelScope.launch {
            scanRepository.mergeDuplicatePage(pageId, duplicateOfPageId)
            _duplicateDialog.value = null
        }
    }

    fun ignoreDuplicatePage(pageId: Long) {
        viewModelScope.launch {
            scanRepository.deletePage(pageId)
            _duplicateDialog.value = null
        }
    }

    fun continueMissingPage() {
        _missingPageDialog.value = null
    }

    fun scanAgainMissingPage(pageId: Long) {
        viewModelScope.launch {
            scanRepository.deletePage(pageId)
            _missingPageDialog.value = null
        }
    }

    fun startCropAdjustment(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val page = _capturedPages.value.getOrNull(index) ?: return@launch
            val bitmap = BitmapFactory.decodeFile(page.rawImagePath) ?: return@launch
            _cropState.value = CropState(index, bitmap, page.corners)
        }
    }

    fun confirmCrop(corners: ImageProcessor.CornerPoints) {
        val state = _cropState.value ?: return
        updateStagingPageCorners(state.pageIndex, corners)
        _cropState.value = null
        state.rawBitmap.recycle()
    }

    fun discardCrop() {
        val state = _cropState.value ?: return
        deleteStagingPage(state.pageIndex)
        _cropState.value = null
        state.rawBitmap.recycle()
    }

    fun finishScanning(onFinish: (Long, Boolean) -> Unit) {
        finalizeSession(onFinish)
    }

    suspend fun getProcessedPreview(page: StagingPage, targetWidth: Int = 800): Bitmap = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(page.rawImagePath, options)
        
        var scale = 1
        while (options.outWidth / scale / 2 >= targetWidth) {
            scale *= 2
        }
        
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val raw = BitmapFactory.decodeFile(page.rawImagePath, decodeOptions) ?: return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        
        val scaleX = raw.width.toFloat() / options.outWidth
        val scaleY = raw.height.toFloat() / options.outHeight
        val scaledCorners = ImageProcessor.CornerPoints(
            topLeft = android.graphics.PointF(page.corners.topLeft.x * scaleX, page.corners.topLeft.y * scaleY),
            topRight = android.graphics.PointF(page.corners.topRight.x * scaleX, page.corners.topRight.y * scaleY),
            bottomLeft = android.graphics.PointF(page.corners.bottomLeft.x * scaleX, page.corners.bottomLeft.y * scaleY),
            bottomRight = android.graphics.PointF(page.corners.bottomRight.x * scaleX, page.corners.bottomRight.y * scaleY),
            isHighConfidence = page.corners.isHighConfidence
        )
        
        var warped = imageProcessor.cropAndWarpPerspective(raw, scaledCorners, answerSheetOrientation.value == "landscape")
        raw.recycle()
        
        if (page.rotationDegrees != 0f) {
            val rotated = imageProcessor.rotateBitmap(warped, page.rotationDegrees)
            warped.recycle()
            warped = rotated
        }
        
        val filtered = when (page.filterMode) {
            FilterMode.ORIGINAL -> warped
            FilterMode.ENHANCED -> imageProcessor.enhanceDocumentReadability(warped)
            FilterMode.EXAM_MODE -> imageProcessor.applyExamModeFilter(warped)
        }
        
        if (warped != filtered) {
            warped.recycle()
        }
        
        return@withContext filtered
    }

    /**
     * Commits all staging pages in batch to the repository (runs perspective warp, filters,
     * saves to database, executes OCR, duplicate checks, and mark detection).
     */
    fun finalizeSession(onComplete: (Long, Boolean) -> Unit) {
        val pagesToProcess = _capturedPages.value
        if (pagesToProcess.isEmpty()) {
            // No pages scanned, clean up copy and exit
            viewModelScope.launch {
                val copy = copyRepository.getCopy(currentCopyId)
                if (copy != null) {
                    copyRepository.deleteCopy(currentCopyId)
                    copyRepository.recalculateSessionStats(currentSessionId)
                }
                onComplete(currentCopyId, false)
            }
            return
        }

        _isExportMode.value = true
        synchronized(this) {
            latestFrame?.recycle()
            latestFrame = null
        }
        _liveCorners.value = null
        _liveCornersSize.value = null
        _cropState.value = null
        digitRecognizer.close()

        viewModelScope.launch {
            _showFinalizeProgress.value = true
            _isProcessing.value = true

            try {
                val finalCopyId = currentCopyId

                // Wait for all active background processing tasks to finish and ensure all pages have isProcessed == true
                _finalizeProgressMessage.value = "Waiting for background page processing to complete..."

                while (true) {
                    val currentList = _capturedPages.value
                    val unprocessedCount = currentList.count { !it.isProcessed }
                    if (unprocessedCount == 0) {
                        break
                    }
                    _finalizeProgressMessage.value = "Finalizing: $unprocessedCount pages remaining..."
                    kotlinx.coroutines.delay(200)
                }

                // Check if any pages failed with an error
                val failedPages = _capturedPages.value.filter { it.error != null }
                if (failedPages.isNotEmpty()) {
                    throw Exception("Some pages failed to process: " + failedPages.joinToString { it.error ?: "Unknown error" })
                }

                // Delete any remaining temp files in cache
                withContext(Dispatchers.IO) {
                    _capturedPages.value.forEach { page ->
                        try {
                            File(page.rawImagePath).delete()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }

                // 6. Complete copy totaling checks, set status to completed
                scanRepository.finishCopy(finalCopyId)

                _showFinalizeProgress.value = false
                _isProcessing.value = false
                onComplete(finalCopyId, true)
            } catch (e: Exception) {
                e.printStackTrace()
                _showFinalizeProgress.value = false
                _isProcessing.value = false
                addStatusMessage("Finalization failed: ${e.message}")
            }
        }
    }


    /**
     * Start scanning next copy in the same session.
     */
    fun startNextCopy(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            scanRepository.finishCopy(currentCopyId)
            val newCopyId = scanRepository.createCopy(currentSessionId)
            currentCopyId = newCopyId
            scanRepository.resetPageDetection()
            _lastScanQuality.value = null
            _capturedPages.value = emptyList()
            _activeReviewPageIndex.value = null
            _pageCount.value = 0
            _markFeed.value = emptyList()
            _statusMessages.value = emptyList()
            _liveCorners.value = null

            _scanState.value = ScanState(
                isScanning = true,
                currentCopyId = newCopyId
            )
            addStatusMessage("New copy started")
            onReady(newCopyId)
        }
    }

    private fun addStatusMessage(message: String) {
        _statusMessages.update { (it + message).takeLast(5) }
    }

    override fun onCleared() {
        super.onCleared()
        pageChangeDetector.reset()
        // Clean up any remaining temp files in cache
        _capturedPages.value.forEach { page ->
            try {
                File(page.rawImagePath).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
        latestFrame?.recycle()
    }
}
