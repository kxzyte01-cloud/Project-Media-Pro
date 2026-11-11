package com.projectmediapro.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.projectmediapro.app.di.AppContainer
import com.projectmediapro.app.editor.EditorScreen
import com.projectmediapro.app.editor.EditorViewModel
import com.projectmediapro.app.home.HomeEffect
import com.projectmediapro.app.home.HomeScreen
import com.projectmediapro.app.home.HomeViewModel
import com.projectmediapro.app.settings.SettingsScreen
import com.projectmediapro.app.settings.SettingsViewModel

/**
 * Single source of truth for all navigation routes in the application. */
object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PROJECT_ID_ARG = "projectId"
    const val EDITOR = "editor/{$PROJECT_ID_ARG}"

    fun editorRoute(projectId: String): String = "editor/$projectId"
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
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(
                    projectRepository = container.projectRepository,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is HomeEffect.OpenProject -> {
                            navController.navigate(AppRoutes.editorRoute(effect.projectId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            HomeScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onOpenProject = { projectId ->
                    navController.navigate(AppRoutes.editorRoute(projectId)) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = AppRoutes.EDITOR,
            arguments = listOf(
                navArgument(AppRoutes.PROJECT_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString(AppRoutes.PROJECT_ID_ARG).orEmpty()
            val viewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.provideFactory(
                    projectId = projectId,
                    projectRepository = container.projectRepository,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            EditorScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
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
