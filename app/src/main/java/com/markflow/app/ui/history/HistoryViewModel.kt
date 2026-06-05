package com.markflow.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.CopyRepository
import com.markflow.app.domain.model.Copy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val copyRepository: CopyRepository
) : ViewModel() {

    val allCopies: StateFlow<List<Copy>> = copyRepository.getAllCopies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
