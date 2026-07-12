/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import kotlin.math.roundToInt

@Immutable
data class DownloadProgressToolbarState(
    val progress: Float,
    val paused: Boolean,
    val canPause: Boolean,
)

@Composable
fun DownloadProgressFloatingToolbar(
    state: DownloadProgressToolbarState,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        remember(state.progress) {
            state.progress.coerceIn(0f, 1f)
        }
    val percent =
        remember(progress) {
            (progress * 100f).roundToInt().coerceIn(0, 100)
        }
    val colorScheme = MaterialTheme.colorScheme

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.widthIn(max = 320.dp),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onCancel,
                containerColor = colorScheme.errorContainer,
                contentColor = colorScheme.onErrorContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.outlineVariant,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = stringResource(R.string.download_progress_percent, percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            DownloadToolbarAction(
                icon = if (state.paused) R.drawable.play else R.drawable.pause,
                contentDescription = stringResource(if (state.paused) R.string.play else R.string.widget_pause),
                enabled = state.canPause,
                onClick = onPauseResume,
            )
        }
    }
}

@Composable
private fun DownloadToolbarAction(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}
