package com.myuntis.app.data.repository

import com.myuntis.app.data.network.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Loads everything in the background so the tabs open with data already there. */
@Singleton
class Preloader @Inject constructor(
    private val cache: DataCache,
    private val sessionManager: SessionManager,
    private val timetable: TimetableRepository,
    private val grades: GradesRepository,
    private val homework: HomeworkRepository,
    private val exams: ExamRepository,
    private val classReg: ClassRegRepository
) {
    suspend fun preload(force: Boolean) {
        sessionManager.ensureFresh()
        coroutineScope {
            val age = CacheKeys.MAX_AGE_MS
            safeLaunch { cache.load(CacheKeys.TIMETABLE, CacheKeys.TIMETABLE_MAX_AGE_MS, force) { timetable.prefetchMultipleWeeks() } }
            safeLaunch { cache.load(CacheKeys.HOMEWORK, age, force) { homework.getHomework() } }
            safeLaunch { cache.load(CacheKeys.EXAMS, age, force) { exams.getExams() } }
            safeLaunch { cache.load(CacheKeys.CLASSREG, age, force) { classReg.getEntries() } }
            safeLaunch { cache.load(CacheKeys.ABSENCES, age, force) { grades.getAbsences() } }
            safeLaunch { preloadGrades(force) }
        }
    }

    private suspend fun preloadGrades(force: Boolean) {
        // current year first, so the Noten tab is ready as early as possible
        cache.load(CacheKeys.grades(null), CacheKeys.MAX_AGE_MS, force) { grades.getSubjectGrades(null) }

        val years = when (val r = cache.load(CacheKeys.YEARS, CacheKeys.MAX_AGE_MS, force) { grades.availableYearsResult() }) {
            is NetworkResult.Success -> r.data
            else -> return
        }
        // index 0 = current year (done above); older years rarely change → long max age
        for (y in years.drop(1)) {
            cache.load(CacheKeys.grades(y.id), CacheKeys.OLD_YEAR_MAX_AGE_MS, false) { grades.getSubjectGrades(y.id) }
        }
    }

    // One failing job must not cancel the others
    private fun CoroutineScope.safeLaunch(block: suspend () -> Unit): Job = launch {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
}