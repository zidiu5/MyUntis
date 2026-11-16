package com.myuntis.app.ui.screens.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.model.BoardPost
import com.myuntis.app.data.network.model.BoardStats
import com.myuntis.app.data.repository.BoardRepository
import com.myuntis.app.data.repository.BoardSnapshot
import com.myuntis.app.data.repository.PostDraft
import com.myuntis.app.data.repository.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

// =============================================================
// VIEW MODEL
// =============================================================
data class BoardUiState(
    val isStudent: Boolean       = true,
    val isLoading: Boolean       = true,
    val isRefreshing: Boolean    = false,
    val posts: List<BoardPost>   = emptyList(),
    val stats: BoardStats?       = null,
    val className: String        = "",
    val schoolName: String       = "",
    val nickname: String         = "",
    val askNickname: Boolean     = false,
    val error: String?           = null,
    val filter: String?          = null,           // null = all
    val doneIds: Set<String>     = emptySet(),
    val subjects: List<String>   = emptyList(),
    val message: String?         = null,
    val busy: Boolean            = false
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
    dataStore: DataStoreManager
) : ViewModel() {

    private val _ui = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.doneIds().collect { ids -> _ui.update { it.copy(doneIds = ids) } }
        }
        viewModelScope.launch {
            dataStore.knownSubjects.collect { list ->
                _ui.update { it.copy(subjects = list.filter { s -> s != "EVT" }.sorted()) }
            }
        }
        viewModelScope.launch {
            if (!repository.isStudentFlow.first()) {
                _ui.update { it.copy(isStudent = false, isLoading = false) }
                return@launch
            }
            repository.getCached()?.let { apply(it) }   // instant
            refresh()
        }
    }

    private fun apply(snap: BoardSnapshot) {
        _ui.update {
            it.copy(
                posts = snap.posts, stats = snap.stats,
                className = snap.session.className, schoolName = snap.session.schoolName,
                nickname = snap.session.nickname,
                askNickname = !snap.session.nicknameConfirmed,
                isLoading = false, isRefreshing = false, error = null
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            when (val r = repository.sync()) {
                is NetworkResult.Success -> apply(r.data)
                is NetworkResult.Error -> _ui.update {
                    it.copy(
                        isLoading = false, isRefreshing = false,
                        error = if (it.posts.isEmpty()) r.message else null,
                        message = if (it.posts.isNotEmpty()) r.message else null
                    )
                }
                else -> _ui.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun setFilter(type: String?) = _ui.update { it.copy(filter = type) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    fun toggleDone(postId: String, done: Boolean) {
        viewModelScope.launch { repository.setDone(postId, done) }
    }

    fun savePost(editing: BoardPost?, draft: PostDraft, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val err = if (editing == null) repository.createPost(draft).errorMessage()
            else repository.updatePost(editing, draft).errorMessage()
            if (err == null) {
                _ui.update { it.copy(busy = false, message = if (editing == null) "Beitrag erstellt" else "Gespeichert") }
                onSuccess()
                refresh()
            } else {
                _ui.update { it.copy(busy = false, message = err) }
            }
        }
    }

    fun deletePost(post: BoardPost) {
        viewModelScope.launch {
            val err = repository.deletePost(post.id).errorMessage()
            _ui.update { it.copy(message = err ?: "Beitrag gelöscht") }
            if (err == null) refresh()
        }
    }

    fun reportPost(post: BoardPost) {
        viewModelScope.launch {
            val err = repository.reportPost(post.id).errorMessage()
            _ui.update { it.copy(message = err ?: "Danke, der Beitrag wurde gemeldet") }
            if (err == null) refresh()
        }
    }

    fun confirmNickname(name: String) {
        viewModelScope.launch {
            val current = _ui.value.nickname
            if (name.trim() == current) {
                repository.confirmNickname()
                _ui.update { it.copy(askNickname = false) }
            } else {
                val err = repository.updateNickname(name).errorMessage()
                if (err == null) _ui.update { it.copy(askNickname = false, nickname = name.trim()) }
                else _ui.update { it.copy(message = err) }
            }
        }
    }
}

// =============================================================
// SCREEN
// =============================================================
private val TYPES = listOf("HOMEWORK" to "Hausaufgabe", "EXAM" to "Prüfung", "INFO" to "Info")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    onBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showEditor    by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<BoardPost?>(null) }
    var confirmDelete by remember { mutableStateOf<BoardPost?>(null) }
    var confirmReport by remember { mutableStateOf<BoardPost?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    if (ui.askNickname && ui.nickname.isNotBlank()) {
        NicknameDialog(current = ui.nickname, onConfirm = viewModel::confirmNickname)
    }

    if (showEditor) {
        PostEditorDialog(
            editing   = editing,
            subjects  = ui.subjects,
            busy      = ui.busy,
            onDismiss = { showEditor = false },
            onSave    = { draft -> viewModel.savePost(editing, draft) { showEditor = false } }
        )
    }

    confirmDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Beitrag löschen?") },
            text  = { Text("„${post.title.orEmpty()}“ wird für alle gelöscht.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePost(post); confirmDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } }
        )
    }

    confirmReport?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmReport = null },
            title = { Text("Beitrag melden?") },
            text  = { Text("Wenn mehrere Mitschüler einen Beitrag melden, wird er ausgeblendet, bis er geprüft wurde.") },
            confirmButton = {
                Button(onClick = { viewModel.reportPost(post); confirmReport = null }) { Text("Melden") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmReport = null }) { Text("Abbrechen") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Schwarzes Brett", fontWeight = FontWeight.Bold)
                        if (ui.className.isNotBlank()) {
                            Text(ui.className, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (ui.isStudent) {
                        if (ui.isRefreshing) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, "Aktualisieren") }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (ui.isStudent && ui.className.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; showEditor = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Neu") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !ui.isStudent -> InfoState("🔒", "Nur für Schüler",
                    "Das Schwarze Brett ist nur für Schüler-Konten verfügbar.")
                ui.isLoading && ui.posts.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null && ui.posts.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.WifiOff, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text(ui.error ?: "", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::refresh) { Text("Erneut versuchen") }
                }
                else -> BoardContent(
                    ui = ui,
                    onFilter = viewModel::setFilter,
                    onToggleDone = viewModel::toggleDone,
                    onEdit = { editing = it; showEditor = true },
                    onDelete = { confirmDelete = it },
                    onReport = { confirmReport = it }
                )
            }
        }
    }
}

