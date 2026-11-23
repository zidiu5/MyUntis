package com.myuntis.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.notifications.NotifType
import com.myuntis.app.data.notifications.NotificationScheduler
import com.myuntis.app.data.notifications.NotificationSettings
import com.myuntis.app.data.notifications.Notifier
import com.myuntis.app.data.repository.AuthRepository
import com.myuntis.app.domain.model.SavedAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

// =============================================================
// VIEW MODEL
// =============================================================
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStore: DataStoreManager,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = dataStore.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val knownSubjects: StateFlow<List<String>> = dataStore.knownSubjects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjectColors: StateFlow<Map<String, String>> = dataStore.subjectColors
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val accounts: StateFlow<List<SavedAccount>> = dataStore.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeAccountId: StateFlow<String?> = dataStore.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notificationSettings: StateFlow<NotificationSettings> = dataStore.notificationSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationSettings())

    val notifStatus: StateFlow<String> = dataStore.notifStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val isStudent: StateFlow<Boolean> = dataStore.userProfile
        .map { it.personType == 5 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()

    fun toggleDarkMode() {
        viewModelScope.launch { dataStore.setDarkMode(!isDarkMode.value) }
    }

    fun setSubjectColor(subjectShort: String, hexColor: String) {
        viewModelScope.launch { dataStore.setSubjectColor(subjectShort, hexColor) }
    }

    fun resetSubjectColor(subjectShort: String) {
        viewModelScope.launch { dataStore.setSubjectColor(subjectShort, "") }
    }

    fun openAccountPicker() {
        viewModelScope.launch {
            dataStore.setLoginIntent("switch")
            _loggedOut.value = true
        }
    }

    fun addAccount() {
        viewModelScope.launch {
            dataStore.setLoginIntent("add")
            _loggedOut.value = true
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            if (dataStore.accounts.first().isNotEmpty()) dataStore.setLoginIntent("switch")
            _loggedOut.value = true
        }
    }

    fun setNotification(type: NotifType, enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setNotifEnabled(type, enabled)
            scheduler.sync()
        }
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            dataStore.setReminderMinutes(minutes)
            scheduler.scheduleReminder(replace = true)
        }
    }

    fun sendTestNotification() {
        notifier.show(
            NotifType.NEW_GRADE, 999, "Testbenachrichtigung",
            listOf("So sehen Benachrichtigungen von MyUntis aus."), route = "grades"
        )
    }

    fun checkNow() = scheduler.runNow()
}

