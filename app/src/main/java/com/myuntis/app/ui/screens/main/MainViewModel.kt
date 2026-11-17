package com.myuntis.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.repository.Preloader
import com.myuntis.app.data.repository.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val preloader: Preloader
) : ViewModel() {

    init {
        viewModelScope.launch { preloader.preload(force = false) }          // app start
        viewModelScope.launch {
            sessionManager.sessionRenewed.collect { preloader.preload(force = true) }  // back from background
        }
    }

    fun onAppResumed() {
        viewModelScope.launch { sessionManager.onAppResumed() }
    }
}