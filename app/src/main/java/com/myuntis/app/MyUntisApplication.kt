package com.myuntis.app

import android.app.Application
import com.myuntis.app.data.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// =============================================================
// APPLICATION CLASS
// =============================================================
@HiltAndroidApp
class MyUntisApplication : Application() {

    @Inject
    lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        // Erstellt die Notification Channels für Android 8+
        notifier.ensureChannels()
    }
}