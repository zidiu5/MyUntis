package com.myuntis.app.ui.screens.grades

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myuntis.app.domain.model.Absence
import com.myuntis.app.domain.model.AbsenceStatistics
import com.myuntis.app.domain.model.GradeEntry
import com.myuntis.app.domain.model.SubjectWithGrades
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

// =============================================================
// GRADE COLOR  –  updated mapping
// 1‑5 → red, 6 → orange, 7 → yellow, 8 → green, 9 → stronger green, 10 → cyan
// =============================================================
private fun gradeColor(v: Float): Color {
    val rounded = v.roundToInt().coerceIn(1, 10)
    return when (rounded) {
        in 1..5 -> Color(0xFFFF4444)   // red
        6       -> Color(0xFFFF8800)   // orange
        7       -> Color(0xFFFFCC00)   // yellow
        8       -> Color(0xFF00FF88)   // green
        9       -> Color(0xFF44FF88)   // stronger green
        10      -> Color(0xFF00D4FF)   // cyan / blue
        else    -> Color(0xFFFF4444)   // fallback red
    }
}

// =============================================================
// MAIN SCREEN
// =============================================================
@Composable
fun GradesScreen(viewModel: GradesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Noten & Fehlstunden",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { viewModel.loadGrades(); viewModel.loadAbsences() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Refresh, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }

        // ── Main tabs: Noten / Fehlstunden ───────────────────
        TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
            GradesTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick  = { viewModel.setTab(tab) },
                    text     = { Text(tab.label) }
                )
            }
        }

        when (uiState.activeTab) {
            GradesTab.GRADES   -> GradesContent(uiState, viewModel)
            GradesTab.ABSENCES -> AbsencesContent(uiState)
        }
    }
}

// =============================================================
// GRADES CONTENT  –  stats + register/analyse sub-tabs
// =============================================================
@Composable
private fun GradesContent(uiState: GradesUiState, viewModel: GradesViewModel) {
    if (uiState.isLoadingGrades) { ShimmerLoading(); return }
    if (uiState.gradesError != null) { ErrorState(uiState.gradesError); return }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Stat cards ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label  = "Realwert",
                value  = "%.2f".format(uiState.realwert),
                color  = gradeColor(uiState.realwert),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label  = "Zeugnis Ø",
                value  = "%.2f".format(uiState.zeugnisAvg),
                color  = gradeColor(uiState.zeugnisAvg),
                modifier = Modifier.weight(1f)
            )
        }

        // ── Sub-tabs: Register / Analyse ─────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            StatsTab.entries.forEach { tab ->
                val selected = uiState.activeStatsTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setStatsTab(tab) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        when (uiState.activeStatsTab) {
            StatsTab.REGISTER -> RegisterTab(uiState)
            StatsTab.ANALYSE  -> AnalyseTab(uiState)
        }
    }
}

// =============================================================
// STAT CARD
// =============================================================
@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color      = color
            )
        }
    }
}

