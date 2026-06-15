/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(song.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = song.id) }
    val isInSpeedDial = remember(speedDialPins, songPin) {
        speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
    }

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?
    )

    val splitArtists = remember(artists, artistSeparators) {
        if (artistSeparators.isEmpty()) {
            artists.map { SplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            artists.flatMap { artist ->
                val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        SplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(SplitArtist(artist.name, artist))
                }
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    AddToPlaylistDialog(  
        isVisible = showChoosePlaylistDialog,  
        onGetSong = {  
            database.withTransaction {  
                insert(song.toMediaMetadata())  
            }  
            listOf(song.id)  
        },  
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { _, playlistNames ->
            val message = when {
                playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showSelectArtistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    if (showSelectArtistDialog) {  
        ListDialog(  
            onDismiss = { showSelectArtistDialog = false },  
        ) {  
            items(splitArtists.distinctBy { it.name }) { splitArtist ->  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    modifier =  
                    Modifier  
                        .height(ListItemHeight)  
                        .clickable {  
                            splitArtist.originalArtist?.let { artist ->
                                navController.navigate("artist/${artist.id}")  
                                showSelectArtistDialog = false  
                                onDismiss()
                            }
                        }  
                        .padding(horizontal = 12.dp),  
                ) {  
                    Box(  
                        contentAlignment = Alignment.CenterStart,  
                        modifier =  
                        Modifier  
                            .fillParentMaxWidth()  
                            .height(ListItemHeight)  
                            .padding(horizontal = 24.dp),  
                    ) {  
                        Text(  
                            text = splitArtist.name,  
                            fontSize = 18.sp,  
                            fontWeight = FontWeight.Bold,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis,  
                        )  
                    }  
                }  
            }  
        }  
    }  

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = joinByBullet(
                        song.artists.joinToString { it.name },
                        song.duration?.let { makeTimeString(it * 1000L) },
                    )
                )
            },
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                ) {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                }
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        database.transaction {
                            librarySong.let { librarySong ->
                                val updatedSong: SongEntity
                                if (librarySong == null) {
                                    insert(song.toMediaMetadata(), SongEntity::toggleLike)
                                    updatedSong = song.toMediaMetadata().toSongEntity().let(SongEntity::toggleLike)
                                } else {
                                    updatedSong = librarySong.song.toggleLike()
                                    update(updatedSong)
                                }
                                syncUtils.likeSong(updatedSong)
                            }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(if (librarySong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (librarySong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val dividerModifier = Modifier.padding(start = 56.dp)
    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val shareText = stringResource(R.string.share)

    val primaryActions = remember(
        song,
        startRadioText,
        playNextText,
        addToQueueText,
        addToPlaylistText,
        shareText,
        onDismiss,
        playerConnection,
    ) {
        listOf(
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = startRadioText,
                onClick = {
                    onDismiss()
                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_play),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = playNextText,
                onClick = {
                    onDismiss()
                    playerConnection.playNext(song.toMediaItem())
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = addToQueueText,
                onClick = {
                    onDismiss()
                    playerConnection.addToQueue(song.toMediaItem())
                },
            ),
            NewAction(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.playlist_add),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = addToPlaylistText,
                onClick = { showChoosePlaylistDialog = true },
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
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, song.shareLink)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            ),
        )
    }

    LazyColumn(
        userScrollEnabled = true,
        contentPadding = PaddingValues(
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
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (librarySong?.song?.inLibrary != null) {
                                stringResource(R.string.remove_from_library)
                            } else {
                                stringResource(R.string.add_to_library)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (librarySong?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        coroutineScope.launch(Dispatchers.IO) {
                            val shouldAdd = librarySong?.song?.inLibrary == null
                            val remoteResult = YouTube.likeVideo(song.id, shouldAdd)
                            if (remoteResult.isFailure) {
                                withContext(Dispatchers.Main) {
                                    Toast
                                        .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                                        .show()
                                }
                                return@launch
                            }

                            val now = LocalDateTime.now()
                            database.withTransaction {
                                val base = librarySong?.song ?: song.toMediaMetadata().toSongEntity()
                                if (librarySong == null) {
                                    insert(song.toMediaMetadata())
                                }
                                update(
                                    base.copy(
                                        liked = shouldAdd,
                                        likedDate = if (shouldAdd) now else null,
                                        inLibrary = if (shouldAdd) now else null,
                                    )
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(
                                if (isInSpeedDial) R.string.remove_from_speed_dial
                                else R.string.pin_to_speed_dial,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            if (!isInSpeedDial) {
                                withContext(Dispatchers.IO) {
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                    }
                                }
                            }

                            val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                            onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                            onDismiss()
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    when (download?.state) {
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
                                modifier = Modifier.clickable {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
                                    )
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
                                modifier = Modifier.clickable {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
                                    )
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
                                modifier = Modifier.clickable {
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                    }
                                    val downloadRequest = DownloadRequest
                                        .Builder(song.id, song.id.toUri())
                                        .setCustomCacheKey(song.id)
                                        .setData(song.title.toByteArray())
                                        .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }

                    if (externalDownloaderEnabled) {
                        HorizontalDivider(
                            modifier = dividerModifier,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                onDismiss()
                                val url = "https://music.youtube.com/watch?v=${song.id}"
                                if (externalDownloaderPackage.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.external_downloader_not_configured), Toast.LENGTH_LONG).show()
                                    return@clickable
                                }
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setPackage(externalDownloaderPackage)
                                    data = android.net.Uri.parse(url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.external_downloader_not_installed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }

        if (splitArtists.isNotEmpty() || song.album != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    Column {
                        if (splitArtists.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                        navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        if (splitArtists.isNotEmpty() && song.album != null) {
                            HorizontalDivider(
                                modifier = dividerModifier,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        song.album?.let { album ->
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate("album/${album.id}")
                                    onDismiss()
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.details)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.info),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        bottomSheetPageState.show {
                            ShowMediaInfo(song.id)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
