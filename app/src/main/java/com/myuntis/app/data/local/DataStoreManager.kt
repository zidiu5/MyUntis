package com.myuntis.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================
// DATASTORE MANAGER
// =============================================================
// DataStore is Android's modern replacement for SharedPreferences.
// Key differences:
// - Asynchronous: uses Kotlin Flow (never blocks the main thread)
// - Type-safe: keys have explicit types
// - Crash-safe: atomic reads/writes, no partial updates
//
// @Singleton: only one instance exists in the whole app (Hilt)
// @ApplicationContext: we need a context but NOT an Activity context
//   (Activity can be destroyed; Application lives as long as the app)
// =============================================================
@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ----- DATASTORE INSTANCE -----
    // The 'by preferencesDataStore' delegate creates a DataStore
    // instance that is tied to this Context.
    // "myuntis_prefs" is the file name on disk.
    private val dataStore: DataStore<Preferences> = context.dataStore

    // ----- PREFERENCE KEYS -----
    // Keys are typed - a stringPreferencesKey can only store Strings.
    // This prevents type mismatches at compile time.
    companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")         // TODO: encrypt in prod
        val KEY_SCHOOL = stringPreferencesKey("school")
        val KEY_SERVER = stringPreferencesKey("server")             // e.g., "herakles.webuntis.com"
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

        val KEY_TIMETABLE_CACHE = stringPreferencesKey("timetable_cache_v2")
    }

    // ----- READ OPERATIONS (Flow) -----
    // Flow automatically emits a new value whenever the data changes.
    // The UI (via ViewModel) collects these flows and updates automatically.

    // Observe all login credentials at once
    val loginCredentials: Flow<LoginCredentials> = dataStore.data
        .catch { exception ->
            // If DataStore throws IOException, emit empty preferences
            // (avoids crashing the app if the file is corrupted)
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

    suspend fun addKnownSubjects(subjects: List<String>) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_KNOWN_SUBJECTS] ?: "")
                .split(",").filter { it.isNotBlank() }.toMutableSet()
            current.addAll(subjects)
            prefs[KEY_KNOWN_SUBJECTS] = current.joinToString(",")
        }
    }

    // ----- WRITE OPERATIONS (suspend functions) -----
    // 'suspend' means these run on a coroutine (not the main thread)

    // Save login credentials after successful login
    suspend fun saveLoginCredentials(
        username: String,
        password: String,
        school: String,
        server: String
    ) {
        dataStore.edit { preferences ->
            // 'edit' provides a MutablePreferences block
            // All changes here are written atomically (all or nothing)
            preferences[KEY_USERNAME] = username
            preferences[KEY_PASSWORD] = password
            preferences[KEY_SCHOOL] = school
            preferences[KEY_SERVER] = server
        }
    }
    suspend fun saveBearerToken(token: String) {
        dataStore.edit { it[KEY_BEARER_TOKEN] = token }
    }

    // Save session data after successful authentication
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

    // Clear all session data (but keep credentials for re-login)
    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_ID)
            preferences[KEY_IS_LOGGED_IN] = false
        }
    }

    // Complete logout: clear everything
    suspend fun clearAllData() {
        dataStore.edit { preferences ->
            preferences.clear()     // Removes ALL stored preferences
        }
    }

    // Save settings
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    suspend fun updateLastSync() {
        dataStore.edit { it[KEY_LAST_SYNC] = System.currentTimeMillis() }
    }
    // Returns a set of homework IDs the user has marked as done
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
    // ── Timetable raw JSON cache ──────────────────────────────
    suspend fun saveTimetableCacheRaw(json: String) {
        dataStore.edit { it[KEY_TIMETABLE_CACHE] = json }
    }

    suspend fun loadTimetableCacheRaw(): String? {
        return try {
            dataStore.data.first()[KEY_TIMETABLE_CACHE]
        } catch (_: Exception) { null }
    }
}

// Extension property to create/access the DataStore
// Created once per app process, tied to the application context
private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "myuntis_prefs")

// ----- DATA CLASSES for grouped preferences -----

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