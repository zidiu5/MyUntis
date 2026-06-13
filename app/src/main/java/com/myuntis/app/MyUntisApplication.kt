package com.myuntis.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// =============================================================
// APPLICATION CLASS
// =============================================================
// Every Android app has an Application class - it's the first
// object created when the app starts, before any Activity.
//
// @HiltAndroidApp triggers Hilt's code generation and creates
// the application-level dependency injection component.
// This MUST be declared in AndroidManifest.xml (android:name)
// =============================================================
@HiltAndroidApp
class MyUntisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization goes here.
        // Hilt is automatically initialized by @HiltAndroidApp.
        // We can add logging setup, crash reporting, etc. here later.
    }
}