package com.myuntis.app.ui.screens.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.GradesRepository
import com.myuntis.app.domain.model.Absence
import com.myuntis.app.domain.model.AbsenceStatistics
import com.myuntis.app.domain.model.SubjectWithGrades
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GradesTab(val label: String) { GRADES("Register"), ABSENCES("Fehlstunden") }
enum class StatsTab(val label: String) { REGISTER("Register"), ANALYSE("Analyse") }

data class GradesUiState(
    val activeTab: GradesTab = GradesTab.GRADES,
    val activeStatsTab: StatsTab = StatsTab.REGISTER,
    val isLoadingGrades: Boolean = true,
    val subjects: List<SubjectWithGrades> = emptyList(),
    val gradesError: String? = null,
    val isLoadingAbsences: Boolean = true,
    val absences: List<Absence> = emptyList(),
    val absenceStats: AbsenceStatistics = AbsenceStatistics(0, 0, 0),
    val absencesError: String? = null
) {
    // Average of ALL individual grades (Realwert)
    val realwert: Float
        get() {
            val all = subjects.flatMap { it.entries }.map { it.markValue }
            return if (all.isEmpty()) 0f else all.average().toFloat()
        }

    // Average of subject averages (Zeugnis)
    val zeugnisAvg: Float
        get() {
            val avgs = subjects.filter { it.entries.isNotEmpty() }.map { it.average }
            return if (avgs.isEmpty()) 0f else avgs.average().toFloat()
        }

    // Grade distribution for donut chart (buckets for each integer 1-10)
    val gradeCounts: List<Pair<String, Int>>
        get() {
            val all = subjects.flatMap { it.entries }
            return (4..10).map { bucket ->          // 4-10 only
                val count = all.count { Math.round(it.markValue) == bucket }
                bucket.toString() to count
            }
        }
}

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val repository: GradesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()

    init { loadGrades(); loadAbsences() }

    fun setTab(tab: GradesTab)          = _uiState.update { it.copy(activeTab = tab) }
    fun setStatsTab(tab: StatsTab)      = _uiState.update { it.copy(activeStatsTab = tab) }

    fun loadGrades() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGrades = true, gradesError = null) }
            when (val r = repository.getSubjectGrades()) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(isLoadingGrades = false, subjects = r.data)
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoadingGrades = false, gradesError = r.message)
                }
                else -> _uiState.update { it.copy(isLoadingGrades = false) }
            }
        }
    }

    fun loadAbsences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAbsences = true, absencesError = null) }
            when (val r = repository.getAbsences()) {
                is NetworkResult.Success -> {
                    val stats = AbsenceStatistics(
                        totalHours     = r.data.size,
                        excusedHours   = r.data.count { it.isExcused },
                        unexcusedHours = r.data.count { !it.isExcused }
                    )
                    _uiState.update {
                        it.copy(isLoadingAbsences = false, absences = r.data, absenceStats = stats)
                    }
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoadingAbsences = false, absencesError = r.message)
                }
                else -> _uiState.update { it.copy(isLoadingAbsences = false) }
            }
        }
    }
}