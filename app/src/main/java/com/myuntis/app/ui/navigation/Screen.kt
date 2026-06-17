package com.myuntis.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Login     : Screen("login")
    object Main      : Screen("main")
    object Dashboard : Screen("dashboard")
    object Timetable : Screen("timetable")
    object Homework  : Screen("homework")
    object Grades    : Screen("grades")
    object Absences  : Screen("absences")
    object Messages  : Screen("messages")
    object Settings  : Screen("settings")
    object ClassReg : Screen("classreg")
    object Exams    : Screen("exams")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// Settings replaces Messages (Messages not yet implemented)
val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Dashboard,
        label  = "Start",
        selectedIcon   = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        screen = Screen.Timetable,
        label  = "Stundenplan",
        selectedIcon   = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    BottomNavItem(
        screen = Screen.Homework,
        label  = "Aufgaben",
        selectedIcon   = Icons.Filled.Assignment,
        unselectedIcon = Icons.Outlined.Assignment
    ),
    BottomNavItem(
        screen = Screen.Grades,
        label  = "Noten",
        selectedIcon   = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star
    ),
    BottomNavItem(
        screen = Screen.Settings,
        label  = "Einstellungen",
        selectedIcon   = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)