@Composable
private fun BoardContent(
    ui: BoardUiState,
    onFilter: (String?) -> Unit,
    onToggleDone: (String, Boolean) -> Unit,
    onEdit: (BoardPost) -> Unit,
    onDelete: (BoardPost) -> Unit,
    onReport: (BoardPost) -> Unit
) {
    val today = LocalDate.now()
    val shown = remember(ui.posts, ui.filter, today) { visiblePosts(ui.posts, ui.filter, today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(ui.filter == null, { onFilter(null) }, { Text("Alle") }) }
                items(TYPES) { (type, label) ->
                    FilterChip(ui.filter == type, { onFilter(type) }, { Text(label) })
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                InfoState("📌", "Noch nichts da",
                    "Trag die erste Hausaufgabe oder Info für deine Klasse ein.")
            }
        } else {
            items(shown, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    done = post.id in ui.doneIds,
                    today = today,
                    onToggleDone = { onToggleDone(post.id, it) },
                    onEdit = { onEdit(post) },
                    onDelete = { onDelete(post) },
                    onReport = { onReport(post) }
                )
            }
        }

        item {
            ui.stats?.let { st ->
                if (st.quotaBytes > 0) {
                    Text(
                        "Speicher: ${st.usedBytes / 1_000_000} von ${st.quotaBytes / 1_000_000} MB · " +
                                "${st.activeMembers} aktive Mitschüler",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(88.dp))   // room for the button
        }
    }
}

/** Upcoming deadlines first (soonest on top), then the rest, newest first. */
private fun visiblePosts(posts: List<BoardPost>, filter: String?, today: LocalDate): List<BoardPost> {
    fun due(p: BoardPost): LocalDate? = p.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val filtered = posts.filter { !it.deleted && (filter == null || it.type == filter) }
    val upcoming = filtered.filter { due(it)?.let { d -> !d.isBefore(today) } == true }.sortedBy { due(it) }
    val ids = upcoming.map { it.id }.toSet()
    val rest = filtered.filter { it.id !in ids }.sortedByDescending { it.createdAt }
    return upcoming + rest
}

// =============================================================
// POST CARD
// =============================================================
private fun typeStyle(type: String): Pair<String, Color> = when (type) {
    "HOMEWORK" -> "Hausaufgabe" to Color(0xFF1565C0)
    "EXAM"     -> "Prüfung" to Color(0xFFE65100)
    "FILE"     -> "Datei" to Color(0xFF6A1B9A)
    else       -> "Info" to Color(0xFF00796B)
}

