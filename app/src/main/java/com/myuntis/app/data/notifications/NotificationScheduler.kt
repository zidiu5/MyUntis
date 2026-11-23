package com.myuntis.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.myuntis.app.data.local.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStoreManager
) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Starts the checks if any switch is on, stops everything otherwise. */
    suspend fun sync() {
        val wm = WorkManager.getInstance(context)
        if (!dataStore.notificationSettings.first().anyEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork(REMINDER_WORK)
            return
        }
        val periodic = PeriodicWorkRequestBuilder<NotificationWorker>(30, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic)
        scheduleReminder(replace = false)
    }

    /**
     * One job that starts at the reminder time (today, or tomorrow if it has passed).
     * replace = true when the user changed the time. Otherwise a pending job is kept.
     */
    suspend fun scheduleReminder(replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        val s = dataStore.notificationSettings.first()
        if (!(s.isOn(NotifType.HOMEWORK_TAB) || s.isOn(NotifType.HOMEWORK_BOARD))) {
            wm.cancelUniqueWork(REMINDER_WORK)
            return
        }
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(s.reminderMinutes / 60, s.reminderMinutes % 60)
        if (!target.isAfter(now)) target = target.plusDays(1)

        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(Duration.between(now, target).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(online)
            .setInputData(workDataOf(NotificationWorker.KEY_REMINDER to true))
            .build()
        wm.enqueueUniqueWork(
            REMINDER_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** One full check right now, also while the app is open ("Jetzt prüfen"). */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setConstraints(online)
            .setInputData(workDataOf(NotificationWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val WORK_NAME = "notification_check"
        const val REMINDER_WORK = "homework_reminder"
    }
}