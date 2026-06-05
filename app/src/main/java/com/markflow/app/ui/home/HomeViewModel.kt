package com.markflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.data.repository.ScanRepository
import com.markflow.app.domain.model.Copy
import com.markflow.app.domain.model.DashboardStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val copyRepository: CopyRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    val recentCopies: StateFlow<List<Copy>> = copyRepository
        .getRecentCopies(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = copyRepository
        .getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    private val _isCreatingScan = MutableStateFlow(false)
    val isCreatingScan: StateFlow<Boolean> = _isCreatingScan.asStateFlow()

    /**
     * Create a new scanning session and copy, then return IDs for navigation.
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
