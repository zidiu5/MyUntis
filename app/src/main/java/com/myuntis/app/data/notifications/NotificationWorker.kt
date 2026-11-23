package com.myuntis.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun checker(): NotificationChecker
        fun scheduler(): NotificationScheduler
    }

    override suspend fun doWork(): Result {
        val reminder = inputData.getBoolean(KEY_REMINDER, false)
        val force    = inputData.getBoolean(KEY_FORCE, false)
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            when {
                // Exact-time job: only the homework reminders (also fine while the app is open)
                reminder -> deps.checker().runReminders()
                // Periodic job: stays quiet while the user is in the app (unless "Jetzt prüfen")
                !force && AppForeground.isForeground ->
                    deps.checker().recordStatus("App war geöffnet, Prüfung übersprungen")
                else -> {
                    deps.checker().runChecks()
                    deps.scheduler().scheduleReminder(replace = false)   // makes sure tomorrow's job exists
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success()      // the next run tries again
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_REMINDER = "reminder"
    }
}