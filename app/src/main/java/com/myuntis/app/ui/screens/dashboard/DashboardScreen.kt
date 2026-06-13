package com.myuntis.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myuntis.app.domain.model.Lesson
import com.myuntis.app.domain.model.LessonCode
import com.myuntis.app.domain.model.isCancelled
import com.myuntis.app.domain.model.timeRange
import com.myuntis.app.ui.theme.gradeColor
import java.time.LocalTime


@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()


    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            DashboardLoadingState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── HEADER ──
                item {
                    DashboardHeader(
                        greeting = uiState.greeting,
                        userName = uiState.userName,
                        className = uiState.className,
                        dateText = uiState.currentDateFormatted,
                        onRefreshClick = { viewModel.loadDashboard() },   // NEU
                        onSettingsClick = onNavigateToSettings
                    )
                }

                // ── ERROR BANNER ──
                if (uiState.errorMessage != null) {
                    item {
                        ErrorBanner(
                            message = uiState.errorMessage!!,
                            onRetry = { viewModel.loadDashboard() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── NEXT LESSON ──
                if (uiState.nextLesson != null) {
                    item {
                        NextLessonCard(
                            lesson = uiState.nextLesson!!,
                            label = uiState.nextLessonLabel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── TODAY'S SCHEDULE HEADER ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Heute",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.todayLessons.size} Stunden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── TODAY'S LESSONS ──
                if (uiState.todayLessons.isEmpty() && uiState.errorMessage == null) {
                    item { EmptyDayCard(modifier = Modifier.padding(horizontal = 16.dp)) }
                } else {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                uiState.todayLessons.forEachIndexed { index, lesson ->
                                    LessonListItem(lesson = lesson)
                                    if (index < uiState.todayLessons.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(
                                                start = 72.dp, end = 16.dp
                                            ),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// =============================================================
// DASHBOARD HEADER
// =============================================================
@Composable
private fun DashboardHeader(
    greeting: String,
    userName: String,
    className: String,
    dateText: String,
    onRefreshClick: () -> Unit,      // NEU
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .padding(top = 16.dp, bottom = 28.dp)
            .padding(horizontal = 20.dp)
    ) {
        Column {
            // Top row: greeting + settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = userName.ifBlank { "Schüler" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Direkt VOR dem Settings-IconButton:
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Aktualisieren",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date and class info row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date chip
                InfoChip(
                    icon = Icons.Outlined.CalendarToday,
                    text = dateText
                )
                // Class chip
                if (className.isNotBlank()) {
                    InfoChip(
                        icon = Icons.Outlined.Group,
                        text = className
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

// =============================================================
// NEXT LESSON CARD
// =============================================================
@Composable
private fun NextLessonCard(
    lesson: Lesson,
    label: String,
    modifier: Modifier = Modifier
) {
    val isRunning = label.startsWith("Läuft")

    // Pulse animation for currently running lesson
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isRunning) "Läuft gerade" else "Nächste Stunde",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Time badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subject name
            Text(
                text = lesson.subject.longName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Details row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time
                LessonDetailChip(
                    icon = Icons.Outlined.AccessTime,
                    text = lesson.timeRange
                )
                // Room
                if (lesson.rooms.isNotEmpty()) {
                    LessonDetailChip(
                        icon = Icons.Outlined.Room,
                        text = lesson.rooms.joinToString(", ")
                    )
                }
                // Teacher
                if (lesson.teachers.isNotEmpty()) {
                    LessonDetailChip(
                        icon = Icons.Outlined.Person,
                        text = lesson.teachers.joinToString(", ")
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonDetailChip(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
        )
    }
}

// =============================================================
// LESSON LIST ITEM
// =============================================================
@Composable
private fun LessonListItem(lesson: Lesson) {
    val now = LocalTime.now()
    val isRunning = lesson.startTime <= now && lesson.endTime > now
    val isPast = lesson.endTime <= now
    val isCancelled = lesson.isCancelled

    val contentAlpha = when {
        isCancelled -> 0.45f
        isPast      -> 0.55f
        else        -> 1.0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isRunning)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time column
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = lesson.startTime.toString().take(5),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                color = if (isRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                fontSize = 13.sp
            )
            Text(
                text = lesson.endTime.toString().take(5),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Colored vertical bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isCancelled)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    else
                        subjectColor(lesson.subject.shortName)
                            .copy(alpha = if (isPast) 0.3f else 1f)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Subject info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = lesson.subject.longName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isRunning) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCancelled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Entfällt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 10.sp
                        )
                    }
                }
                if (lesson.code == com.myuntis.app.domain.model.LessonCode.IRREGULAR) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Vertretung",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Room and teacher
            val details = buildList {
                if (lesson.rooms.isNotEmpty()) add(lesson.rooms.joinToString(", "))
                if (lesson.teachers.isNotEmpty()) add(lesson.teachers.joinToString(", "))
            }.joinToString(" · ")

            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Running indicator
        if (isRunning) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// =============================================================
// EMPTY DAY CARD
// =============================================================
@Composable
private fun EmptyDayCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🎉", fontSize = 40.sp)
            Text(
                text = "Heute kein Unterricht",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Genieße deinen freien Tag!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================
// ERROR BANNER
// =============================================================
@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(
                    "Retry",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// =============================================================
// LOADING STATE (Skeleton)
// =============================================================
@Composable
private fun DashboardLoadingState() {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(
            initialValue = 0.3f, targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = EaseInOut), RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha))
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (it == 0) 100.dp else 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerColor.copy(alpha = shimmerAlpha * 0.3f))
                )
            }
        }
    }
}

// =============================================================
// SUBJECT COLOR HELPER
// =============================================================
// Returns a distinct color per subject short name for the
// colored bar on the left of each lesson item.
// Uses a deterministic hash so the same subject always gets
// the same color within a session.
// =============================================================
@Composable
private fun subjectColor(shortName: String): Color {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF2E7D32),  // Green
        Color(0xFF6A1B9A),  // Purple
        Color(0xFF00695C),  // Teal
        Color(0xFFE65100),  // Orange
        Color(0xFF1565C0),  // Blue
        Color(0xFFAD1457),  // Pink
    )
    val index = (shortName.hashCode() and 0x7FFFFFFF) % colors.size
    return colors[index]
}