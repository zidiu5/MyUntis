package com.myuntis.app.ui.screens.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.CacheKeys
import com.myuntis.app.data.repository.DataCache
import com.myuntis.app.data.repository.GradesRepository
import com.myuntis.app.data.repository.SchoolYearOption
import com.myuntis.app.data.repository.SessionManager
import com.myuntis.app.data.repository.availableYearsResult
import com.myuntis.app.domain.model.Absence
import com.myuntis.app.domain.model.AbsenceStatistics
import com.myuntis.app.domain.model.SubjectWithGrades
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt

// ── Simulation helpers ────────────────────────────────────────
data class SimGrade(val label: String, val value: Float)

sealed interface GoalResult {
    data object Reached : GoalResult                                  // even the worst grade is enough
    data class Impossible(val needed: Float) : GoalResult
    data class Needed(val needed: Float, val label: String) : GoalResult
}

private const val MIN_GRADE = 4f

/** "8" → 8, "8+" → 8.25, "8-" → 7.75, "7/8" → 7.5, "7,5" → 7.5. null = invalid. */
fun parseGrade(text: String): Float? {
    val s = text.trim().replace(',', '.')
    if (s.isEmpty()) return null
    val v: Float? = when {
        '/' in s -> {
            val p = s.split('/')
            val a = p.getOrNull(0)?.toFloatOrNull()
            val b = p.getOrNull(1)?.toFloatOrNull()
            if (p.size == 2 && a != null && b != null) (a + b) / 2f else null
        }
        s.endsWith("+") -> s.dropLast(1).toFloatOrNull()?.plus(0.25f)
        s.endsWith("-") -> s.dropLast(1).toFloatOrNull()?.minus(0.25f)
        else -> s.toFloatOrNull()
    }
    return v?.takeIf { it in 1f..10f }
}

/** 8.25 → "8+", 8.5 → "8/9", 8.75 → "9-" */
fun gradeLabel(v: Float): String {
    val q = (v * 4f).roundToInt()
    val whole = q / 4
    return when (q % 4) {
        0 -> "$whole"
        1 -> "$whole+"
        2 -> "$whole/${whole + 1}"
        else -> "${whole + 1}-"
    }
}

private fun ceilToQuarter(x: Float): Float = (ceil(x * 4f - 0.0001f) / 4f).coerceAtMost(10f)

