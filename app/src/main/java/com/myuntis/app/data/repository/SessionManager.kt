package com.myuntis.app.data.repository

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the WebUntis session alive. If the last login is older than
 * MAX_AGE_MS, a new login is done silently in the background.
 */
@Singleton
class SessionManager @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val mutex = Mutex()   // only ONE re-login at a time

    private val _renewed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits after the app came back and a new session was created. */
    val sessionRenewed: SharedFlow<Unit> = _renewed.asSharedFlow()

    /** Call before network loads. Cheap if the session is still fresh. */
    suspend fun ensureFresh() { refreshIfStale() }

    /** Call when the app returns from the background. */
    suspend fun onAppResumed() {
        if (refreshIfStale()) _renewed.tryEmit(Unit)
    }

    // true = a new login was performed
    private suspend fun refreshIfStale(): Boolean = mutex.withLock {
        val last  = authRepository.lastLoginAt
        val fresh = last != 0L && SystemClock.elapsedRealtime() - last < MAX_AGE_MS
        if (fresh) false else authRepository.refreshSession()
    }

    companion object {
        private const val MAX_AGE_MS = 5 * 60 * 1000L   // 5 minutes
    }
}