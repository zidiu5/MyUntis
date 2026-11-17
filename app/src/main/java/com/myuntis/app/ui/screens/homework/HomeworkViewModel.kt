package com.myuntis.app.ui.screens.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.CacheKeys
import com.myuntis.app.data.repository.DataCache
import com.myuntis.app.data.repository.HomeworkRepository
import com.myuntis.app.data.repository.SessionManager
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
    private val repository: HomeworkRepository,
    private val sessionManager: SessionManager,
    private val cache: DataCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState.asStateFlow()

    init {
        cache.get<List<Homework>>(CacheKeys.HOMEWORK)?.let { list ->
            _uiState.update { it.copy(isLoading = false, allHomework = list) }
        }
        viewModelScope.launch {
            cache.flow<List<Homework>>(CacheKeys.HOMEWORK).collect { list ->
                if (list != null) _uiState.update {
                    it.copy(isLoading = false, allHomework = list, errorMessage = null)
                }
            }
        }
        loadInternal(force = false)
    }

    fun loadHomework() = loadInternal(force = true)

    private fun loadInternal(force: Boolean) {
        viewModelScope.launch {
            val hadData = cache.get<List<Homework>>(CacheKeys.HOMEWORK) != null
            if (!hadData) _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val r = cache.load(CacheKeys.HOMEWORK, CacheKeys.MAX_AGE_MS, force) {
                sessionManager.ensureFresh()
                repository.getHomework()
            }
            if (r is NetworkResult.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = if (hadData) null else r.message) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: HomeworkFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun toggleComplete(homework: Homework) {
        val newState = !homework.isCompleted
        val updated = _uiState.value.allHomework.map {
            if (it.id == homework.id) it.copy(isCompleted = newState) else it
        }
        cache.put(CacheKeys.HOMEWORK, updated)    // the screen follows via the cache flow
        viewModelScope.launch { repository.toggleComplete(homework.id, newState) }
    }
}