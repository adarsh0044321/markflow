package com.markflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.Copy
import com.markflow.app.domain.model.DashboardStats
import com.markflow.app.domain.model.ScanSession
import com.markflow.app.ui.summary.ReportState
import com.markflow.app.util.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository,
    private val reportGenerator: ReportGenerator
) : ViewModel() {

    val recentCopies: StateFlow<List<Copy>> = copyRepository
        .getRecentCopies(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = copyRepository
        .getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val folders: StateFlow<List<ScanSession>> = copyRepository
        .getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessionCopies: StateFlow<List<Copy>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else copyRepository.getCopiesBySession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCreatingScan = MutableStateFlow(false)
    val isCreatingScan: StateFlow<Boolean> = _isCreatingScan.asStateFlow()

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    fun selectSession(sessionId: Long?) {
        _selectedSessionId.value = sessionId
    }

    /**
     * Create a new folder (session) without launching a scan immediately.
     */
    fun createFolder(name: String, maxMarks: Double, passThreshold: Double) {
        viewModelScope.launch {
            try {
                scanRepository.createSession(name, maxMarks, passThreshold)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Create a new scanning session folder and copy, then return IDs for navigation.
     */
    fun startNewScanWithDetails(name: String, maxMarks: Double, passThreshold: Double, onReady: (sessionId: Long, copyId: Long) -> Unit) {
        viewModelScope.launch {
            _isCreatingScan.value = true
            try {
                val sessionId = scanRepository.createSession(name, maxMarks, passThreshold)
                val copyId = scanRepository.createCopy(sessionId)
                onReady(sessionId, copyId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCreatingScan.value = false
            }
        }
    }

    /**
     * Start a scan (add new copy) inside an existing folder/session.
     */
    fun startScanInFolder(sessionId: Long, onReady: (sessionId: Long, copyId: Long) -> Unit) {
        viewModelScope.launch {
            _isCreatingScan.value = true
            try {
                val copyId = scanRepository.createCopy(sessionId)
                onReady(sessionId, copyId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCreatingScan.value = false
            }
        }
    }

    /**
     * Delete a copy and trigger stats recalculation for the session.
     */
    fun deleteCopy(copyId: Long) {
        viewModelScope.launch {
            copyRepository.deleteCopy(copyId)
            val currentSessionId = _selectedSessionId.value
            if (currentSessionId != null) {
                copyRepository.recalculateSessionStats(currentSessionId)
            }
        }
    }

    /**
     * Update student details on a copy and recalculate session stats.
     */
    fun updateCopyDetails(copyId: Long, name: String?, rollNo: String?) {
        viewModelScope.launch {
            copyRepository.updateCopyStudentDetails(
                copyId = copyId,
                name = name,
                roll = rollNo,
                reg = null,
                className = null,
                sec = null
            )
            val currentSessionId = _selectedSessionId.value
            if (currentSessionId != null) {
                copyRepository.recalculateSessionStats(currentSessionId)
            }
        }
    }

    /**
     * Generate class batch PDF report.
     */
    fun generateFolderReport(sessionId: Long) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            try {
                val file = reportGenerator.generateBatchPdfReport(sessionId)
                _reportState.value = ReportState.Success(file)
            } catch (e: Exception) {
                e.printStackTrace()
                _reportState.value = ReportState.Error(e.message ?: "Failed to generate class report")
            }
        }
    }

    /**
     * Generate master report of all classes.
     */
    fun generateAllClassesReport() {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            try {
                val file = reportGenerator.generateAllClassesReport()
                _reportState.value = ReportState.Success(file)
            } catch (e: Exception) {
                e.printStackTrace()
                _reportState.value = ReportState.Error(e.message ?: "Failed to generate master report")
            }
        }
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }

    /**
     * Fallback quick scan if needed.
     */
    fun startNewScan(onReady: (sessionId: Long, copyId: Long) -> Unit) {
        viewModelScope.launch {
            _isCreatingScan.value = true
            try {
                val sessionId = scanRepository.createSession("Quick Scan")
                val copyId = scanRepository.createCopy(sessionId)
                onReady(sessionId, copyId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCreatingScan.value = false
            }
        }
    }
}
