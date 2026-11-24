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
import com.projectmediapro.app.template.TemplateBuilderScreen
import com.projectmediapro.app.template.TemplateBuilderViewModel
import com.projectmediapro.app.template.TemplatesEffect
import com.projectmediapro.app.template.TemplatesScreen
import com.projectmediapro.app.template.TemplatesViewModel
import com.projectmediapro.app.template.UseTemplateEffect
import com.projectmediapro.app.template.UseTemplateScreen
import com.projectmediapro.app.template.UseTemplateViewModel

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val PROJECT_ID_ARG = "projectId"
    const val EDITOR = "editor/{$PROJECT_ID_ARG}"
    const val TEMPLATE_ID_ARG = "templateId"
    const val TEMPLATE_BUILDER = "template/{$TEMPLATE_ID_ARG}"
    const val USE_TEMPLATE = "use_template/{$TEMPLATE_ID_ARG}"

    fun editorRoute(projectId: String): String = "editor/$projectId"
    fun templateBuilderRoute(templateId: String): String = "template/$templateId"
    fun useTemplateRoute(templateId: String): String = "use_template/$templateId"
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
                    mediaStorage = container.mediaStorage,
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
                onOpenTemplates = {
                    navController.navigate(AppRoutes.TEMPLATES) {
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

        composable(route = AppRoutes.TEMPLATES) {
            val viewModel: TemplatesViewModel = viewModel(
                factory = TemplatesViewModel.provideFactory(
                    templateRepository = container.templateRepository,
                    templateMediaStorage = container.templateMediaStorage,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is TemplatesEffect.OpenTemplate -> {
                            navController.navigate(AppRoutes.templateBuilderRoute(effect.templateId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            TemplatesScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onOpenTemplate = { templateId ->
                    navController.navigate(AppRoutes.templateBuilderRoute(templateId)) {
                        launchSingleTop = true
                    }
                },
                onUseTemplate = { templateId ->
                    navController.navigate(AppRoutes.useTemplateRoute(templateId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoutes.TEMPLATE_BUILDER,
            arguments = listOf(
                navArgument(AppRoutes.TEMPLATE_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString(AppRoutes.TEMPLATE_ID_ARG).orEmpty()
            val viewModel: TemplateBuilderViewModel = viewModel(
                factory = TemplateBuilderViewModel.provideFactory(
                    templateId = templateId,
                    repository = container.templateRepository,
                    mediaStorage = container.templateMediaStorage,
                    videoImporter = container.templateVideoImporter,
                    audioImporter = container.templateAudioImporter,
                    overlayImporter = container.templateOverlayImporter,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            TemplateBuilderScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoutes.USE_TEMPLATE,
            arguments = listOf(
                navArgument(AppRoutes.TEMPLATE_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString(AppRoutes.TEMPLATE_ID_ARG).orEmpty()
            val viewModel: UseTemplateViewModel = viewModel(
                factory = UseTemplateViewModel.provideFactory(
                    templateId = templateId,
                    templateRepository = container.templateRepository,
                    projectCreator = container.templateProjectCreator,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is UseTemplateEffect.OpenProject -> {
                            navController.navigate(AppRoutes.editorRoute(effect.projectId)) {
                                popUpTo(AppRoutes.TEMPLATES)
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            UseTemplateScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
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
                    repository = container.projectRepository,
                    videoImporter = container.videoImporter,
                    mediaStorage = container.mediaStorage,
                    audioImporter = container.audioImporter,
                    overlayImporter = container.overlayImporter,
                    videoExporter = container.videoExporter,
                    exportSaver = container.exportSaver,
                    cacheSpaceProvider = container.cacheSpaceProvider,
                    tempDirectory = container.exportTempDirectory,
                    captionRepository = container.captionRepository,
                    pcmAudioExtractor = container.pcmAudioExtractor,
                    transcriptionEngine = container.transcriptionEngine,
                    freezeFrameExtractor = container.freezeFrameExtractor,
                    videoAudioExtractor = container.videoAudioExtractor,
                    frameSnapshotSaver = container.frameSnapshotSaver,
                    projectTemplateSaver = container.projectTemplateSaver,
                    templateRepository = container.templateRepository,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            EditorScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
                onOpenProject = { nextProjectId ->
                    navController.navigate(AppRoutes.editorRoute(nextProjectId)) {
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
                    whisperModelManager = container.whisperModelManager,
                    storageInspector = container.storageInspector,
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
