package com.myuntis.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.notifications.NotificationChecker
import com.myuntis.app.data.notifications.NotificationScheduler
import com.myuntis.app.data.repository.Preloader
import com.myuntis.app.data.repository.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val preloader: Preloader,
    private val scheduler: NotificationScheduler,
    private val checker: NotificationChecker
) : ViewModel() {

    init {
        viewModelScope.launch { scheduler.sync() }

        viewModelScope.launch {
            preloader.preload(force = false)
            checker.markSeenFromApp()
        }

        viewModelScope.launch {
            sessionManager.sessionRenewed.collect {
                preloader.preload(force = true)
                checker.markSeenFromApp()
            }
        }
    }

    fun onAppResumed() {
        viewModelScope.launch { sessionManager.onAppResumed() }
    }

    fun onAppStopped() {
        viewModelScope.launch { checker.markSeenFromApp() }
    }
}