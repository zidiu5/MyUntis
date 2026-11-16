package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.model.GradingLesson
import com.myuntis.app.data.network.model.GradingListResponse
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.data.network.untisToLocalDate
import com.myuntis.app.domain.model.Absence
import com.myuntis.app.domain.model.GradeEntry
import com.myuntis.app.domain.model.SubjectWithGrades
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import retrofit2.Response
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SchoolYearOption(val id: Int, val name: String)

@Singleton
class GradesRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {

    // ── PUBLIC: Grades ─────────────────────────────────────────
    suspend fun getSubjectGrades(yearId: Int? = null): NetworkResult<List<SubjectWithGrades>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie    = sessionId.toSessionCookie()
            val server    = credentials.server
            val school    = credentials.school
            val studentId = profile.personId

            // Get Bearer token FIRST – some servers (e.g. lbshi) require it
            // even for grading endpoints that use Cookie on others
            val bearer = fetchFreshBearer(server, cookie)

            // A past school year was picked → no searching needed
            if (yearId != null) {
                val url  = UntisApiHelper.buildGradingListUrl(server, studentId, yearId)
                val data = fetchGradingList(url, cookie, bearer)?.data
                    ?: return NetworkResult.Error("Dieses Schuljahr konnte nicht geladen werden.")
                return loadSubjects(
                    server, studentId, cookie, bearer,
                    lessons    = data.lessons,
                    finalMarks = data.finalMarkByLessonId.mapValues { it.value.assignedMark?.name ?: "" },
                    yearStart  = data.finalMarkByLessonId.values.firstOrNull()?.schoolyear?.startDate
                )
            }

            // ── Always ask the server for the CURRENT school year (never use cache) ──
            val rpcYearId = fetchCurrentSchoolYearId(server, school, cookie)

            val today    = LocalDate.now()
            val cachedId = dataStore.schoolyearId.first()

            // Year IDs to probe: RPC answer first, then last known ID and the next few
            val yearIds = buildList {
                if (rpcYearId > 0) add(rpcYearId)
                if (cachedId > 0) for (i in 0..6) add(cachedId + i)
            }.distinct()

            // 1) Known IDs first (RPC answer, cached ID and the next few)
            val knownIds = yearIds
            var found = probeYears(server, studentId, cookie, bearer, knownIds, today)

            // 2) Nothing known / nothing matched → scan a wide range once
            if (found == null) {
                val scanIds = (20..150).filter { it !in knownIds }
                found = probeYears(server, studentId, cookie, bearer, scanIds, today)
            }

            if (found == null) {
                return NetworkResult.Error(
                    "Das aktuelle Schuljahr konnte nicht ermittelt werden.\nBitte Refresh versuchen."
                )
            }

            val foundYearId = found.first
            val listData    = found.second.data
                ?: return NetworkResult.Error("Noten konnten nicht geladen werden.")

            // Remember the correct ID for next time
            dataStore.saveSchoolYearId(foundYearId)

