package com.markflow.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.domain.model.Copy
import com.markflow.app.domain.model.DashboardStats
import com.markflow.app.domain.model.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val copyRepository: CopyRepository
) : ViewModel() {

    data class CohortGradeDistribution(
        val gradeA: Int = 0, // >= 85
        val gradeB: Int = 0, // 70 - 84
        val gradeC: Int = 0, // 50 - 69
        val gradeD: Int = 0, // 33 - 49
        val gradeF: Int = 0  // < 33
    )

    val sessions: StateFlow<List<ScanSession>> = copyRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    fun selectSession(sessionId: Long?) {
        _selectedSessionId.value = sessionId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val cohortCopies: StateFlow<List<Copy>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id == null) {
                copyRepository.getAllCopies()
            } else {
                copyRepository.getCopiesBySession(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cohortStats: StateFlow<DashboardStats> = combine(cohortCopies, sessions, selectedSessionId) { copies, sessionList, sessionId ->
        if (copies.isEmpty()) return@combine DashboardStats()

        val totalCopies = copies.size
        val totalPages = copies.sumOf { it.pageCount }
        val totalMarks = copies.sumOf { it.markCount }
        val marksList = copies.map { it.calculatedTotal }.sorted()
        val average = marksList.average()
        val highest = marksList.lastOrNull() ?: 0.0
        val lowest = marksList.firstOrNull() ?: 0.0

        val selectedSession = if (sessionId != null) sessionList.find { it.id == sessionId } else null
        val maxMarks = selectedSession?.maxMarks ?: 100.0
        val passThreshold = selectedSession?.passThreshold ?: 33.0
        val passScore = maxMarks * (passThreshold / 100.0)

        val passCount = copies.count { it.calculatedTotal >= passScore }
        val passPercentage = if (totalCopies > 0) (passCount.toDouble() / totalCopies) * 100.0 else 0.0

        val median = if (totalCopies == 0) 0.0 else if (totalCopies % 2 == 0) {
            (marksList[totalCopies / 2 - 1] + marksList[totalCopies / 2]) / 2.0
        } else {
            marksList[totalCopies / 2]
        }

        val variance = if (totalCopies > 0) marksList.map { (it - average) * (it - average) }.sum() / totalCopies else 0.0
        val stdDev = sqrt(variance)

        DashboardStats(
            totalCopiesScanned = totalCopies,
            totalPagesScanned = totalPages,
            totalMarksDetected = totalMarks,
            averageMarks = average,
            highestMarks = highest,
            lowestMarks = lowest,
            passPercentage = passPercentage,
            medianMarks = median,
            standardDeviation = stdDev
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val cohortGrades: StateFlow<CohortGradeDistribution> = combine(cohortCopies, sessions, selectedSessionId) { copies, sessionList, sessionId ->
        var a = 0; var b = 0; var c = 0; var d = 0; var f = 0
        val selectedSession = if (sessionId != null) sessionList.find { it.id == sessionId } else null
        val maxMarks = selectedSession?.maxMarks ?: 100.0

        copies.forEach { copy ->
            val pct = if (maxMarks > 0) (copy.calculatedTotal / maxMarks) * 100.0 else 0.0
            when {
                pct >= 85.0 -> a++
                pct >= 70.0 -> b++
                pct >= 50.0 -> c++
                pct >= 33.0 -> d++
                else -> f++
            }
        }
        CohortGradeDistribution(a, b, c, d, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CohortGradeDistribution())
}
