/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
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
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.PlaylistListItem
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
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
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

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PlaylistListItem(
            playlist = playlist,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                if (playlist.playlist.isEditable != true) {
                    IconButton(
                        onClick = {
                            database.query {
                                dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (dbPlaylist?.playlist?.bookmarkedAt !=
                                        null
                                    ) {
                                        R.drawable.favorite
                                    } else {
                                        R.drawable.favorite_border
                                    },
                                ),
                            tint =
                                if (dbPlaylist?.playlist?.bookmarkedAt !=
                                    null
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    LocalContentColor.current
                                },
                            contentDescription = null,
                        )
                    }
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val dividerModifier = Modifier.padding(start = 56.dp)
    val startRadioText = stringResource(R.string.start_radio)
    val playText = stringResource(R.string.play)
    val shuffleText = stringResource(R.string.shuffle)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val shareText = stringResource(R.string.share)

    val primaryActions =
        remember(
            songs,
            playText,
            shuffleText,
            shareText,
            playlist.playlist.name,
            dbPlaylist?.playlist?.browseId,
            onDismiss,
            playerConnection,
        ) {
            listOf(
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = playText,
                    onClick = {
                        onDismiss()
                        if (songs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.map(Song::toMediaItem),
                                ),
                            )
                        }
                    },
                ),
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = shuffleText,
                    onClick = {
                        onDismiss()
                        if (songs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.shuffled().map(Song::toMediaItem),
                                ),
                            )
                        }
                    },
                ),
                NewAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = shareText,
                    onClick = {
                        onDismiss()
                        val intent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/playlist?list=${dbPlaylist?.playlist?.browseId}")
                            }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                ),
            )
        }

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions = primaryActions,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    playlist.playlist.browseId?.let { browseId ->
                        ListItem(
                            headlineContent = { Text(text = startRadioText) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = dividerModifier,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    ListItem(
                        headlineContent = { Text(text = playNextText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                coroutineScope.launch {
                                    playerConnection.playNext(songs.map { it.toMediaItem() })
                                }
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = { Text(text = addToQueueText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                                playerConnection.addToQueue(songs.map { it.toMediaItem() })
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (isInSpeedDial) {
                                            R.string.remove_from_speed_dial
                                        } else {
                                            R.string.pin_to_speed_dial
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                val updatedPins = toggleSpeedDialPin(speedDialPins, playlistPin)
                                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (editable && autoPlaylist != true) {
                        HorizontalDivider(
                            modifier = dividerModifier,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.edit)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.edit),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    showEditDialog = true
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    if (autoPlaylist != true && downloadPlaylist != true) {
                        HorizontalDivider(
                            modifier = dividerModifier,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.manage_tags)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.style),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    showAssignTagsDialog = true
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }

        if (downloadPlaylist != true) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    when (downloadState) {
                        Download.STATE_COMPLETED -> {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_download),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        showRemoveDownloadDialog = true
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                leadingContent = {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        showRemoveDownloadDialog = true
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        else -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
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
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }

        if (autoPlaylist != true) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.sync_playlist)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                if (syncProgress == null) {
                                    syncPlaylistToYouTube()
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (playlist.playlist.isHidden) {
                                            R.string.unhide_playlist
                                        } else {
                                            R.string.hide_playlist
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.visibility_off),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                if (playlist.playlist.isHidden) {
                                    onDismiss()
                                    database.query {
                                        update(playlist.playlist.copy(isHidden = false))
                                    }
                                } else {
                                    showHidePlaylistDialog = true
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                showDeletePlaylistDialog = true
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }

        playlist.playlist.shareLink?.let { shareLink ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    ListItem(
                        headlineContent = { Text(text = shareText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareLink)
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
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
