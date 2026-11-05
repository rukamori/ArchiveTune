/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.isSyncEnabled
import moe.rukamori.archivetune.extensions.isUserLoggedIn
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.ui.component.MenuAction
import moe.rukamori.archivetune.ui.component.MenuArtworkStyle
import moe.rukamori.archivetune.ui.component.MenuFavoriteToggle
import moe.rukamori.archivetune.ui.component.MenuHero
import moe.rukamori.archivetune.ui.component.MenuPrimaryActions
import moe.rukamori.archivetune.ui.component.MenuQuickActions
import moe.rukamori.archivetune.ui.component.MenuRowTone
import moe.rukamori.archivetune.ui.component.MenuSection
import moe.rukamori.archivetune.ui.component.MenuSheetLayout
import moe.rukamori.archivetune.ui.component.downloadAction
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import timber.log.Timber
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Immutable
private data class PlaylistSyncProgressUi(
    val completedSongs: Int,
    val totalSongs: Int,
) {
    val percent: Int
        get() =
            if (totalSongs <= 0) {
                0
            } else {
                (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
}

@Composable
public fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
    onChangeCover: (() -> Unit)? = null,
    onRemoveCover: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val playlistPin = remember(playlist.id) { SpeedDialPin(type = SpeedDialPinType.PLAYLIST, id = playlist.id) }
    val isInSpeedDial =
        remember(speedDialPins, playlistPin) {
            speedDialPins.any { it.type == playlistPin.type && it.id == playlistPin.id }
        }
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    var syncProgress by remember {
        mutableStateOf<PlaylistSyncProgressUi?>(null)
    }

    val editable: Boolean = playlist.playlist.isEditable == true

    fun syncPlaylistToYouTube() {
        coroutineScope.launch(Dispatchers.IO) {
            var lastProgressPercent = -1
            var lastProgressCompleted = -1

            fun updateProgress(
                completedSongs: Int,
                totalSongs: Int,
            ) {
                val nextProgressPercent =
                    if (totalSongs <= 0) {
                        -1
                    } else {
                        (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
                            .roundToInt()
                            .coerceIn(0, 100)
                    }
                val shouldUpdate =
                    totalSongs <= 0 ||
                        completedSongs == totalSongs ||
                        nextProgressPercent != lastProgressPercent ||
                        completedSongs - lastProgressCompleted >= 25

                if (!shouldUpdate) return

                lastProgressPercent = nextProgressPercent
                lastProgressCompleted = completedSongs

                coroutineScope.launch(Dispatchers.Main) {
                    syncProgress =
                        PlaylistSyncProgressUi(
                            completedSongs = completedSongs,
                            totalSongs = totalSongs,
                        )
                }
            }

            try {
                if (!context.isSyncEnabled()) {
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                context.getString(
                                    if (context.isUserLoggedIn()) {
                                        R.string.sync_disabled
                                    } else {
                                        R.string.not_logged_in_youtube
                                    },
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                    return@launch
                }

                val browseId = playlist.playlist.browseId ?: YouTube.createPlaylist(playlist.playlist.name).getOrThrow()
                if (playlist.playlist.browseId == null) {
                    updateProgress(completedSongs = 0, totalSongs = songs.size)
                    YouTube
                        .addSongsToPlaylist(
                            playlistId = browseId,
                            videoIds = songs.map(Song::id),
                            onProgress = ::updateProgress,
                        ).getOrThrow()
                    database.query {
                        update(
                            playlist.playlist.copy(
                                browseId = browseId,
                                lastUpdateTime = LocalDateTime.now(),
                                remoteSongCount = songs.size,
                            ),
                        )
                    }
                } else {
                    updateProgress(completedSongs = 0, totalSongs = 0)
                    syncUtils.syncPlaylistNow(
                        browseId = browseId,
                        playlistId = playlist.id,
                        propagateFailures = true,
                    ) { completedSongs, totalSongs ->
                        updateProgress(
                            completedSongs = completedSongs,
                            totalSongs = totalSongs,
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    syncProgress = null
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.playlist_synced),
                            Toast.LENGTH_SHORT,
                        ).show()
                    onDismiss()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                withContext(Dispatchers.Main) {
                    syncProgress = null
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.playlist_sync_failed, e.syncErrorDetail(context)),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        EditPlaylistDialog(
            initialName = playlist.playlist.name,
            onDismiss = { showEditDialog = false },
            onSave = { name ->
                onDismiss()
                database.query {
                    update(
                        playlist.playlist.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now(),
                        ),
                    )
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                }
            },
        )
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist.playlist.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showHidePlaylistDialog by remember {
        mutableStateOf(false)
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    var showAssignTagsDialog by remember {
        mutableStateOf(false)
    }

    if (showAssignTagsDialog) {
        AssignTagsDialog(
            playlistId = playlist.id,
            onDismiss = {
                showAssignTagsDialog = false
                onDismiss()
            },
        )
    }

    if (showHidePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showHidePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.hide_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showHidePlaylistDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showHidePlaylistDialog = false
                        onDismiss()
                        database.query {
                            update(playlist.playlist.copy(isHidden = true))
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        database.transaction {
                            if (playlist.playlist.bookmarkedAt != null) {
                                update(playlist.playlist.toggleLike())
                            }
                            delete(playlist.playlist)
                        }

                        coroutineScope.launch(Dispatchers.IO) {
                            playlist.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val hasSongs = songs.isNotEmpty()
    val heroSongCount = if (autoPlaylist == true) songs.size else playlist.songCount
    val heroSubtitle =
        heroSongCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_song, it, it) }
    val shareLink = dbPlaylist?.playlist?.shareLink ?: playlist.playlist.shareLink
    val canManage = editable && autoPlaylist != true

    val primaryActions =
        buildList {
            add(
                MenuAction(
                    label = stringResource(R.string.play),
                    icon = R.drawable.play,
                    enabled = hasSongs,
                    onClick = {
                        onDismiss()
                        playerConnection.playQueue(
                            ListQueue(
                                title = playlist.playlist.name,
                                items = songs.map(Song::toMediaItem),
                            ),
                        )
                    },
                ),
            )
            add(
                MenuAction(
                    label = stringResource(R.string.shuffle),
                    icon = R.drawable.shuffle,
                    enabled = hasSongs,
                    onClick = {
                        onDismiss()
                        playerConnection.playQueue(
                            ListQueue(
                                title = playlist.playlist.name,
                                items = songs.shuffled().map(Song::toMediaItem),
                            ),
                        )
                    },
                ),
            )
            if (shareLink != null) {
                add(
                    MenuAction(
                        label = stringResource(R.string.share),
                        icon = R.drawable.share,
                        onClick = {
                            onDismiss()
                            val intent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareLink)
                                }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    ),
                )
            }
        }

    val quickActions =
        buildList {
            playlist.playlist.browseId?.let { browseId ->
                add(
                    MenuAction(
                        label = stringResource(R.string.start_radio),
                        icon = R.drawable.radio,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                YouTube.playlist(browseId).getOrNull()?.playlist?.let { playlistItem ->
                                    playlistItem.radioEndpoint?.let { radioEndpoint ->
                                        withContext(Dispatchers.Main) {
                                            playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        }
                                    }
                                }
                            }
                            onDismiss()
                        },
                    ),
                )
            }
            add(
                MenuAction(
                    label = stringResource(R.string.play_next),
                    icon = R.drawable.playlist_play,
                    enabled = hasSongs,
                    onClick = {
                        coroutineScope.launch {
                            playerConnection.playNext(songs.map { it.toMediaItem() })
                        }
                        onDismiss()
                    },
                ),
            )
            add(
                MenuAction(
                    label = stringResource(R.string.add_to_queue),
                    icon = R.drawable.queue_music,
                    enabled = hasSongs,
                    onClick = {
                        onDismiss()
                        playerConnection.addToQueue(songs.map { it.toMediaItem() })
                    },
                ),
            )
            add(
                MenuAction(
                    label = stringResource(if (isInSpeedDial) R.string.remove_from_speed_dial else R.string.pin_to_speed_dial),
                    icon = if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                    active = isInSpeedDial,
                    onClick = {
                        val updatedPins = toggleSpeedDialPin(speedDialPins, playlistPin)
                        onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                        onDismiss()
                    },
                ),
            )
        }

    MenuSheetLayout(
        hero = {
            MenuHero(
                title = playlist.playlist.name,
                style = MenuArtworkStyle.Playlist,
                thumbnails = playlist.thumbnails,
                placeholderIcon = R.drawable.queue_music,
                overline = stringResource(R.string.menu_type_playlist),
                subtitle = heroSubtitle,
                trailing =
                    if (playlist.playlist.isEditable != true) {
                        {
                            MenuFavoriteToggle(
                                checked = dbPlaylist?.playlist?.bookmarkedAt != null,
                                enabled = dbPlaylist != null,
                                onCheckedChange = {
                                    database.query {
                                        dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                                    }
                                },
                            )
                        }
                    } else {
                        null
                    },
            )
        },
        leadingContent = {
            MenuPrimaryActions(actions = primaryActions)
            MenuQuickActions(actions = quickActions)
        },
    ) {
        MenuSection(title = stringResource(R.string.menu_section_manage)) {
            if (canManage) {
                action(
                    title = stringResource(R.string.edit),
                    icon = R.drawable.edit,
                    onClick = { showEditDialog = true },
                )
                onChangeCover?.let { changeCover ->
                    action(
                        title = stringResource(R.string.change_playlist_cover),
                        icon = R.drawable.image,
                        onClick = changeCover,
                    )
                }
                onRemoveCover?.let { removeCover ->
                    action(
                        title = stringResource(R.string.remove_playlist_cover),
                        icon = R.drawable.delete,
                        onClick = removeCover,
                    )
                }
            }
            if (autoPlaylist != true && downloadPlaylist != true) {
                action(
                    title = stringResource(R.string.manage_tags),
                    icon = R.drawable.style,
                    onClick = { showAssignTagsDialog = true },
                )
            }
        }

        if (downloadPlaylist != true && hasSongs) {
            MenuSection(title = stringResource(R.string.menu_section_offline)) {
                downloadAction(
                    state = downloadState,
                    onDownload = {
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                songs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.song.title,
                                    )
                                },
                            downloads = downloadUtil.downloads.value,
                        )
                    },
                    onRemove = { showRemoveDownloadDialog = true },
                )
            }
        }

        if (autoPlaylist != true) {
            MenuSection(title = stringResource(R.string.menu_section_library)) {
                action(
                    title = stringResource(R.string.sync_playlist),
                    icon = R.drawable.sync,
                    enabled = syncProgress == null,
                    onClick = {
                        if (syncProgress == null) {
                            syncPlaylistToYouTube()
                        }
                    },
                )
                action(
                    title =
                        stringResource(
                            if (playlist.playlist.isHidden) {
                                R.string.unhide_playlist
                            } else {
                                R.string.hide_playlist
                            },
                        ),
                    icon = R.drawable.visibility_off,
                    tone = if (playlist.playlist.isHidden) MenuRowTone.Active else MenuRowTone.Neutral,
                    onClick = {
                        if (playlist.playlist.isHidden) {
                            onDismiss()
                            database.query {
                                update(playlist.playlist.copy(isHidden = false))
                            }
                        } else {
                            showHidePlaylistDialog = true
                        }
                    },
                )
            }

            MenuSection(title = null) {
                action(
                    title = stringResource(R.string.delete),
                    icon = R.drawable.delete,
                    tone = MenuRowTone.Destructive,
                    onClick = { showDeletePlaylistDialog = true },
                )
            }
        }
    }

    syncProgress?.let { progress ->
        LoadingScreen(
            isVisible = true,
            value = progress.percent,
            title = stringResource(R.string.sync_playlist),
            stepText =
                if (progress.totalSongs > 0) {
                    stringResource(
                        R.string.playlist_sync_progress_step,
                        progress.completedSongs,
                        progress.totalSongs,
                    )
                } else {
                    stringResource(R.string.please_wait)
                },
            indeterminate = progress.totalSongs <= 0,
        )
    }
}

private fun Throwable.syncErrorDetail(context: android.content.Context): String =
    localizedMessage
        ?.takeIf(String::isNotBlank)
        ?: javaClass.simpleName.takeIf(String::isNotBlank)
        ?: context.getString(R.string.error_unknown)
