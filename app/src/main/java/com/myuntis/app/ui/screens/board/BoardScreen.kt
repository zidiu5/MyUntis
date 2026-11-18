package com.myuntis.app.ui.screens.board

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.network.model.BoardAttachment
import com.myuntis.app.data.network.model.BoardPost
import com.myuntis.app.data.network.model.BoardStats
import com.myuntis.app.data.network.model.StorageItem
import com.myuntis.app.data.network.model.StorageResponse
import com.myuntis.app.data.repository.BoardRepository
import com.myuntis.app.data.repository.BoardSnapshot
import com.myuntis.app.data.repository.PostDraft
import com.myuntis.app.data.repository.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// =============================================================
// VIEW MODEL
// =============================================================
private const val MAX_ATTACHMENTS = 5

enum class UploadStatus { UPLOADING, DONE, ERROR }

data class PendingFile(
    val localId: String,
    val name: String,
    val status: UploadStatus,
    val fileId: String? = null,
    val size: Long = 0,
    val error: String? = null
)

data class OpenRequest(val file: File, val mime: String)

data class BoardUiState(
    val isStudent: Boolean            = true,
    val isLoading: Boolean            = true,
    val isRefreshing: Boolean         = false,
    val posts: List<BoardPost>        = emptyList(),
    val stats: BoardStats?            = null,
    val className: String             = "",
    val schoolName: String            = "",
    val error: String?                = null,
    val filter: String?               = null,
    val doneIds: Set<String>          = emptySet(),
    val subjects: List<String>        = emptyList(),
    val message: String?              = null,
    val busy: Boolean                 = false,
    val pending: List<PendingFile>    = emptyList(),
    val busyAttachmentId: String?     = null,
    val showStorage: Boolean          = false,
    val storage: StorageResponse?     = null,
    val storageLoading: Boolean       = false
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
    dataStore: DataStoreManager
) : ViewModel() {

    private val _ui = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<OpenRequest>(extraBufferCapacity = 1)
    val events: SharedFlow<OpenRequest> = _events.asSharedFlow()

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
            repository.getCached()?.let { apply(it) }
            refresh()
        }
    }

    private fun apply(snap: BoardSnapshot) {
        _ui.update {
            it.copy(
                posts = snap.posts, stats = snap.stats,
                className = snap.session.className, schoolName = snap.session.schoolName,
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
    fun showMessage(text: String) = _ui.update { it.copy(message = text) }

    fun toggleDone(postId: String, done: Boolean) {
        viewModelScope.launch { repository.setDone(postId, done) }
    }

    /** Downloads the file (or takes it from the disk cache). null = failed. */
    suspend fun fileFor(att: BoardAttachment): File? =
        when (val r = repository.downloadAttachment(att)) {
            is NetworkResult.Success -> r.data
            else -> null
        }

    // ── Create / edit ─────────────────────────────────────────
    fun savePost(editing: BoardPost?, draft: PostDraft, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val err = if (editing == null) repository.createPost(draft).errorMessage()
            else repository.updatePost(editing, draft).errorMessage()
            if (err == null) {
                _ui.update {
                    it.copy(busy = false, pending = emptyList(),
                        message = if (editing == null) "Beitrag erstellt" else "Gespeichert")
                }
                onSuccess()
                refresh()
            } else {
                _ui.update { it.copy(busy = false, message = err) }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val err = repository.deletePost(postId).errorMessage()
            _ui.update { it.copy(message = err ?: "Beitrag gelöscht") }
            if (err == null) { refresh(); if (_ui.value.showStorage) loadStorage() }
        }
    }

    fun reportPost(post: BoardPost) {
        viewModelScope.launch {
            val err = repository.reportPost(post.id).errorMessage()
            _ui.update { it.copy(message = err ?: "Danke, der Beitrag wurde gemeldet") }
            if (err == null) refresh()
        }
    }

    // ── Attachments: upload while the editor is open ──────────
    fun clearPending() = _ui.update { it.copy(pending = emptyList()) }

    fun removePending(localId: String) =
        _ui.update { it.copy(pending = it.pending.filterNot { p -> p.localId == localId }) }

    private fun updatePending(localId: String, change: (PendingFile) -> PendingFile) {
        _ui.update { s -> s.copy(pending = s.pending.map { if (it.localId == localId) change(it) else it }) }
    }

    fun addFiles(uris: List<Uri>, keptExisting: Int) {
        val free = (MAX_ATTACHMENTS - keptExisting - _ui.value.pending.size).coerceAtLeast(0)
        if (free == 0) { showMessage("Maximal $MAX_ATTACHMENTS Anhänge pro Beitrag."); return }
        if (uris.size > free) showMessage("Nur $free weitere Anhänge möglich, der Rest wurde ignoriert.")

        viewModelScope.launch {
            for (uri in uris.take(free)) {
                val localId = UUID.randomUUID().toString()
                _ui.update {
                    it.copy(pending = it.pending +
                            PendingFile(localId, "Wird hochgeladen …", UploadStatus.UPLOADING))
                }
                when (val r = repository.uploadFile(uri)) {
                    is NetworkResult.Success -> updatePending(localId) {
                        it.copy(name = r.data.name, size = r.data.size,
                            fileId = r.data.fileId, status = UploadStatus.DONE)
                    }
                    is NetworkResult.Error -> updatePending(localId) {
                        it.copy(name = "Upload fehlgeschlagen", status = UploadStatus.ERROR, error = r.message)
                    }
                    else -> updatePending(localId) { it.copy(status = UploadStatus.ERROR) }
                }
            }
        }
    }

    // ── Attachments: open / save ──────────────────────────────
    fun openAttachment(att: BoardAttachment) {
        if (_ui.value.busyAttachmentId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyAttachmentId = att.id) }
            when (val r = repository.downloadAttachment(att)) {
                is NetworkResult.Success -> _events.emit(OpenRequest(r.data, att.mime))
                is NetworkResult.Error   -> _ui.update { it.copy(message = r.message) }
                else -> {}
            }
            _ui.update { it.copy(busyAttachmentId = null) }
        }
    }

    fun saveAttachment(att: BoardAttachment) {
        if (_ui.value.busyAttachmentId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyAttachmentId = att.id) }
            when (val r = repository.downloadAttachment(att)) {
                is NetworkResult.Success -> {
                    val err = repository.saveAttachment(r.data, att)
                    _ui.update { it.copy(message = err ?: "Gespeichert in Downloads/MyUntis") }
                }
                is NetworkResult.Error -> _ui.update { it.copy(message = r.message) }
                else -> {}
            }
            _ui.update { it.copy(busyAttachmentId = null) }
        }
    }

    // ── Delete vote + storage ─────────────────────────────────
    fun vote(postId: String, vote: Boolean) {
        viewModelScope.launch {
            when (val r = repository.vote(postId, vote)) {
                is NetworkResult.Success -> {
                    val v = r.data
                    _ui.update {
                        it.copy(message = when {
                            v.deleted -> "Der Beitrag wurde gelöscht"
                            vote      -> "Stimme abgegeben (${v.count} von ${v.needed})"
                            else      -> "Stimme zurückgezogen"
                        })
                    }
                    refresh()
                    if (_ui.value.showStorage) loadStorage()
                }
                is NetworkResult.Error -> _ui.update { it.copy(message = r.message) }
                else -> {}
            }
        }
    }

    fun openStorage() { _ui.update { it.copy(showStorage = true) }; loadStorage() }
    fun closeStorage() = _ui.update { it.copy(showStorage = false) }

    private fun loadStorage() {
        viewModelScope.launch {
            _ui.update { it.copy(storageLoading = true) }
            when (val r = repository.getStorage()) {
                is NetworkResult.Success -> _ui.update { it.copy(storage = r.data) }
                is NetworkResult.Error   -> _ui.update { it.copy(message = r.message) }
                else -> {}
            }
            _ui.update { it.copy(storageLoading = false) }
        }
    }
}

