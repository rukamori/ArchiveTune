/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ImportSourcePriorityKey
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.ImportSource
import moe.rukamori.archivetune.models.ImportedSongResult
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playlistimport.ImportSongResolver
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.PlaylistListItem
import moe.rukamori.archivetune.utils.rememberPreference
import timber.log.Timber

@Composable
fun AddToPlaylistDialogOnline(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    songs: SnapshotStateList<Song>, // list of song ids. Songs should be inserted to database in this function.
    onDismiss: () -> Unit,
    onProgressStart: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
    onStatusChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val importResolver = remember { ImportSongResolver() }
    val (importLocalFirst) = rememberPreference(ImportSourcePriorityKey, false)
    var allPlaylists by remember { mutableStateOf(emptyList<Playlist>()) }
    val playlists = remember(allPlaylists) { playlistsForAddToPlaylist(allPlaylists).asReversed() }

    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showResultDialog by remember { mutableStateOf(false) }
    var processingSummary by remember { mutableStateOf<ProcessingSummary?>(null) }
    var reviewResults by remember { mutableStateOf<List<ImportedSongResult>?>(null) }
    var reviewLocalLibrary by remember { mutableStateOf(emptyList<Song>()) }
    var pendingTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingAddToLiked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        database.playlistsByCreateDateAsc().collect {
            allPlaylists = it
        }
    }

    fun prepareSongsForReview(
        targetPlaylist: Playlist?,
        addToLiked: Boolean,
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            val snapshotSongs = songs.toList()
            val total = snapshotSongs.size
            if (total == 0) {
                withContext(Dispatchers.Main) {
                    onProgressStart(false)
                    onPercentageChange(0)
                    onDismiss()
                }
                return@launch
            }

            try {
                withContext(Dispatchers.Main) {
                    onProgressStart(true)
                    onPercentageChange(0)
                    onStatusChange(context.getString(R.string.import_preparing))
                    onDismiss()
                }

                val localLibrary = database.importSongCandidates().first()
                val resolved =
                    importResolver.resolve(
                        songs = snapshotSongs,
                        localLibrary = localLibrary,
                        localFirst = importLocalFirst,
                        onProgress = { completed, count ->
                            val percent = ((completed.toDouble() / count) * 100).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                onPercentageChange(percent)
                                onStatusChange(context.getString(R.string.import_matching_progress, completed, count))
                            }
                        },
                    )

                withContext(Dispatchers.Main) {
                    onPercentageChange(100)
                    pendingTargetPlaylist = targetPlaylist
                    pendingAddToLiked = addToLiked
                    reviewLocalLibrary = localLibrary
                    reviewResults = resolved
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Import matching failed")
                withContext(Dispatchers.Main) {
                    processingSummary =
                        ProcessingSummary(
                            total = total,
                            success = 0,
                            failed = total,
                            failedItems = snapshotSongs.map { it.title },
                        )
                    showResultDialog = true
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onProgressStart(false)
                }
            }
        }
    }

    fun saveReviewedSongs(confirmedResults: List<ImportedSongResult>) {
        val targetPlaylist = pendingTargetPlaylist
        val addToLiked = pendingAddToLiked
        coroutineScope.launch(Dispatchers.IO) {
            val failedSongs = mutableListOf<String>()
            val preparedResults = mutableListOf<ImportedSongResult>()
            val total = confirmedResults.size

            try {
                withContext(Dispatchers.Main) {
                    onProgressStart(true)
                    onPercentageChange(0)
                    onStatusChange(context.getString(R.string.import_saving))
                }

                confirmedResults.forEachIndexed { index, result ->
                    val resolvedSong = result.resolvedSong
                    if (resolvedSong == null || result.resolvedId == null) {
                        failedSongs += result.originalSong.title
                    } else {
                        try {
                            if (result.source == ImportSource.YOUTUBE) {
                                database.insert(resolvedSong.toMediaMetadata())
                            }
                            preparedResults += result
                        } catch (error: Exception) {
                            Timber.e(error, "Failed to prepare imported song: %s", result.originalSong.title)
                            failedSongs += result.originalSong.title
                        }
                    }

                    val percent = (((index + 1).toDouble() / total.coerceAtLeast(1)) * 80).toInt()
                    withContext(Dispatchers.Main) {
                        onPercentageChange(percent)
                        onStatusChange(context.getString(R.string.import_saving_progress, index + 1, total))
                    }
                }

                val savedResults =
                    if (targetPlaylist != null) {
                        try {
                            database.addSongToPlaylist(targetPlaylist, preparedResults.mapNotNull { it.resolvedId })
                            preparedResults
                        } catch (error: Exception) {
                            Timber.e(error, "Failed to add imported songs to playlist")
                            failedSongs += preparedResults.map { it.originalSong.title }
                            emptyList()
                        }
                    } else if (addToLiked) {
                        preparedResults.filter { result ->
                            try {
                                val resolvedSong = checkNotNull(result.resolvedSong)
                                database.query {
                                    if (!resolvedSong.song.liked) {
                                        update(resolvedSong.song.toggleLike())
                                    }
                                }
                                true
                            } catch (error: Exception) {
                                Timber.e(error, "Failed to like imported song: %s", result.originalSong.title)
                                failedSongs += result.originalSong.title
                                false
                            }
                        }
                    } else {
                        preparedResults
                    }

                withContext(Dispatchers.Main) {
                    val distinctFailedSongs = failedSongs.distinct()
                    onPercentageChange(100)
                    processingSummary =
                        ProcessingSummary(
                            total = total,
                            success = savedResults.size,
                            failed = distinctFailedSongs.size,
                            failedItems = distinctFailedSongs,
                        )
                    showResultDialog = true
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onProgressStart(false)
                }
            }
        }
    }

    if (isVisible) {
        ListDialog(
            onDismiss = onDismiss,
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.playlist_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            showCreatePlaylistDialog = true
                        },
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                        modifier =
                            Modifier.clickable {
                            prepareSongsForReview(targetPlaylist = playlist, addToLiked = false)
                        },
                )
            }

            item {
                ListItem(
                    modifier =
                        Modifier.clickable {
                            prepareSongsForReview(targetPlaylist = null, addToLiked = true)
                        },
                    title = stringResource(R.string.liked_songs),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.favorite), // The XML image
                            contentDescription = null,
                            modifier = Modifier.size(40.dp), // Adjust size as needed
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground), // Optional tinting
                        )
                    },
                    trailingContent = {},
                )
            }

            item {
                Text(
                    text = stringResource(R.string.playlist_add_local_to_synced_note),
                    fontSize = TextUnit(12F, TextUnitType.Sp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
        )
    }

    reviewResults?.let { results ->
        ImportReviewScreen(
            results = results,
            localLibrary = reviewLocalLibrary,
            onCancel = {
                reviewResults = null
                reviewLocalLibrary = emptyList()
                pendingTargetPlaylist = null
                pendingAddToLiked = false
            },
            onConfirm = { confirmedResults ->
                reviewResults = null
                reviewLocalLibrary = emptyList()
                saveReviewedSongs(confirmedResults)
                pendingTargetPlaylist = null
                pendingAddToLiked = false
            },
        )
    }

    // Result Dialog
    if (showResultDialog && processingSummary != null) {
        val summary = processingSummary!!
        DefaultDialog(
            title = { Text("Import Complete") },
            onDismiss = { showResultDialog = false },
            buttons = {
                TextButton(onClick = { showResultDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text("OK")
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Processed: ${summary.total}")
                Text("Successfully Imported: ${summary.success}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (summary.failed > 0) {
                    Text("Failed: ${summary.failed}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Failed Items:", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                    ) {
                        items(summary.failedItems) { title ->
                            Text(
                                text = "• $title",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ProcessingSummary(
    val total: Int,
    val success: Int,
    val failed: Int,
    val failedItems: List<String>,
)
