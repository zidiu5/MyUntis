package com.myuntis.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.UntisApiHelper
import com.myuntis.app.data.network.UntisApiService
import com.myuntis.app.data.network.model.SchoolResult
import com.myuntis.app.data.repository.AuthRepository
import com.myuntis.app.domain.model.SavedAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    // School search
    val schoolSearchQuery: String         = "",
    val schoolResults: List<SchoolResult> = emptyList(),
    val isSearching: Boolean              = false,
    val selectedSchool: SchoolResult?     = null,
    // Credentials
    val username: String                  = "",
    val password: String                  = "",
    val showPassword: Boolean             = false,
    // Accounts
    val accounts: List<SavedAccount>      = emptyList(),
    val showAccountPicker: Boolean        = false,
    val canGoBack: Boolean                = false,   // form → back to picker
    // Status (true at start = splash until we know what to show)
    val isLoading: Boolean                = true,
    val errorMessage: String?             = null,
    val isLoginSuccessful: Boolean        = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val apiService: UntisApiService,
    private val dataStore: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init { decideStartScreen() }

    // ── What to show at start ────────────────────────────────
    private fun decideStartScreen() {
        viewModelScope.launch {
            val intent   = dataStore.consumeLoginIntent()   // "", "switch" or "add"
            val accounts = dataStore.accounts.first()
            _uiState.update { it.copy(accounts = accounts) }

            when {
                intent == "add" ->
                    _uiState.update {
                        it.copy(isLoading = false, showAccountPicker = false,
                            canGoBack = accounts.isNotEmpty())
                    }

                (intent == "switch" && accounts.isNotEmpty()) || accounts.size >= 2 ->
                    _uiState.update { it.copy(isLoading = false, showAccountPicker = true) }

                accounts.size == 1 ->
                    doLogin(accounts.first())

                else -> {
                    // Old install (before multi-account): migrate saved credentials
                    val saved = if (!dataStore.isAccountsInitialized())
                        authRepository.getSavedCredentials() else null

                    if (saved != null && saved.password.isNotBlank()) {
                        when (authRepository.login(
                            saved.username, saved.password, saved.school, saved.server
                        )) {
                            is NetworkResult.Success ->
                                _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                            else ->
                                _uiState.update { it.copy(isLoading = false) }
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    private suspend fun doLogin(account: SavedAccount) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        when (val r = authRepository.login(
            username          = account.username,
            password          = account.password,
            school            = account.school,
            server            = account.server,
            schoolDisplayName = account.schoolName
        )) {
            is NetworkResult.Success ->
                _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
            is NetworkResult.Error ->
                _uiState.update {
                    it.copy(isLoading = false, showAccountPicker = true, errorMessage = r.message)
                }
            else ->
                _uiState.update { it.copy(isLoading = false, showAccountPicker = true) }
        }
    }

    // ── Account picker actions ───────────────────────────────
    fun onAccountSelected(account: SavedAccount) {
        viewModelScope.launch { doLogin(account) }
    }

    fun onAddAccount() = _uiState.update {
        it.copy(
            showAccountPicker = false, canGoBack = true, errorMessage = null,
            selectedSchool = null, schoolSearchQuery = "", schoolResults = emptyList(),
            username = "", password = ""
        )
    }

    fun onBackToPicker() =
        _uiState.update { it.copy(showAccountPicker = true, errorMessage = null) }

    fun onRemoveAccount(account: SavedAccount) {
        viewModelScope.launch {
            authRepository.removeAccount(account.id)
            val left = dataStore.accounts.first()
            _uiState.update {
                it.copy(accounts = left, showAccountPicker = left.isNotEmpty(),
                    canGoBack = false, errorMessage = null)
            }
        }
    }

    // ── School search with 400ms debounce ────────────────────
    fun onSchoolSearchChanged(query: String) {
        _uiState.update {
            it.copy(schoolSearchQuery = query, selectedSchool = null, errorMessage = null)
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
            it.copy(selectedSchool = school, schoolSearchQuery = school.displayName,
                schoolResults = emptyList(), isSearching = false)
        }
    }

    fun clearSchoolSelection() = _uiState.update {
        it.copy(selectedSchool = null, schoolSearchQuery = "", schoolResults = emptyList())
    }

    fun onUsernameChanged(value: String) =
        _uiState.update { it.copy(username = value, errorMessage = null) }

    fun onPasswordChanged(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun togglePasswordVisibility() =
        _uiState.update { it.copy(showPassword = !it.showPassword) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ── Login with the form ──────────────────────────────────
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
                username          = s.username.trim(),
                password          = s.password,
                school            = school.loginName,
                server            = school.server,
                schoolDisplayName = school.displayName
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