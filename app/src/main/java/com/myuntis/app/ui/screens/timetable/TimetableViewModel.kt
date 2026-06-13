package com.myuntis.app.ui.screens.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.TimetableRepository
import com.myuntis.app.domain.model.Lesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class TimetableViewMode(val label: String) {
    DAY("Tag"), WEEK("Woche"), MONTH("Monat")
}

data class TimetableUiState(
    val viewMode: TimetableViewMode     = TimetableViewMode.WEEK,
    val selectedDate: LocalDate         = LocalDate.now(),
    val displayedMonth: YearMonth       = YearMonth.now(),
    val lessonsByDate: Map<LocalDate, List<Lesson>> = emptyMap(),
    val isLoading: Boolean              = true,
    val isRefreshing: Boolean           = false,
    val errorMessage: String?           = null,
    val subjectColors: Map<String, String> = emptyMap()
) {
    val weekDates: List<LocalDate>
        get() {
            val monday = selectedDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return (0L..5L).map { monday.plusDays(it) }
        }

    val selectedDayLessons: List<Lesson>
        get() = lessonsByDate[selectedDate] ?: emptyList()
}

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val dataStore: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        // Observe subject color preferences
        viewModelScope.launch {
            dataStore.subjectColors.collect { colors ->
                _uiState.update { it.copy(subjectColors = colors) }
            }
        }

        // Strategy:
        //  1. Load persisted cache → instant display (no network, no spinner)
        //  2. Prefetch ±weeks in background → merges silently when done
        viewModelScope.launch {
            loadCacheInstant()
            prefetchInBackground()
        }
    }

    // ── Init helpers ──────────────────────────────────────────

    /** Show cached data immediately – zero network latency. */
    private suspend fun loadCacheInstant() {
        val cached = repository.getCachedLessons()
        if (cached.isNotEmpty()) {
            _uiState.update {
                it.copy(lessonsByDate = cached, isLoading = false)
            }
        }
        // If cache is empty, keep isLoading=true until prefetch finishes
    }

    /**
     * Prefetch today ±weeks in one network call.
     * Shows spinner only when there is no cached data at all.
     * Updates the state silently when data arrives.
     */
    private fun prefetchInBackground() {
        viewModelScope.launch {
            when (val r = repository.prefetchMultipleWeeks()) {
                is NetworkResult.Success -> {
                    val byDate = r.data.groupBy { it.date }
                    _uiState.update { current ->
                        current.copy(
                            isLoading     = false,
                            isRefreshing  = false,
                            errorMessage  = null,
                            // Merge: fresh data overwrites cached days, rest preserved
                            lessonsByDate = current.lessonsByDate + byDate
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { current ->
                        current.copy(
                            isLoading    = false,
                            isRefreshing = false,
                            // Show error only if we have nothing to show
                            errorMessage = if (current.lessonsByDate.isEmpty())
                                r.message else null
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────

    fun setViewMode(mode: TimetableViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        if (mode == TimetableViewMode.MONTH) {
            ensureMonthLoaded(_uiState.value.displayedMonth)
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        ensureWeekLoaded(date)
    }

    fun navigatePrevious() {
        val s = _uiState.value
        when (s.viewMode) {
            TimetableViewMode.DAY -> {
                val d = s.selectedDate.minusDays(1)
                _uiState.update { it.copy(selectedDate = d) }
                ensureWeekLoaded(d)
            }
            TimetableViewMode.WEEK -> {
                val d = s.selectedDate.minusWeeks(1)
                _uiState.update { it.copy(selectedDate = d) }
                ensureWeekLoaded(d)
            }
            TimetableViewMode.MONTH -> {
                val m = s.displayedMonth.minusMonths(1)
                _uiState.update { it.copy(displayedMonth = m) }
                ensureMonthLoaded(m)
            }
        }
    }

    fun navigateNext() {
        val s = _uiState.value
        when (s.viewMode) {
            TimetableViewMode.DAY -> {
                val d = s.selectedDate.plusDays(1)
                _uiState.update { it.copy(selectedDate = d) }
                ensureWeekLoaded(d)
            }
            TimetableViewMode.WEEK -> {
                val d = s.selectedDate.plusWeeks(1)
                _uiState.update { it.copy(selectedDate = d) }
                ensureWeekLoaded(d)
            }
            TimetableViewMode.MONTH -> {
                val m = s.displayedMonth.plusMonths(1)
                _uiState.update { it.copy(displayedMonth = m) }
                ensureMonthLoaded(m)
            }
        }
    }

    fun goToToday() {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(selectedDate = today, displayedMonth = YearMonth.now())
        }
        ensureWeekLoaded(today)
    }

    /** Manual pull-to-refresh: re-prefetch everything and show spinner. */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        prefetchInBackground()
    }

    // ── Stale-while-revalidate helpers ────────────────────────

    /**
     * If the week for [date] is already in state (from cache or previous load),
     * show it instantly and refresh silently.
     * If it's not in state, show a loading spinner.
     */
    private fun ensureWeekLoaded(date: LocalDate) {
        viewModelScope.launch {
            val monday   = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val saturday = monday.plusDays(5)
            val weekDates = (0L..5L).map { monday.plusDays(it) }

            // Check if ANY day of this week is already loaded
            val alreadyInState = weekDates.any {
                _uiState.value.lessonsByDate.containsKey(it)
            }

            // Show spinner only for uncached weeks
            if (!alreadyInState) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            when (val r = repository.getLessonsForRange(monday, saturday)) {
                is NetworkResult.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        lessonsByDate = state.lessonsByDate + r.data.groupBy { it.date }
                    )
                }
                is NetworkResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        // Suppress error when we already have something to show
                        errorMessage = if (!alreadyInState) r.message else null
                    )
                }
                else -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun ensureMonthLoaded(month: YearMonth) {
        viewModelScope.launch {
            val start = month.atDay(1)
            val end   = month.atEndOfMonth()
            val alreadyInState = _uiState.value.lessonsByDate.keys
                .any { it.month == month.month && it.year == month.year }

            if (!alreadyInState) {
                _uiState.update { it.copy(isLoading = true) }
            }

            when (val r = repository.getLessonsForRange(start, end)) {
                is NetworkResult.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        lessonsByDate = state.lessonsByDate + r.data.groupBy { it.date }
                    )
                }
                is NetworkResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        errorMessage = if (!alreadyInState) r.message else null
                    )
                }
                else -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}