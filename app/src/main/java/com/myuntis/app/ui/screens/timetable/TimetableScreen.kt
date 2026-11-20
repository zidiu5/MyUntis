package com.myuntis.app.ui.screens.timetable

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myuntis.app.data.network.model.CalendarRef
import com.myuntis.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi

// =============================================================
// TIMETABLE SCREEN
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── View-mode selector ──────────────────────────────
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TimetableViewMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = uiState.viewMode == mode,
                    onClick  = { viewModel.setViewMode(mode) },
                    shape    = SegmentedButtonDefaults.itemShape(
                        index = idx, count = TimetableViewMode.entries.size
                    ),
                    label = { Text(mode.label) }
                )
            }
        }

        // ── Navigation header ───────────────────────────────
        DateNavigationHeader(
            uiState    = uiState,
            onPrevious = viewModel::navigatePrevious,
            onNext     = viewModel::navigateNext,
            onToday    = viewModel::goToToday,
            onRefresh  = viewModel::refresh
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Error banner ────────────────────────────────────
        AnimatedVisibility(visible = uiState.errorMessage != null) {
            uiState.errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.WifiOff, null,
                            tint     = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            msg,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::refresh) {
                            Text(
                                "Retry",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // ── Main content ────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            // The big spinner only shows before the very first data arrives
            if (uiState.isLoading && uiState.lessonsByDate.isEmpty()) {
                TimetableLoadingState()
            } else {
                AnimatedContent(
                    targetState    = uiState.viewMode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label          = "view_mode"
                ) { mode ->
                    when (mode) {
                        TimetableViewMode.DAY -> SlidingPager(
                            page      = dayPage(uiState.selectedDate),
                            onSettled = { p -> viewModel.onDayPageSelected(dayDate(p)) }
                        ) { p ->
                            DayPage(uiState, dayDate(p), viewModel::openLesson)
                        }

                        TimetableViewMode.WEEK -> SlidingPager(
                            page      = weekPage(uiState.selectedDate),
                            onSettled = { p -> viewModel.onWeekPageSelected(weekMonday(p)) }
                        ) { p ->
                            WeekGridView(uiState, weekMonday(p), viewModel::openLesson)
                        }

                        TimetableViewMode.MONTH -> SlidingPager(
                            page      = monthPage(uiState.displayedMonth),
                            onSettled = { p -> viewModel.onMonthPageSelected(monthOf(p)) }
                        ) { p ->
                            MonthView(uiState, monthOf(p)) { date ->
                                viewModel.selectDate(date)
                                viewModel.setViewMode(TimetableViewMode.DAY)
                            }
                        }
                    }
                }
            }
        }

        // ── Lesson popup ────────────────────────────────────────────
        uiState.selectedLesson?.let { lesson ->
            LessonDetailSheet(
                lesson       = lesson,
                detail       = uiState.detail,
                customColors = uiState.subjectColors,
                onDismiss    = viewModel::closeLesson
            )
        }
    }
}

