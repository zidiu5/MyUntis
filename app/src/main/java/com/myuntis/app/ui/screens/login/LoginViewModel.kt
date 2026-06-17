package com.myuntis.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.model.SchoolResult
import com.myuntis.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    // School search
    val schoolSearchQuery: String       = "",
    val schoolResults: List<SchoolResult> = emptyList(),
    val isSearching: Boolean            = false,
    val selectedSchool: SchoolResult?   = null,
    // Credentials
    val username: String                = "",
    val password: String                = "",
    val showPassword: Boolean           = false,
    // Status
    val isLoading: Boolean              = false,
    val errorMessage: String?           = null,
    val isLoginSuccessful: Boolean      = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val apiService: UntisApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init { tryAutoLogin() }

    // ── Auto-login with saved credentials ────────────────────
    private fun tryAutoLogin() {
        viewModelScope.launch {
            val saved = authRepository.getSavedCredentials() ?: return@launch
            if (saved.username.isBlank() || saved.password.isBlank()) return@launch

            _uiState.update { it.copy(isLoading = true) }
            when (authRepository.login(saved.username, saved.password, saved.school, saved.server)) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                else ->
                    _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── School search with 400ms debounce ────────────────────
    fun onSchoolSearchChanged(query: String) {
        _uiState.update {
            it.copy(
                schoolSearchQuery = query,
                selectedSchool    = null,
                errorMessage      = null
            )
        }
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(schoolResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val url = UntisApiHelper.buildSchoolSearchUrl()
                val req = UntisApiHelper.buildSchoolSearchRequest(query)
                val schools = apiService.searchSchools(url, req)
                    .body()?.result?.schools ?: emptyList()
                _uiState.update { it.copy(isSearching = false, schoolResults = schools) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false, schoolResults = emptyList()) }
            }
        }
    }

    fun onSchoolSelected(school: SchoolResult) {
        _uiState.update {
            it.copy(
                selectedSchool    = school,
                schoolSearchQuery = school.displayName,
                schoolResults     = emptyList(),
                isSearching       = false
            )
        }
    }

    fun clearSchoolSelection() {
        _uiState.update {
            it.copy(selectedSchool = null, schoolSearchQuery = "", schoolResults = emptyList())
        }
    }

    fun onUsernameChanged(value: String) =
        _uiState.update { it.copy(username = value, errorMessage = null) }

    fun onPasswordChanged(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun togglePasswordVisibility() =
        _uiState.update { it.copy(showPassword = !it.showPassword) }

    fun clearError() =
        _uiState.update { it.copy(errorMessage = null) }

    // ── Login ─────────────────────────────────────────────────
    fun performLogin() {
        val s = _uiState.value

        val school = s.selectedSchool ?: run {
            _uiState.update { it.copy(errorMessage = "Bitte wähle zuerst eine Schule aus") }
            return
        }
        if (s.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Bitte Benutzername eingeben") }
            return
        }
        if (s.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Bitte Passwort eingeben") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = authRepository.login(
                username = s.username.trim(),
                password = s.password,
                school   = school.loginName,   // "lbs-brixen"
                server   = school.server       // "lbs-brixen.webuntis.com"
            )) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                is NetworkResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                else ->
                    _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}