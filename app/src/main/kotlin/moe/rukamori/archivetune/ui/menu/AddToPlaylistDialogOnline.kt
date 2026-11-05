/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ImportSourcePriorityKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.ImportSource
import moe.rukamori.archivetune.models.ImportedSongResult
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playlistimport.ImportSongResolver
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
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
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val importResolver = remember { ImportSongResolver() }
    val (importLocalFirst) = rememberPreference(ImportSourcePriorityKey, false)
    val allPlaylists by database.playlistsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val availablePlaylists = remember(allPlaylists) { playlistsForAddToPlaylist(allPlaylists).asReversed() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchField by rememberSaveable { mutableStateOf(false) }
    val playlists =
        remember(availablePlaylists, searchQuery) {
            val normalizedQuery = searchQuery.trim()
            if (normalizedQuery.isBlank()) {
                availablePlaylists
            } else {
                availablePlaylists.filter { it.playlist.name.contains(normalizedQuery, ignoreCase = true) }
            }
        }

    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showResultDialog by remember { mutableStateOf(false) }
    var processingSummary by remember { mutableStateOf<ProcessingSummary?>(null) }
    var reviewResults by remember { mutableStateOf<List<ImportedSongResult>?>(null) }
    var reviewLocalLibrary by remember { mutableStateOf(emptyList<Song>()) }
    var pendingTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingAddToLiked by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            searchQuery = ""
            showSearchField = false
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
                        val missingSongIds = mutableSetOf<String>()
                        val likeRequests =
                            preparedResults.mapNotNull { result ->
                                val resolvedId = checkNotNull(result.resolvedId)
                                val storedSong = database.getSongById(resolvedId)?.song
                                if (storedSong == null) {
                                    missingSongIds += resolvedId
                                    failedSongs += result.originalSong.title
                                    null
                                } else if (storedSong.liked) {
                                    null
                                } else {
                                    storedSong.toggleLike()
                                }
                            }
                        val failedSongIds = syncUtils.likeSongs(likeRequests) + missingSongIds
                        if (failedSongIds.isNotEmpty()) {
                            preparedResults
                                .filter { it.resolvedId in failedSongIds && it.resolvedId !in missingSongIds }
                                .forEach { result -> failedSongs += result.originalSong.title }
                        }
                        preparedResults.filterNot { result -> result.resolvedId in failedSongIds }
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
        val listState = rememberLazyListState()
        val motion = MaterialTheme.motionScheme
        val colors = MaterialTheme.colorScheme
        val animationsDisabled = LocalAnimationsDisabled.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        AddToPlaylistFrame(
            onDismissRequest = onDismiss,
            listState = listState,
            header = {
                AddToPlaylistHeader(
                    title = stringResource(R.string.import_destination_title),
                    supporting = pluralStringResource(R.plurals.import_n_songs_ready, songs.size, songs.size),
                    icon = R.drawable.playlist_import,
                    badgeCount = 0,
                    badgeActive = true,
                    searchActive = showSearchField,
                    onSearchActiveChange = { active ->
                        showSearchField = active
                        if (!active) searchQuery = ""
                    },
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            },
            controls = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddToPlaylistActionTile(
                        title = stringResource(R.string.liked_songs),
                        supporting = stringResource(R.string.import_liked_songs_supporting),
                        icon = R.drawable.favorite,
                        badgePolygon = MaterialShapes.Heart,
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                        badgeColor = colors.secondary,
                        badgeContentColor = colors.onSecondary,
                        onClick = { prepareSongsForReview(targetPlaylist = null, addToLiked = true) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AddToPlaylistActionTile(
                        title = stringResource(R.string.create_playlist),
                        supporting = stringResource(R.string.add_to_playlist_create_supporting),
                        icon = R.drawable.add,
                        badgePolygon = MaterialShapes.Cookie4Sided,
                        containerColor = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                        badgeColor = colors.tertiary,
                        badgeContentColor = colors.onTertiary,
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ImportDestinationNote()
            },
            footer = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        ) {
            item(key = "import_destination_section", contentType = "section") {
                AddToPlaylistSectionLabel(
                    title = stringResource(R.string.playlists),
                    count = playlists.size,
                )
            }

            if (playlists.isEmpty()) {
                item(key = "import_destination_empty", contentType = "empty") {
                    AddToPlaylistEmptyState(searching = searchQuery.isNotBlank())
                }
            } else {
                itemsIndexed(
                    items = playlists,
                    key = { _, playlist -> playlist.id },
                    contentType = { _, _ -> "playlist" },
                ) { index, playlist ->
                    SegmentedListItem(
                        onClick = { prepareSongsForReview(targetPlaylist = playlist, addToLiked = false) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = playlists.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainerLow),
                        modifier =
                            if (animationsDisabled) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                )
                            },
                        leadingContent = {
                            PlaylistDestinationArtwork(playlist = playlist, selected = false)
                        },
                        supportingContent = {
                            PlaylistDestinationSupporting(playlist = playlist)
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f },
                            )
                        },
                    ) {
                        Text(
                            text = playlist.playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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

    val summary = processingSummary
    if (showResultDialog && summary != null) {
        ImportResultDialog(
            summary = summary,
            onDismiss = { showResultDialog = false },
        )
    }
}

@Composable
private fun ImportDestinationNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.playlist_add_local_to_synced_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ImportResultDialog(
    summary: ProcessingSummary,
    onDismiss: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val successRatio = if (summary.total > 0) summary.success.toFloat() / summary.total else 0f
    val ringProgress = remember(summary) { Animatable(if (animationsDisabled) successRatio else 0f) }
    val ringColor = if (summary.success == 0 && summary.total > 0) colors.error else colors.primary

    LaunchedEffect(summary, animationsDisabled) {
        if (animationsDisabled) {
            ringProgress.snapTo(successRatio)
        } else {
            ringProgress.animateTo(successRatio, motion.slowSpatialSpec())
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        progress = { ringProgress.value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = ringColor,
                        trackColor = colors.surfaceContainerHighest,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = summary.success.toString(),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.import_result_of_total, summary.total),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.import_result_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text =
                            if (summary.failed == 0) {
                                stringResource(R.string.import_result_all_success)
                            } else {
                                pluralStringResource(R.plurals.import_result_some_failed, summary.failed, summary.failed)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summary.failed == 0) colors.onSurfaceVariant else colors.error,
                        textAlign = TextAlign.Center,
                    )
                }

                if (summary.failedItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_result_failed_items),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = colors.surfaceContainerLow,
                        ) {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                items(summary.failedItems) { title ->
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.error),
                                            contentDescription = null,
                                            tint = colors.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(android.R.string.ok))
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
