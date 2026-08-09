/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/Rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.PlaylistTagUiModel
import moe.rukamori.archivetune.viewmodels.PlaylistTagsScreenState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistTagOrderDialog(
    state: PlaylistTagsScreenState,
    initialOrder: List<PlaylistTagUiModel>,
    onDismiss: () -> Unit,
    onConfirm: (List<PlaylistTagUiModel>) -> Unit,
) {
    val tags = remember(initialOrder) { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val tag = tags.removeAt(from.index)
            tags.add(to.index, tag)
        }
    val canConfirm = state is PlaylistTagsScreenState.Success

    DefaultDialog(
        onDismiss = onDismiss,
        constrainContentHeight = true,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(tags.toList()) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.arrange_playlist_tags),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when (state) {
                PlaylistTagsScreenState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(32.dp))
                }

                PlaylistTagsScreenState.Empty -> {
                    Text(
                        text = stringResource(R.string.no_tags_available),
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }

                is PlaylistTagsScreenState.Error -> {
                    Text(
                        text = stringResource(state.messageResId),
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }

                is PlaylistTagsScreenState.Success -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(
                            items = tags,
                            key = { _, tag -> tag.id },
                            contentType = { _, _ -> "playlist_tag_order_item" },
                        ) { index, tag ->
                            ReorderableItem(reorderableState, key = tag.id) {
                                val contentColor = MaterialTheme.colorScheme.onSurface

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = contentColor,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.drag_handle),
                                        contentDescription = null,
                                        tint = contentColor.copy(alpha = 0.6f),
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .draggableHandle(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
