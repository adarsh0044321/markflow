package com.markflow.app.ui.pageview

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.cv.ImageProcessor
import com.markflow.app.ml.OcrProcessor
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.data.repository.SettingsRepository
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.Page
import com.markflow.app.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ToolType {
    NONE, FREE_PEN, TICK, CROSS, PAGE_SEEN, BLANK_PAGE, UNDERLINE, CIRCLE, QUESTION_NUMBER
}

data class AnnotationAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ToolType,
    val points: List<androidx.compose.ui.geometry.Offset>,
    val size: Float = 48f,
    val text: String? = null
)

@HiltViewModel
class PageViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository,
    private val imageProcessor: ImageProcessor,
    private val ocrProcessor: OcrProcessor,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class DiagnosticsState(
        val ocrCharacters: Int,
        val ocrWords: Int,
        val textRegions: Int,
        val inkCoveragePct: Double,
        val redInkPct: Double,
        val edgeDensity: Double,
        val handwritingDensity: Double,
        val blankScore: Double,
        val classificationReason: String
    )

    private val _diagnostics = MutableStateFlow<DiagnosticsState?>(null)
    val diagnostics: StateFlow<DiagnosticsState?> = _diagnostics.asStateFlow()

    val maxQuestionMarks: StateFlow<Double> = settingsRepository.defaultQuestionMarksFlow
        .map { it.toDoubleOrNull() ?: 5.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5.0)

    fun calculateDiagnostics(imagePath: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val file = java.io.File(imagePath)
                if (!file.exists()) return@launch
                val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@launch
                
                val pixelAnalysis = imageProcessor.analyzePagePixels(bitmap)
                
                val ocrText = ocrProcessor.recognizeFullPageText(bitmap)
                val cleanText = ocrText.replace(Regex("\\s"), "")
                val words = ocrText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val regionCount = ocrText.count { it == '\n' } + 1
                
                bitmap.recycle()
                
                _diagnostics.value = DiagnosticsState(
                    ocrCharacters = cleanText.length,
                    ocrWords = words.size,
                    textRegions = regionCount,
                    inkCoveragePct = pixelAnalysis.inkCoveragePct,
                    redInkPct = pixelAnalysis.redInkPct,
                    edgeDensity = pixelAnalysis.edgeDensity,
                    handwritingDensity = pixelAnalysis.handwritingDensity,
                    blankScore = pixelAnalysis.blankScore,
                    classificationReason = pixelAnalysis.classificationReason
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val pageId: Long = savedStateHandle.get<Long>("pageId") ?: 0L

    private val _currentPageId = MutableStateFlow(pageId)
    val currentPageId = _currentPageId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val page: StateFlow<Page?> = _currentPageId
        .flatMapLatest { id -> copyRepository.observePage(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val marks: StateFlow<List<DetectedMark>> = _currentPageId
        .flatMapLatest { id -> copyRepository.getMarksByPage(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val siblingPages: StateFlow<List<Page>> = page
        .filterNotNull()
        .flatMapLatest { p -> copyRepository.getPagesByCopy(p.copyId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagePositionText: StateFlow<String> = combine(page, siblingPages) { p, siblings ->
        if (p == null || siblings.isEmpty()) ""
        else {
            val idx = siblings.indexOfFirst { it.id == p.id }
            if (idx == -1) "" else "Page ${idx + 1} of ${siblings.size}"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val hasPreviousPage: StateFlow<Boolean> = combine(page, siblingPages) { p, siblings ->
        if (p == null || siblings.isEmpty()) false
        else siblings.first().id != p.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasNextPage: StateFlow<Boolean> = combine(page, siblingPages) { p, siblings ->
        if (p == null || siblings.isEmpty()) false
        else siblings.last().id != p.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun navigateToPreviousPage() {
        val currentList = siblingPages.value
        val currentP = page.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentP.id }
        if (currentIndex > 0) {
            _currentPageId.value = currentList[currentIndex - 1].id
        }
    }

    fun navigateToNextPage() {
        val currentList = siblingPages.value
        val currentP = page.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentP.id }
        if (currentIndex >= 0 && currentIndex < currentList.size - 1) {
            _currentPageId.value = currentList[currentIndex + 1].id
        }
    }

    fun deleteCurrentPage(onPageDeleted: () -> Unit) {
        val currentP = page.value ?: return
        val currentList = siblingPages.value
        val currentIndex = currentList.indexOfFirst { it.id == currentP.id }
        
        viewModelScope.launch {
            scanRepository.deletePage(currentP.id)
            val nextList = currentList.filter { it.id != currentP.id }
            if (nextList.isEmpty()) {
                onPageDeleted()
            } else {
                val nextShowIndex = if (currentIndex >= nextList.size) nextList.size - 1 else currentIndex
                _currentPageId.value = nextList[nextShowIndex].id
            }
        }
    }

    fun addManualMark(value: Double, displayValue: String, x: Int, y: Int, isQuestionNumber: Boolean = false) {
        val copyId = page.value?.copyId ?: return
        viewModelScope.launch {
            scanRepository.addManualMark(
                pageId = _currentPageId.value,
                copyId = copyId,
                value = value,
                displayValue = displayValue,
                x = x,
                y = y
            )
            if (isQuestionNumber) {
                // Set the region type of the newly added mark to "question_number"
                val newMarks = copyRepository.getMarksByPage(_currentPageId.value).first()
                val newlyAdded = newMarks.filter { it.isManual && it.displayValue == displayValue }.maxByOrNull { it.createdAt }
                if (newlyAdded != null) {
                    // Update region type and status to ignored
                    scanRepository.ignoreMark(newlyAdded.id)
                }
            }
        }
    }

    fun moveMark(markId: Long, x: Int, y: Int) {
        viewModelScope.launch {
            scanRepository.moveMark(markId, x, y)
        }
    }

    fun deleteMark(markId: Long) {
        viewModelScope.launch {
            scanRepository.deleteMark(markId)
        }
    }

    fun approveMark(markId: Long) {
        viewModelScope.launch {
            scanRepository.approveMark(markId)
        }
    }

    fun saveDrawing(
        imagePath: String,
        annotations: List<AnnotationAction>,
        composeWidth: Float,
        composeHeight: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(imagePath)
                if (!file.exists()) return@launch
                val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@launch
                val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()
                
                val canvas = android.graphics.Canvas(mutableBitmap)
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 8f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                
                val scaleX = mutableBitmap.width.toFloat() / composeWidth
                val scaleY = mutableBitmap.height.toFloat() / composeHeight
                
                annotations.forEach { action ->
                    val pts = action.points
                    if (pts.isEmpty()) return@forEach
                    val s = action.size * scaleX
                    val p0 = pts[0]
                    val x = p0.x * scaleX
                    val y = p0.y * scaleY
                    when (action.type) {
                        ToolType.FREE_PEN -> {
                            if (pts.size > 1) {
                                val path = android.graphics.Path()
                                path.moveTo(pts[0].x * scaleX, pts[0].y * scaleY)
                                for (i in 1 until pts.size) {
                                    path.lineTo(pts[i].x * scaleX, pts[i].y * scaleY)
                                }
                                canvas.drawPath(path, paint)
                            } else if (pts.size == 1) {
                                canvas.drawPoint(x, y, paint)
                            }
                        }
                        ToolType.TICK -> {
                            canvas.drawLine(x - s/2, y, x - s/6, y + s/3, paint)
                            canvas.drawLine(x - s/6, y + s/3, x + s/2, y - s/2, paint)
                        }
                        ToolType.CROSS -> {
                            canvas.drawLine(x - s/2, y - s/2, x + s/2, y + s/2, paint)
                            canvas.drawLine(x - s/2, y + s/2, x + s/2, y - s/2, paint)
                        }
                        ToolType.PAGE_SEEN -> {
                            val dx1 = -s/4
                            canvas.drawLine(x + dx1 - s/3, y, x + dx1 - s/12, y + s/5, paint)
                            canvas.drawLine(x + dx1 - s/12, y + s/5, x + dx1 + s/3, y - s/3, paint)
                            val dx2 = s/4
                            canvas.drawLine(x + dx2 - s/3, y, x + dx2 - s/12, y + s/5, paint)
                            canvas.drawLine(x + dx2 - s/12, y + s/5, x + dx2 + s/3, y - s/3, paint)
                            
                            val stampPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = s * 0.5f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText("SEEN", x - stampPaint.measureText("SEEN")/2f, y + s/2 + stampPaint.textSize/3f, stampPaint)
                        }
                        ToolType.BLANK_PAGE -> {
                            val rectPaint = android.graphics.Paint(paint).apply {
                                style = android.graphics.Paint.Style.STROKE
                            }
                            canvas.drawRect(x - s, y - s/2, x + s, y + s/2, rectPaint)
                            canvas.drawLine(x - s, y - s/2, x + s, y + s/2, paint)
                            
                            val stampPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = s * 0.35f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            canvas.drawText("BLANK", x - stampPaint.measureText("BLANK")/2f, y + stampPaint.textSize/3f, stampPaint)
                        }
                        ToolType.UNDERLINE -> {
                            if (pts.size >= 2) {
                                val p1 = pts[1]
                                canvas.drawLine(x, y, p1.x * scaleX, y, paint)
                            }
                        }
                        ToolType.CIRCLE -> {
                            if (pts.size >= 2) {
                                val p1 = pts[1]
                                val rect = android.graphics.RectF(
                                    minOf(x, p1.x * scaleX),
                                    minOf(y, p1.y * scaleY),
                                    maxOf(x, p1.x * scaleX),
                                    maxOf(y, p1.y * scaleY)
                                )
                                canvas.drawOval(rect, paint)
                            }
                        }
                        ToolType.QUESTION_NUMBER -> {
                            val text = action.text ?: "Q"
                            val stampPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = s * 0.6f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            val textWidth = stampPaint.measureText(text)
                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                style = android.graphics.Paint.Style.FILL
                            }
                            canvas.drawRect(x - textWidth/2f - 6f, y - stampPaint.textSize/2f - 6f, x + textWidth/2f + 6f, y + stampPaint.textSize/2f + 6f, bgPaint)
                            canvas.drawRect(x - textWidth/2f - 6f, y - stampPaint.textSize/2f - 6f, x + textWidth/2f + 6f, y + stampPaint.textSize/2f + 6f, paint)
                            canvas.drawText(text, x - textWidth/2f, y + stampPaint.textSize/3f, stampPaint)
                        }
                        ToolType.NONE -> {}
                    }
                }
                
                val out = java.io.FileOutputStream(file)
                mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.close()

                page.value?.thumbnailPath?.let { thumbPath ->
                    try {
                        val thumbFile = java.io.File(thumbPath)
                        val thumbnail = BitmapUtils.createThumbnail(mutableBitmap)
                        val thumbOut = java.io.FileOutputStream(thumbFile)
                        thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, thumbOut)
                        thumbOut.close()
                        thumbnail.recycle()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }

                mutableBitmap.recycle()
                
                val currentId = _currentPageId.value
                _currentPageId.value = currentId
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun verifyAndSaveCopy(onDone: () -> Unit) {
        val copyId = page.value?.copyId ?: return
        viewModelScope.launch {
            scanRepository.setCopyVerified(copyId, true)
            onDone()
        }
    }
}
