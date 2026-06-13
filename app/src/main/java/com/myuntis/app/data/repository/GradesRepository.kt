package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.mapper.toDomainAbsences
import com.myuntis.app.data.network.model.GradingLesson
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.data.network.untisToLocalDate
import com.myuntis.app.domain.model.Absence
import com.myuntis.app.domain.model.GradeEntry
import com.myuntis.app.domain.model.SubjectWithGrades
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradesRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {

    // ── GRADES ─────────────────────────────────────────────────
    suspend fun getSubjectGrades(): NetworkResult<List<SubjectWithGrades>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie    = sessionId.toSessionCookie()
            val server    = credentials.server
            val school    = credentials.school
            val studentId = profile.personId

            // ── Step 1: Get schoolyearId (REQUIRED for grading list URL) ──
            val schoolyearId = resolveSchoolYearId(server, school, cookie)

            if (schoolyearId <= 0) {
                return NetworkResult.Error(
                    "Schuljahr konnte nicht ermittelt werden.\n" +
                            "Bitte abmelden und neu anmelden."
                )
            }

            // ── Step 2: Grading list WITH schoolyearId in URL ─────────────
            // Browser URL: ?studentId=X&schoolyearId=27  → server returns JSON
            // Without schoolyearId               → server returns HTML login page
            val listUrl = UntisApiHelper.buildGradingListUrl(
                server       = server,
                studentId    = studentId,
                schoolyearId = schoolyearId
            )

            val listResp = try {
                apiService.getGradingList(listUrl, cookie)
            } catch (e: Exception) {
                return NetworkResult.Error("Netzwerkfehler: ${e.localizedMessage}")
            }

            if (!listResp.isSuccessful) {
                return NetworkResult.Error("HTTP ${listResp.code()}: Noten konnten nicht geladen werden.")
            }

            val listData = listResp.body()?.data
                ?: return NetworkResult.Success(emptyList())

            val lessons  = listData.lessons
            val finalMap = listData.finalMarkByLessonId

            if (lessons.isEmpty()) return NetworkResult.Success(emptyList())

            // ── Step 3: Fetch grades for each lesson in parallel ──────────
            val subjects = coroutineScope {
                lessons.map { lesson ->
                    async {
                        loadLessonGrades(
                            server    = server,
                            studentId = studentId,
                            cookie    = cookie,
                            lesson    = lesson,
                            finalMark = finalMap[lesson.id.toString()]
                                ?.assignedMark?.name
                                ?.takeIf { it.isNotBlank() } ?: ""
                        )
                    }
                }.awaitAll()
            }

            NetworkResult.Success(
                subjects
                    .filterNotNull()
                    .filter  { it.entries.isNotEmpty() }
                    .sortedBy { it.subjectShort }
            )

        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Server nicht erreichbar.")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler beim Laden der Noten")
        }
    }

    // ── ABSENCES ───────────────────────────────────────────────
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

            val url = UntisApiHelper.buildAbsencesRestUrl(
                server    = server,
                studentId = studentId,
                startDate = startDate,
                endDate   = endDate
            )

            val resp = try {
                apiService.getAbsencesStudents(url, cookie)
            } catch (_: Exception) { null }

            val absences = resp?.body()?.data
                ?.toDomainAbsences()
                ?.sortedByDescending { it.date }
                ?: emptyList()

            NetworkResult.Success(absences)

        } catch (e: Exception) {
            NetworkResult.Success(emptyList())
        }
    }

    // ── PRIVATE: resolve school year ID ───────────────────────
    // Priority: DataStore cache → JSON-RPC → date-based estimate
    private suspend fun resolveSchoolYearId(
        server: String,
        school: String,
        cookie: String
    ): Int {
        // 1. DataStore cache (fastest)
        val cached = dataStore.schoolyearId.first()
        if (cached > 0) return cached

        // 2. JSON-RPC getCurrentSchoolYear
        return try {
            val rpcUrl = UntisApiHelper.buildJsonRpcUrl(server, school)
            val resp   = apiService.getCurrentSchoolYear(
                url           = rpcUrl,
                sessionCookie = cookie,
                request       = UntisApiHelper.buildGetCurrentSchoolYearRequest()
            )
            val id = resp.body()?.result?.id ?: 0
            if (id > 0) {
                dataStore.saveSchoolYearId(id)
                return id
            }

            // 3. Date-based estimate (last resort)
            // WebUntis school year IDs are sequential integers.
            // We compute based on year: 2025/2026 started ~2025-09 → id ≈ 27 at most schools.
            // This is a fallback only; it may be wrong for some schools.
            val now = LocalDate.now()
            val schoolYear = if (now.monthValue >= 9) now.year else now.year - 1
            // Approximate: id increments by 1 each year, anchor: 2025/26 = ~27
            val estimate = 27 + (schoolYear - 2025)
            if (estimate > 0) {
                dataStore.saveSchoolYearId(estimate)
            }
            estimate

        } catch (e: Exception) {
            // Pure date estimate fallback
            val now = LocalDate.now()
            val schoolYear = if (now.monthValue >= 9) now.year else now.year - 1
            27 + (schoolYear - 2025)
        }
    }

    // ── PRIVATE: load one lesson's grades ─────────────────────
    private suspend fun loadLessonGrades(
        server: String,
        studentId: Int,
        cookie: String,
        lesson: GradingLesson,
        finalMark: String
    ): SubjectWithGrades? {
        return try {
            val url  = UntisApiHelper.buildGradingLessonUrl(server, studentId, lesson.id)
            val resp = try {
                apiService.getGradingLesson(url, cookie)
            } catch (_: Exception) { return null }

            if (!resp.isSuccessful) return null

            val entries = resp.body()?.data?.grades
                ?.mapNotNull { item ->
                    val mark = item.mark ?: return@mapNotNull null
                    // Filter: skip entries with no real grade (deleted/empty = value 0)
                    if (mark.markValue <= 0 || mark.markDisplayValue <= 0.0) return@mapNotNull null

                    GradeEntry(
                        id           = item.id,
                        markName     = mark.name.ifBlank { mark.markDisplayValue.toString() },
                        markValue    = mark.markDisplayValue,  // Already parsed: "9+" = 9.25
                        description  = item.text.trim(),
                        examType     = item.examType?.name ?: "",
                        date         = item.date.untisToLocalDate(),
                        markSchemaId = item.markSchemaId
                    )
                }
                ?.sortedByDescending { it.date }
                ?: return null

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
}