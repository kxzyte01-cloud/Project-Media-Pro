package com.projectmediapro.app.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.editor.TimelineMediaCache
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.videoClips
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultTitle = stringResource(
        R.string.home_default_project_title,
        uiState.projects.size + 1,
    )
    val visibleProjects = remember(uiState.projects, uiState.searchQuery, uiState.sortOrder) {
        uiState.visibleProjects
    }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onEvent(HomeEvent.ShowCreateDialog(defaultTitle)) },
                    shape = MediaProShapes.Small,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    Text(text = stringResource(R.string.home_new_project))
                }
                OutlinedButton(
                    onClick = onOpenTemplates,
                    shape = MediaProShapes.Small,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    Text(text = stringResource(R.string.templates_title))
                }
            }
            if (uiState.projects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { onEvent(HomeEvent.UpdateSearchQuery(it)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    placeholder = { Text(text = stringResource(R.string.home_search_hint)) },
                    trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                        {
                            TextButton(onClick = { onEvent(HomeEvent.UpdateSearchQuery("")) }) {
                                Text(text = stringResource(R.string.home_search_clear))
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = uiState.sortOrder == HomeSortOrder.RECENT,
                        onClick = { onEvent(HomeEvent.SelectSortOrder(HomeSortOrder.RECENT)) },
                        label = { Text(text = stringResource(R.string.home_sort_recent)) },
                    )
                    FilterChip(
                        selected = uiState.sortOrder == HomeSortOrder.TITLE_ASC,
                        onClick = { onEvent(HomeEvent.SelectSortOrder(HomeSortOrder.TITLE_ASC)) },
                        label = { Text(text = stringResource(R.string.home_sort_title)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            if (uiState.projects.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visibleProjects.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val copySuffix = stringResource(R.string.home_copy_suffix)
                val isAnyDuplicating = uiState.duplicatingProjectId != null
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    contentPadding = PaddingValues(bottom = MediaProSpacing.Large),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(
                        items = visibleProjects,
                        key = { it.id },
                    ) { project ->
                        ProjectRowCard(
                            project = project,
                            thumbnailTarget = uiState.thumbnailTargets[project.id],
                            isDuplicating = uiState.duplicatingProjectId == project.id,
                            duplicateEnabled = !isAnyDuplicating,
                            onClick = { onOpenProject(project.id) },
                            onRenameClick = { onEvent(HomeEvent.RequestRename(project.id)) },
                            onDuplicateClick = {
                                onEvent(HomeEvent.DuplicateProject(project.id, copySuffix))
                            },
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

    if (uiState.pendingRenameId != null) {
        RenameProjectDialog(
            draftTitle = uiState.renameDraftTitle,
            onTitleChange = { onEvent(HomeEvent.UpdateRenameTitle(it)) },
            onConfirm = { onEvent(HomeEvent.ConfirmRename) },
            onDismiss = { onEvent(HomeEvent.DismissRenameDialog) },
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
    thumbnailTarget: HomeThumbnailTarget?,
    isDuplicating: Boolean,
    duplicateEnabled: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val formattedDate = remember(project.updatedAt) {
        DateFormat
            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(project.updatedAt))
    }

    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MediaProSpacing.Medium,
                    top = MediaProSpacing.Small,
                    end = MediaProSpacing.Small,
                    bottom = MediaProSpacing.ExtraSmall,
                ),
        ) {
            val clipCount = remember(project) { project.videoClips().size }
            val durationText = remember(project.durationMs) { HomeMetadataFormatter.formatDuration(project.durationMs) }
            val aspectText = remember(project.canvasAspect) { HomeMetadataFormatter.formatAspect(project.canvasAspect) }
            val clipCountText = when (clipCount) {
                0 -> stringResource(R.string.home_project_clip_count_zero)
                1 -> stringResource(R.string.home_project_clip_count_one)
                else -> stringResource(R.string.home_project_clip_count_many, clipCount)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProjectThumbnailBox(thumbnailTarget = thumbnailTarget)
                Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectMetaPill(text = aspectText)
                        ProjectMetaPill(text = durationText)
                        ProjectMetaPill(text = clipCountText)
                    }
                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                    Text(
                        text = stringResource(R.string.home_project_edited, formattedDate),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onRenameClick,
                    enabled = !isDuplicating,
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rename),
                        contentDescription = stringResource(R.string.cd_rename_project),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDuplicateClick,
                    enabled = duplicateEnabled,
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    if (isDuplicating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_duplicate),
                            contentDescription = stringResource(R.string.cd_duplicate_project),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDeleteClick,
                    enabled = !isDuplicating,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
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
private fun RenameProjectDialog(
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSave = draftTitle.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.home_rename_title)) },
        text = {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = onTitleChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSave) onConfirm()
                    },
                ),
                label = { Text(text = stringResource(R.string.home_project_title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canSave,
            ) {
                Text(text = stringResource(R.string.home_rename_confirm))
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
        text = { Text(text = stringResource(R.string.home_delete_message, projectTitle)) },
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

@Composable
private fun ProjectMetaPill(text: String) {
    Surface(
        shape = MediaProShapes.Small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProjectThumbnailBox(
    thumbnailTarget: HomeThumbnailTarget?,
) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = thumbnailTarget) {
        value = if (thumbnailTarget == null) {
            null
        } else {
            TimelineMediaCache.loadVideoFilmstrip(
                filePath = thumbnailTarget.filePath,
                isStillImage = thumbnailTarget.isStillImage,
                trimStartMs = thumbnailTarget.trimStartMs,
                trimEndMs = thumbnailTarget.trimEndMs,
                frameCount = 1,
            ).firstOrNull()?.asImageBitmap()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(MediaProShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_project_placeholder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
