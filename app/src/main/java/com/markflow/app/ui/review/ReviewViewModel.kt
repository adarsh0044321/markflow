package com.markflow.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val copyId: Long = savedStateHandle.get<Long>("copyId") ?: 0L

    val allMarks: StateFlow<List<DetectedMark>> = copyRepository
        .getMarksByCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewMarks: StateFlow<List<DetectedMark>> = copyRepository
        .getMarksNeedingReview(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowConfidenceMarks: StateFlow<List<DetectedMark>> = copyRepository
        .getLowConfidenceMarks(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pages: StateFlow<List<Page>> = copyRepository
        .getPagesByCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningTotal: StateFlow<Double> = scanRepository
        .getRunningTotal(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun approveMark(markId: Long) {
        viewModelScope.launch { scanRepository.approveMark(markId) }
    }

    fun editMark(markId: Long, newValue: Double, displayValue: String) {
        viewModelScope.launch { scanRepository.editMark(markId, newValue, displayValue) }
    }

    fun rejectMark(markId: Long) {
        viewModelScope.launch { scanRepository.rejectMark(markId) }
    }

    fun ignoreMark(markId: Long) {
        viewModelScope.launch { scanRepository.ignoreMark(markId) }
    }
}
