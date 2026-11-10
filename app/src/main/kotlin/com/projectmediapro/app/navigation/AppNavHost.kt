package com.projectmediapro.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.projectmediapro.app.di.AppContainer
import com.projectmediapro.app.home.HomeScreen
import com.projectmediapro.app.home.HomeViewModel
import com.projectmediapro.app.settings.SettingsScreen
import com.projectmediapro.app.settings.SettingsViewModel

/**
 * Single source of truth for all navigation routes in the application.
 * Adding a new screen requires adding one route constant and one `composable(...)` registration block below.
 */
object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
    ) {
        composable(route = AppRoutes.HOME) {
            val viewModel: HomeViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                uiState = uiState,
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = AppRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    themePreferencesStore = container.themePreferencesStore,
                    privacyPolicyDataSource = container.privacyPolicyDataSource,
                    appVersionName = container.appVersionName,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
