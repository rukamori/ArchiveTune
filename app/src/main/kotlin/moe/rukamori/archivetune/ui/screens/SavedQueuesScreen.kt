/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.SavedQueueEntity
import moe.rukamori.archivetune.extensions.toPersistQueue
import moe.rukamori.archivetune.models.QueueSnapshot
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.GridMenu
import moe.rukamori.archivetune.ui.component.GridMenuItem
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.PlaylistThumbnail
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.SavedQueuesViewModel

/**
 * Lists every permanently [SavedQueueEntity] the user has created via "Save Queue" (see
 * [moe.rukamori.archivetune.ui.component.SaveQueueDialog]). Tapping a row restores and plays that
 * queue, preserving song order, the currently playing song/position, shuffle state and repeat
 * mode. Each row also exposes rename, delete and "save as playlist" actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedQueuesScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SavedQueuesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val savedQueues by viewModel.savedQueues.collectAsState()
    val lazyListState = rememberLazyListState()

    var queueToRename by remember { mutableStateOf<SavedQueueEntity?>(null) }
    var queueToDelete by remember { mutableStateOf<SavedQueueEntity?>(null) }

    fun restoreAndPlay(savedQueue: SavedQueueEntity) {
        val persistQueue = savedQueue.toPersistQueue()
        if (persistQueue == null || persistQueue.items.isEmpty()) {
            Toast.makeText(context, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        playerConnection.restoreSavedQueue(
            QueueSnapshot(
                persistQueue = persistQueue,
                repeatMode = savedQueue.repeatMode,
                shuffleModeEnabled = savedQueue.shuffleModeEnabled,
            ),
        )
        navController.navigateUp()
    }

    queueToRename?.let { savedQueue ->
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.rename_saved_queue)) },
            placeholder = { Text(text = stringResource(R.string.saved_queue_name)) },
            isInputValid = { it.trim().isNotEmpty() },
            initialTextFieldValue = TextFieldValue(savedQueue.name),
            onDismiss = { queueToRename = null },
            onDone = { newName ->
                viewModel.rename(savedQueue, newName)
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.saved_queue_renamed, newName.trim()),
                        Toast.LENGTH_SHORT,
                    ).show()
            },
        )
    }

    queueToDelete?.let { savedQueue ->
        DefaultDialog(
            onDismiss = { queueToDelete = null },
            content = {
                Text(
                    text = stringResource(R.string.delete_saved_queue_confirmation, savedQueue.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { queueToDelete = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        viewModel.delete(savedQueue)
                        queueToDelete = null
                        Toast.makeText(context, R.string.saved_queue_deleted, Toast.LENGTH_SHORT).show()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (savedQueues.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.queue_music,
                        text = stringResource(R.string.no_saved_queues),
                    )
                }
            }

            items(items = savedQueues, key = { it.id }) { savedQueue ->
                ListItem(
                    title = savedQueue.name,
                    subtitle =
                        pluralStringResource(
                            R.plurals.n_song,
                            savedQueue.songCount,
                            savedQueue.songCount,
                        ),
                    thumbnailContent = {
                        PlaylistThumbnail(
                            thumbnails = listOfNotNull(savedQueue.thumbnailUrl),
                            size = ListThumbnailSize,
                            placeHolder = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(ListThumbnailSize / 2),
                                )
                            },
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.IconButton(onClick = {
                            menuState.show {
                                GridMenu {
                                    GridMenuItem(
                                        icon = R.drawable.play,
                                        title = R.string.restore_saved_queue,
                                        onClick = {
                                            menuState.dismiss()
                                            restoreAndPlay(savedQueue)
                                        },
                                    )
                                    GridMenuItem(
                                        icon = R.drawable.edit,
                                        title = R.string.rename_saved_queue,
                                        onClick = {
                                            menuState.dismiss()
                                            queueToRename = savedQueue
                                        },
                                    )
                                    GridMenuItem(
                                        icon = R.drawable.playlist_add,
                                        title = R.string.save_queue_as_playlist,
                                        onClick = {
                                            menuState.dismiss()
                                            viewModel.saveAsPlaylist(savedQueue, savedQueue.name) { success ->
                                                val message =
                                                    if (success) {
                                                        context.getString(
                                                            R.string.saved_queue_as_playlist,
                                                            savedQueue.name,
                                                        )
                                                    } else {
                                                        context.getString(R.string.nothing_to_save)
                                                    }
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    )
                                    GridMenuItem(
                                        icon = R.drawable.delete,
                                        title = R.string.delete_saved_queue,
                                        onClick = {
                                            menuState.dismiss()
                                            queueToDelete = savedQueue
                                        },
                                    )
                                }
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { restoreAndPlay(savedQueue) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    queueToRename = savedQueue
                                },
                            ),
                )
            }
        }

        TopAppBar(
            title = { Text(text = stringResource(R.string.saved_queues)) },
            navigationIcon = {
                IconButton(onClick = {
                    navController.navigateUp()
                }, onLongClick = {
                    navController.backToMain()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}
