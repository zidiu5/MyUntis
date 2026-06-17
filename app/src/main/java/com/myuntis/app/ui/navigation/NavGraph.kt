package com.myuntis.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myuntis.app.ui.screens.login.LoginScreen
import com.myuntis.app.ui.screens.main.MainScreen
import com.myuntis.app.ui.screens.classreg.ClassRegScreen
import com.myuntis.app.ui.screens.exams.ExamsScreen

// =============================================================
// APP NAV GRAPH
// =============================================================
// The root navigation graph of the entire app.
// Handles only the top-level flow: Login ↔ Main.
// The bottom navigation within Main is in MainScreen.kt.
//
// Navigation flow:
//   Login ──(success)──▶ Main
//   Main  ──(logout) ──▶ Login
// =============================================================
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        // Always start at Login; LoginViewModel auto-navigates
        // to Main if the user is already logged in
        startDestination = Screen.Login.route,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        }
    ) {
        // ---- LOGIN ----
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        // Remove Login from backstack: Back button exits app
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ---- MAIN (with bottom navigation) ----
        // ---- MAIN (with bottom navigation) ----
        composable(Screen.Main.route) {
            MainScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigate = { route ->          // NEU: für ClassReg, Exams etc.
                    navController.navigate(route)
                }
            )
        }

        composable(Screen.ClassReg.route) {
            ClassRegScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Exams.route) {
            ExamsScreen(onBack = { navController.popBackStack() })
        }
    }
}