package com.myuntis.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.mapper.toLessons
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.domain.model.*
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

// Simple serialization container – no LocalDate/LocalTime in Gson
private data class CachedLesson(
    val id: Int, val date: String, val start: String, val end: String,
    val ss: String, val sl: String, val si: Int,
    val te: List<String>, val ro: List<String>,
    val lt: String, val lc: String
)

private fun Lesson.toCached() = CachedLesson(
    id = id,
    date  = date.toString(),
    start = startTime.toString().take(5),
    end   = endTime.toString().take(5),
    ss    = subject.shortName,
    sl    = subject.longName,
    si    = subject.id,
    te    = teachers, ro = rooms,
    lt    = lessonType.name, lc = code.name
)

private fun CachedLesson.toDomain(): Lesson? = try {
    Lesson(
        id         = id,
        date       = LocalDate.parse(date),
        startTime  = LocalTime.parse(start),
        endTime    = LocalTime.parse(end),
        subject    = Subject(id = si, shortName = ss, longName = sl),
        teachers   = te, rooms = ro,
        lessonType = runCatching { LessonType.valueOf(lt) }.getOrDefault(LessonType.REGULAR),
        code       = runCatching { LessonCode.valueOf(lc) }.getOrDefault(LessonCode.NONE)
    )
} catch (_: Exception) { null }

@Singleton
class TimetableRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager,
    private val authRepository: AuthRepository
) {

    // ── Public API ────────────────────────────────────────────

    suspend fun getTodayLessons() =
        fetchLessons(LocalDate.now(), LocalDate.now())

    suspend fun getLessonsForRange(startDate: LocalDate, endDate: LocalDate) =
        fetchLessons(startDate, endDate)

    // Loads today ±4 weeks in a single network call.
    // Called at login and on manual refresh for instant week-navigation.
    suspend fun prefetchMultipleWeeks(
        center: LocalDate = LocalDate.now(),
        weeksBefore: Int  = 3,
        weeksAhead: Int   = 5
    ): NetworkResult<List<Lesson>> {
        val monday = center.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start  = monday.minusWeeks(weeksBefore.toLong())
        val end    = monday.plusWeeks(weeksAhead.toLong()).plusDays(5)
        return doFetch(start, end)
    }

    // Reads the persisted cache from DataStore.
    // Called on ViewModel init for instant (offline) display.
    suspend fun getCachedLessons(): Map<LocalDate, List<Lesson>> {
        return try {
            val raw = dataStore.loadTimetableCacheRaw()
                ?: return emptyMap()
            val type = object : TypeToken<List<CachedLesson>>() {}.type
            val items: List<CachedLesson> = Gson().fromJson(raw, type)
            items.mapNotNull { it.toDomain() }
                .groupBy { it.date }
        } catch (_: Exception) { emptyMap() }
    }

    // ── Core fetch ────────────────────────────────────────────

    private suspend fun fetchLessons(
        startDate: LocalDate,
        endDate: LocalDate
    ): NetworkResult<List<Lesson>> {
        return try {
            val result = doFetch(startDate, endDate)

            // Auto-relogin on session expiry
            if (result is NetworkResult.Error &&
                (result.message.contains("401") ||
                        result.message.contains("403") ||
                        result.message.contains("Session", ignoreCase = true))) {
                if (authRepository.refreshSession()) {
                    return doFetch(startDate, endDate)
                }
            }
            result
        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Server nicht erreichbar.")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler")
        }
    }

    private suspend fun doFetch(
        startDate: LocalDate,
        endDate: LocalDate
    ): NetworkResult<List<Lesson>> {
        val credentials = dataStore.loginCredentials.first()
        val profile     = dataStore.userProfile.first()
        val sessionId   = dataStore.sessionId.first()
            ?: return NetworkResult.Error("Keine aktive Session.")

        val server = credentials.server
        val cookie = sessionId.toSessionCookie()

        val url = UntisApiHelper.buildRestTimetableUrl(
            server    = server,
            studentId = profile.personId,
            startDate = startDate,
            endDate   = endDate
        )

        val bearer = refreshBearer(server, cookie)
            ?: return NetworkResult.Error("Token-Fehler. Bitte neu anmelden.")

        val response = apiService.getTimetableRestBearer(url, "Bearer $bearer")

        if (!response.isSuccessful) {
            return NetworkResult.Error("HTTP ${response.code()}: ${response.message()}")
        }

        val lessons = response.body()?.toLessons()
            ?.sortedWith(compareBy({ it.date }, { it.startTime }))
            ?: emptyList()

        // Persist to DataStore (merged, pruned to ±6 weeks)
        saveToCache(lessons)

        // Save known subject short names for Settings
        if (lessons.isNotEmpty()) {
            val realSubjects = lessons
                .map { it.subject.shortName }
                .filter { it.isNotBlank() && it != "-" && it != "EVT" }
                .distinct()
            val hasEvents = lessons.any { it.subject.shortName == "EVT" }
            dataStore.addKnownSubjects(
                if (hasEvents) realSubjects + "EVT" else realSubjects
            )
        }

        return NetworkResult.Success(lessons)
    }

    // ── Cache helpers ─────────────────────────────────────────

    // Merges new lessons into existing cache, prunes old entries.
    private suspend fun saveToCache(newLessons: List<Lesson>) {
        if (newLessons.isEmpty()) return
        try {
            val existing  = getCachedLessons()
            val newDates  = newLessons.map { it.date }.toSet()

            // Keep old lessons whose dates are NOT covered by this fetch
            val preserved = existing.values
                .flatten()
                .filter { it.date !in newDates }

            // Prune: only keep lessons within ±6 weeks of today
            val cutoff = LocalDate.now().minusWeeks(5)
            val limit  = LocalDate.now().plusWeeks(7)
            val merged = (preserved + newLessons)
                .filter { it.date >= cutoff && it.date <= limit }

            val json = Gson().toJson(merged.map { it.toCached() })
            dataStore.saveTimetableCacheRaw(json)
        } catch (_: Exception) {
            // Cache write failure is non-fatal; app still works
        }
    }

    private suspend fun refreshBearer(server: String, cookie: String): String? {
        return try {
            val tokenUrl = UntisApiHelper.buildTokenUrl(server)
            val response = apiService.getNewToken(tokenUrl, cookie)
            if (response.isSuccessful) {
                response.body()?.string()
                    ?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.startsWith("ey") }
                    ?.also { dataStore.saveBearerToken(it) }
            } else null
        } catch (_: Exception) {
            dataStore.bearerToken.first()
        }
    }
}