/** What the next [count] grades must average so that the subject average reaches [target]. */
fun goalFor(current: List<Float>, target: Float, count: Int): GoalResult {
    val needed = (target * (current.size + count) - current.sum()) / count
    return when {
        needed > 10.001f    -> GoalResult.Impossible(needed)
        needed <= MIN_GRADE -> GoalResult.Reached
        else                -> GoalResult.Needed(needed, gradeLabel(ceilToQuarter(needed)))
    }
}

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
    val absencesError: String? = null,
    val schoolYears: List<SchoolYearOption> = emptyList(),
    val selectedYearId: Int? = null,      // null = current school year
    val sim: Map<Int, List<SimGrade>> = emptyMap(),     // lessonId → test grades
) {
    val hasSim: Boolean get() = sim.values.any { it.isNotEmpty() }
    val simCount: Int get() = sim.values.sumOf { it.size }

    /** real grades + test grades of one subject */
    fun gradesOf(subject: SubjectWithGrades): List<Float> =
        subject.entries.map { it.markValue } + sim[subject.lessonId].orEmpty().map { it.value }

    fun averageOf(subject: SubjectWithGrades): Float =
        gradesOf(subject).let { if (it.isEmpty()) 0f else it.average().toFloat() }

    // Average of ALL single grades (Realwert)
    val realwert: Float
        get() {
            val all = subjects.flatMap { gradesOf(it) }
            return if (all.isEmpty()) 0f else all.average().toFloat()
        }

    // Average of subject averages (Zeugnis)
    val zeugnisAvg: Float
        get() {
            val avgs = subjects.map { gradesOf(it) }.filter { it.isNotEmpty() }.map { it.average() }
            return if (avgs.isEmpty()) 0f else avgs.average().toFloat()
        }

    // Grade distribution for donut chart (buckets for each integer 1-10)
    val gradeCounts: List<Pair<String, Int>>
        get() {
            val all = subjects.flatMap { gradesOf(it) }
            return (4..10).map { bucket ->          // 4-10 only
                bucket.toString() to all.count { Math.round(it) == bucket }
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GradesViewModel @Inject constructor(
    private val repository: GradesRepository,
    private val sessionManager: SessionManager,
    private val cache: DataCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()

    init {
        // Whatever is cached shows up at once (no spinner, no flicker)
        cache.get<List<SubjectWithGrades>>(CacheKeys.grades(null))?.let { list ->
            _uiState.update { it.copy(isLoadingGrades = false, subjects = list) }
        }
        cache.get<List<Absence>>(CacheKeys.ABSENCES)?.let { applyAbsences(it) }
        cache.get<List<SchoolYearOption>>(CacheKeys.YEARS)?.let { years ->
            _uiState.update { it.copy(schoolYears = years) }
        }

        // Follow the cache: the preloader and refreshes update this screen by themselves
        viewModelScope.launch {
            _uiState.map { it.selectedYearId }.distinctUntilChanged()
                .flatMapLatest { id -> cache.flow<List<SubjectWithGrades>>(CacheKeys.grades(id)) }
                .collect { list ->
                    if (list != null) _uiState.update {
                        it.copy(isLoadingGrades = false, subjects = list, gradesError = null)
                    }
                }
        }
        viewModelScope.launch {
            cache.flow<List<Absence>>(CacheKeys.ABSENCES).collect { if (it != null) applyAbsences(it) }
        }
        viewModelScope.launch {
            cache.flow<List<SchoolYearOption>>(CacheKeys.YEARS).collect { years ->
                if (years != null) _uiState.update { it.copy(schoolYears = years) }
            }
        }

        // Nothing cached yet? Load now (does nothing if the preloader is already on it)
        loadGradesInternal(force = false)
        loadAbsencesInternal(force = false)
    }

    fun setTab(tab: GradesTab)     = _uiState.update { it.copy(activeTab = tab) }
    fun setStatsTab(tab: StatsTab) = _uiState.update { it.copy(activeStatsTab = tab) }

    fun selectYear(yearId: Int?) {
        val cached = cache.get<List<SubjectWithGrades>>(CacheKeys.grades(yearId))
        _uiState.update {
            it.copy(
                selectedYearId = yearId,
                subjects = cached ?: emptyList(),
                isLoadingGrades = cached == null,
                gradesError = null,
                sim = emptyMap() // clear simulations on year switch
            )
        }
        loadGradesInternal(force = false)
    }

    // Refresh buttons: force a new download
    fun loadGrades()   = loadGradesInternal(force = true)
    fun loadAbsences() = loadAbsencesInternal(force = true)

    private fun loadGradesInternal(force: Boolean) {
        viewModelScope.launch {
            val yearId  = _uiState.value.selectedYearId
            val key     = CacheKeys.grades(yearId)
            val hadData = cache.get<List<SubjectWithGrades>>(key) != null
            if (!hadData) _uiState.update { it.copy(isLoadingGrades = true, gradesError = null) }

            val r = cache.load(key, CacheKeys.MAX_AGE_MS, force) {
                sessionManager.ensureFresh()
                repository.getSubjectGrades(yearId)
            }
            if (r is NetworkResult.Error) {
                // keep showing cached data; only show the error if there is nothing to show
                _uiState.update { it.copy(isLoadingGrades = false, gradesError = if (hadData) null else r.message) }
            } else {
                _uiState.update { it.copy(isLoadingGrades = false) }
            }

            if (cache.get<List<SchoolYearOption>>(CacheKeys.YEARS) == null) {
                cache.load(CacheKeys.YEARS, CacheKeys.MAX_AGE_MS, false) { repository.availableYearsResult() }
            }
        }
    }

    private fun loadAbsencesInternal(force: Boolean) {
        viewModelScope.launch {
            val hadData = cache.get<List<Absence>>(CacheKeys.ABSENCES) != null
            if (!hadData) _uiState.update { it.copy(isLoadingAbsences = true, absencesError = null) }

            val r = cache.load(CacheKeys.ABSENCES, CacheKeys.MAX_AGE_MS, force) {
                sessionManager.ensureFresh()
                repository.getAbsences()
            }
            if (r is NetworkResult.Error) {
                _uiState.update { it.copy(isLoadingAbsences = false, absencesError = if (hadData) null else r.message) }
            } else {
                _uiState.update { it.copy(isLoadingAbsences = false) }
            }
        }
    }

    private fun applyAbsences(list: List<Absence>) {
        val stats = AbsenceStatistics(
            totalHours     = list.size,
            excusedHours   = list.count { it.isExcused },
            unexcusedHours = list.count { !it.isExcused }
        )
        _uiState.update {
            it.copy(isLoadingAbsences = false, absences = list, absenceStats = stats, absencesError = null)
        }
    }

    // ── Simulation ────────────────────────────────────────────────
    /** false = the text is not a valid grade */
    fun addSimGrade(lessonId: Int, text: String): Boolean {
        val value = parseGrade(text) ?: return false
        val grade = SimGrade(label = text.trim().replace(',', '.'), value = value)
        _uiState.update { it.copy(sim = it.sim + (lessonId to (it.sim[lessonId].orEmpty() + grade))) }
        return true
    }

    fun removeSimGrade(lessonId: Int, index: Int) {
        _uiState.update { s ->
            val list = s.sim[lessonId].orEmpty().filterIndexed { i, _ -> i != index }
            s.copy(sim = if (list.isEmpty()) s.sim - lessonId else s.sim + (lessonId to list))
        }
    }

    fun resetSim() = _uiState.update { it.copy(sim = emptyMap()) }
}