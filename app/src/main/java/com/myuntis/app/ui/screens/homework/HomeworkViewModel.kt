package com.myuntis.app.ui.screens.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.HomeworkRepository
import com.myuntis.app.domain.model.Homework
import com.myuntis.app.domain.model.isDueToday
import com.myuntis.app.domain.model.isDueTomorrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// Filter options for homework list
enum class HomeworkFilter(val label: String) {
    TODAY("Heute"),
    TOMORROW("Morgen"),
    WEEK("Woche"),
    ALL("Alle")
}

data class HomeworkUiState(
    val isLoading: Boolean = true,
    val allHomework: List<Homework> = emptyList(),
    val activeFilter: HomeworkFilter = HomeworkFilter.ALL,
    val errorMessage: String? = null
) {
    // Filtered + sorted homework list shown in the UI
    val filteredHomework: List<Homework>
        get() {
            val today    = LocalDate.now()
            val endOfWeek = today.plusDays(7)
            return allHomework
                .filter { hw ->
                    when (activeFilter) {
                        HomeworkFilter.TODAY    -> hw.isDueToday
                        HomeworkFilter.TOMORROW -> hw.isDueTomorrow
                        HomeworkFilter.WEEK     -> hw.dueDate in today..endOfWeek
                        HomeworkFilter.ALL      -> true
                    }
                }
                .sortedWith(
                    compareBy(
                        { it.isCompleted },   // Incomplete first
                        { it.dueDate }        // Then by due date
                    )
                )
        }

    // Count open (not completed) homework
    val openCount: Int get() = allHomework.count { !it.isCompleted }

    // Group filtered homework by due date for section headers
    val groupedByDate: Map<LocalDate, List<Homework>>
        get() = filteredHomework.groupBy { it.dueDate }
}

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val repository: HomeworkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState.asStateFlow()

    init { loadHomework() }

    fun loadHomework() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getHomework()) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(isLoading = false, allHomework = result.data)
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: HomeworkFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun toggleComplete(homework: Homework) {
        val newState = !homework.isCompleted

        // Optimistic UI update (instant feedback, no waiting for API)
        _uiState.update { state ->
            state.copy(
                allHomework = state.allHomework.map {
                    if (it.id == homework.id) it.copy(isCompleted = newState) else it
                }
            )
        }

        // Persist to local storage
        viewModelScope.launch {
            repository.toggleComplete(homework.id, newState)
        }
    }
}