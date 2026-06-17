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

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val innerNavController = rememberNavController()
    val navBackStackEntry  by innerNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Routes that live INSIDE the bottom nav (inner NavController)
    val innerRoutes = setOf(
        Screen.Dashboard.route,
        Screen.Timetable.route,
        Screen.Homework.route,
        Screen.Grades.route,
        Screen.Messages.route,
        Screen.Settings.route
    )

    // Smart navigate: bottom-nav routes → inner controller,
    //                 classreg / exams  → outer controller (Root NavGraph)
    val smartNavigate: (String) -> Unit = { route ->
        if (route in innerRoutes) {
            innerNavController.navigate(route) {
                popUpTo(innerNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState    = true
            }
        } else {
            // classreg, exams → handled by Root NavGraph
            onNavigate(route)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy
                        ?.any { it.route == item.screen.route } == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick  = { smartNavigate(item.screen.route) },
                        icon     = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon
                                else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label          = { Text(item.label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = innerNavController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn(tween(200)) },
            exitTransition   = { fadeOut(tween(200)) }
        ) {
            composable(Screen.Dashboard.route) {
                // Pass smartNavigate so Dashboard can reach ALL features:
                // – bottom-nav items go through inner controller (no crash)
                // – classreg / exams go through outer controller
                DashboardScreen(onNavigate = smartNavigate)
            }
            composable(Screen.Timetable.route) { TimetableScreen() }
            composable(Screen.Homework.route)  { HomeworkScreen() }
            composable(Screen.Grades.route)    { GradesScreen() }
            composable(Screen.Messages.route)  { MessagesScreen() }
            composable(Screen.Settings.route)  {
                SettingsScreen(onLogout = onLogout)
            }
        }
    }
}