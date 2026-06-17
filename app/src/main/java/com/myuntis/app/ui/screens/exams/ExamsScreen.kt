// ═══════════════════════════════════════════════════════════════
// FILE: ui/screens/exams/ExamsScreen.kt
// ═══════════════════════════════════════════════════════════════

package com.myuntis.app.ui.screens.exams

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.ExamRepository
import com.myuntis.app.domain.model.Exam
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ExamsUiState(
    val isLoading: Boolean    = true,
    val exams: List<Exam>     = emptyList(),
    val error: String?        = null
) {
    val pastExams: List<Exam>   get() = exams.filter { it.isPast }
    val futureExams: List<Exam> get() = exams.filter { !it.isPast }
}

@HiltViewModel
class ExamsViewModel @Inject constructor(
    private val repository: ExamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamsUiState())
    val uiState: StateFlow<ExamsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = repository.getExams()) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, exams = r.data) }
                is NetworkResult.Error ->
                    _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onBack: () -> Unit,
    viewModel: ExamsViewModel = hiltViewModel()
) {
    val uiState   by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Items: past exams + optional "now" separator + future exams + optional "none upcoming"
    // Scroll to first future exam (= pastExams.size) after data loads
    val pastCount = uiState.pastExams.size

    LaunchedEffect(uiState.exams) {
        if (uiState.exams.isNotEmpty()) {
            // pastCount items + 1 separator item = index of first future
            val targetIndex = (pastCount).coerceAtLeast(0)
            listState.scrollToItem(targetIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Prüfungen", fontWeight = FontWeight.Bold)
                        if (uiState.exams.isNotEmpty()) {
                            Text(
                                "${uiState.futureExams.size} kommend · ${uiState.pastExams.size} vergangen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Outlined.Refresh, "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.WifiOff, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.error!!, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::load) { Text("Erneut versuchen") }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── Past exams (top, dimmed) ──────────────
                        if (uiState.pastExams.isNotEmpty()) {
                            item {
                                SectionDivider(
                                    label = "Vergangene Prüfungen",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                                )
                            }
                        }
                        itemsIndexed(
                            items = uiState.pastExams,
                            key   = { i, e -> "past_${e.id}_$i" }
                        ) { _, exam ->
                            ExamCard(exam = exam, dimmed = true)
                        }

                        // ── Separator ─────────────────────────────
                        item {
                            SectionDivider(
                                label = "Aktuelle & kommende Prüfungen",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ── Future exams ──────────────────────────
                        if (uiState.futureExams.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🎉", fontSize = 42.sp)
                                    Text(
                                        "Keine aktuellen Prüfungen",
                                        style      = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Keine weiteren Prüfungen eingetragen.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = uiState.futureExams,
                                key   = { i, e -> "future_${e.id}_$i" }
                            ) { _, exam ->
                                ExamCard(exam = exam, dimmed = false)
                            }
                        }

                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionDivider(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = color.copy(alpha = 0.3f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color,
            fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.weight(1f), color = color.copy(alpha = 0.3f))
    }
}

@Composable
private fun ExamCard(exam: Exam, dimmed: Boolean) {
    val dateFmt = DateTimeFormatter.ofPattern("EEE, dd. MMM yyyy", Locale.GERMAN)
    var expanded by remember { mutableStateOf(false) }

    val alpha     = if (dimmed) 0.55f else 1f
    val today     = LocalDate.now()
    val isToday   = exam.date == today
    val isSoon    = !exam.isPast && exam.date <= today.plusDays(3)
    val accentColor = when {
        dimmed    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        isToday   -> MaterialTheme.colorScheme.error
        isSoon    -> Color(0xFFE65100)
        else      -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = when {
                isToday && !dimmed ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else               ->
                    MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(if (dimmed) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Subject badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        exam.subject.take(4),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        fontSize   = if (exam.subject.length <= 2) 14.sp else 9.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Date + time
                    Text(
                        exam.date.format(dateFmt).replaceFirstChar { it.uppercaseChar() },
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Text(
                        "${exam.startTime.toString().take(5)} – ${exam.endTime.toString().take(5)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }

                // Grade if available (past exams)
                if (exam.grade.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            exam.grade,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = accentColor
                        )
                    }
                }

                // Today badge
                if (isToday && !dimmed) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Text("Heute",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold)
                    }
                }

                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp
                    else Icons.Outlined.KeyboardArrowDown,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }

            // ── Expanded details ─────────────────────────────
            if (expanded) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (exam.name.isNotBlank()) {
                        Text(
                            exam.name,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (exam.description.isNotBlank() && exam.description != exam.name) {
                        Text(
                            exam.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (exam.teachers.isNotEmpty()) {
                        ExamDetailRow("Lehrer", exam.teachers.joinToString(", "))
                    }
                    if (exam.rooms.isNotEmpty()) {
                        ExamDetailRow("Raum", exam.rooms.joinToString(", "))
                    }
                    if (exam.grade.isNotBlank()) {
                        ExamDetailRow("Note", exam.grade)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}