// =============================================================
// DATE NAVIGATION HEADER
// =============================================================
@Composable
private fun DateNavigationHeader(
    uiState: TimetableUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit
) {
    val today = LocalDate.now()

    val headerText = remember(uiState.viewMode, uiState.selectedDate, uiState.displayedMonth) {
        when (uiState.viewMode) {
            TimetableViewMode.DAY ->
                uiState.selectedDate
                    .format(DateTimeFormatter.ofPattern("EEE, dd. MMM yyyy", Locale.GERMAN))
                    .replaceFirstChar { it.uppercaseChar() }

            TimetableViewMode.WEEK -> {
                val mon = uiState.weekDates.first()
                val sat = uiState.weekDates.last()
                val kw  = mon.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                "KW $kw · " + if (mon.month == sat.month) {
                    "${mon.dayOfMonth}. – " +
                            sat.format(DateTimeFormatter.ofPattern("dd. MMM", Locale.GERMAN))
                } else {
                    mon.format(DateTimeFormatter.ofPattern("dd. MMM", Locale.GERMAN)) + " – " +
                            sat.format(DateTimeFormatter.ofPattern("dd. MMM", Locale.GERMAN))
                }
            }

            TimetableViewMode.MONTH ->
                uiState.displayedMonth
                    .atDay(1)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
                    .replaceFirstChar { it.uppercaseChar() }
        }
    }

    val isAtCurrent = when (uiState.viewMode) {
        TimetableViewMode.DAY   -> uiState.selectedDate == today
        TimetableViewMode.WEEK  -> today in uiState.weekDates
        TimetableViewMode.MONTH -> uiState.displayedMonth == YearMonth.now()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, "Zurück")
        }
        Text(
            text       = headerText,
            modifier   = Modifier.weight(1f),
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, "Nächste")
        }
        if (!isAtCurrent) {
            TextButton(
                onClick = onToday,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    "Heute",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // Refresh icon (no spinning circle)
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Outlined.Refresh,
                "Aktualisieren",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================
// SLIDING PAGER – swipe follows the finger, arrows slide too
// =============================================================
private const val PAGE_COUNT = 20_000
private val EPOCH_MONDAY: LocalDate = LocalDate.of(2020, 1, 6)       // a Monday
private val EPOCH_MONTH: YearMonth = YearMonth.of(2020, 1)

private fun dayPage(d: LocalDate) =
    ChronoUnit.DAYS.between(EPOCH_MONDAY, d).toInt().coerceIn(0, PAGE_COUNT - 1)
private fun dayDate(p: Int): LocalDate = EPOCH_MONDAY.plusDays(p.toLong())

private fun weekPage(d: LocalDate) = ChronoUnit.WEEKS.between(
    EPOCH_MONDAY, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
).toInt().coerceIn(0, PAGE_COUNT - 1)
private fun weekMonday(p: Int): LocalDate = EPOCH_MONDAY.plusWeeks(p.toLong())

private fun monthPage(m: YearMonth) =
    ChronoUnit.MONTHS.between(EPOCH_MONTH, m).toInt().coerceIn(0, PAGE_COUNT - 1)
private fun monthOf(p: Int): YearMonth = EPOCH_MONTH.plusMonths(p.toLong())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlidingPager(
    page: Int,                          // page the app state says we should be on
    onSettled: (Int) -> Unit,           // finger swipe ended on this page
    content: @Composable (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = page, pageCount = { PAGE_COUNT })
    val currentOnSettled by rememberUpdatedState(onSettled)

    // Changed from outside (arrows, "Heute", month → day): slide there
    LaunchedEffect(page) {
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    // Finished a swipe: tell the app (idempotent, so the loop above does not repeat)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnSettled(it) }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxSize(),
        key      = { it }
    ) { p -> content(p) }
}

@Composable
private fun DayPage(uiState: TimetableUiState, date: LocalDate, onLessonClick: (Lesson) -> Unit) {
    val lessons = uiState.lessonsByDate[date].orEmpty()
    val loaded = lessons.isNotEmpty() ||
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) in uiState.loadedWeeks
    if (!loaded) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    } else {
        DayView(lessons, date, uiState.subjectColors, onLessonClick)
    }
}

