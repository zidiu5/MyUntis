package com.myuntis.app.ui.screens.main

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myuntis.app.data.notifications.NavRequests
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
    val mainViewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current

    // App comes back from background → renew session if it is older than 5 min
    DisposableEffect(context) {
        val activity = context.findComponentActivity()
        if (activity == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mainViewModel.onAppResumed()
                    Lifecycle.Event.ON_STOP  -> mainViewModel.onAppStopped()
                    else -> {}
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }

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

    // A tapped notification asks to open a screen
    val requestedRoute by NavRequests.route.collectAsState()
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let {
            smartNavigate(it)
            NavRequests.consume()
        }
    }

    // One shared size for all labels: shrinks until the longest one fits on one line
    var navLabelSize by remember { mutableFloatStateOf(12f) }

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
                        label = {
                            Text(
                                text = item.label,
                                fontSize = navLabelSize.sp,
                                maxLines = 1,
                                softWrap = false,
                                onTextLayout = { r ->
                                    if (r.didOverflowWidth && navLabelSize > 8f) navLabelSize -= 0.5f
                                }
                            )
                        },
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

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper    -> baseContext.findComponentActivity()
    else                 -> null
}