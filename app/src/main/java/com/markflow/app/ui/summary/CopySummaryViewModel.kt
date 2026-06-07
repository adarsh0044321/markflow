package com.markflow.app.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.*
import com.markflow.app.util.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ReportState {
    object Idle : ReportState
    object Loading : ReportState
    data class Success(val file: File) : ReportState
    data class Error(val message: String) : ReportState
}

@HiltViewModel
class CopySummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository,
    private val reportGenerator: ReportGenerator
) : ViewModel() {

    private val copyId: Long = savedStateHandle.get<Long>("copyId") ?: 0L

    val copy: StateFlow<Copy?> = copyRepository.observeCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pages: StateFlow<List<Page>> = copyRepository.getPagesByCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val marks: StateFlow<List<DetectedMark>> = copyRepository.getMarksByCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val issues: StateFlow<List<Issue>> = copyRepository.getIssuesByCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningTotal: StateFlow<Double> = scanRepository.getRunningTotal(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val questionMarks: StateFlow<List<QuestionMark>> = copyRepository.getQuestionMarksForCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditTrail: StateFlow<List<AuditLog>> = copyRepository.getAuditTrailForCopy(copyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    fun generateReport(
        studentName: String,
        rollNumber: String,
        registrationNumber: String,
        className: String,
        section: String
    ) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            try {
                copyRepository.updateCopyStudentDetails(
                    copyId = copyId,
                    name = studentName.takeIf { it.isNotBlank() },
                    roll = rollNumber.takeIf { it.isNotBlank() },
                    reg = registrationNumber.takeIf { it.isNotBlank() },
                    className = className.takeIf { it.isNotBlank() },
                    sec = section.takeIf { it.isNotBlank() }
                )
                val file = reportGenerator.generateCopyReport(copyId)
                _reportState.value = ReportState.Success(file)
            } catch (e: Exception) {
                e.printStackTrace()
                _reportState.value = ReportState.Error(e.message ?: "Failed to generate report")
            }
        }
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }

    fun resolveIssue(issueId: Long) {
        viewModelScope.launch { copyRepository.resolveIssue(issueId) }
    }

    fun adjustTotal(newTotal: Double, bonus: Double, penalty: Double, reason: String) {
        viewModelScope.launch {
            scanRepository.adjustCopyTotal(copyId, newTotal, bonus, penalty, reason)
        }
    }

    fun setVerified(isVerified: Boolean) {
        viewModelScope.launch {
            scanRepository.setCopyVerified(copyId, isVerified)
        }
    }
}