            val finalMap = listData.finalMarkByLessonId
            return loadSubjects(
                server, studentId, cookie, bearer,
                lessons    = listData.lessons,
                finalMarks = finalMap.mapValues { it.value.assignedMark?.name ?: "" },
                yearStart  = finalMap.values.firstOrNull()?.schoolyear?.startDate
            )

        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Server nicht erreichbar.")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler beim Laden der Noten")
        }
    }

    // ── PUBLIC: Absences ───────────────────────────────────────
    suspend fun getAbsences(): NetworkResult<List<Absence>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie    = sessionId.toSessionCookie()
            val server    = credentials.server
            val studentId = profile.personId

            val now       = LocalDate.now()
            val startDate = if (now.monthValue >= 9)
                LocalDate.of(now.year, 9, 1)
            else
                LocalDate.of(now.year - 1, 9, 1)
            val endDate = startDate.plusMonths(10)

            val url = UntisApiHelper.buildAbsencesRestUrl(server, studentId, startDate, endDate)

            var resp = try { apiService.getAbsencesStudents(url, cookie) }
            catch (_: Exception) { null }

            if (resp?.isSuccessful != true || resp.body()?.data == null) {
                val bearer = fetchFreshBearer(server, cookie)
                if (bearer != null) {
                    resp = try {
                        apiService.getAbsencesStudentsBearer(url, "Bearer $bearer")
                    } catch (_: Exception) { null }
                }
            }

            val absences = resp?.body()?.data
                ?.toDomainAbsences()
                ?.sortedByDescending { it.date }
                ?: emptyList()

            NetworkResult.Success(absences)
        } catch (e: Exception) {
            NetworkResult.Error("Fehlstunden konnten nicht geladen werden")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private suspend fun loadSubjects(
        server: String,
        studentId: Int,
        cookie: String,
        bearer: String?,
        lessons: List<GradingLesson>,
        finalMarks: Map<String, String>,
        yearStart: String?
    ): NetworkResult<List<SubjectWithGrades>> {
        if (lessons.isEmpty()) return NetworkResult.Success(emptyList())

        val diag = GradeDiag()

        // Safety net: ignore grades older than (start of this school year - 1 month)
        val minDate = yearStart?.let { runCatching { LocalDate.parse(it).minusMonths(1) }.getOrNull() }
            ?: LocalDate.now().let { t ->
                (if (t.monthValue >= 9) LocalDate.of(t.year, 9, 1)
                else LocalDate.of(t.year - 1, 9, 1)).minusMonths(1)
            }

        val subjects = coroutineScope {
            lessons.map { lesson ->
                async {
                    loadLessonGrades(
                        server    = server,
                        studentId = studentId,
                        cookie    = cookie,
                        bearer    = bearer,
                        diag      = diag,
                        lesson    = lesson,
                        finalMark = finalMarks[lesson.id.toString()] ?: "",
                        minDate   = minDate
                    )
                }
            }.awaitAll()
        }

        val result = subjects
            .filterNotNull()
            .filter   { it.entries.isNotEmpty() }
            .sortedBy { it.subjectShort }

        // Every single request failed → real error. Otherwise "no grades" is a valid answer.
        if (result.isEmpty() && diag.failed.get() == lessons.size) {
            return NetworkResult.Error("Noten der Fächer konnten nicht geladen werden.\nBitte Refresh versuchen.")
        }
        return NetworkResult.Success(result)
    }

    /** School years the student has data for: current one first, then older ones. */
    suspend fun getAvailableSchoolYears(): List<SchoolYearOption> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first() ?: return emptyList()
            val currentId   = dataStore.schoolyearId.first()
            if (currentId <= 0) return emptyList()

            val cookie = sessionId.toSessionCookie()
            val bearer = fetchFreshBearer(credentials.server, cookie)
            val ids    = (currentId downTo (currentId - 8).coerceAtLeast(1)).toList()

            coroutineScope {
                ids.map { id ->
                    async {
                        val url  = UntisApiHelper.buildGradingListUrl(credentials.server, profile.personId, id)
                        val data = fetchGradingList(url, cookie, bearer)?.data
                        val sy   = data?.finalMarkByLessonId?.values?.firstOrNull()?.schoolyear
                        if (data != null && data.lessons.isNotEmpty() && sy != null)
                            SchoolYearOption(id, sy.name) else null
                    }
                }.awaitAll()
            }
                .filterNotNull()
                .distinctBy { it.name }
                .sortedByDescending { it.id }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Tries the given school year IDs (10 at a time, in parallel).
     * Returns the first ID whose school year contains today, with its response.
     */
    private suspend fun probeYears(
        server: String,
        studentId: Int,
        cookie: String,
        bearer: String?,
        ids: List<Int>,
        today: LocalDate
    ): Pair<Int, GradingListResponse>? {
        for (chunk in ids.chunked(10)) {
            val results = coroutineScope {
                chunk.map { id ->
                    async {
                        val url  = UntisApiHelper.buildGradingListUrl(server, studentId, id)
                        val resp = fetchGradingList(url, cookie, bearer)
                        val data = resp?.data
                        val sy   = data?.finalMarkByLessonId?.values?.firstOrNull()?.schoolyear

                        val matches = data != null && data.lessons.isNotEmpty() && sy != null &&
                                runCatching {
                                    LocalDate.parse(sy.startDate) <= today &&
                                            today <= LocalDate.parse(sy.endDate)
                                }.getOrDefault(false)

                        if (matches && resp != null) id to resp else null
                    }
                }.awaitAll()
            }
            results.firstNotNullOfOrNull { it }?.let { return it }
        }
        return null
    }

    /**
     * Fetches grading list trying:
     *  1. Cookie auth
     *  2. Bearer auth (if Cookie returns no data)
     * Returns null if both fail.
     */
    private suspend fun fetchGradingList(
        url: String,
        cookie: String,
        bearer: String?
    ): GradingListResponse? {
        // Try Cookie
        val r1 = try { apiService.getGradingList(url, cookie) }
        catch (_: Exception) { null }
        if (r1?.isSuccessful == true && r1.body()?.data != null) {
            return r1.body()
        }

        // Try Bearer
        if (bearer != null) {
            val r2 = try { apiService.getGradingListBearer(url, "Bearer $bearer") }
            catch (_: Exception) { null }
            if (r2?.isSuccessful == true && r2.body()?.data != null) {
                return r2.body()
            }
        }

        return null
    }

    /**
     * Fetches grades for one lesson.
     * Tries Cookie first, then Bearer as fallback.
     */
    private suspend fun loadLessonGrades(
        server: String,
        studentId: Int,
        cookie: String,
        bearer: String?,
        lesson: GradingLesson,
        finalMark: String,
        diag: GradeDiag,
        minDate: LocalDate
    ): SubjectWithGrades? {
        return try {
            val url = UntisApiHelper.buildGradingLessonUrl(server, studentId, lesson.id)

            // Try Cookie
            var resp = try { apiService.getGradingLesson(url, cookie) }
            catch (_: Exception) { null }

            // Fallback: Bearer (for servers like lbshi)
            if (resp?.isSuccessful != true || resp.body()?.data == null) {
                if (bearer != null) {
                    resp = try { apiService.getGradingLessonBearer(url, "Bearer $bearer") }
                    catch (_: Exception) { null }
                }
            }

            val grades = resp?.body()?.data?.grades
                ?: run { diag.failed.incrementAndGet(); return null }
            diag.rawGrades.addAndGet(grades.size)

            val entries = grades.mapNotNull { item ->
                val mark = item.mark ?: return@mapNotNull null
                if (mark.markValue <= 0 || mark.markDisplayValue <= 0.0) {
                    diag.droppedMark.incrementAndGet(); return@mapNotNull null
                }
                val date = item.date.untisToLocalDate()
                if (date < minDate) {
                    diag.droppedDate.incrementAndGet(); return@mapNotNull null
                }

                GradeEntry(
                    id           = item.id,
                    markName     = mark.name.ifBlank { mark.markDisplayValue.toString() },
                    markValue    = mark.markDisplayValue,
                    description  = item.text.trim(),
                    examType     = item.examType?.name ?: "",
                    date         = date,
                    markSchemaId = item.markSchemaId
                )
            }.sortedByDescending { it.date }

            if (entries.isEmpty()) return null

            SubjectWithGrades(
                lessonId      = lesson.id,
                subjectShort  = lesson.subjects.ifBlank { "?" },
                subjectLong   = lesson.subjects.ifBlank { "?" },
                teachers      = lesson.teachers,
                entries       = entries,
                finalMarkName = finalMark
            )
        } catch (_: Exception) { null }
    }

    // Asks the server which school year is current TODAY (no cache!)
    private suspend fun fetchCurrentSchoolYearId(
        server: String,
        school: String,
        cookie: String
    ): Int {
        return try {
            val rpcUrl = UntisApiHelper.buildJsonRpcUrl(server, school)
            val resp = apiService.getCurrentSchoolYear(
                url           = rpcUrl,
                sessionCookie = cookie,
                request       = UntisApiHelper.buildGetCurrentSchoolYearRequest()
            )
            resp.body()?.result?.id ?: 0
        } catch (_: Exception) { 0 }
    }

    /**
     * Always fetches a fresh Bearer token.
     * /api/token/new exchanges JSESSIONID for a JWT.
     */
    private suspend fun fetchFreshBearer(server: String, cookie: String): String? {
        return try {
            val tokenUrl = UntisApiHelper.buildTokenUrl(server)
            val response = apiService.getNewToken(tokenUrl, cookie)
            if (response.isSuccessful) {
                val token = response.body()?.string()
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.takeIf { it.isNotBlank() && it.startsWith("ey") }
                if (token != null) {
                    dataStore.saveBearerToken(token)
                    token
                } else {
                    // Return cached as last resort
                    dataStore.bearerToken.first()
                }
            } else {
                dataStore.bearerToken.first()
            }
        } catch (_: Exception) {
            dataStore.bearerToken.first()
        }
    }
}

