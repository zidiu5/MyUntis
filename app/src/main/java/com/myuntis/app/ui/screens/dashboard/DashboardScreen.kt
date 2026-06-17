// ═══════════════════════════════════════════════════════════════
// FILE: ui/screens/dashboard/DashboardScreen.kt
// ═══════════════════════════════════════════════════════════════

package com.myuntis.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuntis.app.data.local.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class DashboardUiState(
    val fullName: String  = "",
    val className: String = "",
    val greeting: String  = ""
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dataStore: DataStoreManager
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = dataStore.userProfile
        .map { profile ->
            val hour = LocalTime.now().hour
            val greeting = when {
                hour in 0..11  -> "Guten Morgen"
                hour in 12..17 -> "Guten Nachmittag"
                else           -> "Guten Abend"
            }
            DashboardUiState(
                fullName  = profile.fullName.ifBlank { profile.className },
                className = profile.className,
                greeting  = greeting
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())
}

// Feature card data
private data class FeatureCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

private val featureCards = listOf(
    FeatureCard(
        title    = "Stundenplan",
        subtitle = "Woche & Monat",
        icon     = Icons.Outlined.CalendarMonth,
        color    = Color(0xFF1565C0),
        route    = "timetable"
    ),
    FeatureCard(
        title    = "Aufgaben",
        subtitle = "Hausaufgaben",
        icon     = Icons.Outlined.Assignment,
        color    = Color(0xFF2E7D32),
        route    = "homework"
    ),
    FeatureCard(
        title    = "Noten",
        subtitle = "Beurteilungen",
        icon     = Icons.Outlined.Star,
        color    = Color(0xFFE65100),
        route    = "grades"
    ),
    FeatureCard(
        title    = "Prüfungen",
        subtitle = "Alle Tests & SAs",
        icon     = Icons.Outlined.School,
        color    = Color(0xFF6A1B9A),
        route    = "exams"
    ),
    FeatureCard(
        title    = "Klassenbuch",
        subtitle = "Einträge",
        icon     = Icons.Outlined.MenuBook,
        color    = Color(0xFFAD1457),
        route    = "classreg"
    ),
    FeatureCard(
        title    = "Einstellungen",
        subtitle = "Dark Mode, Farben",
        icon     = Icons.Outlined.Settings,
        color    = Color(0xFF37474F),
        route    = "settings"
    )
)

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy", Locale.GERMAN))
        .replaceFirstChar { it.uppercaseChar() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Gradient header ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A237E), Color(0xFF1565C0))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    uiState.greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    uiState.fullName.ifBlank { "Willkommen" },
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Date chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.DateRange, null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(today, style = MaterialTheme.typography.bodySmall,
                                color = Color.White, fontSize = 11.sp)
                        }
                    }
                    // Class chip
                    if (uiState.className.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Group, null,
                                    tint = Color.White, modifier = Modifier.size(14.dp))
                                Text(uiState.className, style = MaterialTheme.typography.bodySmall,
                                    color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Feature grid ─────────────────────────────────────
        Text(
            "Übersicht",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(featureCards) { card ->
                FeatureCardItem(card = card, onClick = { onNavigate(card.route) })
            }
        }
    }
}

@Composable
private fun FeatureCardItem(card: FeatureCard, onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement   = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(card.icon, null,
                    tint = card.color, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(
                    card.title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}
