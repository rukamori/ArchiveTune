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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
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
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.ExternalDownloaderLaunchResult
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.openExternalDownloader
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import timber.log.Timber
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
    val artists =
        remember {
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
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?,
    )

    val splitArtists =
        remember(artists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                artists.flatMap { artist ->
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
            val message =
                when {
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
                                    navController.navigateToArtist(artist.id)
                                    showSelectArtistDialog = false
                                    onDismiss()
                                }
                            }.padding(horizontal = 12.dp),
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

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val songThumbnails = remember(song.thumbnail) { listOf(song.thumbnail) }
    val heroSubtitle = remember(song.artists) { song.artists.joinToString { it.name } }
    val heroMeta =
        remember(song.album?.name, song.duration) {
            joinByBullet(
                song.album?.name,
                song.duration?.let { makeTimeString(it * 1000L) },
            )
        }
    val isLiked = librarySong?.song?.liked == true
    val inLibrary = librarySong?.song?.inLibrary != null

    val toggleLike: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val requestedSong =
                    database.withTransaction {
                        val currentSong =
                            getSongById(song.id)
                                ?: run {
                                    insert(song.toMediaMetadata())
                                    getSongById(song.id)
                                }
                                ?: return@withTransaction null
                        currentSong.song.toggleLike()
                    } ?: return@launch
                syncUtils.likeSong(requestedSong).onFailure { error ->
                    Timber.w(error, "Failed to update liked song ${song.id}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Failed to prepare liked song ${song.id}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val primaryActions =
        listOf(
            MenuAction(
                label = stringResource(R.string.play_next),
                icon = R.drawable.playlist_play,
                onClick = {
                    onDismiss()
                    playerConnection.playNext(song.toMediaItem())
                },
            ),
            MenuAction(
                label = stringResource(R.string.add_to_queue),
                icon = R.drawable.queue_music,
                onClick = {
                    onDismiss()
                    playerConnection.addToQueue(song.toMediaItem())
                },
            ),
            MenuAction(
                label = stringResource(R.string.start_radio),
                icon = R.drawable.radio,
                onClick = {
                    onDismiss()
                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                },
            ),
        )

    val quickActions =
        listOf(
            MenuAction(
                label = stringResource(R.string.add_to_playlist),
                icon = R.drawable.playlist_add,
                onClick = { showChoosePlaylistDialog = true },
            ),
            MenuAction(
                label = stringResource(R.string.share),
                icon = R.drawable.share,
                onClick = {
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, song.shareLink)
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            ),
            MenuAction(
                label = stringResource(if (isInSpeedDial) R.string.remove_from_speed_dial else R.string.pin_to_speed_dial),
                icon = if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                active = isInSpeedDial,
                onClick = {
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
            ),
        )

    MenuSheetLayout(
        hero = {
            MenuHero(
                title = song.title,
                style = MenuArtworkStyle.Song,
                thumbnails = songThumbnails,
                placeholderIcon = R.drawable.music_note,
                overline = stringResource(R.string.menu_type_song),
                subtitle = heroSubtitle,
                meta = heroMeta,
                trailing = {
                    MenuFavoriteToggle(
                        checked = isLiked,
                        onCheckedChange = { toggleLike() },
                    )
                },
            )
        },
        leadingContent = {
            MenuPrimaryActions(actions = primaryActions)
            MenuQuickActions(actions = quickActions)
        },
    ) {
        MenuSection(title = stringResource(R.string.menu_section_library)) {
            action(
                title = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
                icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
                tone = if (inLibrary) MenuRowTone.Active else MenuRowTone.Neutral,
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val requestedSong =
                                database.withTransaction {
                                    val currentSong =
                                        getSongById(song.id)?.song
                                            ?: song.toMediaMetadata().toSongEntity().also {
                                                insert(song.toMediaMetadata())
                                            }
                                    val shouldAdd = currentSong.inLibrary == null
                                    val now = LocalDateTime.now()
                                    currentSong.copy(
                                        liked = shouldAdd,
                                        likedDate = if (shouldAdd) now else null,
                                        inLibrary = if (shouldAdd) now else null,
                                    )
                                }
                            syncUtils.likeSong(requestedSong).onFailure { error ->
                                Timber.w(error, "Failed to update song library state ${song.id}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Timber.e(error, "Failed to prepare song library state ${song.id}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
            )
        }

        MenuSection(title = stringResource(R.string.menu_section_offline)) {
            downloadAction(
                state = download?.state,
                onDownload = {
                    database.transaction {
                        insert(song.toMediaMetadata())
                    }
                    val downloadRequest =
                        DownloadRequest
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
                onRemove = {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        song.id,
                        false,
                    )
                },
            )
            if (externalDownloaderEnabled) {
                action(
                    title = stringResource(R.string.open_with_downloader),
                    icon = R.drawable.download,
                    onClick = {
                        onDismiss()
                        val url = "https://music.youtube.com/watch?v=${song.id}"
                        when (context.openExternalDownloader(externalDownloaderPackage, url)) {
                            ExternalDownloaderLaunchResult.STARTED -> Unit
                            ExternalDownloaderLaunchResult.NOT_CONFIGURED -> {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.external_downloader_not_configured),
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                            ExternalDownloaderLaunchResult.NOT_INSTALLED -> {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.external_downloader_not_installed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
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
            song.album?.let { album ->
                action(
                    title = stringResource(R.string.view_album),
                    icon = R.drawable.album,
                    navigates = true,
                    onClick = {
                        navController.navigateToAlbum(album.id)
                        onDismiss()
                    },
                )
            }
            action(
                title = stringResource(R.string.details),
                icon = R.drawable.info,
                navigates = true,
                onClick = {
                    onDismiss()
                    bottomSheetPageState.show {
                        ShowMediaInfo(song.id)
                    }
                },
            )
        }
    }
}
