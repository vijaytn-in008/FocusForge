package com.focusforge.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.focusforge.app.ui.navigation.BottomNavBar
import com.focusforge.app.ui.navigation.NavGraph
import com.focusforge.app.ui.navigation.Screen
import com.focusforge.app.ui.theme.FocusForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_NAV_DESTINATION = "extra_nav_destination"
    }

    private val pendingNavDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNavDestination.value = intent?.getStringExtra(EXTRA_NAV_DESTINATION)

        setContent {
            FocusForgeTheme {
                RequestNotificationPermissionOnAndroid13Plus()

                val navController = rememberNavController()
                val targetRoute = pendingNavDestination.value

                LaunchedEffect(targetRoute) {
                    if (!targetRoute.isNullOrBlank()) {
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        pendingNavDestination.value = null
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavBar(navController = navController)
                    }
                ) { innerPadding ->
                    NavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val destination = intent.getStringExtra(EXTRA_NAV_DESTINATION)
        if (!destination.isNullOrBlank()) {
            pendingNavDestination.value = destination
        }
    }
}

/**
 * Handles the runtime notification permission required starting on Android 13 (Tiramisu / API 33).
 * This ensures the persistent timer countdown in the notification drawer can be presented to the user.
 */
@Composable
private fun ComponentActivity.RequestNotificationPermissionOnAndroid13Plus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ ->
            // Permission result handled gracefully; service works regardless
        }

        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this@RequestNotificationPermissionOnAndroid13Plus,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
