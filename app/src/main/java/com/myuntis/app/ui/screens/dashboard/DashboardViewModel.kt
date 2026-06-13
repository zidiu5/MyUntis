package com.myuntis.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.TimetableRepository
import com.myuntis.app.domain.model.Lesson
import com.myuntis.app.domain.model.isCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// =============================================================
// DASHBOARD UI STATE
// =============================================================
data class DashboardUiState(
    val isLoading: Boolean = true,
    val greeting: String = "Hallo",
    val userName: String = "",
    val className: String = "",
    val schoolName: String = "",
    val currentDateFormatted: String = "",
    val todayLessons: List<Lesson> = emptyList(),
    val nextLesson: Lesson? = null,
    val nextLessonLabel: String = "",   // "In 5 Minuten", "Läuft gerade", etc.
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val dataStore: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    // Load all dashboard data
    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Load user profile from local storage (fast, no network)
            val profile = dataStore.userProfile.first()
            val credentials = dataStore.loginCredentials.first()

            _uiState.update {
                it.copy(
                    greeting = computeGreeting(),
                    userName = profile.fullName.ifBlank { "Schüler" },
                    className = profile.className,
                    schoolName = credentials.school,
                    currentDateFormatted = formatCurrentDate()
                )
            }

            // Fetch today's timetable from API
            when (val result = timetableRepository.getTodayLessons()) {
                is NetworkResult.Success -> {
                    val todayLessons = result.data
                    val (next, label) = computeNextLesson(todayLessons)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            todayLessons = todayLessons,
                            nextLesson = next,
                            nextLessonLabel = label
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
                is NetworkResult.Loading -> { /* handled by isLoading */ }
            }
        }
    }

    // Pull-to-refresh
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = timetableRepository.getTodayLessons()) {
                is NetworkResult.Success -> {
                    val (next, label) = computeNextLesson(result.data)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            todayLessons = result.data,
                            nextLesson = next,
                            nextLessonLabel = label
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(isRefreshing = false, errorMessage = result.message)
                    }
                }
                else -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ----- HELPERS -----

    private fun computeGreeting(): String {
        return when (LocalTime.now().hour) {
            in 0..5   -> "Gute Nacht"
            in 6..11  -> "Guten Morgen"
            in 12..13 -> "Mahlzeit"
            in 14..17 -> "Guten Nachmittag"
            in 18..22 -> "Guten Abend"
            else      -> "Gute Nacht"
        }
    }

    private fun formatCurrentDate(): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy",
            java.util.Locale.GERMAN)
        return LocalDate.now().format(formatter)
            .replaceFirstChar { it.uppercaseChar() }
    }

    // Finds the next or currently running lesson and generates a label
    private fun computeNextLesson(lessons: List<Lesson>): Pair<Lesson?, String> {
        val now = LocalTime.now()

        // 1. Is there a lesson currently running?
        val running = lessons.firstOrNull {
            !it.isCancelled && it.startTime <= now && it.endTime > now
        }
        if (running != null) {
            val minutesLeft = ChronoUnit.MINUTES.between(now, running.endTime)
            return Pair(running, "Läuft gerade · noch ${minutesLeft} Min.")
        }

        // 2. Find the next upcoming lesson today
        val next = lessons
            .filter { !it.isCancelled && it.startTime > now }
            .minByOrNull { it.startTime }

        if (next == null) return Pair(null, "")

        val minutesUntil = ChronoUnit.MINUTES.between(now, next.startTime)
        val label = when {
            minutesUntil <= 0  -> "Gleich"
            minutesUntil < 60  -> "In ${minutesUntil} Minuten"
            else -> {
                val h = minutesUntil / 60
                val m = minutesUntil % 60
                if (m == 0L) "In ${h}h" else "In ${h}h ${m}min"
            }
        }
        return Pair(next, label)
    }
}