@Composable
private fun PostCard(
    post: BoardPost,
    done: Boolean,
    today: LocalDate,
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    val mine = post.author?.mine == true
    val surface = MaterialTheme.colorScheme.surface

    // Reported by others and hidden by the server
    if (post.hidden && !mine) {
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
            CardDefaults.cardColors(containerColor = surface),
            CardDefaults.cardElevation(0.dp)
        ) {
            Text("Dieser Beitrag wurde gemeldet und ist ausgeblendet.",
                Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var expanded by remember(post.id, post.updatedAt) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val (typeLabel, typeColor) = typeStyle(post.type)
    val due = post.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val isHomework = post.type == "HOMEWORK"
    val dim = if (isHomework && done) 0.55f else 1f

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Type · subject · menu
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = typeColor.copy(alpha = 0.12f).compositeOver(surface)) {
                    Text(typeLabel, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.SemiBold)
                }
                if (!post.subject.isNullOrBlank()) {
                    Text(post.subject, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (post.hidden && mine) {
                    Text("ausgeblendet", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.MoreVert, "Mehr", Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (post.canEdit) DropdownMenuItem(
                            text = { Text("Bearbeiten") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() }
                        )
                        if (mine) DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() }
                        ) else DropdownMenuItem(
                            text = { Text("Melden") },
                            leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                            onClick = { menu = false; onReport() }
                        )
                    }
                }
            }

            // Title
            Text(
                post.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isHomework && done) TextDecoration.LineThrough else TextDecoration.None,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
            )

            // Due date
            if (due != null) {
                val days = ChronoUnit.DAYS.between(today, due)
                val (label, color) = when {
                    days < 0L  -> "überfällig seit ${due.format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN))}" to
                            (if (isHomework && done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    days == 0L -> "Heute fällig" to MaterialTheme.colorScheme.error
                    days == 1L -> "Morgen fällig" to Color(0xFFE65100)
                    else       -> "Fällig ${due.format(DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN))}" to
                            MaterialTheme.colorScheme.primary
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Event, null, Modifier.size(14.dp), tint = color)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }

            // Body
            if (!post.body.isNullOrBlank()) {
                Text(
                    post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
                )
            }

            // Attachments (opening comes in part B)
            post.attachments.forEach { att ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AttachFile, null, Modifier.size(14.dp))
                    Text("${att.name} · ${att.size / 1000} KB", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Footer: author + private "done" tick
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "von ${post.author?.nickname.orEmpty()}" + if (post.openEdit) " · offen zum Ergänzen" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    post.lastEditedBy?.let {
                        Text("zuletzt bearbeitet von $it", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isHomework) {
                    Text("Erledigt", style = MaterialTheme.typography.labelMedium)
                    Checkbox(checked = done, onCheckedChange = onToggleDone)
                }
            }
        }
    }
}

// =============================================================
// DIALOGS
// =============================================================
@Composable
private fun NicknameDialog(current: String, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Heißt du so?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("So sehen dich deine Mitschüler im Schwarzen Brett.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.trim().length >= 2) { Text("Weiter") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostEditorDialog(
    editing: BoardPost?,
    subjects: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (PostDraft) -> Unit
) {
    val isAuthor = editing == null || editing.author?.mine == true

    var type     by remember { mutableStateOf(editing?.type ?: "HOMEWORK") }
    var subject  by remember { mutableStateOf(editing?.subject.orEmpty()) }
    var title    by remember { mutableStateOf(editing?.title.orEmpty()) }
    var body     by remember { mutableStateOf(editing?.body.orEmpty()) }
    var dueDate  by remember { mutableStateOf(editing?.dueDate) }
    var openEdit by remember { mutableStateOf(editing?.openEdit ?: false) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        dueDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (editing == null) "Neuer Beitrag" else "Beitrag bearbeiten") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isAuthor && type != "FILE") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TYPES) { (t, label) ->
                            FilterChip(type == t, { type = t }, { Text(label) })
                        }
                    }
                }

                OutlinedTextField(
                    value = subject, onValueChange = { if (it.length <= 40) subject = it },
                    label = { Text("Fach") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (subjects.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subjects) { s ->
                            FilterChip(subject == s, { subject = if (subject == s) "" else s }, { Text(s) })
                        }
                    }
                }

                OutlinedTextField(
                    value = title, onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Titel (z. B. Aufgabe 3 + 4)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body, onValueChange = { if (it.length <= 4000) body = it },
                    label = { Text("Beschreibung (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPicker = true }) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            dueDate?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN)) }
                                ?: "Fällig am …"
                        )
                    }
                    if (dueDate != null) {
                        IconButton(onClick = { dueDate = null }) { Icon(Icons.Outlined.Close, "Datum entfernen") }
                    }
                }

                if (isAuthor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Andere dürfen ergänzen", style = MaterialTheme.typography.bodyMedium)
                            Text("Text und Datum ändern. Löschen darfst nur du.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = openEdit, onCheckedChange = { openEdit = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().isNotEmpty() && !busy,
                onClick = { onSave(PostDraft(type, subject, title, body, dueDate, openEdit)) }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Speichern")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } }
    )
}

@Composable
private fun InfoState(emoji: String, title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}