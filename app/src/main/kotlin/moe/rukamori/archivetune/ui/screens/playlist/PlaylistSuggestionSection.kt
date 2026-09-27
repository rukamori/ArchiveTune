/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel

@Composable
fun PlaylistSuggestionsSection(
    modifier: Modifier = Modifier,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(false)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)

    val playlistSuggestions by viewModel.playlistSuggestions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingSuggestions.collectAsStateWithLifecycle()

    // State for duplicate check dialog
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var songToCheck by remember { mutableStateOf<SongItem?>(null) }

    val currentSuggestions = playlistSuggestions
    if (currentSuggestions == null && !isLoading) return

    // Duplicate Check Dialog
    if (showDuplicateDialog && songToCheck != null) {
        val song = songToCheck!!
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        songToCheck = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            // Add to current playlist anyway
                            val browseId =
                                viewModel.playlist.value
                                    ?.playlist
                                    ?.browseId
                            viewModel.addSongToPlaylist(song, browseId)

                            val playlistName =
                                viewModel.playlist.value
                                    ?.playlist
                                    ?.name
                            val message =
                                if (playlistName != null) {
                                    context.getString(R.string.added_to_playlist, playlistName)
                                } else {
                                    context.getString(R.string.add_to_playlist)
                                }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        showDuplicateDialog = false
                        songToCheck = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.add_anyway))
                }
            },
            onDismiss = {
                showDuplicateDialog = false
                songToCheck = null
            },
        ) {
            Text(text = stringResource(R.string.duplicates_description_single))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavigationTitle(
                title = stringResource(R.string.you_might_like),
                subtitle =
                    currentSuggestions?.let { s ->
                        if (s.totalQueries > 1) {
                            "${s.currentQueryIndex + 1} / ${s.totalQueries}"
                        } else {
                            null
                        }
                    },
                modifier = Modifier.weight(1f),
            )
            if (currentSuggestions != null && currentSuggestions.totalQueries > 1) {
                androidx.compose.material3.IconButton(
                    onClick = remember(viewModel) { { viewModel.changeSuggestionPage(-1) } },
                    enabled = !isLoading && currentSuggestions.currentQueryIndex > 0,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.widget_previous),
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = remember(viewModel) { { viewModel.changeSuggestionPage(1) } },
                    enabled = !isLoading &&
                        currentSuggestions.currentQueryIndex < currentSuggestions.totalQueries - 1,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = stringResource(R.string.next),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        currentSuggestions?.let { suggestions ->
            // Suggestions List (Vertical)
            suggestions.items.forEach { item ->
                YouTubeListItem(
                    item = item,
                    isActive = item.id == mediaMetadata?.id,
                    isPlaying = isPlaying == true,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                val songItem = item as? SongItem
                                if (songItem != null) {
                                    // Check for duplicates in current playlist first
                                    songToCheck = songItem
                                    coroutineScope.launch {
                                        val isDuplicate =
                                            withContext(Dispatchers.IO) {
                                                val duplicates =
                                                    database.playlistDuplicates(
                                                        viewModel.playlistId,
                                                        listOf(songItem.id),
                                                    )
                                                duplicates.isNotEmpty()
                                            }

                                        if (isDuplicate) {
                                            showDuplicateDialog = true
                                        } else {
                                            // No duplicate, add directly
                                            val browseId =
                                                viewModel.playlist.value
                                                    ?.playlist
                                                    ?.browseId
                                            val success =
                                                viewModel.addSongToPlaylist(
                                                    song = songItem,
                                                    browseId = browseId,
                                                )

                                            if (success) {
                                                val playlistName =
                                                    viewModel.playlist.value
                                                        ?.playlist
                                                        ?.name
                                                val message =
                                                    if (playlistName != null) {
                                                        context.getString(R.string.added_to_playlist, playlistName)
                                                    } else {
                                                        context.getString(R.string.add_to_playlist)
                                                    }
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = stringResource(R.string.add_to_playlist),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                if (playerConnection == null) return@clickable
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    if (item is SongItem) {
                                        val songItems = suggestions.items.filterIsInstance<SongItem>()
                                        val startIndex = songItems.indexOfFirst { it.id == item.id }
                                        if (startIndex != -1) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.you_might_like),
                                                    items = songItems.map { it.toMediaItem() },
                                                    startIndex = startIndex,
                                                ),
                                            )
                                        }
                                    }
                                }
                            },
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(
                        onClick = { viewModel.resetAndLoadPlaylistSuggestions() },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.refresh_suggestions))
                        }
                    }
                }
            }
        }
    }
}
