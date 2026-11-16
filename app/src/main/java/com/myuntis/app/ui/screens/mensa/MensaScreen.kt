package com.myuntis.app.ui.screens.mensa

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.MensaConfig
import com.myuntis.app.data.repository.MensaRepository
import com.myuntis.app.domain.model.MensaData
import com.myuntis.app.domain.model.MensaDay
import com.myuntis.app.domain.model.findPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

// =============================================================
// VIEW MODEL
// =============================================================
data class MensaUiState(
    val supported: Boolean   = true,
    val isLoading: Boolean   = true,
    val data: MensaData?     = null,
    val error: String?       = null,
    val vegetarian: Boolean  = false,
    val weekOffset: Int      = 0          // weeks relative to this week
)

@HiltViewModel
class MensaViewModel @Inject constructor(
    private val repository: MensaRepository,
    private val dataStore: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MensaUiState(weekOffset = startOffset()))
    val uiState: StateFlow<MensaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.mensaVegetarian.collect { veg ->
                _uiState.update { it.copy(vegetarian = veg) }
            }
        }
        viewModelScope.launch {
            val supported = repository.supportedFlow.first()
            _uiState.update { it.copy(supported = supported, isLoading = supported) }
            if (supported) load()
        }
    }

    // On Saturday/Sunday the next week is more useful
    private fun startOffset(): Int = if (LocalDate.now().dayOfWeek.value >= 6) 1 else 0

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.data == null, error = null) }
            when (val r = repository.getMensa()) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, data = r.data, error = null) }
                is NetworkResult.Error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = if (it.data == null) r.message else null)
                    }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setVegetarian(value: Boolean) {
        viewModelScope.launch { dataStore.setMensaVegetarian(value) }
    }

    fun previousWeek() = _uiState.update { it.copy(weekOffset = it.weekOffset - 1) }
    fun nextWeek()     = _uiState.update { it.copy(weekOffset = it.weekOffset + 1) }
    fun goToCurrentWeek() = _uiState.update { it.copy(weekOffset = startOffset()) }
}

// =============================================================
// SCREEN
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(
    onBack: () -> Unit,
    viewModel: MensaViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensa", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    if (ui.supported) {
                        IconButton(onClick = viewModel::load) {
                            Icon(Icons.Outlined.Refresh, "Aktualisieren")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !ui.supported -> InfoState(
                    "🍽️", "Keine Daten zur Mensa",
                    "Für deine Schule gibt es keinen Mensaplan in der App."
                )
                ui.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.data == null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.WifiOff, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text(ui.error ?: "Mensaplan konnte nicht geladen werden.",
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::load) { Text("Erneut versuchen") }
                }
                else -> MensaContent(
                    ui          = ui,
                    data        = ui.data!!,
                    onVeg       = viewModel::setVegetarian,
                    onPrev      = viewModel::previousWeek,
                    onNext      = viewModel::nextWeek,
                    onThisWeek  = viewModel::goToCurrentWeek
                )
            }
        }
    }
}

@Composable
private fun MensaContent(
    ui: MensaUiState,
    data: MensaData,
    onVeg: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit
) {
    val today       = LocalDate.now()
    val todayMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monday      = todayMonday.plusWeeks(ui.weekOffset.toLong())
    val friday      = monday.plusDays(4)
    val kw          = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val dateFmt     = DateTimeFormatter.ofPattern("dd.MM.")
    val dateFmtY    = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Plan of the week start, only used for the "Woche X von N" label
    val base    = data.findPlan(MensaConfig.seasonFor(monday), ui.vegetarian)
    val baseIdx = base?.let { MensaConfig.weekIndex(monday, it.weeks.size) } ?: 0

    // Each day comes from the plan of ITS date (Sommer / Winter switch by date)
    val entries: List<Pair<MensaDay, LocalDate?>> = run {
        val result = mutableListOf<Pair<MensaDay, LocalDate?>>()
        for (i in 0..4) {
            val date = monday.plusDays(i.toLong())
            val plan = data.findPlan(MensaConfig.seasonFor(date), ui.vegetarian) ?: continue
            val week = plan.weeks.getOrNull(MensaConfig.weekIndex(monday, plan.weeks.size))
                ?: continue
            week.days.getOrNull(i)?.let { result.add(it to date) }
        }
        // Extra days (e.g. "Samstag") if the plan has them
        base?.weeks?.getOrNull(baseIdx)?.days?.drop(5)?.forEach { result.add(it to null) }
        result
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Filter chips (only Standard / Vegetarisch) ───────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!ui.vegetarian, { onVeg(false) }, { Text("Standard") })
                FilterChip(ui.vegetarian,  { onVeg(true)  }, { Text("Vegetarisch") })
            }
        }

        // ── Week navigation ──────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Vorherige Woche") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "KW $kw · ${monday.format(dateFmt)}–${friday.format(dateFmtY)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (base != null) {
                        Text(
                            "Woche ${baseIdx + 1} von ${base.weeks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "Nächste Woche") }
            }
            if (ui.weekOffset != (if (today.dayOfWeek.value >= 6) 1 else 0)) {
                TextButton(onClick = onThisWeek, modifier = Modifier.fillMaxWidth()) {
                    Text("Zur aktuellen Woche")
                }
            }
            if (data.fromCache) {
                Text(
                    "Offline – zuletzt gespeicherter Plan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Days ─────────────────────────────────────────────
        if (entries.isEmpty()) {
            item { InfoState("🍽️", "Keine Daten zur Mensa", "Für diese Woche ist kein Plan vorhanden.") }
        } else {
            itemsIndexed(entries, key = { i, e -> "${monday}_${e.first.name}_$i" }) { _, entry ->
                val (day, date) = entry
                MensaDayCard(
                    day     = day,
                    date    = date,
                    isToday = date == today,
                    isPast  = date != null && date.isBefore(today),
                    legend  = data.legend
                )
            }
            item {
                Text(
                    "Angaben ohne Gewähr. Bitte Allergene bei Bedarf vor Ort nachfragen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// =============================================================
// DAY CARD
// =============================================================
@Composable
private fun MensaDayCard(
    day: MensaDay,
    date: LocalDate?,
    isToday: Boolean,
    isPast: Boolean,
    legend: Map<String, String>
) {
    var expanded by remember(day.name, day.dish) { mutableStateOf(false) }

    val surface   = MaterialTheme.colorScheme.surface
    // Opaque tint (no transparent card colour → no shadow shining through)
    val cardColor = if (isToday)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f).compositeOver(surface)
    else surface
    val textAlpha = if (isPast) 0.55f else 1f

    val lines = day.dish.split(";").map { it.trim() }.filter { it.isNotBlank() }
    val hasDish = lines.isNotEmpty()

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(enabled = hasDish) { expanded = !expanded },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(if (isPast) 0.dp else 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weekday + date
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        day.name.take(2),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                    )
                    if (date != null) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("dd.MM.")),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                        )
                    }
                }

                // Dish
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!hasDish) {
                        Text("Kein Eintrag",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha))
                    } else {
                        Text(
                            lines.first(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                        )
                        lines.drop(1).forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                            )
                        }
                    }
                }

                if (hasDish) {
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                    )
                }
            }

            if (expanded && hasDish) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Allergene", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (day.allergens.isEmpty()) {
                        Text("Keine Allergene eingetragen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        day.allergens.forEach { code ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(code, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                Text(legend[code] ?: "Unbekannt",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoState(emoji: String, title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}