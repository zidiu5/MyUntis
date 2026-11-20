package com.myuntis.app.ui.screens.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.model.CalendarEntryDetail
import com.myuntis.app.data.repository.CacheKeys
import com.myuntis.app.data.repository.DataCache
import com.myuntis.app.data.repository.SessionManager
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

sealed interface LessonDetailState {
    data object Loading : LessonDetailState
    data object Failed : LessonDetailState
    data class Ready(val entry: CalendarEntryDetail) : LessonDetailState
}

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
    val subjectColors: Map<String, String> = emptyMap(),
    val loadedWeeks: Set<LocalDate> = emptySet(),     // weeks we already asked the server for
    val selectedLesson: Lesson? = null,               // lesson shown in the popup
    val detail: LessonDetailState = LessonDetailState.Loading
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
    private val dataStore: DataStoreManager,
    private val sessionManager: SessionManager,
    private val cache: DataCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    private fun mondayOf(d: LocalDate): LocalDate =
        d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    init {
        // Observe subject color preferences
        viewModelScope.launch {
            dataStore.subjectColors.collect { colors ->
                _uiState.update { it.copy(subjectColors = colors) }
            }
        }

        // The preloader (or this screen) fills the cache → merge it into the UI
        viewModelScope.launch {
            cache.flow<List<Lesson>>(CacheKeys.TIMETABLE).collect { lessons ->
                if (lessons != null) {
                    val todayMonday = mondayOf(LocalDate.now())
                    val window = (-3L..5L).map { todayMonday.plusWeeks(it) }   // = range of prefetchMultipleWeeks()
                    _uiState.update { st ->
                        st.copy(
                            isLoading = false, isRefreshing = false, errorMessage = null,
                            lessonsByDate = st.lessonsByDate + lessons.groupBy { it.date },
                            loadedWeeks = st.loadedWeeks + window + lessons.map { mondayOf(it.date) }
                        )
                    }
                }
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
                it.copy(
                    lessonsByDate = cached, isLoading = false,
                    loadedWeeks = it.loadedWeeks + cached.keys.map { d -> mondayOf(d) }
                )
            }
        }
        // If cache is empty, keep isLoading=true until prefetch finishes
    }

    /**
     * Prefetch today ±weeks in one network call.
     * Shows spinner only when there is no cached data at all.
     * Updates the state silently when data arrives.
     */
    private fun prefetchInBackground(force: Boolean = false) {
        viewModelScope.launch {
            val r = cache.load(CacheKeys.TIMETABLE, CacheKeys.TIMETABLE_MAX_AGE_MS, force) {
                sessionManager.ensureFresh()
                repository.prefetchMultipleWeeks()
            }
            if (r is NetworkResult.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false, isRefreshing = false,
                        errorMessage = if (it.lessonsByDate.isEmpty()) r.message else null
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
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
        prefetchInBackground(force = true)
        val monday = mondayOf(_uiState.value.selectedDate)
        _uiState.update { it.copy(loadedWeeks = it.loadedWeeks - monday) }
        ensureWeekLoaded(monday)
    }

    // ── Pager reports a finished swipe ────────────────────────
    fun onDayPageSelected(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        _uiState.update { it.copy(selectedDate = date) }
        ensureWeekLoaded(date)
    }

    fun onWeekPageSelected(monday: LocalDate) {
        val current = _uiState.value.selectedDate
        if (mondayOf(current) == monday) return
        val newDate = monday.plusDays((current.dayOfWeek.value - 1).toLong())   // same weekday
        _uiState.update { it.copy(selectedDate = newDate) }
        ensureWeekLoaded(newDate)
    }

    fun onMonthPageSelected(month: YearMonth) {
        if (month == _uiState.value.displayedMonth) return
        _uiState.update { it.copy(displayedMonth = month) }
        ensureMonthLoaded(month)
    }

    // ── Lesson popup ──────────────────────────────────────────
    fun openLesson(lesson: Lesson) {
        _uiState.update { it.copy(selectedLesson = lesson, detail = LessonDetailState.Loading) }
        viewModelScope.launch {
            val key = "lesson:${lesson.date}|${lesson.startTime}|${lesson.endTime}|${lesson.subject.shortName}"
            val r = cache.load(key, CacheKeys.MAX_AGE_MS, false) {
                sessionManager.ensureFresh()
                repository.getLessonDetail(lesson)
            }
            if (_uiState.value.selectedLesson != lesson) return@launch   // closed or another one opened
            _uiState.update {
                it.copy(detail = if (r is NetworkResult.Success) LessonDetailState.Ready(r.data)
                else LessonDetailState.Failed)
            }
        }
    }

    fun closeLesson() = _uiState.update { it.copy(selectedLesson = null) }

    // ── Stale-while-revalidate helpers ────────────────────────

    private fun ensureWeekLoaded(date: LocalDate) {
        val monday = mondayOf(date)
        if (monday in _uiState.value.loadedWeeks) return      // prefetch / cache / earlier visit
        viewModelScope.launch {
            when (val r = repository.getLessonsForRange(monday, monday.plusDays(5))) {
                is NetworkResult.Success -> _uiState.update { st ->
                    st.copy(
                        isLoading = false, isRefreshing = false,
                        lessonsByDate = st.lessonsByDate + r.data.groupBy { it.date },
                        loadedWeeks = st.loadedWeeks + monday
                    )
                }
                is NetworkResult.Error -> _uiState.update {
                    // mark as loaded so the page shows "no lessons" + the error banner, not a spinner forever
                    it.copy(isLoading = false, isRefreshing = false,
                        errorMessage = r.message, loadedWeeks = it.loadedWeeks + monday)
                }
                else -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun ensureMonthLoaded(month: YearMonth) {
        val mondays = generateSequence(mondayOf(month.atDay(1))) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
        if (mondays.all { it in _uiState.value.loadedWeeks }) return
        viewModelScope.launch {
            when (val r = repository.getLessonsForRange(month.atDay(1), month.atEndOfMonth())) {
                is NetworkResult.Success -> _uiState.update { st ->
                    st.copy(
                        isLoading = false, isRefreshing = false,
                        lessonsByDate = st.lessonsByDate + r.data.groupBy { it.date },
                        loadedWeeks = st.loadedWeeks + mondays
                    )
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false,
                        errorMessage = r.message, loadedWeeks = it.loadedWeeks + mondays)
                }
                else -> _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}