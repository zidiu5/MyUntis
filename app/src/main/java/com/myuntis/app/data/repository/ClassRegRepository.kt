package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.data.network.untisToLocalDate
import com.myuntis.app.data.network.untisToLocalTime
import com.myuntis.app.domain.model.ClassRegEntry
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassRegRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {
    suspend fun getEntries(): NetworkResult<List<ClassRegEntry>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val profile     = dataStore.userProfile.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie    = sessionId.toSessionCookie()
            val server    = credentials.server
            val studentId = profile.personId

            // Full school year range
            val now = LocalDate.now()
            val startDate = if (now.monthValue >= 9)
                LocalDate.of(now.year, 9, 1)
            else
                LocalDate.of(now.year - 1, 9, 1)
            val endDate = if (now.monthValue >= 9)
                LocalDate.of(now.year + 1, 7, 1)
            else
                LocalDate.of(now.year, 7, 1)

            val url = UntisApiHelper.buildClassRegUrl(server, studentId, startDate, endDate)

            // 1) Cookie
            val c = runCatching { apiService.getClassRegEvents(url, cookie) }.getOrNull()
            c?.takeIf { it.isSuccessful }?.body()?.data?.let {
                return NetworkResult.Success(mapRows(it.rows))
            }

            // 2) Bearer
            val bearer = fetchFreshBearer(server, cookie)
                ?: return NetworkResult.Error("Keine Berechtigung. Bitte neu anmelden.")
            val b = runCatching {
                apiService.getClassRegEventsBearer(url, "Bearer $bearer")
            }.getOrNull()
            b?.takeIf { it.isSuccessful }?.body()?.data?.let {
                return NetworkResult.Success(mapRows(it.rows))
            }

            return NetworkResult.Error("Klassenbuch konnte nicht geladen werden.")

        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler beim Laden")
        }
    }

    private fun mapRows(rows: List<com.myuntis.app.data.network.model.ClassRegRow>): List<ClassRegEntry> {
        return rows.mapNotNull { row ->
            try {
                ClassRegEntry(
                    id          = row.id,
                    date        = row.createDate.untisToLocalDate(),
                    time        = row.createTime.untisToLocalTime(),
                    subjectName = row.subjectName,
                    teacherName = row.creatorName.substringBefore(",").trim(),
                    text        = row.text.trim(),
                    category    = row.categoryName?.trim() ?: "",
                    reason      = row.eventReasonName?.trim() ?: "",
                    isPersonal  = row.elemType == "STUDENT"
                )
            } catch (_: Exception) { null }
        }.sortedByDescending { it.date }
    }

    private suspend fun fetchFreshBearer(server: String, cookie: String): String? {
        return try {
            val tokenUrl = UntisApiHelper.buildTokenUrl(server)
            val response = apiService.getNewToken(tokenUrl, cookie)
            if (response.isSuccessful) {
                response.body()?.string()
                    ?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.startsWith("ey") }
                    ?.also { dataStore.saveBearerToken(it) }
            } else null
        } catch (_: Exception) { null }
    }
}