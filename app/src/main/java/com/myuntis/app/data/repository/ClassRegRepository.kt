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

            // Step 1: try with Cookie
            val cookieResp = try { apiService.getClassRegEvents(url, cookie) }
            catch (_: Exception) { null }

            if (cookieResp?.isSuccessful == true) {
                val rows = cookieResp.body()?.data?.rows ?: emptyList()
                if (rows.isNotEmpty()) {
                    return NetworkResult.Success(mapRows(rows))
                }
                // rows empty → session probably expired, try Bearer below
            }

            // Step 2: get fresh Bearer token and retry
            val bearer = fetchFreshBearer(server, cookie)
                ?: return NetworkResult.Error("Keine Berechtigung. Bitte neu anmelden.")

            val bearerResp = try {
                apiService.getClassRegEvents(url, "$bearer")
            } catch (_: Exception) { null }

            // Note: we reuse getClassRegEvents with a cookie header.
            // Since the API accepts either, we pass the Bearer as a
            // "Cookie" value which some WebUntis versions accept,
            // OR we simply retry with a fresh session login.
            // Best approach: just re-login silently.
            val refreshed = refreshAndRetry(
                server = server,
                credentials = credentials,
                studentId = studentId,
                startDate = startDate,
                endDate = endDate
            )

            if (refreshed != null) return refreshed

            NetworkResult.Success(emptyList())

        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Fehler beim Laden")
        }
    }

    private suspend fun refreshAndRetry(
        server: String,
        credentials: com.myuntis.app.data.local.LoginCredentials,
        studentId: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): NetworkResult<List<ClassRegEntry>>? {
        return try {
            // Re-authenticate to get a fresh session cookie
            val rpcUrl = UntisApiHelper.buildJsonRpcUrl(server, credentials.school)
            val loginResp = apiService.authenticate(
                url     = rpcUrl,
                request = UntisApiHelper.buildLoginRequest(credentials.username, credentials.password)
            )
            val newSessionId = loginResp.body()?.result?.sessionId
                ?: return null

            dataStore.saveSession(
                sessionId  = newSessionId,
                personId   = dataStore.userProfile.first().personId,
                personType = dataStore.userProfile.first().personType,
                klasseId   = dataStore.userProfile.first().klasseId,
                fullName   = dataStore.userProfile.first().fullName,
                className  = dataStore.userProfile.first().className
            )

            val newCookie = newSessionId.let { "JSESSIONID=$it" }
            val url       = UntisApiHelper.buildClassRegUrl(server, studentId, startDate, endDate)
            val resp      = apiService.getClassRegEvents(url, newCookie)

            if (resp.isSuccessful) {
                val rows = resp.body()?.data?.rows ?: emptyList()
                NetworkResult.Success(mapRows(rows))
            } else null
        } catch (_: Exception) { null }
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