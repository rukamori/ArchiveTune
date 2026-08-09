/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
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
import moe.rukamori.archivetune.constants.LibraryFilter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LibraryChipOrderDialog(
    initialOrder: List<LibraryFilter>,
    onDismiss: () -> Unit,
    onConfirm: (List<LibraryFilter>) -> Unit,
) {
    val filters = remember { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val filter = filters.removeAt(from.index)
            filters.add(to.index, filter)
        }

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
                onClick = { onConfirm(filters.toList()) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.arrange_library_chips),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = filters,
                    key = { _, filter -> filter.name },
                    contentType = { _, _ -> "library_chip_order_item" },
                ) { index, filter ->
                    ReorderableItem(reorderableState, key = filter.name) {
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
                                text = stringResource(filter.labelResId()),
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

private fun LibraryFilter.labelResId(): Int =
    when (this) {
        LibraryFilter.LIBRARY -> R.string.filter_library
        LibraryFilter.PLAYLISTS -> R.string.playlists
        LibraryFilter.SPOTIFY -> R.string.spotify_playlists
        LibraryFilter.SONGS -> R.string.songs
        LibraryFilter.ARTISTS -> R.string.artists
        LibraryFilter.ALBUMS -> R.string.albums
    }