// =============================================================
// SCREEN
// =============================================================
private val TYPES = listOf(
    "HOMEWORK" to "Hausaufgabe", "EXAM" to "Prüfung", "INFO" to "Info", "FILE" to "Datei"
)

private const val DONE = "DONE"

private val PICK_TYPES = arrayOf(
    "image/*",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
)

private fun formatBytes(b: Long): String = when {
    b >= 1_000_000 -> String.format(Locale.GERMAN, "%.1f MB", b / 1_000_000.0)
    b >= 1_000     -> "${b / 1000} KB"
    else           -> "$b B"
}

private fun attachmentIcon(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Outlined.Image
    mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
    else                      -> Icons.Outlined.Description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    onBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showEditor    by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<BoardPost?>(null) }
    var confirmDelete by remember { mutableStateOf<BoardPost?>(null) }
    var confirmReport by remember { mutableStateOf<BoardPost?>(null) }

    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<ViewerState?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    // Open downloaded files with the default app
    LaunchedEffect(Unit) {
        viewModel.events.collect { req ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", req.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, req.mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { context.startActivity(intent) }
            catch (_: ActivityNotFoundException) { viewModel.showMessage("Keine App zum Öffnen gefunden.") }
        }
    }


    if (showEditor) {
        PostEditorDialog(
            editing   = editing,
            subjects  = ui.subjects,
            busy      = ui.busy,
            pending   = ui.pending,
            onPick    = viewModel::addFiles,
            onRemovePending = viewModel::removePending,
            onDismiss = { showEditor = false; viewModel.clearPending() },
            onSave    = { draft ->
                viewModel.savePost(editing, draft) { showEditor = false }
            }
        )
    }

    confirmDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Beitrag löschen?") },
            text  = { Text("„${post.title.orEmpty()}“ wird mit allen Anhängen für alle gelöscht.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePost(post.id); confirmDelete = null },
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

    if (ui.showStorage) {
        StorageSheet(
            storage   = ui.storage,
            loading   = ui.storageLoading,
            onDismiss = viewModel::closeStorage,
            onVote    = viewModel::vote,
            onDelete  = { item -> confirmDelete = BoardPost(id = item.postId, title = item.title) }
        )
    }

    viewer?.let { v ->
        AttachmentViewer(
            items = v.items,
            startIndex = v.index,
            loadFile = { viewModel.fileFor(it) },
            onOpenExternal = viewModel::openAttachment,
            onSave = viewModel::saveAttachment,
            onDismiss = { viewer = null }
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
                    onClick = { viewModel.clearPending(); editing = null; showEditor = true },
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
                    onToggleDone = { id, done ->
                        viewModel.toggleDone(id, done)
                        if (done) scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val r = snackbar.showSnackbar(
                                message = "In „Erledigte“ verschoben",
                                actionLabel = "Rückgängig",
                                duration = SnackbarDuration.Short
                            )
                            if (r == SnackbarResult.ActionPerformed) viewModel.toggleDone(id, false)
                        }
                    },
                    onEdit = { viewModel.clearPending(); editing = it; showEditor = true },
                    onDelete = { confirmDelete = it },
                    onReport = { confirmReport = it },
                    onVote = viewModel::vote,
                    onOpenAttachment = viewModel::openAttachment,
                    onSaveAttachment = viewModel::saveAttachment,
                    onOpenStorage = viewModel::openStorage,
                    loadFile = { viewModel.fileFor(it) },
                    onPreview = { items, index -> viewer = ViewerState(items, index) }
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
    onReport: (BoardPost) -> Unit,
    onVote: (String, Boolean) -> Unit,
    onOpenAttachment: (BoardAttachment) -> Unit,
    onSaveAttachment: (BoardAttachment) -> Unit,
    onOpenStorage: () -> Unit,
    loadFile: suspend (BoardAttachment) -> File?,
    onPreview: (List<BoardAttachment>, Int) -> Unit
) {
    val today = LocalDate.now()
    val shown = remember(ui.posts, ui.filter, ui.doneIds, today) {
        visiblePosts(ui.posts, ui.filter, ui.doneIds, today)
    }
    val doneCount = remember(ui.posts, ui.doneIds) {
        ui.posts.count { !it.deleted && it.type == "HOMEWORK" && it.id in ui.doneIds }
    }

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
                item {
                    FilterChip(
                        selected = ui.filter == DONE,
                        onClick  = { onFilter(DONE) },
                        label    = { Text(if (doneCount > 0) "Erledigte ($doneCount)" else "Erledigte") }
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                when {
                    ui.filter == DONE -> InfoState("✅", "Noch nichts erledigt",
                        "Abgehakte Hausaufgaben erscheinen hier. Das siehst nur du.")
                    doneCount > 0 && (ui.filter == null || ui.filter == "HOMEWORK") ->
                        InfoState("🎉", "Alles erledigt",
                            "Deine abgehakten Hausaufgaben findest du unter „Erledigte“.")
                    else -> InfoState("📌", "Noch nichts da",
                        "Trag die erste Hausaufgabe oder Info für deine Klasse ein.")
                }
            }
        } else {
            items(shown, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    done = post.id in ui.doneIds,
                    today = today,
                    busyAttachmentId = ui.busyAttachmentId,
                    onToggleDone = { onToggleDone(post.id, it) },
                    onEdit = { onEdit(post) },
                    onDelete = { onDelete(post) },
                    onReport = { onReport(post) },
                    onVote = { onVote(post.id, it) },
                    onOpenAttachment = onOpenAttachment,
                    onSaveAttachment = onSaveAttachment,
                    loadFile = loadFile,
                    onPreview = onPreview
                )
            }
        }

        item {
            ui.stats?.let { st ->
                if (st.quotaBytes > 0) {
                    TextButton(onClick = onOpenStorage, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Dateispeicher: ${formatBytes(st.usedBytes)} von ${formatBytes(st.quotaBytes)} · " +
                                    "${st.activeMembers} aktive Mitschüler · Aufräumen",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

/** Done homework only shows up under "Erledigte"; upcoming deadlines come first elsewhere. */
private fun visiblePosts(
    posts: List<BoardPost>, filter: String?, doneIds: Set<String>, today: LocalDate
): List<BoardPost> {
    fun due(p: BoardPost): LocalDate? = p.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun isDone(p: BoardPost) = p.type == "HOMEWORK" && p.id in doneIds
    val base = posts.filter { !it.deleted }

    if (filter == DONE) {
        return base.filter { isDone(it) }.sortedByDescending { due(it) ?: LocalDate.MIN }
    }
    val filtered = base.filter { !isDone(it) && (filter == null || it.type == filter) }
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
    busyAttachmentId: String?,
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onVote: (Boolean) -> Unit,
    onOpenAttachment: (BoardAttachment) -> Unit,
    onSaveAttachment: (BoardAttachment) -> Unit,
    loadFile: suspend (BoardAttachment) -> File?,
    onPreview: (List<BoardAttachment>, Int) -> Unit
) {
    val mine = post.author?.mine == true
    val surface = MaterialTheme.colorScheme.surface

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
    val votes = post.deleteVotes

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
                        if (mine) {
                            DropdownMenuItem(
                                text = { Text("Löschen") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; onDelete() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (votes?.mine == true) "Stimme zurückziehen" else "Für Löschen stimmen") },
                                leadingIcon = { Icon(Icons.Outlined.HowToVote, null) },
                                onClick = { menu = false; onVote(votes?.mine != true) }
                            )
                            DropdownMenuItem(
                                text = { Text("Melden") },
                                leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                                onClick = { menu = false; onReport() }
                            )
                        }
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

            // Attachments: images as thumbnails, PDFs in the viewer, other files in another app
            val images   = post.attachments.filter { it.isImage() }
            val previews = post.attachments.filter { it.canPreview() }       // viewer list: images + PDFs

            if (images.isNotEmpty()) {
                val single = images.size == 1
                Row(
                    Modifier.fillMaxWidth().then(
                        if (single) Modifier else Modifier.horizontalScroll(rememberScrollState())
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    images.forEach { img ->
                        ImageThumb(
                            att = img,
                            loadFile = loadFile,
                            modifier = if (single) Modifier.fillMaxWidth().height(180.dp) else Modifier.size(110.dp),
                            onClick = { onPreview(previews, previews.indexOf(img)) }
                        )
                    }
                }
            }

            post.attachments.filter { !it.isImage() }.forEach { att ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (att.isPdf()) onPreview(previews, previews.indexOf(att)) else onOpenAttachment(att)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(attachmentIcon(att.mime), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(att.name, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatBytes(att.size) + if (att.isPdf()) " · Vorschau" else " · öffnet in anderer App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (busyAttachmentId == att.id) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onSaveAttachment(att) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Download, "In Downloads speichern", Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Delete vote status
            if (votes != null && votes.count > 0) {
                Text(
                    "Löschung: ${votes.count} von ${votes.needed} Stimmen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
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
// STORAGE SHEET
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSheet(
    storage: StorageResponse?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onVote: (String, Boolean) -> Unit,
    onDelete: (StorageItem) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Speicher aufräumen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (storage == null) {
                if (loading) CircularProgressIndicator() else Text("Keine Daten.")
            } else {
                LinearProgressIndicator(
                    progress = { (storage.usedBytes.toFloat() / storage.quotaBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${formatBytes(storage.usedBytes)} von ${formatBytes(storage.quotaBytes)} belegt",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Fremde Beiträge werden gelöscht, sobald genug Mitschüler dafür stimmen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (storage.items.isEmpty()) {
                    Text("Keine Beiträge mit Dateien.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(storage.items, key = { it.postId }) { item ->
                            val mine = item.author?.mine == true
                            val votes = item.deleteVotes
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        listOfNotNull(item.subject?.takeIf { it.isNotBlank() }, item.title)
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${formatBytes(item.size)} · von ${item.author?.nickname.orEmpty()}" +
                                                (votes?.takeIf { it.count > 0 }?.let { " · ${it.count} von ${it.needed} Stimmen" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (mine) {
                                    TextButton(onClick = { onDelete(item) }) { Text("Löschen") }
                                } else {
                                    OutlinedButton(onClick = { onVote(item.postId, votes?.mine != true) }) {
                                        Text(if (votes?.mine == true) "Zurückziehen" else "Löschen")
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// DIALOGS
// =============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostEditorDialog(
    editing: BoardPost?,
    subjects: List<String>,
    busy: Boolean,
    pending: List<PendingFile>,
    onPick: (List<Uri>, Int) -> Unit,
    onRemovePending: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (PostDraft) -> Unit
) {
    val isAuthor = editing == null || editing.author?.mine == true
    val existing = editing?.attachments.orEmpty()

    var type     by remember { mutableStateOf(editing?.type ?: "HOMEWORK") }
    var subject  by remember { mutableStateOf(editing?.subject.orEmpty()) }
    var title    by remember { mutableStateOf(editing?.title.orEmpty()) }
    var body     by remember { mutableStateOf(editing?.body.orEmpty()) }
    var dueDate  by remember { mutableStateOf(editing?.dueDate) }
    var openEdit by remember { mutableStateOf(editing?.openEdit ?: false) }
    var keep     by remember { mutableStateOf(existing.map { it.id }.toSet()) }
    var showPicker by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPick(uris, keep.size)
    }

    val attachmentIds = existing.filter { it.id in keep }.map { it.id } +
            pending.filter { it.status == UploadStatus.DONE }.mapNotNull { it.fileId }
    val uploading = pending.any { it.status == UploadStatus.UPLOADING }
    val canSave = title.trim().isNotEmpty() && !busy && !uploading &&
            (type != "FILE" || attachmentIds.isNotEmpty())

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
                if (isAuthor) {
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

                // ── Attachments ──────────────────────────────
                Text("Anhänge (${attachmentIds.size}/$MAX_ATTACHMENTS)", style = MaterialTheme.typography.labelLarge)

                existing.forEach { att ->
                    val kept = att.id in keep
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(attachmentIcon(att.mime), null, Modifier.size(18.dp))
                        Text(
                            att.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = if (kept) TextDecoration.None else TextDecoration.LineThrough
                        )
                        // Only the author may remove files (others can only add)
                        if (isAuthor) {
                            TextButton(onClick = { keep = if (kept) keep - att.id else keep + att.id }) {
                                Text(if (kept) "Entfernen" else "Behalten")
                            }
                        }
                    }
                }

                pending.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (p.status) {
                            UploadStatus.UPLOADING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            UploadStatus.DONE      -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            UploadStatus.ERROR     -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            p.error?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { onRemovePending(p.localId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, "Entfernen", Modifier.size(16.dp))
                        }
                    }
                }

                OutlinedButton(
                    onClick = { launcher.launch(PICK_TYPES) },
                    enabled = attachmentIds.size + pending.count { it.status != UploadStatus.DONE } < MAX_ATTACHMENTS
                ) {
                    Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Datei anhängen")
                }
                Text(
                    "Fotos, PDF, Word, Excel, PowerPoint · max. 10 MB pro Datei",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (type == "FILE" && attachmentIds.isEmpty()) {
                    Text("Ein Datei-Beitrag braucht mindestens einen Anhang.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                if (isAuthor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Andere dürfen ergänzen", style = MaterialTheme.typography.bodyMedium)
                            Text("Text und Datum ändern, Dateien hinzufügen. Löschen darfst nur du.",
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
                enabled = canSave,
                onClick = { onSave(PostDraft(type, subject, title, body, dueDate, openEdit, attachmentIds)) }
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