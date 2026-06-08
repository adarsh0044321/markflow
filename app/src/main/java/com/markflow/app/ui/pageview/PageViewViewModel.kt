package com.markflow.app.ui.pageview

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.cv.ImageProcessor
import com.markflow.app.ml.OcrProcessor
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.Page
import com.markflow.app.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PageViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository,
    private val imageProcessor: ImageProcessor,
    private val ocrProcessor: OcrProcessor
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

    fun calculateDiagnostics(imagePath: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val file = java.io.File(imagePath)
                if (!file.exists()) return@launch
                val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@launch
                
                // 1. Pixel analysis
                val pixelAnalysis = imageProcessor.analyzePagePixels(bitmap)
                
                // 2. OCR details
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

    fun addManualMark(value: Double, displayValue: String, x: Int, y: Int) {
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

    fun saveDrawing(imagePath: String, strokes: List<List<androidx.compose.ui.geometry.Offset>>, composeWidth: Float, composeHeight: Float) {
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
                
                strokes.forEach { stroke ->
                    if (stroke.size > 1) {
                        val path = android.graphics.Path()
                        path.moveTo(stroke[0].x * scaleX, stroke[0].y * scaleY)
                        for (i in 1 until stroke.size) {
                            path.lineTo(stroke[i].x * scaleX, stroke[i].y * scaleY)
                        }
                        canvas.drawPath(path, paint)
                    } else if (stroke.size == 1) {
                        canvas.drawPoint(stroke[0].x * scaleX, stroke[0].y * scaleY, paint)
                    }
                }
                
                val out = java.io.FileOutputStream(file)
                mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.close()

                // Regenerate thumbnail if it exists to keep list views updated
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
                
                // Trigger state refresh
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
