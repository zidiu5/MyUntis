package com.myuntis.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val school: String = "", val server: String = "",
    val username: String = "", val password: String = "",
    val showPassword: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false
)

sealed class LoginEvent {
    data class SchoolChanged(val value: String)    : LoginEvent()
    data class ServerChanged(val value: String)    : LoginEvent()
    data class UsernameChanged(val value: String)  : LoginEvent()
    data class PasswordChanged(val value: String)  : LoginEvent()
    object TogglePasswordVisibility                : LoginEvent()
    object PerformLogin                            : LoginEvent()
    object ClearError                              : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init { tryAutoLogin() }

    // On every app start: try silent re-login with saved credentials.
    // This ensures the JSESSIONID and Bearer token are always fresh.
    private fun tryAutoLogin() {
        viewModelScope.launch {
            val saved = authRepository.getSavedCredentials()
            if (saved == null || saved.username.isBlank() || saved.password.isBlank()) return@launch

            // Pre-fill the form in case auto-login fails
            _uiState.update {
                it.copy(
                    school   = saved.school,   server   = saved.server,
                    username = saved.username, password = saved.password,
                    isLoading = true
                )
            }

            when (authRepository.login(saved.username, saved.password, saved.school, saved.server)) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                else ->
                    _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.SchoolChanged    -> _uiState.update { it.copy(school    = event.value, errorMessage = null) }
            is LoginEvent.ServerChanged    -> _uiState.update { it.copy(server    = event.value, errorMessage = null) }
            is LoginEvent.UsernameChanged  -> _uiState.update { it.copy(username  = event.value, errorMessage = null) }
            is LoginEvent.PasswordChanged  -> _uiState.update { it.copy(password  = event.value, errorMessage = null) }
            is LoginEvent.TogglePasswordVisibility -> _uiState.update { it.copy(showPassword = !it.showPassword) }
            is LoginEvent.PerformLogin     -> performLogin()
            is LoginEvent.ClearError       -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun performLogin() {
        val s = _uiState.value
        val err = when {
            s.server.isBlank()   -> "Bitte Server eingeben"
            s.school.isBlank()   -> "Bitte Schule eingeben"
            s.username.isBlank() -> "Bitte Benutzername eingeben"
            s.password.isBlank() -> "Bitte Passwort eingeben"
            else                 -> null
        }
        if (err != null) { _uiState.update { it.copy(errorMessage = err) }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = authRepository.login(s.username.trim(), s.password, s.school.trim(), s.server.trim())) {
                is NetworkResult.Success -> _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                is NetworkResult.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}