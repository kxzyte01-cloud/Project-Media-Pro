package com.projectmediapro.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.projectmediapro.feature.captions.WhisperModelState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.component.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                },
                onNavigationClick = onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            SectionHeader(title = stringResource(R.string.settings_section_appearance))
            AppearanceSelector(
                selected = uiState.themePreference,
                onSelect = { onEvent(SettingsEvent.SelectTheme(it)) },
            )

            SectionHeader(title = stringResource(R.string.settings_section_whisper))
            WhisperModelCard(
                state = uiState.whisperModelState,
                onDownload = { onEvent(SettingsEvent.DownloadWhisperModel) },
                onCancel = { onEvent(SettingsEvent.CancelWhisperDownload) },
                onDelete = { onEvent(SettingsEvent.DeleteWhisperModel) },
            )

            SectionHeader(title = stringResource(R.string.settings_section_privacy))
            Surface(
                shape = MediaProShapes.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = uiState.privacyPolicyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MediaProSpacing.Medium),
                )
            }

            SectionHeader(title = stringResource(R.string.settings_section_about))
            AboutRow(
                label = stringResource(R.string.settings_about_app_name_label),
                value = stringResource(R.string.app_name),
            )
            AboutRow(
                label = stringResource(R.string.settings_about_version_label),
                value = uiState.appVersionName,
            )
            AboutRow(
                label = stringResource(R.string.settings_about_license_label),
                value = stringResource(R.string.settings_about_license_value),
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
        }
    }
}

@Composable
private fun AppearanceSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    val options = listOf(
        ThemePreference.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemePreference.LIGHT to stringResource(R.string.settings_theme_light),
        ThemePreference.DARK to stringResource(R.string.settings_theme_dark),
    )

    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (preference, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
                    .selectable(
                        selected = (preference == selected),
                        onClick = { onSelect(preference) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = MediaProSpacing.Small),
            ) {
                RadioButton(
                    selected = (preference == selected),
                    onClick = null,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = MediaProSpacing.Medium),
                )
            }
        }
    }
}

@Composable
private fun WhisperModelCard(
    state: WhisperModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(MediaProSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.settings_whisper_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_whisper_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                WhisperModelState.NotInstalled -> {
                    Button(
                        onClick = onDownload,
                        shape = MediaProShapes.Small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.settings_whisper_download))
                    }
                }
                is WhisperModelState.Downloading -> {
                    Text(
                        text = stringResource(R.string.settings_whisper_downloading, state.percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = { (state.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        shape = MediaProShapes.Small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.settings_whisper_cancel))
                    }
                }
                is WhisperModelState.Installed -> {
                    val sizeMb = ((state.sizeBytes / (1024L * 1024L)).coerceAtLeast(1L)).toInt()
                    Text(
                        text = stringResource(R.string.settings_whisper_installed, sizeMb),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = onDelete,
                        shape = MediaProShapes.Small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_whisper_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                WhisperModelState.Error -> {
                    Text(
                        text = stringResource(R.string.settings_whisper_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onDownload,
                        shape = MediaProShapes.Small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.settings_whisper_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
            .padding(vertical = MediaProSpacing.Small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
