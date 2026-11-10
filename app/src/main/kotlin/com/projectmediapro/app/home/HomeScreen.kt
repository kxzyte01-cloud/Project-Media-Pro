package com.projectmediapro.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onCreateProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

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
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            Spacer(Modifier.height(MediaProSpacing.Medium))
            Text(
                text = stringResource(R.string.home_projects_heading),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(MediaProSpacing.Small))
            Button(
                onClick = { showCreateDialog = true },
                enabled = !uiState.isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_new_project))
            }
            Spacer(Modifier.height(MediaProSpacing.Medium))

            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.projects.isEmpty() -> Text(
                    text = stringResource(R.string.home_projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.projects, key = { it.id }) { project ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onOpenProject(project.id)
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        project.title,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.home_project_clips_count,
                                            project.tracks.sumOf { it.clips.size },
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { projectToDelete = project.id }) {
                                    Text(stringResource(R.string.home_delete_project))
                                }
                            }
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            HomeError.LOAD_FAILED -> R.string.home_load_failed
                            HomeError.CREATE_FAILED -> R.string.home_create_failed
                            HomeError.DELETE_FAILED -> R.string.home_delete_failed
                            HomeError.INVALID_TITLE -> R.string.home_invalid_title
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onClearError) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; projectTitle = "" },
            title = { Text(stringResource(R.string.home_new_project)) },
            text = {
                OutlinedTextField(
                    value = projectTitle,
                    onValueChange = { projectTitle = it.take(80) },
                    label = { Text(stringResource(R.string.home_project_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = projectTitle.isNotBlank() && !uiState.isCreating,
                    onClick = {
                        onCreateProject(projectTitle.trim())
                        showCreateDialog = false
                        projectTitle = ""
                    },
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; projectTitle = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    projectToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(R.string.home_delete_confirm_title)) },
            text = { Text(stringResource(R.string.home_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(id)
                        projectToDelete = null
                    },
                ) { Text(stringResource(R.string.home_delete_project)) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
