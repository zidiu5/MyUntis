package com.myuntis.app.data.repository

import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.local.LoginCredentials
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.toSessionCookie
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.domain.model.User
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) {

    // Login: authenticates against WebUntis and saves session
    suspend fun login(
        username: String,
        password: String,
        school: String,
        server: String
    ): NetworkResult<User> {
        return try {
            val url = UntisApiHelper.buildJsonRpcUrl(server, school)
            val request = UntisApiHelper.buildLoginRequest(username, password)

            val response = apiService.authenticate(url = url, request = request)

            if (!response.isSuccessful) {
                return NetworkResult.Error(
                    message = "HTTP ${response.code()}: ${response.message()}",
                    code = response.code()
                )
            }

            val body = response.body()

            if (body?.error != null) {
                return NetworkResult.Error(
                    message = translateError(body.error.code, body.error.message),
                    code = body.error.code
                )
            }

            val loginResult = body?.result
                ?: return NetworkResult.Error("Leere Antwort vom Server")

            val sessionCookie = loginResult.sessionId.toSessionCookie()

            // Fetch additional user info
            val studentInfo = fetchStudentInfo(url, sessionCookie)
            val className = fetchClassName(url, sessionCookie, loginResult.klasseId ?: 0)

            // Persist credentials and session
            dataStore.saveLoginCredentials(username, password, school, server)
            dataStore.saveSession(
                sessionId = loginResult.sessionId,
                personId = loginResult.personId,
                personType = loginResult.personType,
                klasseId = loginResult.klasseId ?: 0,
                fullName = studentInfo?.longName ?: username,
                className = className
            )

            val nameParts = (studentInfo?.longName ?: username).split(" ")
            NetworkResult.Success(
                User(
                    id = loginResult.personId,
                    username = username,
                    firstName = studentInfo?.foreName ?: nameParts.firstOrNull() ?: "",
                    lastName = nameParts.lastOrNull() ?: "",
                    fullName = studentInfo?.longName ?: username,
                    schoolClass = className,
                    schoolName = school,
                    personType = loginResult.personType,
                    sessionId = loginResult.sessionId
                )
            )

        } catch (e: java.net.UnknownHostException) {
            NetworkResult.Error("Kein Internet oder Server nicht erreichbar.\nBitte Server-Name prüfen: $server.webuntis.com")
        } catch (e: java.net.SocketTimeoutException) {
            NetworkResult.Error("Server antwortet nicht (Timeout).\nBitte später erneut versuchen.")
        } catch (e: Exception) {
            NetworkResult.Error("Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
        }
    }

    suspend fun logout(): NetworkResult<Unit> {
        return try {
            val creds = dataStore.loginCredentials.first()
            val sessionId = dataStore.sessionId.first()
            if (sessionId != null) {
                val url = UntisApiHelper.buildJsonRpcUrl(creds.server, creds.school)
                apiService.logout(
                    url = url,
                    sessionCookie = sessionId.toSessionCookie(),
                    request = UntisApiHelper.buildLogoutRequest()
                )
            }
            dataStore.clearAllData()
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            dataStore.clearAllData()
            NetworkResult.Success(Unit)
        }
    }

    suspend fun isLoggedIn(): Boolean = dataStore.isLoggedIn.first()

    suspend fun getSessionId(): String? = dataStore.sessionId.first()

    // Returns saved credentials if they exist (for pre-filling login form)
    suspend fun getSavedCredentials(): LoginCredentials? {
        val creds = dataStore.loginCredentials.first()
        return if (creds.username.isNotEmpty()) creds else null
    }

    // Translates WebUntis error codes to German messages
    private fun translateError(code: Int, message: String): String {
        return when (code) {
            -8504 -> "Falscher Benutzername oder Passwort"
            -8520 -> "Kein Zugang für diesen Benutzer"
            -8509 -> "Schule nicht gefunden. Bitte Schul-ID prüfen."
            -8510 -> "Session abgelaufen. Bitte erneut anmelden."
            else  -> "Login fehlgeschlagen (Code $code): $message"
        }
    }

    private suspend fun fetchStudentInfo(
        url: String,
        sessionCookie: String
    ): com.myuntis.app.data.network.model.ApiStudent? {
        return try {
            apiService.getCurrentStudent(
                url = url,
                sessionCookie = sessionCookie,
                request = UntisApiHelper.buildGetStudentRequest()
            ).body()?.result
        } catch (e: Exception) { null }
    }

    private suspend fun fetchClassName(
        url: String,
        sessionCookie: String,
        klasseId: Int
    ): String {
        return try {
            apiService.getKlassen(
                url = url,
                sessionCookie = sessionCookie,
                request = UntisApiHelper.buildGetKlassenRequest()
            ).body()?.result?.firstOrNull { it.id == klasseId }?.name ?: ""
        } catch (e: Exception) { "" }
    }
    // Re-login with saved credentials when session expires mid-use
    suspend fun refreshSession(): Boolean {
        return try {
            val creds = dataStore.loginCredentials.first()
            if (creds.username.isBlank() || creds.server.isBlank()) return false
            dataStore.clearSession()
            val result = login(creds.username, creds.password, creds.school, creds.server)
            result is NetworkResult.Success
        } catch (_: Exception) { false }
    }
}