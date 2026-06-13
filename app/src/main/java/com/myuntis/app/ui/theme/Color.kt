package com.myuntis.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================
// MYUNTIS COLOR PALETTE
// =============================================================
// Naming convention: ColorNameShade
// Shade 10 = very dark, Shade 99 = very light
// Based on Material 3 tonal palette system
// =============================================================

// ----- PRIMARY: Deep Blue -----
// Used for: Main buttons, FAB, active navigation items
val Blue10 = Color(0xFF001945)   // Very dark blue (for text on light bg)
val Blue20 = Color(0xFF002E6E)   // Dark blue
val Blue30 = Color(0xFF1A4B9E)   // Medium-dark blue
val Blue40 = Color(0xFF1565C0)   // PRIMARY - Main brand color
val Blue80 = Color(0xFFAFC6FF)   // Light blue (dark mode primary)
val Blue90 = Color(0xFFDBE4FF)   // Very light blue (dark mode container)

// ----- SECONDARY: Indigo -----
// Used for: Secondary buttons, tags, chips
val Indigo10 = Color(0xFF0D1259)
val Indigo20 = Color(0xFF222888)
val Indigo30 = Color(0xFF3A3FA0)
val Indigo40 = Color(0xFF5C6BC0)  // SECONDARY
val Indigo80 = Color(0xFFBBC3FF)
val Indigo90 = Color(0xFFDEE0FF)

// ----- TERTIARY: Cyan -----
// Used for: Accent elements, highlights, special badges
val Cyan10 = Color(0xFF001F26)
val Cyan20 = Color(0xFF003641)
val Cyan30 = Color(0xFF004E5F)
val Cyan40 = Color(0xFF00ACC1)   // TERTIARY - Accent color
val Cyan80 = Color(0xFF5DD5EC)
val Cyan90 = Color(0xFFB3EEFF)

// ----- ERROR: Red -----
// Used for: Error states, missing homework, unexcused absences
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red40 = Color(0xFFBA1A1A)    // ERROR
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)

// ----- SUCCESS: Green -----
// Used for: Completed homework, excused absences, good grades
val Green10 = Color(0xFF002204)
val Green20 = Color(0xFF003909)
val Green40 = Color(0xFF2E7D32)  // SUCCESS (not standard M3 but we need it)
val Green80 = Color(0xFF92D196)
val Green90 = Color(0xFFCCEDCC)

// ----- WARNING: Orange -----
// Used for: Pending items, warnings, upcoming deadlines
val Orange10 = Color(0xFF2D1500)
val Orange20 = Color(0xFF4B2400)
val Orange40 = Color(0xFFE65100)  // WARNING
val Orange80 = Color(0xFFFFB77C)
val Orange90 = Color(0xFFFFDCBE)

// ----- NEUTRAL: Blue-Grey -----
// Used for: Backgrounds, surfaces, dividers
val BlueGrey10 = Color(0xFF101C2B)
val BlueGrey20 = Color(0xFF1E2F41)
val BlueGrey30 = Color(0xFF2D4356)
val BlueGrey40 = Color(0xFF3D566B)
val BlueGrey80 = Color(0xFFB8CAD9)
val BlueGrey90 = Color(0xFFD4E4F4)
val BlueGrey95 = Color(0xFFEAF1F8)
val BlueGrey99 = Color(0xFFF8F9FF)  // App background (Light)

// ----- SURFACE COLORS -----
// Used for: Cards, dialogs, bottom sheets
val SurfaceLight = Color(0xFFFFFFFF)         // Pure white cards
val SurfaceDark = Color(0xFF1A1C1E)          // Dark mode surface
val SurfaceVariantLight = Color(0xFFE3E8F0)  // Slightly tinted surface
val SurfaceVariantDark = Color(0xFF42474E)   // Dark mode surface variant

// ----- GLASSMORPHISM COLORS -----
// Semi-transparent colors for glass effects
// The alpha value (first two hex digits after 0x) controls transparency
// 80 = 50% transparent, 40 = 25% transparent, 1A = ~10% transparent
val GlassWhite = Color(0x80FFFFFF)           // 50% white
val GlassWhiteLight = Color(0x40FFFFFF)      // 25% white
val GlassDark = Color(0x801A1C2E)            // 50% dark
val GlassPrimary = Color(0x401565C0)         // 25% primary blue
val GlassBlur = Color(0x1AFFFFFF)            // 10% white (subtle)

// ----- GRADE COLORS -----
// Specific colors for grade display
val GradeExcellent = Color(0xFF2E7D32)    // 1 = Sehr gut  (Green)
val GradeGood = Color(0xFF558B2F)         // 2 = Gut        (Light Green)
val GradeSatisfactory = Color(0xFFF57F17) // 3 = Befriedigend (Amber)
val GradeSufficient = Color(0xFFE65100)   // 4 = Genügend   (Orange)
val GradeFailed = Color(0xFFB71C1C)       // 5 = Nicht genügend (Red)

// ----- TIMETABLE SUBJECT COLORS -----
// Each subject gets a distinct color for visual differentiation
val SubjectMath = Color(0xFF1565C0)        // Blue
val SubjectGerman = Color(0xFF6A1B9A)      // Purple
val SubjectEnglish = Color(0xFF00695C)     // Teal
val SubjectHistory = Color(0xFF4E342E)     // Brown
val SubjectScience = Color(0xFF2E7D32)     // Green
val SubjectArt = Color(0xFFAD1457)         // Pink
val SubjectPE = Color(0xFFE65100)          // Orange
val SubjectMusic = Color(0xFF0277BD)       // Light Blue
val SubjectGeography = Color(0xFF558B2F)   // Light Green
val SubjectDefault = Color(0xFF37474F)     // Blue Grey