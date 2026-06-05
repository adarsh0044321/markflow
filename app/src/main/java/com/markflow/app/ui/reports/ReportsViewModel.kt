package com.markflow.app.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.domain.model.ScanSession
import com.markflow.app.ui.summary.ReportState
import com.markflow.app.util.FileUtils
import com.markflow.app.util.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copyRepository: CopyRepository,
    private val reportGenerator: ReportGenerator
) : ViewModel() {

    val sessions: StateFlow<List<ScanSession>> = copyRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reportFiles = MutableStateFlow<List<File>>(emptyList())
    val reportFiles: StateFlow<List<File>> = _reportFiles.asStateFlow()

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    init {
        loadReportFiles()
    }

    fun loadReportFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = FileUtils.getReportsDir(context)
            val files = dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".pdf") || it.name.endsWith(".xlsx")) }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            _reportFiles.value = files
        }
    }

    fun generateBatchPdf(sessionId: Long) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            try {
                val file = reportGenerator.generateBatchPdfReport(sessionId)
                _reportState.value = ReportState.Success(file)
                loadReportFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                _reportState.value = ReportState.Error(e.message ?: "Failed to generate batch PDF report")
            }
        }
    }

    fun generateBatchExcel(sessionId: Long) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            try {
                val file = reportGenerator.generateBatchExcelReport(sessionId)
                _reportState.value = ReportState.Success(file)
                loadReportFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                _reportState.value = ReportState.Error(e.message ?: "Failed to generate batch Excel report")
            }
        }
    }

    fun deleteReportFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    file.delete()
                }
                loadReportFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }
}
