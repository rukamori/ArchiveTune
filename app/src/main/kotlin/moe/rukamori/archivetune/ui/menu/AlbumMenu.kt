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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.media3.exoplayer.offline.Download.STATE_STOPPED
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.MenuAction
import moe.rukamori.archivetune.ui.component.MenuArtworkStyle
import moe.rukamori.archivetune.ui.component.MenuFavoriteToggle
import moe.rukamori.archivetune.ui.component.MenuHero
import moe.rukamori.archivetune.ui.component.MenuPrimaryActions
import moe.rukamori.archivetune.ui.component.MenuQuickActions
import moe.rukamori.archivetune.ui.component.MenuSection
import moe.rukamori.archivetune.ui.component.MenuSheetLayout
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.downloadAction
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AlbumMenu(
    originalAlbum: Album,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val libraryAlbum by database.album(originalAlbum.id).collectAsStateWithLifecycle(initialValue = originalAlbum)
    val album = libraryAlbum ?: originalAlbum
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(album.id) {
        database.albumSongs(album.id).collect {
            songs = it
        }
    }

    var downloadState by remember {
        mutableStateOf(STATE_STOPPED)
    }

    val remoteSongs = remember(songs) { songs.filterNot { it.song.isLocal } }

    LaunchedEffect(remoteSongs, album.album.isLocal) {
        if (album.album.isLocal || remoteSongs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (remoteSongs.all { downloads[it.id]?.state == STATE_COMPLETED }) {
                    STATE_COMPLETED
                } else if (remoteSongs.all {
                        downloads[it.id]?.state == STATE_QUEUED ||
                            downloads[it.id]?.state == STATE_DOWNLOADING ||
                            downloads[it.id]?.state == STATE_COMPLETED
                    }
                ) {
                    STATE_DOWNLOADING
                } else {
                    STATE_STOPPED
                }
        }
    }

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val albumPin = remember(album.id) { SpeedDialPin(type = SpeedDialPinType.ALBUM, id = album.id) }
    val isInSpeedDial =
        remember(speedDialPins, albumPin) {
            speedDialPins.any { it.type == albumPin.type && it.id == albumPin.id }
        }
    val isLocalAlbum = album.album.isLocal

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: moe.rukamori.archivetune.db.entities.ArtistEntity?,
    )

    val splitArtists =
        remember(album.artists, artistSeparators, isLocalAlbum) {
            if (isLocalAlbum || artistSeparators.isEmpty()) {
                album.artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                album.artists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
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

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songs.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.already_in_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier =
                        Modifier
                            .clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                SongListItem(song = song)
            }
        }
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = splitArtists.distinctBy { it.name },
                key = { it.name },
            ) { splitArtist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .height(ListItemHeight)
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigateToArtist(artist.id)
                                    showSelectArtistDialog = false
                                    onDismiss()
                                }
                            }.padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model =
                                splitArtist.originalArtist?.thumbnailUrl?.resize(
                                    width = 200,
                                    height = 200,
                                    ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(ListThumbnailSize)
                                    .clip(CircleShape),
                        )
                    }
                    Text(
                        text = splitArtist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    val albumThumbnails = remember(album.album.thumbnailUrl) { listOfNotNull(album.album.thumbnailUrl) }
    val heroSubtitle = remember(album.artists) { album.artists.joinToString { it.name } }
    val songCount = album.album.songCount
    val heroMeta =
        joinByBullet(
            album.album.year?.toString(),
            songCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_song, it, it) },
        )
    val hasSongs = songs.isNotEmpty()

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
                                title = album.album.title,
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
                                title = album.album.title,
                                items = songs.shuffled().map(Song::toMediaItem),
                            ),
                        )
                    },
                ),
            )
            if (!isLocalAlbum) {
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
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://music.youtube.com/playlist?list=${album.album.playlistId}",
                                    )
                                }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    ),
                )
            }
        }

    val quickActions =
        listOf(
            MenuAction(
                label = stringResource(R.string.play_next),
                icon = R.drawable.playlist_play,
                enabled = hasSongs,
                onClick = {
                    onDismiss()
                    playerConnection.playNext(songs.map { it.toMediaItem() })
                },
            ),
            MenuAction(
                label = stringResource(R.string.add_to_queue),
                icon = R.drawable.queue_music,
                enabled = hasSongs,
                onClick = {
                    onDismiss()
                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                },
            ),
            MenuAction(
                label = stringResource(R.string.add_to_playlist),
                icon = R.drawable.playlist_add,
                enabled = hasSongs,
                onClick = { showChoosePlaylistDialog = true },
            ),
            MenuAction(
                label = stringResource(if (isInSpeedDial) R.string.remove_from_speed_dial else R.string.pin_to_speed_dial),
                icon = if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                active = isInSpeedDial,
                onClick = {
                    val updatedPins = toggleSpeedDialPin(speedDialPins, albumPin)
                    onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                    onDismiss()
                },
            ),
        )

    MenuSheetLayout(
        hero = {
            MenuHero(
                title = album.album.title,
                style = MenuArtworkStyle.Album,
                thumbnails = albumThumbnails,
                placeholderIcon = R.drawable.album,
                overline = stringResource(R.string.menu_type_album),
                subtitle = heroSubtitle,
                meta = heroMeta,
                trailing = {
                    MenuFavoriteToggle(
                        checked = album.album.bookmarkedAt != null,
                        onCheckedChange = {
                            database.query {
                                update(album.album.toggleLike())
                            }
                        },
                    )
                },
            )
        },
        leadingContent = {
            MenuPrimaryActions(actions = primaryActions)
            MenuQuickActions(actions = quickActions)
        },
    ) {
        if (!isLocalAlbum && remoteSongs.isNotEmpty()) {
            MenuSection(title = stringResource(R.string.menu_section_offline)) {
                downloadAction(
                    state = downloadState,
                    onDownload = {
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                remoteSongs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.song.title,
                                    )
                                },
                            downloads = downloadUtil.downloads.value,
                        )
                    },
                    onRemove = {
                        remoteSongs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }
                    },
                )
            }
        }

        MenuSection(title = stringResource(R.string.menu_section_go_to)) {
            if (splitArtists.isNotEmpty()) {
                action(
                    title = stringResource(R.string.view_artist),
                    icon = R.drawable.artist,
                    navigates = true,
                    onClick = {
                        if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                            navController.navigateToArtist(splitArtists[0].originalArtist!!.id)
                            onDismiss()
                        } else {
                            showSelectArtistDialog = true
                        }
                    },
                )
            }
        }

        if (!isLocalAlbum) {
            MenuSection(title = stringResource(R.string.menu_section_manage)) {
                action(
                    title = stringResource(R.string.refetch),
                    icon = R.drawable.sync,
                    iconRotation = { rotationAnimation },
                    onClick = {
                        refetchIconDegree -= 360
                        scope.launch(Dispatchers.IO) {
                            YouTube.album(album.id).onSuccess {
                                database.transaction {
                                    update(album.album, it, album.artists)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
