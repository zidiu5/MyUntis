package com.myuntis.app.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myuntis.app.data.network.model.SchoolResult

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) onLoginSuccess()
    }

    // Full-screen gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1B4B), Color(0xFF1565C0), Color(0xFF1E88E5))
                )
            )
    ) {
        if (uiState.isLoading && uiState.selectedSchool == null) {
            // Auto-login splash
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("MyUntis", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
            }
        } else {
            LoginContent(
                uiState  = uiState,
                onSearchChanged  = viewModel::onSchoolSearchChanged,
                onSchoolSelected = viewModel::onSchoolSelected,
                onClearSchool    = viewModel::clearSchoolSelection,
                onUsernameChanged= viewModel::onUsernameChanged,
                onPasswordChanged= viewModel::onPasswordChanged,
                onTogglePw       = viewModel::togglePasswordVisibility,
                onLogin          = viewModel::performLogin,
                isLoggingIn      = uiState.isLoading
            )
        }
    }
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onSearchChanged: (String) -> Unit,
    onSchoolSelected: (SchoolResult) -> Unit,
    onClearSchool: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePw: () -> Unit,
    onLogin: () -> Unit,
    isLoggingIn: Boolean
) {
    val focusManager     = LocalFocusManager.current
    val usernameFocus    = remember { FocusRequester() }
    val passwordFocus    = remember { FocusRequester() }

    LaunchedEffect(uiState.selectedSchool) {
        if (uiState.selectedSchool != null) {
            kotlinx.coroutines.delay(150)   // Warte auf Recomposition
            usernameFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        // ── Logo + Title ─────────────────────────────────────
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.School, null,
                tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("MyUntis", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black, color = Color.White)
        Text("WebUntis für dein Handy",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f))

        Spacer(Modifier.height(40.dp))

        // ── Login Card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Text("Anmelden", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)

                // ── Step 1: School search ─────────────────────
                if (uiState.selectedSchool == null) {
                    SchoolSearchField(
                        query      = uiState.schoolSearchQuery,
                        results    = uiState.schoolResults,
                        isSearching = uiState.isSearching,
                        onQueryChanged  = onSearchChanged,
                        onSchoolSelected = { school ->
                            onSchoolSelected(school)
                        }
                    )
                } else {
                    // ── Selected school chip ──────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.School, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.selectedSchool.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(uiState.selectedSchool.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                        }
                        IconButton(
                            onClick  = onClearSchool,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Close, "Schule ändern",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // ── Step 2: Username ──────────────────────
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChanged,
                        label         = { Text("Benutzername") },
                        leadingIcon   = { Icon(Icons.Outlined.Person, null) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocus),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect    = false,
                            imeAction      = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocus.requestFocus() }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // ── Step 3: Password ──────────────────────
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label         = { Text("Passwort") },
                        leadingIcon   = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon  = {
                            IconButton(onClick = onTogglePw) {
                                Icon(
                                    if (uiState.showPassword) Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                    null
                                )
                            }
                        },
                        visualTransformation = if (uiState.showPassword)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); onLogin() }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // ── Error message ─────────────────────────────
                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    uiState.errorMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Error, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                // ── Login button ──────────────────────────────
                if (uiState.selectedSchool != null) {
                    Button(
                        onClick  = { focusManager.clearFocus(); onLogin() },
                        enabled  = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Outlined.Login, null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Anmelden", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Inoffizielle App · Nicht von Untis GmbH",
            style     = MaterialTheme.typography.labelSmall,
            color     = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================
// SCHOOL SEARCH FIELD + RESULTS
// =============================================================
@Composable
private fun SchoolSearchField(
    query: String,
    results: List<SchoolResult>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSchoolSelected: (SchoolResult) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label       = { Text("Schule suchen…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp).padding(2.dp),
                        strokeWidth = 2.dp
                    )
                } else if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Outlined.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Search
            ),
            placeholder = { Text("z.B. Brixen, München, Tschugg…") },
            shape = RoundedCornerShape(12.dp)
        )

        // Results list
        AnimatedVisibility(visible = results.isNotEmpty()) {
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape     = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(results, key = { it.loginName }) { school ->
                        SchoolResultItem(
                            school  = school,
                            onClick = { onSchoolSelected(school) }
                        )
                        if (school != results.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color    = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                            )
                        }
                    }
                }
            }
        }

        // Hint text
        if (query.length < 2 && !isSearching) {
            Text(
                "Tippe mindestens 2 Buchstaben um zu suchen",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        } else if (results.isEmpty() && query.length >= 2 && !isSearching) {
            Text(
                "Keine Schulen gefunden",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
private fun SchoolResultItem(school: SchoolResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                school.displayName.take(1),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(school.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            if (school.address.isNotBlank()) {
                Text(school.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp))
    }
}