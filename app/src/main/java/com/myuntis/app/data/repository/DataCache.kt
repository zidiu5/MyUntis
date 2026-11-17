package com.myuntis.app.data.repository

import android.os.SystemClock
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object CacheKeys {
    const val TIMETABLE = "timetable"
    const val HOMEWORK  = "homework"
    const val EXAMS     = "exams"
    const val CLASSREG  = "classreg"
    const val ABSENCES  = "absences"
    const val YEARS     = "years"
    fun grades(yearId: Int?) = "grades:" + (yearId ?: "current")

    const val MAX_AGE_MS           = 10 * 60 * 1000L   // screens reuse data up to 10 min
    const val TIMETABLE_MAX_AGE_MS = 5 * 60 * 1000L
    const val OLD_YEAR_MAX_AGE_MS  = 12 * 60 * 60 * 1000L   // old school years do not change
}

/** In-memory cache that survives tab switches. Cleared when another account becomes active. */
@Singleton
class DataCache @Inject constructor(dataStore: DataStoreManager) {

    private val store = ConcurrentHashMap<String, Any>()
    private val times = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val version = MutableStateFlow(0L)
    @Volatile private var generation = 0
    @Volatile private var accountId: String? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            dataStore.activeAccountId.distinctUntilChanged().collect { id ->
                val previous = accountId
                accountId = id
                if (previous != null && previous != id) clear()   // other account → drop everything
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? = store[key] as T?

    fun put(key: String, value: Any) {
        store[key] = value
        times[key] = SystemClock.elapsedRealtime()
        version.update { it + 1 }
    }

    fun clear() {
        generation++
        store.clear(); times.clear()
        version.update { it + 1 }
    }

    fun ageMs(key: String): Long =
        times[key]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE

    /** Emits the current value and every later change (null = nothing cached). */
    fun <T : Any> flow(key: String): Flow<T?> =
        version.map { get<T>(key) }.distinctUntilChanged()

    /**
     * Returns cached data if it is younger than [maxAgeMs] (and not [force]d), otherwise runs [fetch].
     * Parallel calls for the same key wait for each other, so nothing is loaded twice.
     */
    suspend fun <T : Any> load(
        key: String,
        maxAgeMs: Long,
        force: Boolean,
        fetch: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            val cached = get<T>(key)
            if (!force && cached != null && ageMs(key) < maxAgeMs) {
                return@withLock NetworkResult.Success(cached)
            }
            val gen = generation
            val result = fetch()
            // ignore results that arrive after an account switch
            if (result is NetworkResult.Success && gen == generation) put(key, result.data)
            result
        }
    }
}

/** Year list as a cacheable result (an empty list counts as an error, so it is not cached). */
suspend fun GradesRepository.availableYearsResult(): NetworkResult<List<SchoolYearOption>> {
    val years = getAvailableSchoolYears()
    return if (years.isEmpty()) NetworkResult.Error("Keine Schuljahre gefunden")
    else NetworkResult.Success(years)
}