// =============================================================
// REGISTER TAB  –  subject cards
// =============================================================
@Composable
private fun RegisterTab(uiState: GradesUiState) {
    if (uiState.subjects.isEmpty()) {
        EmptyState("📋", "Keine Noten",
            "Für dieses Schuljahr wurden noch keine Noten eingetragen.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(uiState.subjects, key = { it.lessonId }) { subject ->
            SubjectGradeCard(subject)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================================================
// SUBJECT GRADE CARD
// =============================================================
@Composable
private fun SubjectGradeCard(subject: SubjectWithGrades) {
    var expanded by remember { mutableStateOf(false) }
    val avg = subject.average

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // ── Header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject short name circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(gradeColor(avg).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        subject.subjectShort.take(4),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = gradeColor(avg),
                        fontSize   = 9.sp,
                        textAlign  = TextAlign.Center
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        subject.subjectShort,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "${subject.entries.size} Beurteilung${if (subject.entries.size != 1) "en" else ""}  ·  ${subject.teachers}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                // Average badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(gradeColor(avg).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "Ø %.1f".format(avg),
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color      = gradeColor(avg)
                    )
                }

                Spacer(Modifier.width(6.dp))

                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp
                    else Icons.Outlined.KeyboardArrowDown,
                    null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ── Expanded grade entries ───────────────────────
            if (expanded) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Final mark if available
                if (subject.finalMarkName.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zeugnisnote",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(subject.finalMarkName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }

                subject.entries.forEachIndexed { i, entry ->
                    GradeEntryRow(entry)
                    if (i < subject.entries.lastIndex) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 56.dp, end = 14.dp),
                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            thickness = 0.5.dp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun GradeEntryRow(entry: GradeEntry) {
    val color = gradeColor(entry.markValue)
    val fmt   = DateTimeFormatter.ofPattern("dd.MM.yy")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Grade circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                entry.markName,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color      = color,
                fontSize   = if (entry.markName.length <= 2) 14.sp else 10.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.description.ifBlank {
                    entry.examType.ifBlank { "Beurteilung" }
                },
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines   = 2
            )
            if (entry.examType.isNotBlank() && entry.description.isNotBlank()) {
                Text(entry.examType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            entry.date.format(fmt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =============================================================
// ANALYSE TAB  –  donut chart
// =============================================================
@Composable
private fun AnalyseTab(uiState: GradesUiState) {
    val counts = uiState.gradeCounts
    val total  = counts.sumOf { it.second }

    // Updated colour mapping for grades 1..10
    val bucketColors = listOf(
        Color(0xFFFF4444),  // 4
        Color(0xFFFF6644),  // 5
        Color(0xFFFF8800),  // 6
        Color(0xFFFFCC00),  // 7
        Color(0xFF00FF88),  // 8
        Color(0xFF44FF88),  // 9
        Color(0xFF00D4FF)   // 10
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Notenverteilung",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (total == 0) {
                    Text("Keine Noten vorhanden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Donut chart
                    DonutChart(
                        counts = counts,
                        total  = total
                    )

                    // Legend
                    val nonZero = counts.filter { it.second > 0 }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        nonZero.chunked(4).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { (label, cnt) ->
                                    val idx = (label.toIntOrNull() ?: 4) - 4
                                    val c   = if (idx in bucketColors.indices) bucketColors[idx]
                                    else Color.Gray
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c.copy(alpha = 0.12f))
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                label,
                                                style      = MaterialTheme.typography.labelSmall,
                                                color      = c,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "×$cnt",
                                                style      = MaterialTheme.typography.labelSmall,
                                                color      = c.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                                // Fill remaining slots if row < 4
                                repeat(4 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subject averages list
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Fach-Übersicht",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp))

                uiState.subjects.filter { it.entries.isNotEmpty() }.forEach { subject ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            subject.subjectShort,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(70.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )

                        // Progress bar
                        // Scale from 4 (min) to 10 (max) so bar starts at 0% for grade 4
                        val fraction = ((subject.average - 4f) / 6f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(gradeColor(subject.average))
                            )
                        }

                        Text(
                            "%.1f".format(subject.average),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = gradeColor(subject.average),
                            modifier   = Modifier.width(32.dp),
                            textAlign  = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// DONUT CHART  –  drawn with Canvas
// =============================================================
@Composable
private fun DonutChart(counts: List<Pair<String, Int>>, total: Int) {
    // Updated colour mapping for grades 1..10 (same as in AnalyseTab)
    val bucketColors = listOf(
        Color(0xFFFF4444),  // 4  → red
        Color(0xFFFF6644),  // 5  → red-orange
        Color(0xFFFF8800),  // 6  → orange
        Color(0xFFFFCC00),  // 7  → yellow
        Color(0xFF00FF88),  // 8  → green
        Color(0xFF44FF88),  // 9  → strong green
        Color(0xFF00D4FF)   // 10 → cyan
    )

    val avgText = counts
        .filter { it.second > 0 }
        .sumOf { (label, cnt) -> (label.toIntOrNull() ?: 0) * cnt }
        .let { if (total > 0) "%.1f".format(it.toFloat() / total) else "" }

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 38.dp.toPx()
            val radius  = (size.minDimension - strokeW) / 2f
            val topLeft = Offset(
                x = center.x - radius,
                y = center.y - radius
            )
            val arcSize = Size(radius * 2, radius * 2)

            var startAngle = -90f
            val gapDeg     = 2f   // tiny gap between arcs

            counts.forEachIndexed { i, (_, cnt) ->
                if (cnt > 0) {
                    val sweep = (cnt.toFloat() / total) * 360f - gapDeg
                    val c     = if (i in bucketColors.indices) bucketColors[i] else Color.Gray

                    drawArc(
                        color       = c,
                        startAngle  = startAngle,
                        sweepAngle  = sweep.coerceAtLeast(0f),
                        useCenter   = false,
                        topLeft     = topLeft,
                        size        = arcSize,
                        style       = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep + gapDeg
                }
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                avgText,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color      = gradeColor(avgText.toFloatOrNull() ?: 0f)
            )
            Text(
                "$total Noten",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================
// ABSENCES CONTENT
// =============================================================
@Composable
private fun AbsencesContent(uiState: GradesUiState) {
    when {
        uiState.isLoadingAbsences -> ShimmerLoading()
        uiState.absences.isEmpty() -> EmptyState("✅", "Keine Fehlstunden",
            "Super! Keine Fehlstunden eingetragen.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { AbsenceStatsCard(uiState.absenceStats) }
            items(uiState.absences, key = { it.id }) { AbsenceCard(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AbsenceStatsCard(stats: AbsenceStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Überblick", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBadge2("Gesamt",        stats.totalHours, MaterialTheme.colorScheme.onPrimaryContainer)
                StatBadge2("Entschuldigt",  stats.excusedHours, Color(0xFF2E7D32))
                StatBadge2("Unentschuldigt",stats.unexcusedHours, MaterialTheme.colorScheme.error)
            }
            if (stats.totalHours > 0) {
                Spacer(Modifier.height(16.dp))
                val frac = stats.excusedHours.toFloat() / stats.totalHours
                Text("Entschuldigungsquote", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF2E7D32),
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun StatBadge2(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun AbsenceCard(absence: Absence) {
    val fmt = DateTimeFormatter.ofPattern("EEE, dd. MMM yyyy", Locale.GERMAN)
    var expanded by remember { mutableStateOf(false) }
    val statusColor = if (absence.isExcused) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val hasDetail   = absence.detail.isNotBlank() || absence.reason.isNotBlank()

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(enabled = hasDetail) { expanded = !expanded },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (absence.isExcused) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                Column(modifier = Modifier.weight(1f)) {
                    Text(absence.date.format(fmt).replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${absence.startTime.toString().take(5)} – ${absence.endTime.toString().take(5)}" +
                                if (absence.reason.isNotBlank()) " · ${absence.reason}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(if (absence.isExcused) "Entschuldigt" else "Unentschuldigt",
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        color = statusColor)
                }
                if (hasDetail) {
                    Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            if (expanded && hasDetail) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (absence.reason.isNotBlank())
                        AbsenceDetailRow("Grund", absence.reason)
                    if (absence.detail.isNotBlank())
                        AbsenceDetailRow("Bemerkung", absence.detail)
                    AbsenceDetailRow("Zeit",
                        "${absence.startTime.toString().take(5)} – ${absence.endTime.toString().take(5)}")
                    AbsenceDetailRow("Status",
                        if (absence.isExcused) "✅ Entschuldigt" else "❌ Unentschuldigt")
                }
            }
        }
    }
}

@Composable
private fun AbsenceDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

// =============================================================
// EMPTY / ERROR / LOADING
// =============================================================
@Composable
private fun EmptyState(emoji: String, title: String, message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShimmerLoading() {
    val shimmer by rememberInfiniteTransition(label = "s").animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "sv"
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(6) { i ->
            Box(modifier = Modifier.fillMaxWidth().height(if (i == 0) 80.dp else 60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = shimmer)))
        }
    }
}