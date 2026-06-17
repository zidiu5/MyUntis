package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val PERSON_TYPE_STUDENT = 5
private const val PERSON_TYPE_PARENT  = 12

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {

    suspend fun login(
        username: String,
        password: String,
        school: String,
        server: String
    ): NetworkResult<Unit> {
        return try {
            val url = UntisApiHelper.buildJsonRpcUrl(server, school)
            val req = UntisApiHelper.buildLoginRequest(username, password)

            val response = apiService.authenticate(url, req)
            val result   = response.body()?.result

            if (result == null || response.body()?.error != null) {
                val msg = response.body()?.error?.message ?: "Login fehlgeschlagen"
                return NetworkResult.Error(msg)
            }

            val sessionId  = result.sessionId  ?: return NetworkResult.Error("Keine Session")
            val personType = result.personType ?: PERSON_TYPE_STUDENT

            // Save credentials for auto-login
            dataStore.saveLoginCredentials(username, password, school, server)

            // ── Resolve actual student ID ─────────────────────
            // For student accounts (type 5): personId IS the student ID
            // For parent accounts (type 12): personId is the PARENT ID
            //   → we must fetch the child's student ID separately
            val personId = when (personType) {
                PERSON_TYPE_STUDENT -> result.personId ?: 0
                PERSON_TYPE_PARENT  -> {
                    val cookie    = "JSESSIONID=$sessionId"
                    val studentId = resolveStudentIdForParent(server, cookie)
                    studentId ?: result.personId ?: 0  // fallback to parent ID
                }
                else -> result.personId ?: 0
            }

            // Resolve class name
            val className = resolveClassName(server, school, sessionId, personId, personType)

            dataStore.saveSession(
                sessionId  = sessionId,
                personId   = personId,
                personType = personType,
                klasseId   = result.klasseId ?: 0,
                fullName   = result.personName ?: username,
                className  = className
            )

            NetworkResult.Success(Unit)

        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Server nicht erreichbar. Serveradresse prüfen.")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Unbekannter Fehler")
        }
    }

    // ── Resolve student ID for parent accounts ────────────────
    // Tries timetable/filter endpoint first (most reliable),
    // then falls back to app/data endpoint.
    private suspend fun resolveStudentIdForParent(
        server: String,
        cookie: String
    ): Int? {
        // Attempt 1: timetable/filter → preSelected student
        try {
            val filterUrl = UntisApiHelper.buildTimetableFilterUrl(server)
            val resp = apiService.getTimetableFilter(filterUrl, cookie)
            if (resp.isSuccessful) {
                val preSelected = resp.body()?.preSelected
                if (preSelected != null && preSelected.id > 0) {
                    return preSelected.id
                }
                // If preSelected is null, try students list
                val firstStudent = resp.body()?.students?.firstOrNull()?.student
                if (firstStudent != null && firstStudent.id > 0) {
                    return firstStudent.id
                }
            }
        } catch (_: Exception) {}

        // Attempt 2: Bearer token + timetable/filter
        try {
            val tokenUrl = UntisApiHelper.buildTokenUrl(server)
            val tokenResp = apiService.getNewToken(tokenUrl, cookie)
            if (tokenResp.isSuccessful) {
                val bearer = tokenResp.body()?.string()
                    ?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.startsWith("ey") }

                if (bearer != null) {
                    val filterUrl = UntisApiHelper.buildTimetableFilterUrl(server)
                    val resp = apiService.getTimetableFilterBearer(filterUrl, "Bearer $bearer")
                    if (resp.isSuccessful) {
                        val preSelected = resp.body()?.preSelected
                        if (preSelected != null && preSelected.id > 0) {
                            return preSelected.id
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    // ── Resolve class name for the user ──────────────────────
    private suspend fun resolveClassName(
        server: String,
        school: String,
        sessionId: String,
        personId: Int,
        personType: Int
    ): String {
        return try {
            val cookie   = "JSESSIONID=$sessionId"
            val filterUrl = UntisApiHelper.buildTimetableFilterUrl(server)
            val resp = apiService.getTimetableFilter(filterUrl, cookie)
            if (resp.isSuccessful) {
                // For student: preSelected.displayName has class info
                // For parent: students list has the child's class
                when (personType) {
                    PERSON_TYPE_STUDENT -> {
                        resp.body()?.students
                            ?.firstOrNull { it.student?.id == personId }
                            ?.student?.displayName ?: ""
                    }
                    PERSON_TYPE_PARENT -> {
                        resp.body()?.preSelected?.displayName ?: ""
                    }
                    else -> ""
                }
            } else ""
        } catch (_: Exception) { "" }
    }

    suspend fun isLoggedIn(): Boolean =
        dataStore.sessionId.first() != null &&
                dataStore.isLoggedIn.first()

    suspend fun getSavedCredentials() =
        dataStore.loginCredentials.first().let {
            if (it.username.isBlank()) null else it
        }

    suspend fun refreshSession(): Boolean {
        return try {
            val creds = dataStore.loginCredentials.first()
            if (creds.username.isBlank()) return false
            val result = login(creds.username, creds.password, creds.school, creds.server)
            result is NetworkResult.Success
        } catch (_: Exception) { false }
    }

    suspend fun logout() {
        try {
            val sessionId = dataStore.sessionId.first() ?: return
            val creds = dataStore.loginCredentials.first()
            val url   = UntisApiHelper.buildJsonRpcUrl(creds.server, creds.school)
            apiService.logout(url, "JSESSIONID=$sessionId",
                UntisApiHelper.buildLogoutRequest())
        } catch (_: Exception) {}
        dataStore.clearAllData()
    }
}