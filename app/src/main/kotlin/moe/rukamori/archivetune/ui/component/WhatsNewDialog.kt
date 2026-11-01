/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.WhatsNewCategory
import moe.rukamori.archivetune.viewmodels.WhatsNewContent
import moe.rukamori.archivetune.viewmodels.WhatsNewState
import androidx.compose.ui.window.Dialog as PlatformDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewDialog(
    state: WhatsNewState.Showing,
    disableAnimations: Boolean,
    onDismiss: () -> Unit,
    onReleaseNotes: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogTitle = stringResource(R.string.whats_new_title, state.version.removePrefix("v"))
    val entryColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

    PlatformDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !state.isDismissing,
            dismissOnClickOutside = !state.isDismissing,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .semantics { paneTitle = dialogTitle },
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                key(state.version) {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        item(key = "release_header", contentType = "release_header") {
                            WhatsNewReleaseHeader(version = state.version)
                        }
                        when (val content = state.content) {
                            is WhatsNewContent.Success -> content.sections.forEach { section ->
                                item(key = section.category.name, contentType = "category_header") {
                                    val titleRes = when (section.category) {
                                        WhatsNewCategory.FEATURES -> R.string.whats_new_features
                                        WhatsNewCategory.FIXES -> R.string.whats_new_fixes
                                        WhatsNewCategory.IMPROVEMENTS -> R.string.whats_new_improvements
                                    }
                                    val iconRes = when (section.category) {
                                        WhatsNewCategory.FEATURES -> R.drawable.auto_awesome
                                        WhatsNewCategory.FIXES -> R.drawable.check
                                        WhatsNewCategory.IMPROVEMENTS -> R.drawable.tune
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp)
                                            .semantics(mergeDescendants = true) { heading() },
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            painter = painterResource(iconRes),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Text(
                                            text = stringResource(titleRes),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = section.entries,
                                    key = { _, entry -> "${section.category.name}:$entry" },
                                    contentType = { _, _ -> "release_change" },
                                ) { index, entry ->
                                    SegmentedListItem(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        shapes = ListItemDefaults.segmentedShapes(index = index, count = section.entries.size),
                                        colors = entryColors,
                                        content = { Text(entry, style = MaterialTheme.typography.bodyLarge) },
                                    )
                                }
                            }
                            WhatsNewContent.Loading -> item(key = "loading", contentType = "status") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                        .semantics(mergeDescendants = true) {
                                            liveRegion = LiveRegionMode.Polite
                                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                        },
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (!disableAnimations) LoadingIndicator(modifier = Modifier.clearAndSetSemantics {})
                                    Text(
                                        text = stringResource(R.string.whats_new_loading),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            WhatsNewContent.Empty -> item(key = "empty", contentType = "status") {
                                WhatsNewNotice(
                                    title = stringResource(R.string.whats_new_no_highlights_title),
                                    message = stringResource(R.string.whats_new_no_highlights),
                                )
                            }
                            is WhatsNewContent.Error -> item(key = "error", contentType = "status") {
                                val loadFailed = content.reason == WhatsNewContent.Reason.LOAD_FAILED
                                Column {
                                    WhatsNewNotice(
                                        title = stringResource(if (loadFailed) R.string.whats_new_load_failed_title else R.string.release_notes),
                                        message = stringResource(if (loadFailed) R.string.whats_new_load_failed_message else R.string.whats_new_not_published),
                                    )
                                    if (loadFailed) {
                                        TextButton(
                                            onClick = onRetry,
                                            enabled = !state.isDismissing,
                                            modifier = Modifier.padding(horizontal = 24.dp),
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(stringResource(R.string.retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.dismissalFailed) {
                        Text(
                            text = stringResource(R.string.whats_new_acknowledgment_failed, stringResource(R.string.got_it)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onReleaseNotes,
                            enabled = !state.isDismissing,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.release_notes))
                        }
                        Button(
                            onClick = onDismiss,
                            enabled = !state.isDismissing,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(if (state.isDismissing) R.string.whats_new_saving else R.string.got_it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsNewReleaseHeader(version: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.new_release),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = stringResource(R.string.whats_new_heading),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.whats_new_version, version.removePrefix("v")),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun WhatsNewNotice(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
