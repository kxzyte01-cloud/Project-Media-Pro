package com.projectmediapro.app.navigation

import androidx.compose.runtime.Composable
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
import com.projectmediapro.app.home.HomeScreen
import com.projectmediapro.app.home.HomeViewModel
import com.projectmediapro.app.settings.SettingsScreen
import com.projectmediapro.app.settings.SettingsViewModel

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{projectId}"
    fun editor(projectId: String) = "editor/$projectId"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController, startDestination = AppRoutes.HOME, modifier = modifier) {
        composable(AppRoutes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(container.projectRepository),
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                uiState = state,
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true }
                },
                onCreateProject = { title ->
                    homeViewModel.createProject(title) { id ->
                        navController.navigate(AppRoutes.editor(id))
                    }
                },
                onOpenProject = { id -> navController.navigate(AppRoutes.editor(id)) },
                onDeleteProject = homeViewModel::deleteProject,
                onClearError = homeViewModel::clearError,
            )
        }
        composable(
            route = AppRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor-$projectId",
                factory = EditorViewModel.provideFactory(
                    projectId,
                    container.projectRepository,
                    container.videoImportDataSource,
                ),
            )
            val state by editorViewModel.uiState.collectAsStateWithLifecycle()
            EditorScreen(
                uiState = state,
                onImportVideo = editorViewModel::importVideo,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    themePreferencesStore = container.themePreferencesStore,
                    privacyPolicyDataSource = container.privacyPolicyDataSource,
                    appVersionName = container.appVersionName,
                ),
            )
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                uiState = state,
                onEvent = settingsViewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
