package com.myuntis.app.ui.screens.homework

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myuntis.app.domain.model.Homework
import com.myuntis.app.domain.model.daysUntilDue
import com.myuntis.app.domain.model.isOverdue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeworkScreen(viewModel: HomeworkViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ──────────────────────────────────────────
        HomeworkTopBar(
            openCount = uiState.openCount,
            isLoading = uiState.isLoading,
            onRefresh = viewModel::loadHomework
        )

        // ── Filter chips ─────────────────────────────────────
        FilterRow(
            active   = uiState.activeFilter,
            onSelect = viewModel::setFilter
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Content ──────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> HomeworkLoadingState()

                uiState.errorMessage != null -> HomeworkErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::loadHomework
                )

                uiState.filteredHomework.isEmpty() -> HomeworkEmptyState(
                    filter = uiState.activeFilter
                )

                else -> HomeworkList(
                    grouped  = uiState.groupedByDate,
                    onToggle = viewModel::toggleComplete
                )
            }
        }
    }
}

// =============================================================
// TOP BAR
// =============================================================
@Composable
private fun HomeworkTopBar(
    openCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text       = "Hausaufgaben",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            AnimatedVisibility(visible = openCount > 0) {
                Text(
                    text  = "$openCount offen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(
            onClick  = onRefresh,
            enabled  = !isLoading,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Aktualisieren",
                    tint   = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// =============================================================
// FILTER ROW
// =============================================================
@Composable
private fun FilterRow(
    active: HomeworkFilter,
    onSelect: (HomeworkFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeworkFilter.entries.forEach { filter ->
            FilterChip(
                selected = active == filter,
                onClick  = { onSelect(filter) },
                label    = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (active == filter) ({
                    Icon(
                        Icons.Outlined.Check, null,
                        modifier = Modifier.size(14.dp)
                    )
                }) else null
            )
        }
    }
}

// =============================================================
// HOMEWORK LIST  (grouped by due date)
// =============================================================
@Composable
private fun HomeworkList(
    grouped: Map<LocalDate, List<Homework>>,
    onToggle: (Homework) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        grouped.entries
            .sortedBy { it.key }
            .forEach { (date, items) ->

                // Section header
                item(key = "header_$date") {
                    DateSectionHeader(date = date)
                }

                // Homework items for this date
                items(
                    items = items,
                    key   = { it.id }
                ) { homework ->
                    HomeworkCard(
                        homework = homework,
                        onToggle = { onToggle(homework) }
                    )
                }
            }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// =============================================================
// DATE SECTION HEADER
// =============================================================
@Composable
private fun DateSectionHeader(date: LocalDate) {
    val today    = LocalDate.now()
    val tomorrow = today.plusDays(1)

    val label = when (date) {
        today    -> "Heute"
        tomorrow -> "Morgen"
        else     -> date.format(
            DateTimeFormatter.ofPattern("EEEE, dd. MMMM", Locale.GERMAN)
        ).replaceFirstChar { it.uppercaseChar() }
    }

    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    val color = when {
        daysUntil < 0  -> MaterialTheme.colorScheme.error
        daysUntil == 0L -> MaterialTheme.colorScheme.error
        daysUntil == 1L -> MaterialTheme.colorScheme.tertiary
        else            -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text       = label,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = color
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = color.copy(alpha = 0.25f),
            thickness = 1.dp
        )
    }
}

// =============================================================
// HOMEWORK CARD
// =============================================================
@Composable
private fun HomeworkCard(
    homework: Homework,
    onToggle: () -> Unit
) {
    val isOverdue   = homework.isOverdue
    val isCompleted = homework.isCompleted
    val daysLeft    = homework.daysUntilDue

    // Subject color (same hash logic as timetable)
    val subjColor = subjectColor(homework.subjectShortName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isOverdue   -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else        -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCompleted) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox
            Checkbox(
                checked  = isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(top = 2.dp),
                colors   = CheckboxDefaults.colors(
                    checkedColor   = MaterialTheme.colorScheme.primary,
                    uncheckedColor = if (isOverdue)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                // Subject name + short name chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = homework.subjectName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted)
                            TextDecoration.LineThrough
                        else
                            TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Subject short-name badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(subjColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text       = homework.subjectShortName,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = subjColor,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 10.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Homework text
                Text(
                    text = homework.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Bottom info row: teacher + due label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Teacher
                    if (homework.teacherName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Person, null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text  = homework.teacherName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Due label
                    val dueLabelText = when {
                        isCompleted -> "Erledigt ✓"
                        isOverdue   -> "Überfällig!"
                        daysLeft == 0L -> "Heute fällig"
                        daysLeft == 1L -> "Morgen fällig"
                        else        -> "In $daysLeft Tagen"
                    }
                    val dueLabelColor = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isOverdue   -> MaterialTheme.colorScheme.error
                        daysLeft <= 1L -> MaterialTheme.colorScheme.tertiary
                        else        -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(dueLabelColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text       = dueLabelText,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = dueLabelColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// EMPTY / ERROR / LOADING STATES
// =============================================================
@Composable
private fun HomeworkEmptyState(filter: HomeworkFilter) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (filter) {
                HomeworkFilter.TODAY    -> "Heute keine Aufgaben!"
                HomeworkFilter.TOMORROW -> "Morgen keine Aufgaben!"
                HomeworkFilter.WEEK     -> "Diese Woche keine Aufgaben!"
                HomeworkFilter.ALL      -> "Keine Hausaufgaben"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Alles erledigt – weiter so! 💪",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeworkErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.WifiOff, null,
            modifier = Modifier.size(52.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Erneut versuchen")
        }
    }
}

@Composable
private fun HomeworkLoadingState() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue  = 0.08f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label         = "shimmerVal"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(5) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (i % 2 == 0) 80.dp else 100.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = shimmer)
                    )
            )
        }
    }
}

// =============================================================
// SUBJECT COLOR  –  same hash logic as timetable
// =============================================================
@Composable
private fun subjectColor(shortName: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF2E7D32), Color(0xFF6A1B9A),
        Color(0xFF00695C), Color(0xFFE65100),
        Color(0xFF1565C0), Color(0xFFAD1457),
        Color(0xFF4E342E), Color(0xFF0277BD)
    )
    return palette[(shortName.hashCode() and 0x7FFFFFFF) % palette.size]
}