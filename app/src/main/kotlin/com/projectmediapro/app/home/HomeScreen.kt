package com.projectmediapro.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Project
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultTitle = stringResource(
        R.string.home_default_project_title,
        uiState.projects.size + 1,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = stringResource(R.string.app_name),
                actionIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.cd_open_settings),
                    )
                },
                onActionClick = onOpenSettings,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
            Text(
                text = stringResource(R.string.home_projects_heading),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = stringResource(errorStringRes(uiState.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Button(
                onClick = { onEvent(HomeEvent.ShowCreateDialog(defaultTitle)) },
                shape = MediaProShapes.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
            ) {
                Text(text = stringResource(R.string.home_new_project))
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
            if (uiState.projects.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(
                        items = uiState.projects,
                        key = { it.id },
                    ) { project ->
                        ProjectRowCard(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            onDeleteClick = { onEvent(HomeEvent.RequestDelete(project.id)) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCreateDialogVisible) {
        CreateProjectDialog(
            draftTitle = uiState.draftTitle,
            onTitleChange = { onEvent(HomeEvent.UpdateDraftTitle(it)) },
            onConfirm = { onEvent(HomeEvent.ConfirmCreate(defaultTitle)) },
            onDismiss = { onEvent(HomeEvent.DismissCreateDialog) },
        )
    }

    val pendingDeleteProject = uiState.projects.firstOrNull { it.id == uiState.pendingDeleteId }
    if (pendingDeleteProject != null) {
        DeleteProjectDialog(
            projectTitle = pendingDeleteProject.title,
            onConfirm = { onEvent(HomeEvent.ConfirmDelete) },
            onDismiss = { onEvent(HomeEvent.CancelDelete) },
        )
    }
}

@Composable
private fun ProjectRowCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val formattedDate = DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(project.updatedAt))

    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MediaProSpacing.Medium,
                    top = MediaProSpacing.Small,
                    bottom = MediaProSpacing.Small,
                    end = MediaProSpacing.ExtraSmall,
                ),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = stringResource(R.string.home_project_edited, formattedDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.cd_delete_project),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.home_new_project)) },
        text = {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = onTitleChange,
                singleLine = true,
                label = { Text(text = stringResource(R.string.home_project_title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.home_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}

@Composable
private fun DeleteProjectDialog(
    projectTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.home_delete_title)) },
        text = {
            Text(text = stringResource(R.string.home_delete_message, projectTitle))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.home_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}

private fun errorStringRes(error: HomeError): Int = when (error) {
    HomeError.LOAD_FAILED -> R.string.error_load_failed
    HomeError.SAVE_FAILED -> R.string.error_save_failed
    HomeError.DELETE_FAILED -> R.string.error_delete_failed
}
