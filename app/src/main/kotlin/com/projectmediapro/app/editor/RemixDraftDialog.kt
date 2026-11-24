package com.projectmediapro.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.RemixPreset

private data class RemixCardSpec(
    val preset: RemixPreset,
    val titleRes: Int,
    val descRes: Int,
    val accentColor: Color,
)

@Composable
fun RemixDraftDialog(
    onSelectPreset: (RemixPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        RemixCardSpec(
            preset = RemixPreset.STORY_CUT,
            titleRes = R.string.editor_remix_story_title,
            descRes = R.string.editor_remix_story_desc,
            accentColor = Color(0xFF2C7A7B),
        ),
        RemixCardSpec(
            preset = RemixPreset.RHYTHM_CUT,
            titleRes = R.string.editor_remix_rhythm_title,
            descRes = R.string.editor_remix_rhythm_desc,
            accentColor = Color(0xFF3B82F6),
        ),
        RemixCardSpec(
            preset = RemixPreset.MINIMAL_CUT,
            titleRes = R.string.editor_remix_minimal_title,
            descRes = R.string.editor_remix_minimal_desc,
            accentColor = Color(0xFF94A3B8),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.editor_remix_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.editor_remix_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                options.forEach { option ->
                    Surface(
                        shape = MediaProShapes.Small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
                            .clickable { onSelectPreset(option.preset) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(option.accentColor),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(MediaProSpacing.Small),
                            ) {
                                Text(
                                    text = stringResource(option.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(option.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}
