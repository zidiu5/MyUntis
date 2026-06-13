package com.myuntis.app.ui.screens.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myuntis.app.ui.navigation.Screen
import com.myuntis.app.ui.navigation.bottomNavItems
import com.myuntis.app.ui.screens.dashboard.DashboardScreen
import com.myuntis.app.ui.screens.grades.GradesScreen
import com.myuntis.app.ui.screens.homework.HomeworkScreen
import com.myuntis.app.ui.screens.messages.MessagesScreen
import com.myuntis.app.ui.screens.settings.SettingsScreen
import com.myuntis.app.ui.screens.timetable.TimetableScreen

// =============================================================
// MAIN SCREEN
// =============================================================
// Container for the main app experience after login.
// Contains a bottom NavigationBar and an inner NavHost.
//
// Two navigation controllers exist in our app:
// 1. Outer NavController (in NavGraph): Login ↔ Main
// 2. Inner NavController (here): Dashboard ↔ Timetable ↔ etc.
// =============================================================
@Composable
fun MainScreen(
    onLogout: () -> Unit           // Called when user logs out
) {
    // Inner NavController: only for bottom nav destinations
    val innerNavController = rememberNavController()

    // Observe the current destination to highlight the active tab
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            // =================================================
            // MATERIAL 3 NAVIGATION BAR
            // =================================================
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy
                        ?.any { it.route == item.screen.route } == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            innerNavController.navigate(item.screen.route) {
                                // Pop up to start destination: avoids backstack buildup
                                // when tapping bottom nav items multiple times
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Don't create multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when re-selecting a tab
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon
                                else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        alwaysShowLabel = true  // Show labels even when unselected
                    )
                }
            }
        }
    ) { innerPadding ->
        // =================================================
        // INNER NAV HOST
        // =================================================
        // This NavHost lives inside the Scaffold's content area,
        // automatically padded to avoid overlap with the bottom bar.
        NavHost(
            navController = innerNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            // Smooth fade transition between tabs
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition  = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToSettings = {
                        innerNavController.navigate(Screen.Settings.route)
                    }
                )
            }
            composable(Screen.Timetable.route) { TimetableScreen() }
            composable(Screen.Homework.route)  { HomeworkScreen() }
            composable(Screen.Grades.route)    { GradesScreen() }
            composable(Screen.Messages.route)  { MessagesScreen() }
            composable(Screen.Settings.route)  { SettingsScreen(onLogout = onLogout) }
        }
    }
}