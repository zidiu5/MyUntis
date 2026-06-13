package com.myuntis.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================
// LIGHT COLOR SCHEME
// =============================================================
// Each color role has a specific semantic meaning in Material 3.
// Primary = main interactive elements
// Container = background for grouped elements
// On* = foreground color placed ON TOP of the named background
// =============================================================
private val LightColorScheme = lightColorScheme(
    // Primary blue
    primary = Blue40,                        // #1565C0 - Buttons, FAB
    onPrimary = SurfaceLight,                // White text on blue buttons
    primaryContainer = Blue90,               // #DBE4FF - Light blue containers
    onPrimaryContainer = Blue10,             // Dark text on light blue

    // Secondary indigo
    secondary = Indigo40,                    // #5C6BC0 - Secondary actions
    onSecondary = SurfaceLight,
    secondaryContainer = Indigo90,           // #DEE0FF
    onSecondaryContainer = Indigo10,

    // Tertiary cyan
    tertiary = Cyan40,                       // #00ACC1 - Accents/highlights
    onTertiary = SurfaceLight,
    tertiaryContainer = Cyan90,              // #B3EEFF
    onTertiaryContainer = Cyan10,

    // Error red
    error = Red40,
    onError = SurfaceLight,
    errorContainer = Red90,
    onErrorContainer = Red10,

    // Backgrounds & Surfaces
    background = BlueGrey99,                 // #F8F9FF - Very light blue-white
    onBackground = BlueGrey10,               // Near-black text

    surface = SurfaceLight,                  // White cards
    onSurface = BlueGrey10,
    surfaceVariant = SurfaceVariantLight,    // #E3E8F0 - Slightly tinted
    onSurfaceVariant = BlueGrey40,

    // Other
    outline = BlueGrey80,                    // Borders, dividers
    outlineVariant = BlueGrey90,             // Subtle borders
    scrim = BlueGrey10,                      // Modal backdrop
    inverseSurface = BlueGrey20,             // For snackbars
    inverseOnSurface = BlueGrey95,
    inversePrimary = Blue80
)

// =============================================================
// DARK COLOR SCHEME
// =============================================================
// In dark mode, lighter shades become the "active" colors
// because they stand out against the dark background.
// =============================================================
private val DarkColorScheme = darkColorScheme(
    primary = Blue80,                        // #AFC6FF - Lighter blue for dark mode
    onPrimary = Blue20,                      // Dark blue text on light blue button
    primaryContainer = Blue30,               // #1A4B9E - Container in dark mode
    onPrimaryContainer = Blue90,             // Light text on dark container

    secondary = Indigo80,
    onSecondary = Indigo20,
    secondaryContainer = Indigo30,
    onSecondaryContainer = Indigo90,

    tertiary = Cyan80,
    onTertiary = Cyan20,
    tertiaryContainer = Cyan30,
    onTertiaryContainer = Cyan90,

    error = Red80,
    onError = Red20,
    errorContainer = Red20,
    onErrorContainer = Red90,

    background = BlueGrey10,                 // #101C2B - Deep blue-dark background
    onBackground = BlueGrey90,               // Light text

    surface = SurfaceDark,                   // #1A1C1E - Dark cards
    onSurface = BlueGrey90,
    surfaceVariant = SurfaceVariantDark,     // #42474E
    onSurfaceVariant = BlueGrey80,

    outline = BlueGrey40,
    outlineVariant = BlueGrey30,
    scrim = BlueGrey10,
    inverseSurface = BlueGrey90,
    inverseOnSurface = BlueGrey20,
    inversePrimary = Blue40
)

// =============================================================
// MAIN THEME COMPOSABLE
// =============================================================
// This is the single entry point for all theming in the app.
// It wraps the entire app content and provides the theme.
//
// Parameters:
// - darkTheme: override system setting (used for manual toggle)
// - dynamicColor: use wallpaper colors on Android 12+ devices
// - content: the app's UI content (lambda)
// =============================================================
@Composable
fun MyUntisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // Follow system setting by default
    dynamicColor: Boolean = true,                 // Enable Material You on Android 12+
    content: @Composable () -> Unit
) {
    // Determine which color scheme to use
    val colorScheme = when {
        // Dynamic Color: only available on Android 12 (API 31) and above
        // Uses the user's wallpaper to generate a personalized color palette
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        // If no dynamic color or older Android: use our custom schemes
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // SideEffect: runs after every recomposition
    // Used here to update the STATUS BAR color to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {  // isInEditMode = true in Android Studio Preview
        SideEffect {
            val window = (view.context as Activity).window

            // Set status bar color to match the surface color
            // This makes the UI look edge-to-edge and modern
            window.statusBarColor = colorScheme.surface.toArgb()

            // Tell the system if the status bar icons should be
            // light or dark (based on theme)
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Apply the Material Theme to all content below
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyUntisTypography,
        content = content
    )
}

// =============================================================
// CONVENIENCE EXTENSIONS
// =============================================================
// These allow us to access our custom colors that are NOT
// part of the standard Material 3 color roles.
// Usage: MaterialTheme.colorScheme.success
// =============================================================

// We can't extend ColorScheme directly, so we create a
// separate object to hold our custom semantic colors.
object MyUntisColors {
    val success = Green40
    val onSuccess = SurfaceLight
    val successContainer = Green90
    val onSuccessContainer = Green10

    val warning = Orange40
    val onWarning = SurfaceLight
    val warningContainer = Orange90
    val onWarningContainer = Orange10

    // Grade-specific colors
    val gradeExcellent = GradeExcellent
    val gradeGood = GradeGood
    val gradeSatisfactory = GradeSatisfactory
    val gradeSufficient = GradeSufficient
    val gradeFailed = GradeFailed

    // Glass colors
    val glassWhite = GlassWhite
    val glassWhiteLight = GlassWhiteLight
    val glassDark = GlassDark
    val glassPrimary = GlassPrimary
}

// Helper function: Returns appropriate grade color based on grade value
// Grade 1 = best (green), Grade 5 = worst (red)
fun gradeColor(grade: Float): androidx.compose.ui.graphics.Color {
    return when {
        grade <= 1.5f -> GradeExcellent
        grade <= 2.5f -> GradeGood
        grade <= 3.5f -> GradeSatisfactory
        grade <= 4.5f -> GradeSufficient
        else -> GradeFailed
    }
}