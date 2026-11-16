package com.myuntis.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myuntis.app.domain.model.SavedAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.myuntis.app.data.network.model.BoardCache
import com.myuntis.app.data.network.model.BoardSession

// =============================================================
// DATASTORE MANAGER
// =============================================================
@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_SCHOOL = stringPreferencesKey("school")
        val KEY_SERVER = stringPreferencesKey("server")
        val KEY_SESSION_ID = stringPreferencesKey("session_id")
        val KEY_PERSON_ID = intPreferencesKey("person_id")
        val KEY_PERSON_TYPE = intPreferencesKey("person_type")
        val KEY_KLASSE_ID = intPreferencesKey("klasse_id")
        val KEY_FULL_NAME = stringPreferencesKey("full_name")
        val KEY_CLASS_NAME = stringPreferencesKey("class_name")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        val KEY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val KEY_COMPLETED_HOMEWORK = stringPreferencesKey("completed_homework_ids")
        val KEY_SCHOOLYEAR_ID = intPreferencesKey("schoolyear_id")
        val KEY_SUBJECT_COLORS = stringPreferencesKey("subject_colors_v2")
        val KEY_KNOWN_SUBJECTS = stringPreferencesKey("known_subjects")
        val KEY_SUBJECTS_YEAR = intPreferencesKey("known_subjects_year")

        val KEY_TIMETABLE_CACHE = stringPreferencesKey("timetable_cache_v2")

        val KEY_ACCOUNTS       = stringPreferencesKey("accounts_v1")
        val KEY_ACTIVE_ACCOUNT = stringPreferencesKey("active_account_id")
        val KEY_LOGIN_INTENT   = stringPreferencesKey("login_intent")

        val KEY_MENSA_CACHE  = stringPreferencesKey("mensa_cache_v1")
        val KEY_MENSA_VEG    = booleanPreferencesKey("mensa_vegetarian")
        val KEY_BOARD_SESSIONS = stringPreferencesKey("board_sessions_v1")
    }

    // ----- READ OPERATIONS (Flow) -----

    val loginCredentials: Flow<LoginCredentials> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            LoginCredentials(
                username = preferences[KEY_USERNAME] ?: "",
                password = preferences[KEY_PASSWORD] ?: "",
                school = preferences[KEY_SCHOOL] ?: "",
                server = preferences[KEY_SERVER] ?: ""
            )
        }

    val isLoggedIn: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_IS_LOGGED_IN] ?: false }

    val sessionId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_SESSION_ID] }

    val userProfile: Flow<UserProfile> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            UserProfile(
                personId = preferences[KEY_PERSON_ID] ?: 0,
                personType = preferences[KEY_PERSON_TYPE] ?: 5,
                klasseId = preferences[KEY_KLASSE_ID] ?: 0,
                fullName = preferences[KEY_FULL_NAME] ?: "",
                className = preferences[KEY_CLASS_NAME] ?: ""
            )
        }

    val isDarkMode: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_DARK_MODE] ?: false }

    val isAutoSync: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_AUTO_SYNC] ?: true }

    val bearerToken: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BEARER_TOKEN] }

    val schoolyearId: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SCHOOLYEAR_ID] ?: 0 }

    // ── Subject colors ────────────────────────────────────────────
    val subjectColors: Flow<Map<String, String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[KEY_SUBJECT_COLORS] ?: return@map emptyMap()
            raw.split(";").filter { it.contains(":") }.associate { entry ->
                val i = entry.indexOf(":")
                entry.substring(0, i) to entry.substring(i + 1)
            }
        }

    val knownSubjects: Flow<List<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            (prefs[KEY_KNOWN_SUBJECTS] ?: "").split(",").filter { it.isNotBlank() }
        }

    // ── Mensa ─────────────────────────────────────────────────
    val mensaVegetarian: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_MENSA_VEG] ?: false }

    suspend fun setSubjectColor(subjectShort: String, hexColor: String) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY_SUBJECT_COLORS] ?: ""
            val map = raw.split(";").filter { it.contains(":") }.associate { e ->
                val i = e.indexOf(":"); e.substring(0, i) to e.substring(i + 1)
            }.toMutableMap()
            map[subjectShort] = hexColor
            prefs[KEY_SUBJECT_COLORS] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    suspend fun addKnownSubjectsForYear(schoolYear: Int, subjects: List<String>) {
        dataStore.edit { prefs ->
            val sameYear = prefs[KEY_SUBJECTS_YEAR] == schoolYear
            val current = if (sameYear)
                (prefs[KEY_KNOWN_SUBJECTS] ?: "")
                    .split(",").filter { it.isNotBlank() }.toMutableSet()
            else
                mutableSetOf()

            current.addAll(subjects)
            prefs[KEY_KNOWN_SUBJECTS] = current.joinToString(",")
            prefs[KEY_SUBJECTS_YEAR]  = schoolYear
        }
    }

    // ----- WRITE OPERATIONS -----

    suspend fun saveLoginCredentials(
        username: String,
        password: String,
        school: String,
        server: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = username
            preferences[KEY_PASSWORD] = password
            preferences[KEY_SCHOOL] = school
            preferences[KEY_SERVER] = server
        }
    }

    suspend fun saveBearerToken(token: String) {
        dataStore.edit { it[KEY_BEARER_TOKEN] = token }
    }

    suspend fun saveSession(
        sessionId: String,
        personId: Int,
        personType: Int,
        klasseId: Int,
        fullName: String,
        className: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_SESSION_ID] = sessionId
            preferences[KEY_PERSON_ID] = personId
            preferences[KEY_PERSON_TYPE] = personType
            preferences[KEY_KLASSE_ID] = klasseId
            preferences[KEY_FULL_NAME] = fullName
            preferences[KEY_CLASS_NAME] = className
            preferences[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_ID)
            preferences[KEY_IS_LOGGED_IN] = false
        }
    }

    suspend fun clearAllData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    suspend fun updateLastSync() {
        dataStore.edit { it[KEY_LAST_SYNC] = System.currentTimeMillis() }
    }

    suspend fun getCompletedHomeworkIds(): Set<Int> {
        val raw = dataStore.data.first()[KEY_COMPLETED_HOMEWORK] ?: ""
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    suspend fun setHomeworkCompleted(id: Int, completed: Boolean) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_COMPLETED_HOMEWORK] ?: "")
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toMutableSet()

            if (completed) current.add(id) else current.remove(id)

            prefs[KEY_COMPLETED_HOMEWORK] = current.joinToString(",")
        }
    }

    suspend fun saveSchoolYearId(id: Int) {
        dataStore.edit { it[KEY_SCHOOLYEAR_ID] = id }
    }

    suspend fun saveTimetableCacheRaw(json: String) {
        dataStore.edit { it[KEY_TIMETABLE_CACHE] = json }
    }

    suspend fun loadTimetableCacheRaw(): String? {
        return try {
            dataStore.data.first()[KEY_TIMETABLE_CACHE]
        } catch (_: Exception) { null }
    }

    // ── Multiple accounts ─────────────────────────────────────────
    private fun parseAccounts(raw: String?): List<SavedAccount> = try {
        if (raw.isNullOrBlank()) emptyList()
        else Gson().fromJson<List<SavedAccount>>(
            raw, object : TypeToken<List<SavedAccount>>() {}.type
        ) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val accounts: Flow<List<SavedAccount>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { parseAccounts(it[KEY_ACCOUNTS]) }

    val activeAccountId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ACTIVE_ACCOUNT] }

    suspend fun isAccountsInitialized(): Boolean =
        dataStore.data.first()[KEY_ACCOUNTS] != null

    suspend fun upsertAccount(account: SavedAccount) {
        dataStore.edit { prefs ->
            val list = parseAccounts(prefs[KEY_ACCOUNTS]).toMutableList()
            val i = list.indexOfFirst { it.id == account.id }
            if (i >= 0) list[i] = account else list.add(account)
            prefs[KEY_ACCOUNTS]       = Gson().toJson(list)
            prefs[KEY_ACTIVE_ACCOUNT] = account.id
        }
    }

    suspend fun removeAccount(id: String) {
        dataStore.edit { prefs ->
            val list = parseAccounts(prefs[KEY_ACCOUNTS]).filterNot { it.id == id }
            prefs[KEY_ACCOUNTS] = Gson().toJson(list)
        }
    }

    suspend fun setLoginIntent(intent: String) {
        dataStore.edit { it[KEY_LOGIN_INTENT] = intent }
    }

    suspend fun consumeLoginIntent(): String {
        var value = ""
        dataStore.edit { prefs ->
            value = prefs[KEY_LOGIN_INTENT] ?: ""
            prefs.remove(KEY_LOGIN_INTENT)
        }
        return value
    }

    suspend fun clearUserData() {
        dataStore.edit { prefs ->
            prefs[KEY_TIMETABLE_CACHE] = "[]"
            prefs.remove(KEY_KNOWN_SUBJECTS)
            prefs.remove(KEY_SUBJECTS_YEAR)
        }
    }

    // ── Mensa ─────────────────────────────────────────────────
    suspend fun setMensaVegetarian(value: Boolean) { dataStore.edit { it[KEY_MENSA_VEG] = value } }
    suspend fun saveMensaCache(json: String)       { dataStore.edit { it[KEY_MENSA_CACHE] = json } }

    suspend fun loadMensaCache(): String? =
        try { dataStore.data.first()[KEY_MENSA_CACHE] } catch (_: Exception) { null }

    // ── Schwarzes Brett ───────────────────────────────────────────
    private fun parseBoardSessions(raw: String?): Map<String, BoardSession> = try {
        if (raw.isNullOrBlank()) emptyMap()
        else Gson().fromJson<Map<String, BoardSession>>(
            raw, object : TypeToken<Map<String, BoardSession>>() {}.type
        ) ?: emptyMap()
    } catch (_: Exception) { emptyMap() }

    suspend fun loadBoardSessions(): Map<String, BoardSession> =
        parseBoardSessions(dataStore.data.first()[KEY_BOARD_SESSIONS])

    suspend fun saveBoardSession(accountId: String, session: BoardSession) {
        dataStore.edit { prefs ->
            val map = parseBoardSessions(prefs[KEY_BOARD_SESSIONS]).toMutableMap()
            map[accountId] = session
            prefs[KEY_BOARD_SESSIONS] = Gson().toJson(map)
        }
    }

    suspend fun loadBoardCache(accountId: String): BoardCache? = try {
        dataStore.data.first()[stringPreferencesKey("board_cache_$accountId")]
            ?.let { Gson().fromJson(it, BoardCache::class.java) }
    } catch (_: Exception) { null }

    suspend fun saveBoardCache(accountId: String, cache: BoardCache) {
        dataStore.edit { it[stringPreferencesKey("board_cache_$accountId")] = Gson().toJson(cache) }
    }

    // "done" ticks, only on this phone, per account
    fun boardDoneIds(accountId: String): Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[stringSetPreferencesKey("board_done_$accountId")] ?: emptySet() }

    suspend fun setBoardDone(accountId: String, postId: String, done: Boolean) {
        dataStore.edit { prefs ->
            val key = stringSetPreferencesKey("board_done_$accountId")
            val set = (prefs[key] ?: emptySet()).toMutableSet()
            if (done) set.add(postId) else set.remove(postId)
            prefs[key] = set
        }
    }
}

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "myuntis_prefs")

data class LoginCredentials(
    val username: String,
    val password: String,
    val school: String,
    val server: String
)

data class UserProfile(
    val personId: Int,
    val personType: Int,
    val klasseId: Int,
    val fullName: String,
    val className: String
)