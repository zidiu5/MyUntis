package com.myuntis.app.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.repository.AuthRepository
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
    private val dataStore: DataStoreManager
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = dataStore.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val knownSubjects: StateFlow<List<String>> = dataStore.knownSubjects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjectColors: StateFlow<Map<String, String>> = dataStore.subjectColors
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = true
        }
    }
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

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    var showLogoutDialog   by remember { mutableStateOf(false) }
    var colorPickerSubject by remember { mutableStateOf<String?>(null) }
    var subjectsExpanded   by remember { mutableStateOf(false) }

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon  = { Icon(Icons.Outlined.Logout, null) },
            title = { Text("Abmelden?") },
            text  = { Text("Möchtest du dich wirklich abmelden?") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; viewModel.logout() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Abmelden") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Color picker dialog
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

        // ── Subject Colors (collapsible) ──────────────────────
        item {
            Spacer(Modifier.height(8.dp))

            // Section header + collapse toggle
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Palette, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "Fachfarben",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (knownSubjects.isEmpty()) "Lade zuerst den Stundenplan"
                                else "${knownSubjects.size} Fächer · Tippen zum ${if (subjectsExpanded) "Schließen" else "Bearbeiten"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        if (subjectsExpanded) Icons.Outlined.KeyboardArrowUp
                        else Icons.Outlined.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded subject list
            if (subjectsExpanded && knownSubjects.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column {
                        knownSubjects.sorted().forEachIndexed { index, subject ->
                            val hexColor = subjectColors[subject]
                                .takeIf { !it.isNullOrBlank() }
                            val displayColor = if (hexColor != null) {
                                try { Color(android.graphics.Color.parseColor(hexColor)) }
                                catch (_: Exception) { hashColor(subject) }
                            } else {
                                hashColor(subject)
                            }

                            ListItem(
                                headlineContent = {
                                    Text(
                                        subject,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (hexColor != null) hexColor else "Standard-Farbe",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                ),
                                                CircleShape
                                            )
                                    )
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Reset button – only shown if custom color exists
                                        if (hexColor != null) {
                                            IconButton(
                                                onClick = { viewModel.resetSubjectColor(subject) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.RestartAlt, "Zurücksetzen",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Outlined.ChevronRight, null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { colorPickerSubject = subject }
                            )
                            if (index < knownSubjects.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 60.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Account ───────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Konto")
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            "Abmelden",
                            color      = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    supportingContent = {
                        Text(
                            "Session beenden und zum Login",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent  = {
                        Icon(Icons.Outlined.Logout, null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    modifier        = Modifier.clickable { showLogoutDialog = true }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================================================
// HSV COLOR PICKER DIALOG
// Full hue wheel + saturation/brightness triangle inside
// =============================================================
@Composable
private fun HsvColorPickerDialog(
    subjectName: String,
    currentHex: String?,
    onColorPicked: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    // Parse current hex to HSV, default to vivid blue
    val initHsv = remember(currentHex) {
        if (!currentHex.isNullOrBlank()) {
            try {
                val parsed = android.graphics.Color.parseColor(currentHex)
                val hsv    = FloatArray(3)
                android.graphics.Color.colorToHSV(parsed, hsv)
                Triple(hsv[0], hsv[1], hsv[2])
            } catch (_: Exception) { Triple(210f, 0.8f, 0.9f) }
        } else {
            Triple(210f, 0.8f, 0.9f)
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(pickedColor)
                )
                Text("Farbe für $subjectName")
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Hue wheel ──────────────────────────────────
                HueWheel(
                    hue      = hue,
                    onHueChanged = { hue = it },
                    modifier = Modifier.size(200.dp)
                )

                // ── SB sliders ─────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Saturation
                    Text(
                        "Sättigung: ${(saturation * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradientSlider(
                        value    = saturation,
                        onChange = { saturation = it },
                        startColor = Color.hsv(hue, 0f, brightness),
                        endColor   = Color.hsv(hue, 1f, brightness)
                    )

                    // Brightness
                    Text(
                        "Helligkeit: ${(brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradientSlider(
                        value    = brightness,
                        onChange = { brightness = it },
                        startColor = Color.Black,
                        endColor   = Color.hsv(hue, saturation, 1f)
                    )
                }

                // ── Preview + hex ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(pickedColor)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        hexString,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (brightness > 0.5f) Color.Black else Color.White
                    )
                }

                // ── Reset ──────────────────────────────────────
                if (!currentHex.isNullOrBlank()) {
                    TextButton(
                        onClick = { onReset(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Standard-Farbe wiederherstellen")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorPicked(hexString)
                    onDismiss()
                }
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// =============================================================
// HUE WHEEL
// Drawn with Canvas. User drags the selector around the ring.
// =============================================================
@Composable
private fun HueWheel(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Canvas(
        modifier = modifier.pointerInput(Unit) {
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
        }
    ) {
        drawHueWheel()
        drawHueSelector(hue)
    }
}

private fun DrawScope.drawHueWheel() {
    val cx      = size.width  / 2f
    val cy      = size.height / 2f
    val outerR  = size.minDimension / 2f
    val innerR  = outerR * 0.7f
    val steps   = 360

    for (i in 0 until steps) {
        val startAngle = i.toFloat()
        val color = Color.hsv(startAngle, 1f, 1f)
        drawArc(
            color      = color,
            startAngle = startAngle,
            sweepAngle = 1.5f,
            useCenter  = false,
            topLeft    = Offset(cx - outerR, cy - outerR),
            size       = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
            style      = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (outerR - innerR)
            )
        )
    }
}

private fun DrawScope.drawHueSelector(hue: Float) {
    val cx     = size.width  / 2f
    val cy     = size.height / 2f
    val outerR = size.minDimension / 2f
    val innerR = outerR * 0.7f
    val midR   = (outerR + innerR) / 2f
    val rad    = Math.toRadians(hue.toDouble())
    val sx     = cx + midR * cos(rad).toFloat()
    val sy     = cy + midR * sin(rad).toFloat()

    // White ring selector
    drawCircle(Color.White, radius = (outerR - innerR) / 2f * 0.9f, center = Offset(sx, sy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    drawCircle(Color.hsv(hue, 1f, 1f), radius = (outerR - innerR) / 2f * 0.65f, center = Offset(sx, sy))
}

// =============================================================
// GRADIENT SLIDER
// A draggable slider that shows a colour gradient as its track.
// =============================================================
@Composable
private fun GradientSlider(
    value: Float,
    onChange: (Float) -> Unit,
    startColor: Color,
    endColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(listOf(startColor, endColor))
            )
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
        // Thumb
        Box(
            modifier = Modifier
                .offset(x = (value * (1f - 28f / maxOf(1f, 280f)) * 280f).dp)
                .size(28.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, Color.White), CircleShape)
                .background(Color.Transparent)
        )
    }
}

// =============================================================
// HELPERS
// =============================================================
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// Hash-based default colour (same logic as timetable)
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