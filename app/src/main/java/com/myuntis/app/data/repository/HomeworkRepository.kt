package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.mapper.toDomainHomework
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.domain.model.Homework
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeworkRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {

    suspend fun getHomework(): NetworkResult<List<Homework>> {
        return try {
            val credentials = dataStore.loginCredentials.first()
            val sessionId   = dataStore.sessionId.first()
                ?: return NetworkResult.Error("Keine aktive Session.")

            val cookie = sessionId.toSessionCookie()
            val server = credentials.server
            val start  = LocalDate.now().minusWeeks(2)
            val end    = LocalDate.now().plusWeeks(6)

            val url = UntisApiHelper.buildHomeworkRestUrl(
                server    = server,
                startDate = start,
                endDate   = end
            )

            // ── Try 1: Cookie auth (this API uses JSESSIONID) ─
            var response = apiService.getHomeworksRest(url, cookie)

            // ── Try 2: Bearer token (fallback) ────────────────
            if (!response.isSuccessful || response.body()?.data == null) {
                val bearer = dataStore.bearerToken.first()
                if (bearer != null) {
                    response = apiService.getHomeworksRestBearer(url, "Bearer $bearer")
                }
            }

            // ── Try 3: Fresh Bearer token ─────────────────────
            if (!response.isSuccessful || response.body()?.data == null) {
                val freshBearer = fetchFreshBearer(server, cookie)
                if (freshBearer != null) {
                    dataStore.saveBearerToken(freshBearer)
                    response = apiService.getHomeworksRestBearer(url, "Bearer $freshBearer")
                }
            }

            // ── Handle result ─────────────────────────────────
            if (!response.isSuccessful) {
                // 401 = session expired, don't show as error, return empty
                if (response.code() == 401 || response.code() == 403) {
                    return NetworkResult.Success(emptyList())
                }
                return NetworkResult.Error("HTTP ${response.code()}")
            }

            val data = response.body()?.data
            // Null data = server returned HTML/error page parsed as null
            // Return empty list gracefully
                ?: return NetworkResult.Success(emptyList())

            val completedIds = dataStore.getCompletedHomeworkIds()
            val homework = data
                .toDomainHomework(completedIds)
                .sortedBy { it.dueDate }

            NetworkResult.Success(homework)

        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Server nicht erreichbar.")
        } catch (e: com.google.gson.JsonSyntaxException) {
            // Server returned HTML instead of JSON (session expired redirect)
            // Return empty instead of crashing
            NetworkResult.Success(emptyList())
        } catch (e: Exception) {
            if (e.message?.contains("malformed") == true ||
                e.message?.contains("JsonReader") == true) {
                // HTML response = likely session redirect = treat as empty
                NetworkResult.Success(emptyList())
            } else {
                NetworkResult.Error(e.localizedMessage ?: "Unbekannter Fehler")
            }
        }
    }

    suspend fun toggleComplete(homeworkId: Int, isComplete: Boolean) {
        dataStore.setHomeworkCompleted(homeworkId, isComplete)
    }

    private suspend fun fetchFreshBearer(server: String, cookie: String): String? {
        return try {
            val tokenUrl = UntisApiHelper.buildTokenUrl(server)
            val response = apiService.getNewToken(tokenUrl, cookie)
            if (response.isSuccessful) {
                response.body()?.string()
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.takeIf { it.isNotBlank() && it.startsWith("ey") }
            } else null
        } catch (_: Exception) { null }
    }
}