// =============================================================
// SETTINGS SCREEN
// =============================================================
@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val loggedOut     by viewModel.loggedOut.collectAsState()
    val isDarkMode    by viewModel.isDarkMode.collectAsState()
    val knownSubjects by viewModel.knownSubjects.collectAsState()
    val subjectColors by viewModel.subjectColors.collectAsState()
    val accounts      by viewModel.accounts.collectAsState()
    val activeId      by viewModel.activeAccountId.collectAsState()
    val activeAccount = accounts.firstOrNull { it.id == activeId }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    var showLogoutDialog   by remember { mutableStateOf(false) }
    var colorPickerSubject by remember { mutableStateOf<String?>(null) }
    var subjectsExpanded   by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon  = { Icon(Icons.Outlined.Logout, null) },
            title = { Text("Abmelden?") },
            text  = { Text("Dieses Konto wird von diesem Gerät entfernt. Du kannst es jederzeit wieder hinzufügen.") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; viewModel.logout() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Abmelden") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    colorPickerSubject?.let { subject ->
        HsvColorPickerDialog(
            subjectName   = subject,
            currentHex    = subjectColors[subject],
            onColorPicked = { hex -> viewModel.setSubjectColor(subject, hex) },
            onReset       = { viewModel.resetSubjectColor(subject) },
            onDismiss     = { colorPickerSubject = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider()
        }

        // ── Appearance ────────────────────────────────────────
        item {
            SectionLabel("Darstellung")
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    supportingContent = {
                        Text(
                            if (isDarkMode) "Dunkles Design aktiv" else "Helles Design aktiv",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent  = {
                        Icon(
                            if (isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            null
                        )
                    },
                    trailingContent = {
                        Switch(checked = isDarkMode, onCheckedChange = { viewModel.toggleDarkMode() })
                    }
                )
            }
        }

        // ── Subject Colors ────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { subjectsExpanded = !subjectsExpanded },
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Fachfarben", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (knownSubjects.isEmpty()) "Lade zuerst den Stundenplan"
                                else "${knownSubjects.size} Fächer · Tippen zum ${if (subjectsExpanded) "Schließen" else "Bearbeiten"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(if (subjectsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (subjectsExpanded && knownSubjects.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column {
                        knownSubjects.sorted().forEachIndexed { index, subject ->
                            val hexColor = subjectColors[subject].takeIf { !it.isNullOrBlank() }
                            val displayColor = if (hexColor != null) {
                                try { Color(android.graphics.Color.parseColor(hexColor)) } catch (_: Exception) { hashColor(subject) }
                            } else { hashColor(subject) }

                            ListItem(
                                headlineContent = { Text(subject, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text(if (hexColor != null) hexColor else "Standard-Farbe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = {
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(displayColor).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), CircleShape))
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (hexColor != null) {
                                            IconButton(onClick = { viewModel.resetSubjectColor(subject) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Outlined.RestartAlt, "Zurücksetzen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                            }
                                        }
                                        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                modifier = Modifier.clickable { colorPickerSubject = subject }
                            )
                            if (index < knownSubjects.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        // ── Konten ────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Konten")
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    activeAccount?.let { acc ->
                        ListItem(
                            headlineContent = { Text(acc.displayName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(listOf(acc.className, acc.schoolName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { acc.username }, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Outlined.Person, null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                    if (accounts.size >= 2) {
                        ListItem(
                            headlineContent   = { Text("Konto wechseln") },
                            supportingContent = { Text("${accounts.size} Konten gespeichert", style = MaterialTheme.typography.bodySmall) },
                            leadingContent    = { Icon(Icons.Outlined.SwitchAccount, null) },
                            trailingContent   = { Icon(Icons.Outlined.ChevronRight, null) },
                            modifier = Modifier.clickable { viewModel.openAccountPicker() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                    ListItem(
                        headlineContent = { Text("Konto hinzufügen") },
                        leadingContent  = { Icon(Icons.Outlined.PersonAdd, null) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                        modifier = Modifier.clickable { viewModel.addAccount() }
                    )
                }
            }
        }

        // ── Konto (Logout) ────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Konto")
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
            ) {
                ListItem(
                    headlineContent = { Text("Abmelden", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Session beenden und zum Login", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent  = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    modifier        = Modifier.clickable { showLogoutDialog = true }
                )
            }
        }

        // ── Erweiterte Einstellungen (Benachrichtigungen) ─────
        item {
            Spacer(Modifier.height(8.dp))
            NotificationSection(viewModel)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================================================
// NOTIFICATION SECTION
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSection(viewModel: SettingsViewModel) {
    val settings by viewModel.notificationSettings.collectAsState()
    val isStudent by viewModel.isStudent.collectAsState()
    val status by viewModel.notifStatus.collectAsState()
    val context = LocalContext.current

    var expanded by rememberSaveable { mutableStateOf(false) }

    // Re-check the system state when we come back (e.g. from the system settings)
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val blocked = remember(tick, settings) {
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var showTimePicker by remember { mutableStateOf(false) }
    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = settings.reminderMinutes / 60,
            initialMinute = settings.reminderMinutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Erinnerung um") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderMinutes(state.hour * 60 + state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } }
        )
    }

    val types = NotifType.entries.filter { it != NotifType.HOMEWORK_BOARD || isStudent }
    val activeCount = types.count { settings.isOn(it) }
    val homeworkOn = settings.isOn(NotifType.HOMEWORK_TAB) || settings.isOn(NotifType.HOMEWORK_BOARD)
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    // ── Header (always visible) ──────────────────────────────
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Benachrichtigungen", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    val subtitle = when {
                        blocked && activeCount > 0 -> "Im System ausgeschaltet"
                        activeCount == 0           -> "Alle aus"
                        else                       -> "$activeCount von ${types.size} aktiv"
                    }
                    Text(
                        subtitle, style = MaterialTheme.typography.bodySmall,
                        color = if (blocked && activeCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                null, tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ── Content (collapsed by default) ───────────────────────
    AnimatedVisibility(visible = expanded) {
        Card(
            modifier = Modifier.padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.3f))
        ) {
            Column {
                types.forEachIndexed { index, type ->
                    ListItem(
                        headlineContent = { Text(type.title) },
                        supportingContent = {
                            Text(type.description, style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.isOn(type),
                                onCheckedChange = { on ->
                                    if (on) ensurePermission()
                                    viewModel.setNotification(type, on)
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index < types.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                if (homeworkOn) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Erinnerung um") },
                        supportingContent = {
                            Text("Uhrzeit am Vortag der Abgabe", style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            TextButton(onClick = { showTimePicker = true }) { Text(settings.reminderText) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                if (blocked && settings.anyEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text("Benachrichtigungen sind für die App ausgeschaltet",
                                color = MaterialTheme.colorScheme.error)
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }) { Text("Öffnen") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = viewModel::sendTestNotification) { Text("Test senden") }
                    TextButton(onClick = viewModel::checkNow, enabled = settings.anyEnabled) { Text("Jetzt prüfen") }
                }

                // What the last background check found
                Text(
                    if (status.isBlank()) "Noch keine Prüfung gelaufen."
                    else "Letzte Prüfung: $status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    "Die App prüft etwa alle 30 Minuten im Hintergrund, solange sie nicht geöffnet ist. " +
                            "Sofort-Meldungen sind nicht möglich, weil Untis keine Push-Nachrichten anbietet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
                )
            }
        }
    }
}


// =============================================================
// HSV COLOR PICKER DIALOG
// =============================================================
@Composable
private fun HsvColorPickerDialog(
    subjectName: String,
    currentHex: String?,
    onColorPicked: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val initHsv = remember(currentHex) {
        if (!currentHex.isNullOrBlank()) {
            try {
                val parsed = android.graphics.Color.parseColor(currentHex)
                val hsv    = FloatArray(3)
                android.graphics.Color.colorToHSV(parsed, hsv)
                Triple(hsv[0], hsv[1], hsv[2])
            } catch (_: Exception) { Triple(210f, 0.8f, 0.9f) }
        } else { Triple(210f, 0.8f, 0.9f) }
    }

    var hue        by remember { mutableFloatStateOf(initHsv.first) }
    var saturation by remember { mutableFloatStateOf(initHsv.second) }
    var brightness by remember { mutableFloatStateOf(initHsv.third) }

    val pickedColor = Color.hsv(hue, saturation, brightness)
    val hexString = remember(hue, saturation, brightness) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        "#%06X".format(argb and 0xFFFFFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(pickedColor))
                Text("Farbe für $subjectName")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HueWheel(hue = hue, onHueChanged = { hue = it }, modifier = Modifier.size(200.dp))

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sättigung: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GradientSlider(value = saturation, onChange = { saturation = it }, startColor = Color.hsv(hue, 0f, brightness), endColor = Color.hsv(hue, 1f, brightness))

                    Text("Helligkeit: ${(brightness * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GradientSlider(value = brightness, onChange = { brightness = it }, startColor = Color.Black, endColor = Color.hsv(hue, saturation, 1f))
                }

                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(pickedColor).padding(12.dp), horizontalArrangement = Arrangement.Center) {
                    Text(hexString, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (brightness > 0.5f) Color.Black else Color.White)
                }

                if (!currentHex.isNullOrBlank()) {
                    TextButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Standard-Farbe wiederherstellen")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onColorPicked(hexString); onDismiss() }) { Text("Übernehmen") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun HueWheel(hue: Float, onHueChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    if (change.pressed) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        onHueChanged(angle)
                        change.consume()
                    }
                }
            }
        }
    }) {
        drawHueWheel()
        drawHueSelector(hue)
    }
}

private fun DrawScope.drawHueWheel() {
    val cx = size.width  / 2f
    val cy = size.height / 2f
    val outerR = size.minDimension / 2f
    val innerR = outerR * 0.7f
    for (i in 0 until 360) {
        drawArc(
            color      = Color.hsv(i.toFloat(), 1f, 1f),
            startAngle = i.toFloat(),
            sweepAngle = 1.5f,
            useCenter  = false,
            topLeft    = Offset(cx - outerR, cy - outerR),
            size       = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
            style      = androidx.compose.ui.graphics.drawscope.Stroke(width = (outerR - innerR))
        )
    }
}

private fun DrawScope.drawHueSelector(hue: Float) {
    val cx = size.width  / 2f
    val cy = size.height / 2f
    val outerR = size.minDimension / 2f
    val innerR = outerR * 0.7f
    val midR   = (outerR + innerR) / 2f
    val rad    = Math.toRadians(hue.toDouble())
    val sx     = cx + midR * cos(rad).toFloat()
    val sy     = cy + midR * sin(rad).toFloat()

    drawCircle(Color.White, radius = (outerR - innerR) / 2f * 0.9f, center = Offset(sx, sy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    drawCircle(Color.hsv(hue, 1f, 1f), radius = (outerR - innerR) / 2f * 0.65f, center = Offset(sx, sy))
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, startColor: Color, endColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(startColor, endColor)))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val v = (change.position.x / size.width).coerceIn(0f, 1f)
                                onChange(v)
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.offset(x = (value * (1f - 28f / maxOf(1f, 280f)) * 280f).dp)
                .size(28.dp).clip(CircleShape).border(BorderStroke(2.dp, Color.White), CircleShape).background(Color.Transparent)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun hashColor(shortName: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary, Color(0xFF2E7D32), Color(0xFF6A1B9A),
        Color(0xFF00695C), Color(0xFFE65100), Color(0xFF1565C0),
        Color(0xFFAD1457), Color(0xFF4E342E), Color(0xFF0277BD)
    )
    return palette[(shortName.hashCode() and 0x7FFFFFFF) % palette.size]
}