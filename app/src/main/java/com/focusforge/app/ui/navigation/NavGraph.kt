package com.focusforge.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.focusforge.app.ui.blocker.BlockedAppsScreen
import com.focusforge.app.ui.blocker.BlockedAppsViewModel
import com.focusforge.app.ui.history.HistoryScreen
import com.focusforge.app.ui.home.HomeScreen
import com.focusforge.app.ui.permissions.PermissionsScreen
import com.focusforge.app.ui.protection.PrivateListScreen
import com.focusforge.app.ui.protection.PrivateListViewModel
import com.focusforge.app.ui.protection.ProtectionScreen
import com.focusforge.app.ui.protection.ProtectionViewModel
import com.focusforge.app.ui.protection.WhyBlockedScreen
import com.focusforge.app.ui.protection.WhyBlockedViewModel
import com.focusforge.app.ui.timer.TimerScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToTimer = {
                    navController.navigate(Screen.Timer.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToShield = {
                    navController.navigate(Screen.Shield.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToProtection = {
                    navController.navigate(Screen.Protection.route)
                }
            )
        }

        composable(Screen.Timer.route) {
            TimerScreen()
        }

        composable(Screen.Shield.route) {
            val blockedAppsViewModel: BlockedAppsViewModel = hiltViewModel()
            BlockedAppsScreen(
                onNavigateToPermissions = {
                    navController.navigate(Screen.Permissions.route)
                },
                onNavigateToProtection = {
                    navController.navigate(Screen.Protection.route)
                },
                viewModel = blockedAppsViewModel
            )
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }

        composable(
            route = Screen.Permissions.route,
            enterTransition = { slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)) }
        ) {
            PermissionsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Protection.route,
            enterTransition = { slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)) }
        ) {
            val protectionViewModel: ProtectionViewModel = hiltViewModel()
            ProtectionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivateList = { navController.navigate(Screen.PrivateList.route) },
                onNavigateToWhyBlocked = { navController.navigate(Screen.WhyBlocked.route) },
                viewModel = protectionViewModel
            )
        }

        composable(
            route = Screen.PrivateList.route,
            enterTransition = { slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)) }
        ) {
            val privateListViewModel: PrivateListViewModel = hiltViewModel()
            PrivateListScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = privateListViewModel
            )
        }

        composable(
            route = Screen.WhyBlocked.route,
            enterTransition = { slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)) }
        ) {
            val whyBlockedViewModel: WhyBlockedViewModel = hiltViewModel()
            WhyBlockedScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = whyBlockedViewModel
            )
        }
    }
}