// =============================================================
// WEEK GRID VIEW
// Auto-fits all lessons into the available screen height.
// No vertical scroll – everything visible at once.
// =============================================================
@Composable
private fun WeekGridView(
    uiState: TimetableUiState,
    monday: LocalDate,
    onLessonClick: (Lesson) -> Unit
) {
    val weekDates  = remember(monday) { (0L..5L).map { monday.plusDays(it) } }
    val today      = LocalDate.now()
    val allLessons = weekDates.flatMap { uiState.lessonsByDate[it] ?: emptyList() }

    if (allLessons.isEmpty()) {
        if (monday in uiState.loadedWeeks) EmptyWeekState()
        else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val gridStartMin = allLessons
        .minOf { it.startTime.hour * 60 + it.startTime.minute }
        .let { (it - 5).coerceAtLeast(6 * 60) }
    val gridEndMin = allLessons
        .maxOf { it.endTime.hour * 60 + it.endTime.minute }
        .let { (it + 10).coerceAtMost(22 * 60) }

    val timeLabels = (allLessons.map { it.startTime } + allLessons.map { it.endTime })
        .distinct().sorted()
    val timeColWidth = 44.dp

    val divColor  = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val todayBg   = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.07f)
    val surfaceBg = MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Day header row ───────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().background(surfaceBg)) {
            Box(
                modifier = Modifier.width(timeColWidth).padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "KW\n${weekDates.first().format(DateTimeFormatter.ofPattern("ww"))}",
                    style     = MaterialTheme.typography.labelSmall,
                    fontSize  = 9.sp,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            weekDates.forEach { date ->
                WeekDayHeaderCell(date = date, isToday = date == today, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Auto-fit grid ────────────────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val availableHeight = this.maxHeight
            val gridMinutes     = (gridEndMin - gridStartMin).toFloat().coerceAtLeast(1f)
            val minuteHeightDp  = (availableHeight.value / gridMinutes).dp

            Row(modifier = Modifier.fillMaxSize()) {

                // Time labels
                Box(modifier = Modifier.width(timeColWidth).fillMaxHeight()) {
                    timeLabels.forEach { time ->
                        val minFromStart = time.hour * 60 + time.minute - gridStartMin
                        val yDp = (minuteHeightDp * minFromStart.toFloat() - 7.dp).coerceAtLeast(0.dp)
                        Text(
                            text = time.toString().take(5),
                            modifier = Modifier.offset(y = yDp).fillMaxWidth().padding(end = 3.dp),
                            textAlign = TextAlign.End,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // One column per day
                weekDates.forEach { date ->
                    val dayLessons = uiState.lessonsByDate[date] ?: emptyList()
                    val isToday = date == today

                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val colWidth = this.maxWidth

                        if (isToday) Box(Modifier.fillMaxSize().background(todayBg))

                        timeLabels.forEach { time ->
                            val minFromStart = time.hour * 60 + time.minute - gridStartMin
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .offset(y = minuteHeightDp * minFromStart.toFloat())
                                    .background(divColor)
                            )
                        }

                        groupSideBySide(dayLessons).forEach { group ->
                            val cnt = group.size
                            group.forEachIndexed { idx, lesson ->
                                val startMin = lesson.startTime.hour * 60 + lesson.startTime.minute - gridStartMin
                                val endMin   = lesson.endTime.hour * 60 + lesson.endTime.minute - gridStartMin
                                if (startMin < 0) return@forEachIndexed

                                val yDp = minuteHeightDp * startMin.toFloat()
                                val hDp = (minuteHeightDp * (endMin - startMin).toFloat()).coerceAtLeast(14.dp)
                                val xDp = (colWidth.value / cnt * idx).dp
                                val wDp = (colWidth.value / cnt).dp - 1.5.dp

                                LessonGridBlock(
                                    lesson       = lesson,
                                    modifier     = Modifier.offset(x = xDp, y = yDp).width(wDp).height(hDp),
                                    onClick      = { onLessonClick(lesson) },     // ← popup, not day view
                                    customColors = uiState.subjectColors
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// WEEK DAY HEADER CELL
// =============================================================
@Composable
private fun WeekDayHeaderCell(
    date: LocalDate,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val dayAbbr = date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        .take(2)
        .replaceFirstChar { it.uppercaseChar() }

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = dayAbbr,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = if (isToday) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize   = 11.sp,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// =============================================================
// LESSON GRID BLOCK
// Inner border  = subject color
// Outer border  = status:
//   EXAM              → yellow  (#FFD600)
//   CANCELLED         → red     (#FF4444)
//   IRREGULAR/ADDITIONAL (Vertretung/extra) → green (#44FF88)
// =============================================================
@Composable
private fun LessonGridBlock(
    lesson: Lesson,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    customColors: Map<String, String> = emptyMap()
) {
    val isCancelled = lesson.isCancelled
    val color       = subjectColor(lesson.subject.shortName, customColors)

    val outerBorderColor: Color? = when {
        isCancelled                          -> Color(0xFFFF4444)
        lesson.code == LessonCode.IRREGULAR ||
                lesson.code == LessonCode.ADDITIONAL -> Color(0xFF44FF88)
        lesson.lessonType == LessonType.EXAM -> Color(0xFFFFD600)
        else                                 -> null
    }

    val innerBorderColor = if (isCancelled)
        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    else
        color.copy(alpha = 0.55f)

    val bgAlpha = if (isCancelled) 0.07f else 0.17f

    BoxWithConstraints(
        modifier = modifier.then(
            if (outerBorderColor != null)
                Modifier.border(BorderStroke(2.dp, outerBorderColor), RoundedCornerShape(5.dp))
            else Modifier
        )
    ) {
        val blockWidth  = this.maxWidth
        val blockHeight = this.maxHeight
        val isNarrow    = blockWidth  < 38.dp
        val isVeryShort = blockHeight < 28.dp
        val showRoom    = !isNarrow && blockHeight > 56.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (outerBorderColor != null) 2.dp else 0.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = bgAlpha))
                .border(BorderStroke(1.5.dp, innerBorderColor), RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (isNarrow) 1.dp else 3.dp,
                    vertical   = 2.dp
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = lesson.subject.shortName,
                color = if (isCancelled) MaterialTheme.colorScheme.error else color,
                fontWeight = FontWeight.Bold,
                fontSize   = if (isNarrow) 8.sp else 10.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textDecoration = if (isCancelled) TextDecoration.LineThrough
                else TextDecoration.None
            )
            if (!isNarrow && !isVeryShort) {
                lesson.teachers.firstOrNull()?.let { teacher ->
                    Text(
                        text     = teacher,
                        fontSize = 8.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showRoom) {
                lesson.rooms.firstOrNull()?.let { room ->
                    Text(
                        text     = room,
                        fontSize = 8.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCancelled && !isVeryShort) {
                Text("✗", fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================
// DAY VIEW
// =============================================================
@Composable
private fun DayView(
    lessons: List<Lesson>,
    selectedDate: LocalDate,
    customColors: Map<String, String> = emptyMap(),
    onLessonClick: (Lesson) -> Unit = {}
) {
    if (lessons.isEmpty()) {
        EmptyDayContent(selectedDate)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // FIX: Use itemsIndexed with a composite key so multiple lessons with the same ID don't crash
        itemsIndexed(lessons, key = { i, l -> "${l.id}_${l.startTime}_$i" }) { _, lesson ->
            LessonDetailCard(lesson, customColors, onClick = { onLessonClick(lesson) })
        }
    }
}

@Composable
private fun LessonDetailCard(
    lesson: Lesson,
    customColors: Map<String, String> = emptyMap(),
    onClick: () -> Unit = {}
) {
    val isCancelled = lesson.isCancelled
    val now         = LocalTime.now()
    val isRunning   = !isCancelled && lesson.startTime <= now && lesson.endTime > now
    val isPast      = lesson.endTime <= now
    val alpha       = if (isPast && !isRunning) 0.55f else 1f
    val subjColor   = subjectColor(lesson.subject.shortName, customColors)

    // Outer border for exam / status
    val outerBorderColor: Color? = when {
        isCancelled                          -> Color(0xFFFF4444)
        lesson.code == LessonCode.IRREGULAR ||
                lesson.code == LessonCode.ADDITIONAL -> Color(0xFF44FF88)
        lesson.lessonType == LessonType.EXAM -> Color(0xFFFFD600)
        else                                 -> null
    }

    Box(
        modifier = Modifier.fillMaxWidth().then(
            if (outerBorderColor != null)
                Modifier.border(BorderStroke(2.dp, outerBorderColor), RoundedCornerShape(17.dp))
            else Modifier
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (outerBorderColor != null) 2.dp else 0.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCancelled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    isRunning   -> MaterialTheme.colorScheme.primaryContainer
                    else        -> MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(if (isRunning) 4.dp else 1.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {

                // Time column with colored vertical bar
                Column(
                    modifier = Modifier.width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        lesson.startTime.toString().take(5),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(subjColor.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        lesson.endTime.toString().take(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = lesson.subject.longName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            modifier = Modifier.weight(1f),
                            maxLines  = 2,
                            textDecoration = if (isCancelled) TextDecoration.LineThrough
                            else TextDecoration.None
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isCancelled)
                                StatusChip("Entfällt",    Color(0xFFFF4444))
                            if (lesson.code == LessonCode.IRREGULAR ||
                                lesson.code == LessonCode.ADDITIONAL)
                                StatusChip("Vertretung", Color(0xFF44FF88))
                            if (lesson.lessonType == LessonType.EXAM)
                                StatusChip("Prüfung",    Color(0xFFFFD600))
                            if (isRunning)
                                StatusChip("Jetzt",      MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (lesson.teachers.isNotEmpty())
                        IconTextRow(Icons.Outlined.Person, lesson.teachers.joinToString(", "), alpha)
                    if (lesson.rooms.isNotEmpty())
                        IconTextRow(Icons.Outlined.Room, lesson.rooms.joinToString(", "), alpha)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text       = text,
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IconTextRow(icon: ImageVector, text: String, alpha: Float) {
    Row(
        modifier  = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================
// MONTH VIEW
// =============================================================
@Composable
private fun MonthView(
    uiState: TimetableUiState,
    month: YearMonth,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDay    = month.atDay(1)
    val startOffset = firstDay.dayOfWeek.value - 1   // 0 = Monday
    val daysInMonth = month.lengthOfMonth()
    val today       = LocalDate.now()
    val numRows     = ((startOffset + daysInMonth) + 6) / 7

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                listOf("Mo","Di","Mi","Do","Fr","Sa","So").forEach { d ->
                    Text(
                        d,
                        modifier   = Modifier.weight(1f),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
        }

        items(numRows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0..6).forEach { col ->
                    val dayNum = row * 7 + col - startOffset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (dayNum in 1..daysInMonth) {
                            val date    = month.atDay(dayNum)
                            val lessons = uiState.lessonsByDate[date] ?: emptyList()
                            MonthCell(
                                date       = date,
                                isToday    = date == today,
                                isSelected = date == uiState.selectedDate,
                                isWeekend  = col >= 5,
                                lessons    = lessons,
                                onClick    = { onDateSelected(date) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isWeekend: Boolean,
    lessons: List<Lesson>,
    onClick: () -> Unit
) {
    val active    = lessons.filter { !it.isCancelled }
    val cancelled = lessons.any    { it.isCancelled  }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday    -> MaterialTheme.colorScheme.primaryContainer
                        else       -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday    -> MaterialTheme.colorScheme.onPrimaryContainer
                    isWeekend  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else       -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.Center) {
                active.take(3).forEach { lesson ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(subjectColor(lesson.subject.shortName))
                    )
                }
                if (cancelled) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

// =============================================================
// EMPTY STATES
// =============================================================
@Composable
private fun EmptyDayContent(date: LocalDate) {
    val isWeekend = date.dayOfWeek.value >= 6
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isWeekend) "🏖️" else "📅", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (isWeekend) "Wochenende!" else "Kein Unterricht",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isWeekend) "Erhol dich gut! 😊"
            else "Für diesen Tag sind keine Stunden eingetragen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyWeekState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth, null,
                modifier = Modifier.size(48.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                "Keine Stunden diese Woche",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================
// LOADING STATE (shimmer)
// =============================================================
@Composable
private fun TimetableLoadingState() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue  = 0.08f,
        targetValue   = 0.22f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = EaseInOut),
            RepeatMode.Reverse
        ),
        label = "shimmerVal"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(7) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (i == 0) 90.dp else 65.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = shimmer)
                    )
            )
        }
    }
}

// =============================================================
// OVERLAP GROUPING
// Groups lessons that share overlapping time into sets.
// Each set is drawn side-by-side in the column.
// =============================================================
private fun groupSideBySide(lessons: List<Lesson>): List<List<Lesson>> {
    if (lessons.isEmpty()) return emptyList()
    val result  = mutableListOf<List<Lesson>>()
    val visited = mutableSetOf<Int>()

    lessons.forEachIndexed { i, lesson ->
        if (i in visited) return@forEachIndexed
        val overlapping = lessons.filterIndexed { j, other ->
            j != i &&
                    lesson.startTime < other.endTime &&
                    lesson.endTime   > other.startTime
        }
        if (overlapping.isEmpty()) {
            result.add(listOf(lesson))
            visited.add(i)
        } else {
            val group = (listOf(lesson) + overlapping).distinct()
            group.forEach { l -> visited.add(lessons.indexOf(l)) }
            result.add(group)
        }
    }
    return result
}

// =============================================================
// SUBJECT COLOR
// Custom hex overrides hash-based colour.
// No-arg variant used in MonthView (no customColors available).
// =============================================================
@Composable
private fun subjectColor(
    shortName: String,
    customColors: Map<String, String> = emptyMap()
): Color {
    // Try custom color first
    val hex = customColors[shortName]
    if (hex != null) {
        try {
            // return inside try → catch falls through to default
            return Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            // parsing failed → fall through to hash-based default below
        }
    }
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

// =============================================================
// LESSON POPUP
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailSheet(
    lesson: Lesson,
    detail: LessonDetailState,
    customColors: Map<String, String>,
    onDismiss: () -> Unit
) {
    val entry = (detail as? LessonDetailState.Ready)?.entry
    val color = subjectColor(lesson.subject.shortName, customColors)
    val gray  = MaterialTheme.colorScheme.onSurfaceVariant

    val title = entry?.subject?.longName?.takeIf { it.isNotBlank() }
        ?: lesson.subject.longName.ifBlank { lesson.subject.shortName }
    val dateText = lesson.date.format(DateTimeFormatter.ofPattern("EEEE, dd. MMMM", Locale.GERMAN))
    val timeText = "${lesson.startTime.toString().take(5)} – ${lesson.endTime.toString().take(5)}"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(5.dp).height(48.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text("$dateText · $timeText", style = MaterialTheme.typography.bodyMedium, color = gray)
                }
            }

            // Status chips (known right away from the timetable)
            val chips = buildList {
                if (lesson.isCancelled) add("Entfällt" to Color(0xFFFF4444))
                if (lesson.code == LessonCode.IRREGULAR) add("Vertretung" to Color(0xFF44FF88))
                if (lesson.code == LessonCode.ADDITIONAL) add("Zusatzstunde" to Color(0xFF44FF88))
                if (lesson.lessonType == LessonType.EXAM) add("Prüfung" to Color(0xFFFFD600))
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { (text, c) -> StatusChip(text, c) }
                }
            }

            // Teachers
            DetailRow(Icons.Outlined.Person, "Lehrer") {
                when {
                    entry != null && entry.teachers.isNotEmpty() -> entry.teachers.forEach { RefLine(it) }
                    lesson.teachers.isNotEmpty() -> Text(lesson.teachers.joinToString(", "))
                    else -> Text("–", color = gray)
                }
            }

            // Rooms
            DetailRow(Icons.Outlined.MeetingRoom, "Raum") {
                when {
                    entry != null && entry.rooms.isNotEmpty() -> entry.rooms.forEach { RefLine(it) }
                    lesson.rooms.isNotEmpty() -> Text(lesson.rooms.joinToString(", "))
                    else -> Text("–", color = gray)
                }
            }

            // Loading / failed hint
            if (detail is LessonDetailState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Details werden geladen …", style = MaterialTheme.typography.labelMedium, color = gray)
                }
            }
            if (detail is LessonDetailState.Failed) {
                Text("Weitere Details konnten nicht geladen werden.",
                    style = MaterialTheme.typography.labelMedium, color = gray)
            }

            // Details from Untis
            entry?.exam?.let { ex ->
                DetailRow(Icons.Outlined.School, "Prüfung") {
                    Text(ex.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                    ex.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                }
            }
            entry?.substText?.takeIf { it.isNotBlank() }?.let {
                DetailRow(Icons.Outlined.SwapHoriz, "Vertretung") { Text(it) }
            }
            if (entry?.exam == null) {
                entry?.lessonInfo?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(Icons.Outlined.Info, "Info") { Text(it) }
                }
            }
            entry?.homeworks?.takeIf { it.isNotEmpty() }?.let { list ->
                DetailRow(Icons.Outlined.EditNote, "Hausaufgaben") {
                    list.forEach { hw ->
                        Text(hw.text.orEmpty())
                        formatDue(hw.dueDateTime)?.let {
                            Text("Fällig: $it", style = MaterialTheme.typography.labelMedium, color = gray)
                        }
                        hw.remark?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = gray)
                        }
                    }
                }
            }
            entry?.notesAll?.takeIf { it.isNotBlank() }?.let {
                DetailRow(Icons.Outlined.Notes, "Notiz") { Text(it) }
            }
            entry?.teachingContent?.takeIf { it.isNotBlank() }?.let {
                DetailRow(Icons.Outlined.Notes, "Unterrichtsinhalt") { Text(it) }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(22.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun RefLine(ref: CalendarRef) {
    val name  = ref.longName?.takeIf { it.isNotBlank() } ?: ref.displayName ?: ref.shortName.orEmpty()
    val short = ref.shortName?.takeIf { it.isNotBlank() && it != name }
    val removed = ref.status == "REMOVED"
    val added   = ref.status == "ADDED"
    Text(
        text = name + (short?.let { " ($it)" } ?: "") +
                when { added -> " · neu"; removed -> " · entfällt"; else -> "" },
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (removed) TextDecoration.LineThrough else TextDecoration.None,
        color = if (added) Color(0xFF2E9E4F) else MaterialTheme.colorScheme.onSurface
    )
}

private fun formatDue(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        java.time.LocalDateTime.parse(raw).toLocalDate()
            .format(DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN))
    }.getOrDefault(raw)
}