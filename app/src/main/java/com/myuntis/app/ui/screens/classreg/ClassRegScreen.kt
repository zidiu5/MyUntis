// ═══════════════════════════════════════════════════════════════
// FILE: ui/screens/classreg/ClassRegScreen.kt
// ═══════════════════════════════════════════════════════════════

package com.myuntis.app.ui.screens.classreg

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.network.NetworkResult
import com.myuntis.app.data.repository.ClassRegRepository
import com.myuntis.app.domain.model.ClassRegEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ClassRegUiState(
    val isLoading: Boolean         = true,
    val entries: List<ClassRegEntry> = emptyList(),
    val error: String?             = null
)

@HiltViewModel
class ClassRegViewModel @Inject constructor(
    private val repository: ClassRegRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClassRegUiState())
    val uiState: StateFlow<ClassRegUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = repository.getEntries()) {
                is NetworkResult.Success ->
                    _uiState.update { it.copy(isLoading = false, entries = r.data) }
                is NetworkResult.Error ->
                    _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassRegScreen(
    onBack: () -> Unit,
    viewModel: ClassRegViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Klassenbuch", fontWeight = FontWeight.Bold) },
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
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.WifiOff, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.error!!, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::load) { Text("Erneut versuchen") }
                    }
                }
                uiState.entries.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📖", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Keine Einträge", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.entries, key = { it.id }) { entry ->
                            ClassRegEntryCard(entry)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassRegEntryCard(entry: ClassRegEntry) {
    val fmt = DateTimeFormatter.ofPattern("EEE, dd. MMM yyyy", Locale.GERMAN)
    var expanded by remember { mutableStateOf(false) }

    // Personal entries (STUDENT) are highlighted differently from class entries
    val dotColor = if (entry.isPersonal) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
    val label    = if (entry.isPersonal) "Persönlich" else "Klasse"

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (entry.isPersonal)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type dot
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Column(modifier = Modifier.weight(1f)) {
                    // Subject
                    Text(
                        entry.subjectName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1
                    )
                    Text(
                        "${entry.date.format(fmt).replaceFirstChar { it.uppercaseChar() }} · ${entry.time.toString().take(5)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Personal / Class badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = dotColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = dotColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp
                    else Icons.Outlined.KeyboardArrowDown,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 14.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entry.text.isNotBlank()) {
                        Text(
                            entry.text,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (entry.teacherName.isNotBlank()) {
                        DetailRow("Lehrer", entry.teacherName)
                    }
                    if (entry.category.isNotBlank()) {
                        DetailRow("Kategorie", entry.category)
                    }
                    if (entry.reason.isNotBlank() && entry.reason != entry.category) {
                        DetailRow("Grund", entry.reason)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
