/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.WhatsNewCategory
import moe.rukamori.archivetune.updates.WhatsNewSection
import moe.rukamori.archivetune.viewmodels.WhatsNewContent
import moe.rukamori.archivetune.viewmodels.WhatsNewState
import androidx.compose.material3.ListItem as MaterialListItem

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
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.new_release),
                        contentDescription = null,
                        modifier = Modifier.size(AlertDialogDefaults.IconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        title = { Text(stringResource(R.string.whats_new_title, state.version.removePrefix("v"))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val content = state.content) {
                    WhatsNewContent.Loading -> Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!disableAnimations) LoadingIndicator(modifier = Modifier.clearAndSetSemantics {})
                        Text(stringResource(R.string.whats_new_loading))
                    }
                    is WhatsNewContent.Success -> WhatsNewReleaseContent(
                        version = state.version,
                        sections = content.sections,
                        modifier = Modifier.weight(1f),
                    )
                    WhatsNewContent.Empty -> Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MaterialListItem(
                            colors = listItemColors,
                            leadingContent = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                            content = { Text(stringResource(R.string.whats_new_no_highlights_title)) },
                            supportingContent = { Text(stringResource(R.string.whats_new_no_highlights)) },
                        )
                    }
                    is WhatsNewContent.Error -> Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val loadFailed = content.reason == WhatsNewContent.Reason.LOAD_FAILED
                        MaterialListItem(
                            colors = listItemColors,
                            leadingContent = {
                                Icon(
                                    painterResource(if (loadFailed) R.drawable.error else R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            content = {
                                Text(
                                    text = stringResource(if (loadFailed) R.string.whats_new_load_failed_title else R.string.release_notes),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            },
                            supportingContent = {
                                Text(stringResource(if (loadFailed) R.string.whats_new_load_failed_message else R.string.whats_new_not_published))
                            },
                        )
                        if (loadFailed) {
                            TextButton(
                                onClick = onRetry,
                                enabled = !state.isDismissing,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                if (state.dismissalFailed) {
                    Text(
                        text = stringResource(R.string.whats_new_acknowledgment_failed, stringResource(R.string.got_it)),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, enabled = !state.isDismissing, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (state.isDismissing) R.string.whats_new_saving else R.string.got_it))
            }
        },
        dismissButton = {
            TextButton(onClick = onReleaseNotes, enabled = !state.isDismissing) {
                Text(stringResource(R.string.release_notes))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.isDismissing,
            dismissOnClickOutside = !state.isDismissing,
        ),
    )
}

@Composable
private fun WhatsNewReleaseContent(
    version: String,
    sections: ImmutableList<WhatsNewSection>,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable(version) { mutableStateOf(sections.first().category.name) }
    val selectedIndex = sections.indexOfFirst { it.category.name == selectedCategory }.coerceAtLeast(0)
    val section = sections[selectedIndex]
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)

    Column(modifier = modifier.fillMaxWidth()) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            sections.forEachIndexed { index, item ->
                val titleRes = when (item.category) {
                    WhatsNewCategory.FEATURES -> R.string.whats_new_features
                    WhatsNewCategory.FIXES -> R.string.whats_new_fixes
                    WhatsNewCategory.IMPROVEMENTS -> R.string.whats_new_improvements
                }
                Tab(
                    selected = selectedIndex == index,
                    onClick = { selectedCategory = item.category.name },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = stringResource(titleRes),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }
        key(section.category) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(
                    items = section.entries,
                    key = { _, entry -> entry },
                    contentType = { _, _ -> "release_change" },
                ) { index, entry ->
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = section.entries.size),
                        colors = colors,
                        content = { Text(entry, style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
        }
    }
}
