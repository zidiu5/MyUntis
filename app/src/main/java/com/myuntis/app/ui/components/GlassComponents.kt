package com.myuntis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myuntis.app.ui.theme.GlassWhite
import com.myuntis.app.ui.theme.GlassWhiteLight

// =============================================================
// GLASS CARD COMPONENT
// =============================================================
// A Card with a frosted glass appearance.
// Creates depth through:
// 1. Semi-transparent background
// 2. Gradient overlay (lighter at top, darker at bottom)
// 3. Subtle white border (simulates glass edge highlight)
// 4. Soft shadow via Material elevation
// =============================================================
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),    // Rounded corners
    glassColor: Color = GlassWhite,              // Background glass tint
    borderColor: Color = GlassWhiteLight,        // Edge highlight color
    borderWidth: Dp = 1.dp,                      // Edge highlight thickness
    elevation: Dp = 2.dp,                        // Shadow depth
    content: @Composable BoxScope.() -> Unit     // Content inside the card
) {
    Card(
        modifier = modifier,
        shape = shape,
        // Material3 Card elevation - creates subtle shadow
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            // Use transparent container so our custom background shows through
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                // Step 1: Clip to the card shape
                .clip(shape)
                // Step 2: Apply gradient background (glass effect)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            glassColor,                                    // Top: more opaque
                            glassColor.copy(alpha = glassColor.alpha * 0.7f) // Bottom: less opaque
                        )
                    )
                )
                // Step 3: Subtle border (glass edge highlight)
                .border(
                    width = borderWidth,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            borderColor,                         // Top border: more visible
                            borderColor.copy(alpha = 0.1f)       // Bottom border: nearly invisible
                        )
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

// =============================================================
// SURFACE GLASS CARD
// =============================================================
// Uses Material3 surface color with glass treatment.
// Best for cards that need to match the app's color scheme
// automatically in both light and dark mode.
// =============================================================
@Composable
fun SurfaceGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    alpha: Float = 0.85f,          // How transparent the card is (0=invisible, 1=solid)
    elevation: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    GlassCard(
        modifier = modifier,
        shape = shape,
        // Use the theme's surface color with our chosen transparency
        glassColor = surfaceColor.copy(alpha = alpha),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        elevation = elevation,
        content = content
    )
}

// =============================================================
// PRIMARY GLASS CARD
// =============================================================
// A card with the primary color tint - used for highlighting
// important information (next lesson, important homework, etc.)
// =============================================================
@Composable
fun PrimaryGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primaryContainer

    GlassCard(
        modifier = modifier,
        shape = shape,
        glassColor = primaryColor.copy(alpha = 0.9f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        borderWidth = 1.5.dp,
        elevation = 6.dp,
        content = content
    )
}

// =============================================================
// GRADIENT BACKGROUND
// =============================================================
// Full-screen gradient background for screens.
// Creates a beautiful depth effect for the login screen
// and dashboard header.
// =============================================================
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    topColor: Color = MaterialTheme.colorScheme.primary,
    bottomColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    // Color stops: 0.0 = top, 1.0 = bottom
                    colorStops = arrayOf(
                        0.0f to topColor,                       // Full primary at top
                        0.3f to topColor.copy(alpha = 0.8f),    // Fading out
                        0.7f to bottomColor.copy(alpha = 0.95f), // Transitioning to bg
                        1.0f to bottomColor                      // Full background at bottom
                    )
                )
            ),
        content = content
    )
}

// =============================================================
// STATUS BADGE
// =============================================================
// Small colored pill/badge for displaying status information.
// Used for: homework status, absence type, grade categories
// =============================================================
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))    // Pill shape (fully rounded)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

// =============================================================
// SECTION HEADER
// =============================================================
// Consistent section titles used throughout the app.
// Includes title + optional action button (e.g. "See all")
// =============================================================
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Show action button only if both text and click handler are provided
        if (actionText != null && onActionClick != null) {
            androidx.compose.material3.TextButton(onClick = onActionClick) {
                androidx.compose.material3.Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}