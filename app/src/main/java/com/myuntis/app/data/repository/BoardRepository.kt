package com.myuntis.app.data.repository

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.model.BoardCache
import com.myuntis.app.data.network.model.BoardPost
import com.myuntis.app.data.network.model.BoardSession
import com.myuntis.app.data.network.model.BoardStats
import com.myuntis.app.data.network.toSessionCookie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What the user typed in the editor. */
data class PostDraft(
    val type: String,
    val subject: String,
    val title: String,
    val body: String,
    val dueDate: String?,        // YYYY-MM-DD or null
    val openEdit: Boolean
)

data class BoardSnapshot(
    val session: BoardSession,
    val posts: List<BoardPost>,
    val stats: BoardStats?
)

fun NetworkResult<*>.errorMessage(): String? = (this as? NetworkResult.Error)?.message

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BoardRepository @Inject constructor(
    private val api: UntisApiService,
    private val dataStore: DataStoreManager,
    private val sessionManager: SessionManager
) {
    companion object {
        const val BASE = "https://board.zidiu1.com/api/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val loginMutex = Mutex()
    private val loggedInThisRun = mutableSetOf<String>()   // account ids

    /** Only student accounts (personType 5) may use the board. */
    val isStudentFlow: Flow<Boolean> = dataStore.userProfile.map { it.personType == 5 }

    // ── "done" ticks: local only, per account ─────────────────
    fun doneIds(): Flow<Set<String>> = dataStore.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet()) else dataStore.boardDoneIds(id)
    }

    suspend fun setDone(postId: String, done: Boolean) {
        val id = dataStore.activeAccountId.first() ?: return
        dataStore.setBoardDone(id, postId, done)
    }

    // ── Cache ─────────────────────────────────────────────────
    suspend fun getCached(): BoardSnapshot? {
        val accId   = dataStore.activeAccountId.first() ?: return null
        val session = dataStore.loadBoardSessions()[accId] ?: return null
        val cache   = dataStore.loadBoardCache(accId) ?: return null
        if (cache.schoolId != session.schoolId || cache.classId != session.classId) return null
        return BoardSnapshot(session, cache.posts, cache.stats)
    }

    // ── Sync (incremental via updatedSince) ───────────────────
    suspend fun sync(): NetworkResult<BoardSnapshot> {
        val accId = dataStore.activeAccountId.first()
            ?: return NetworkResult.Error("Kein Konto aktiv.")

        val session0 = when (val r = ensureSession(false)) {
            is NetworkResult.Success -> r.data
            is NetworkResult.Error   -> return NetworkResult.Error(r.message)
            else                     -> return NetworkResult.Error("Anmeldung fehlgeschlagen.")
        }

        var cache = dataStore.loadBoardCache(accId)
        if (cache != null && (cache.schoolId != session0.schoolId || cache.classId != session0.classId)) {
            cache = null     // other class (e.g. new school year) → start fresh
        }

        val merged = LinkedHashMap<String, BoardPost>()
        cache?.posts?.forEach { merged[it.id] = it }
        var since: String? = cache?.serverTime?.takeIf { it.isNotBlank() }
        var finalTime: String? = since
        var stats: BoardStats? = cache?.stats

        var pages = 0
        while (pages < 30) {
            pages++
            val sinceNow = since
            val r = authed { s ->
                val query = if (sinceNow != null)
                    "?updatedSince=" + URLEncoder.encode(sinceNow, "UTF-8") else ""
                api.boardGetPosts(boardUrl(s, "posts") + query, "Bearer ${s.token}")
            }
            val page = when (r) {
                is NetworkResult.Success -> r.data
                is NetworkResult.Error   -> return NetworkResult.Error(r.message)
                else                     -> return NetworkResult.Error("Fehler beim Laden.")
            }

            page.posts.forEach { if (it.deleted) merged.remove(it.id) else merged[it.id] = it }
            if (page.board != null) stats = page.board

            if (page.hasMore && page.posts.isNotEmpty()) {
                val next = page.posts.last().updatedAt
                if (next == since) break          // no progress → stop
                since = next
            } else {
                if (page.serverTime.isNotBlank()) finalTime = page.serverTime
                break
            }
        }

        val session = dataStore.loadBoardSessions()[accId] ?: session0
        val posts   = merged.values.toList()
        dataStore.saveBoardCache(
            accId,
            BoardCache(session.schoolId, session.classId, finalTime ?: "", posts, stats)
        )
        return NetworkResult.Success(BoardSnapshot(session, posts, stats))
    }

    // ── Create / update / delete / report ─────────────────────
    suspend fun createPost(d: PostDraft): NetworkResult<BoardPost> {
        val json = JsonObject().apply {
            addProperty("type", d.type)
            addProperty("title", d.title.trim())
            d.subject.trim().takeIf { it.isNotEmpty() }?.let { addProperty("subject", it) }
            d.body.trim().takeIf { it.isNotEmpty() }?.let { addProperty("body", it) }
            d.dueDate?.let { addProperty("dueDate", it) }
            addProperty("openEdit", d.openEdit)
        }
        return authed { s ->
            api.boardCreatePost(boardUrl(s, "posts"), "Bearer ${s.token}",
                json.toString().toRequestBody(JSON))
        }
    }

    /** Sends only the fields that really changed (others may not touch type/openEdit). */
    suspend fun updatePost(original: BoardPost, d: PostDraft): NetworkResult<BoardPost> {
        val isAuthor = original.author?.mine == true
        val json = JsonObject()

        if (isAuthor && d.type != original.type) json.addProperty("type", d.type)

        val title = d.title.trim()
        if (title != original.title.orEmpty()) json.addProperty("title", title)

        val subject = d.subject.trim().ifEmpty { null }
        if (subject != original.subject) {
            if (subject == null) json.add("subject", JsonNull.INSTANCE)
            else json.addProperty("subject", subject)
        }

        val body = d.body.trim()
        if (body != original.body.orEmpty()) json.addProperty("body", body)

        if (d.dueDate != original.dueDate) {
            if (d.dueDate == null) json.add("dueDate", JsonNull.INSTANCE)
            else json.addProperty("dueDate", d.dueDate)
        }

        if (isAuthor && d.openEdit != original.openEdit) json.addProperty("openEdit", d.openEdit)

        if (json.entrySet().isEmpty()) return NetworkResult.Success(original)

        return authed { s ->
            api.boardUpdatePost(boardUrl(s, "posts/${original.id}"), "Bearer ${s.token}",
                json.toString().toRequestBody(JSON))
        }
    }

    suspend fun deletePost(postId: String): NetworkResult<Unit> =
        authedUnit { s -> api.boardDeletePost(boardUrl(s, "posts/$postId"), "Bearer ${s.token}") }

    suspend fun reportPost(postId: String): NetworkResult<Unit> =
        authedUnit { s -> api.boardReportPost(boardUrl(s, "posts/$postId/report"), "Bearer ${s.token}") }

    // ── Nickname ──────────────────────────────────────────────
    suspend fun updateNickname(nickname: String): NetworkResult<Unit> {
        val json = JsonObject().apply { addProperty("nickname", nickname.trim()) }
        val r = authedUnit { s ->
            api.boardUpdateMe("$BASE/me", "Bearer ${s.token}", json.toString().toRequestBody(JSON))
        }
        if (r is NetworkResult.Success) saveNickname(nickname.trim())
        return r
    }

    suspend fun confirmNickname() = saveNickname(null)

    private suspend fun saveNickname(newName: String?) {
        val accId = dataStore.activeAccountId.first() ?: return
        val s = dataStore.loadBoardSessions()[accId] ?: return
        dataStore.saveBoardSession(
            accId, s.copy(nickname = newName ?: s.nickname, nicknameConfirmed = true)
        )
    }

    // ─────────────────────────────────────────────────────────
    // SESSION: Untis token → board token
    // ─────────────────────────────────────────────────────────
    private fun boardUrl(s: BoardSession, path: String) =
        "$BASE/schools/${s.schoolId}/classes/${s.classId}/$path"

    private fun isExpired(s: BoardSession): Boolean =
        runCatching { Instant.parse(s.expiresAt).isBefore(Instant.now().plusSeconds(3600)) }
            .getOrDefault(true)

    private fun normalizeHost(server: String): String =
        server.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore("/")
            .removeSuffix(".webuntis.com") + ".webuntis.com"

    private suspend fun fetchUntisToken(server: String, sessionId: String): String? {
        return try {
            val r = api.getNewToken(UntisApiHelper.buildTokenUrl(server), sessionId.toSessionCookie())
            if (r.isSuccessful) {
                r.body()?.string()?.trim()?.removeSurrounding("\"")?.takeIf { it.startsWith("ey") }
            } else null
        } catch (_: Exception) { null }
    }

    private fun fallbackOrError(saved: BoardSession?, message: String): NetworkResult<BoardSession> =
        if (saved != null && !isExpired(saved)) NetworkResult.Success(saved)
        else NetworkResult.Error(message)

    private suspend fun ensureSession(force: Boolean): NetworkResult<BoardSession> =
        loginMutex.withLock {
            val accId = dataStore.activeAccountId.first()
                ?: return@withLock NetworkResult.Error("Kein Konto aktiv.")
            val saved = dataStore.loadBoardSessions()[accId]

            // Already logged in during this app run and token still valid
            if (!force && saved != null && accId in loggedInThisRun && !isExpired(saved)) {
                return@withLock NetworkResult.Success(saved)
            }

            sessionManager.ensureFresh()
            val creds     = dataStore.loginCredentials.first()
            val sessionId = dataStore.sessionId.first()
                ?: return@withLock fallbackOrError(saved, "Keine aktive Untis-Session.")

            val untisToken = fetchUntisToken(creds.server, sessionId)
                ?: return@withLock fallbackOrError(saved, "Untis-Anmeldung fehlgeschlagen.")

            val resp = try {
                api.boardAuth("$BASE/auth/untis", "Bearer $untisToken", normalizeHost(creds.server))
            } catch (e: Exception) {
                return@withLock fallbackOrError(saved, networkMessage(e))
            }

            if (resp.isSuccessful) {
                val body  = resp.body()
                val board = body?.boards?.firstOrNull()
                if (body == null || board == null || body.token.isBlank()) {
                    return@withLock NetworkResult.Error("Keine Klasse für dieses Konto gefunden.")
                }
                val session = BoardSession(
                    token             = body.token,
                    expiresAt         = body.expiresAt,
                    memberId          = body.member.id,
                    nickname          = body.member.nickname,
                    nicknameConfirmed = saved?.takeIf { it.memberId == body.member.id }
                        ?.nicknameConfirmed ?: false,
                    schoolId          = board.schoolId,
                    schoolName        = board.schoolName,
                    classId           = board.classId,
                    className         = board.className
                )
                dataStore.saveBoardSession(accId, session)
                loggedInThisRun.add(accId)
                NetworkResult.Success(session)
            } else {
                // Untis down → keep working with the old board token
                if (resp.code() == 502 && saved != null && !isExpired(saved)) {
                    loggedInThisRun.add(accId)
                    NetworkResult.Success(saved)
                } else {
                    NetworkResult.Error(messageFor(errorCode(resp), resp.code()))
                }
            }
        }

    /** Runs a board call; on an expired board session it logs in again once and retries. */
    private suspend fun <T> authedCall(
        block: suspend (BoardSession) -> Response<T>
    ): NetworkResult<Response<T>> {
        var session = when (val r = ensureSession(false)) {
            is NetworkResult.Success -> r.data
            is NetworkResult.Error   -> return NetworkResult.Error(r.message)
            else                     -> return NetworkResult.Error("Anmeldung fehlgeschlagen.")
        }
        for (attempt in 0..1) {
            val resp = try {
                block(session)
            } catch (e: Exception) {
                return NetworkResult.Error(networkMessage(e))
            }
            if (resp.isSuccessful) return NetworkResult.Success(resp)

            val code = errorCode(resp)
            if (attempt == 0 && (code == "invalid_session" || code == "membership_expired")) {
                session = when (val r = ensureSession(true)) {
                    is NetworkResult.Success -> r.data
                    is NetworkResult.Error   -> return NetworkResult.Error(r.message)
                    else                     -> return NetworkResult.Error("Anmeldung fehlgeschlagen.")
                }
                continue
            }
            return NetworkResult.Error(messageFor(code, resp.code()))
        }
        return NetworkResult.Error("Anmeldung fehlgeschlagen.")
    }

    private suspend fun <T> authed(block: suspend (BoardSession) -> Response<T>): NetworkResult<T> =
        when (val r = authedCall(block)) {
            is NetworkResult.Success ->
                r.data.body()?.let { NetworkResult.Success(it) }
                    ?: NetworkResult.Error("Leere Antwort vom Server.")
            is NetworkResult.Error -> NetworkResult.Error(r.message)
            else -> NetworkResult.Error("Fehler beim Schwarzen Brett.")
        }

    private suspend fun <T> authedUnit(block: suspend (BoardSession) -> Response<T>): NetworkResult<Unit> =
        when (val r = authedCall(block)) {
            is NetworkResult.Success -> NetworkResult.Success(Unit)
            is NetworkResult.Error   -> NetworkResult.Error(r.message)
            else -> NetworkResult.Error("Fehler beim Schwarzen Brett.")
        }

    // ── Errors ────────────────────────────────────────────────
    private fun networkMessage(e: Exception): String =
        if (e is java.net.UnknownHostException || e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException) "Keine Verbindung zum Schwarzen Brett."
        else e.localizedMessage ?: "Netzwerkfehler"

    private fun errorCode(resp: Response<*>): String = try {
        val raw = resp.errorBody()?.string().orEmpty()
        JsonParser.parseString(raw).asJsonObject.get("error").asString
    } catch (_: Exception) { "" }

    private fun messageFor(code: String, http: Int): String = when (code) {
        "not_a_student"      -> "Das Schwarze Brett ist nur für Schüler-Konten."
        "untis_rejected"     -> "Untis hat die Anmeldung abgelehnt. Bitte neu anmelden."
        "untis_unavailable"  -> "Untis ist gerade nicht erreichbar."
        "banned"             -> "Dein Zugang zum Schwarzen Brett wurde gesperrt."
        "host_mismatch"      -> "Diese Schule wird nicht unterstützt."
        "rate_limited"       -> "Zu viele Anfragen. Bitte kurz warten."
        "not_author",
        "edit_not_allowed"   -> "Das darfst du bei diesem Beitrag nicht."
        "own_post"           -> "Das geht bei eigenen Beiträgen nicht."
        "bad_nickname"       -> "Dieser Name ist nicht erlaubt (2–24 Zeichen)."
        "bad_request"        -> "Ungültige Eingabe."
        "too_large"          -> "Der Beitrag ist zu groß."
        "not_found"          -> "Beitrag nicht gefunden."
        else                 -> "Fehler beim Schwarzen Brett (HTTP $http)."
    }
}