// Extension for AbsencesStudentsData → domain Absence
private fun com.myuntis.app.data.network.model.AbsencesStudentsData.toDomainAbsences()
        : List<Absence> {
    return absences.map { item ->
        Absence(
            id          = item.id,
            date        = item.startDate.let {
                val s = it.toString().padStart(8, '0')
                java.time.LocalDate.of(
                    s.substring(0,4).toInt(),
                    s.substring(4,6).toInt(),
                    s.substring(6,8).toInt()
                )
            },
            startTime   = item.startTime.let {
                val s = it.toString().padStart(4, '0')
                java.time.LocalTime.of(s.substring(0,2).toInt(), s.substring(2,4).toInt())
            },
            endTime     = item.endTime.let {
                val s = it.toString().padStart(4, '0')
                java.time.LocalTime.of(s.substring(0,2).toInt(), s.substring(2,4).toInt())
            },
            subjectName = "",
            reason      = item.reason.trim(),
            detail      = item.text.trim(),
            isExcused   = item.isExcused,
            teacherName = ""
        )
    }
}

private class GradeDiag {
    val failed       = java.util.concurrent.atomic.AtomicInteger(0) // request/parse failed
    val rawGrades    = java.util.concurrent.atomic.AtomicInteger(0) // grades the server sent
    val droppedMark  = java.util.concurrent.atomic.AtomicInteger(0) // mark value <= 0
    val droppedDate  = java.util.concurrent.atomic.AtomicInteger(0) // too old
}