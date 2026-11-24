package com.focusforge.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.focusforge.app.ui.history.HistoryScreen
import com.focusforge.app.ui.home.HomeScreen
import com.focusforge.app.ui.timer.TimerScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToTimer = {
                    navController.navigate(Screen.Timer.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(Screen.Timer.route) {
            TimerScreen()
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }
    }
}
