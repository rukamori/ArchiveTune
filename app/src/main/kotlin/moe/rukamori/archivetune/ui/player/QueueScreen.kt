/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.joinByBullet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    screenState: QueueScreenState,
    visible: Boolean,
    pureBlack: Boolean,
    onAction: (QueueAction) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    val model = when (screenState) {
        is QueueScreenState.Success -> screenState.model
        is QueueScreenState.Error -> screenState.previous
        else -> null
    }
    val background = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    if (model == null) {
        Column(modifier.fillMaxSize().background(background).then(headerModifier), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            BottomSheetDefaults.DragHandle()
            when (screenState) {
                QueueScreenState.Loading -> LoadingIndicator()
                QueueScreenState.Empty -> {
                    Icon(painterResource(R.drawable.queue_music), null, Modifier.size(48.dp))
                    Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.titleLarge)
                }
                is QueueScreenState.Error -> {
                    Text(stringResource(screenState.message))
                    TextButton(onClick = { onAction(QueueAction.Retry) }) { Text(stringResource(R.string.retry)) }
                }
                is QueueScreenState.Success -> Unit
            }
        }
        return
    }
    BackHandler(enabled = visible && model.selectionCount > 0) { onAction(QueueAction.ClearSelection) }
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val scrollingHeader = maxHeight < 560.dp || LocalDensity.current.fontScale >= 1.5f
        val currentIndex = remember(model.entries) { model.entries.values.indexOfFirst { it.current } }
        val listState = rememberLazyListState()
        val reorderable = rememberReorderableLazyListState(listState) { from, to ->
            val source = from.key as? String
            val target = to.key as? String
            if (source != null && target != null) onAction(QueueAction.Drag(source, target))
        }
        LaunchedEffect(reorderable.isAnyItemDragging) {
            if (!reorderable.isAnyItemDragging) onAction(QueueAction.FinishDrag)
        }
        LaunchedEffect(visible) {
            if (visible) {
                val index = model.entries.values.indexOfFirst { it.current }
                if (index >= 0) listState.scrollToItem(index + if (scrollingHeader) 1 else 0)
            }
        }
        Scaffold(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            containerColor = background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (model.selectionCount > 0) {
                    QueueSelectionToolbar(model, onAction, Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp))
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                QueueHeader(model, onAction, headerModifier, showPlaybackControls = !scrollingHeader)
                if (!scrollingHeader) {
                    QueueRecommendationChips(model, onAction)
                    if (screenState is QueueScreenState.Error) QueueError(screenState.message, onAction)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                ) {
                    if (scrollingHeader) {
                        item(key = "queue_header", contentType = "queue_header") {
                            Column {
                                QueuePlaybackControls(model, onAction)
                                QueueRecommendationChips(model, onAction)
                                if (screenState is QueueScreenState.Error) QueueError(screenState.message, onAction)
                            }
                        }
                    }
                    itemsIndexed(model.entries.values, key = { _, row -> row.id }, contentType = { _, row -> if (row.current) "queue_current" else "queue_song" }) { index, entry ->
                        ReorderableItem(
                            reorderable,
                            key = entry.id,
                            modifier = Modifier.padding(vertical = if (entry.current) 4.dp else 0.dp),
                        ) { dragging ->
                            val swipe = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.8f })
                            LaunchedEffect(swipe.currentValue) {
                                if (swipe.currentValue != SwipeToDismissBoxValue.Settled) onAction(QueueAction.Remove(entry.id))
                            }
                            val handle = if (model.locked) Modifier else Modifier.draggableHandle()
                            val previousId = model.entries.values.getOrNull(index - 1)?.id
                            val nextId = model.entries.values.getOrNull(index + 1)?.id
                            val playing = entry.current && model.playing
                            val liked = entry.current && model.liked
                            val selecting = model.selectionCount > 0
                            val controls = remember(playing, liked, model.locked, selecting, previousId, nextId) {
                                QueueRowControls(playing, liked, model.locked, selecting, previousId, nextId)
                            }
                            val groupIndex = when {
                                entry.current -> 0
                                currentIndex < 0 || index < currentIndex -> index
                                else -> index - currentIndex - 1
                            }
                            val groupCount = when {
                                entry.current -> 1
                                currentIndex < 0 -> model.entries.values.size
                                index < currentIndex -> currentIndex
                                else -> model.entries.values.size - currentIndex - 1
                            }
                            val content: @Composable () -> Unit = remember(entry, groupIndex, groupCount, controls, dragging, pureBlack, visible, onAction, handle) {
                                { QueueSongRow(entry, groupIndex, groupCount, controls, dragging, pureBlack, visible, onAction, handle) }
                            }
                            if (model.locked || model.selectionCount > 0 || dragging) content()
                            else SwipeToDismissBox(
                                state = swipe,
                                backgroundContent = {
                                    if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                                                .background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                                            contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                Alignment.CenterStart
                                            } else {
                                                Alignment.CenterEnd
                                            },
                                        ) {
                                            Icon(painterResource(R.drawable.delete), null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                },
                            ) { content() }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
private data class QueueRowControls(
    val playing: Boolean,
    val liked: Boolean,
    val locked: Boolean,
    val selecting: Boolean,
    val previousId: String?,
    val nextId: String?,
)

@Composable
private fun QueueSongRow(
    entry: QueueEntryUi,
    index: Int,
    count: Int,
    controls: QueueRowControls,
    dragging: Boolean,
    pureBlack: Boolean,
    loadArtwork: Boolean,
    onAction: (QueueAction) -> Unit,
    handle: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val target = when {
        dragging -> colors.surfaceContainerHighest
        entry.current -> colors.primaryContainer
        pureBlack -> colors.surface
        entry.recommended -> colors.surfaceContainer
        else -> colors.surfaceContainerLow
    }
    val container by animateColorAsState(target, MaterialTheme.motionScheme.fastEffectsSpec(), label = "QueueRowColor")
    val click = remember(entry.id, onAction) { { onAction(QueueAction.Play(entry.id)) } }
    val select = remember(entry.id, onAction) { { onAction(QueueAction.Select(entry.id)) } }
    val menu = remember(entry.id, onAction) { { onAction(QueueAction.Menu(entry.id)) } }
    val moveUp = stringResource(R.string.queue_move_up)
    val moveDown = stringResource(R.string.queue_move_down)
    val reorderDescription = stringResource(R.string.queue_reorder)
    val accessibility = remember(controls.locked, controls.previousId, controls.nextId, entry.id, onAction, moveUp, moveDown) {
        buildList {
            if (!controls.locked) {
                controls.previousId?.let { id ->
                    add(CustomAccessibilityAction(moveUp) {
                        onAction(QueueAction.Drag(entry.id, id))
                        onAction(QueueAction.FinishDrag)
                        true
                    })
                }
                controls.nextId?.let { id ->
                    add(CustomAccessibilityAction(moveDown) {
                        onAction(QueueAction.Drag(entry.id, id))
                        onAction(QueueAction.FinishDrag)
                        true
                    })
                }
            }
        }
    }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    SegmentedListItem(
        selected = entry.selected,
        onClick = click,
        onLongClick = select,
        onLongClickLabel = stringResource(R.string.queue_select_song),
        shapes = shapes,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .semantics { customActions = accessibility },
        colors = ListItemDefaults.segmentedColors(containerColor = container),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!controls.locked) {
                    Box(
                        modifier = handle.size(48.dp).semantics { contentDescription = reorderDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.drag_handle), null, Modifier.size(24.dp))
                    }
                }
                QueueArtwork(entry.artworkUrl, entry.current && controls.locked, loadArtwork)
            }
        },
        overlineContent = if (entry.current) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (controls.playing) R.drawable.play else R.drawable.pause), null, Modifier.size(16.dp))
                    Text(stringResource(if (controls.playing) R.string.playing_desc else R.string.together_playback_paused),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        } else null,
        supportingContent = {
            val metadata = remember(entry.artists, entry.duration) { joinByBullet(entry.artists, entry.duration) }
            Text(
                metadata,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (controls.selecting) {
                    Checkbox(checked = entry.selected, onCheckedChange = { select() })
                } else {
                    if (entry.current) {
                        IconButton(onClick = { onAction(QueueAction.ToggleLike) }) {
                            Icon(
                                painterResource(if (controls.liked) R.drawable.favorite else R.drawable.favorite_border),
                                stringResource(if (controls.liked) R.string.action_remove_like else R.string.action_like),
                            )
                        }
                    }
                    IconButton(onClick = menu) { Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_label)) }
                }
            }
        },
    ) {
        Text(entry.title, style = if (entry.current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (entry.current) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueArtwork(url: String?, current: Boolean, load: Boolean) {
    val context = LocalContext.current
    val size = if (current) 64.dp else 52.dp
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val request = remember(context, url, pixels, load) { ImageRequest.Builder(context).data(url.takeIf { load }).size(pixels).build() }
    AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(if (current) MaterialTheme.shapes.large else MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
private fun QueueSelectionToolbar(model: QueueUiModel, onAction: (QueueAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(pluralStringResource(R.plurals.n_selected, model.selectionCount, model.selectionCount),
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { onAction(QueueAction.ClearSelection) }) {
                    Icon(painterResource(R.drawable.close), stringResource(R.string.close))
                }
            },
        ) {
            val allSelected = model.selectionCount == model.entries.values.size
            IconButton(onClick = { onAction(QueueAction.SelectAll) }) {
                Icon(painterResource(if (allSelected) R.drawable.deselect else R.drawable.select_all),
                    stringResource(if (allSelected) R.string.queue_deselect_all else R.string.queue_select_all))
            }
            IconButton(onClick = { onAction(QueueAction.AddToPlaylist) }) {
                Icon(painterResource(R.drawable.playlist_add), stringResource(R.string.add_to_playlist))
            }
            IconButton(onClick = { onAction(QueueAction.NewPlaylist) }) {
                Icon(painterResource(R.drawable.queue_music), stringResource(R.string.create_playlist))
            }
            IconButton(onClick = { onAction(QueueAction.RemoveSelection) }, enabled = !model.guest) {
                Icon(painterResource(R.drawable.delete), stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun QueueError(message: Int, onAction: (QueueAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(message), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onAction(QueueAction.Retry) }) { Text(stringResource(R.string.retry)) }
    }
}
