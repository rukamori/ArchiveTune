/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodModeEnabledKey
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.CanvasArtworkRefetchResult
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.MenuAction
import moe.rukamori.archivetune.ui.component.MenuArtworkStyle
import moe.rukamori.archivetune.ui.component.MenuHero
import moe.rukamori.archivetune.ui.component.MenuQuickActions
import moe.rukamori.archivetune.ui.component.MenuRowIcon
import moe.rukamori.archivetune.ui.component.MenuRowTone
import moe.rukamori.archivetune.ui.component.MenuSection
import moe.rukamori.archivetune.ui.component.MenuSheetLayout
import moe.rukamori.archivetune.ui.component.downloadAction
import moe.rukamori.archivetune.ui.component.menuEntrance
import moe.rukamori.archivetune.ui.component.rememberControlledSliderState
import moe.rukamori.archivetune.ui.component.toMenuAction
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.ExternalDownloaderLaunchResult
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.openExternalDownloader
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    sleepTimerEnabled: Boolean = false,
    onSleepTimerClick: (() -> Unit)? = null,
    onPlayNextFromQueue: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float -> deviceMusicVolumeController.setVolumeFraction(volume) }
        }
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (aodFeatureEnabled, onAodFeatureEnabledChange) = rememberPreference(AodModeEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (archiveTuneCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
    val playerDesignStyle by rememberEnumPreference(PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.V4)
    val lowDataModeActive = rememberLowDataModeActive()
    val canvasNetworkAllowed by playerConnection.canvasNetworkAllowed.collectAsStateWithLifecycle()
    val isCanvasArtworkRefetching by playerConnection.isCanvasArtworkRefetching.collectAsStateWithLifecycle()
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(mediaMetadata.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = mediaMetadata.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }
    val isLocalMedia =
        remember(librarySong?.song?.isLocal, mediaMetadata.id) {
            librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId()
        }
    val castPlayerMenuAction = rememberCastPlayerMenuAction()

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
                insert(mediaMetadata)
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
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
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
                        if (thumbUrl.isNullOrBlank()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigateToArtist(artist.id)
                                    showSelectArtistDialog = false
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }

    var showEqualizerDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            openSystemEqualizer = {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(
                            AudioEffect.EXTRA_AUDIO_SESSION,
                            playerConnection.localPlayer.audioSessionId,
                        )
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                }
            },
        )
    }

    val nowPlayingTitle =
        remember(mediaMetadata.title) {
            mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
        }

    val nowPlayingSubtitle =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = " • ") { it.name }
        }

    val playerThumbnails = remember(mediaMetadata.thumbnailUrl) { listOfNotNull(mediaMetadata.thumbnailUrl) }
    val playbackParameters by playerConnection.playbackParameters.collectAsState()

    val quickActions =
        buildList {
            castPlayerMenuAction?.let { add(it.toMenuAction()) }
            onSleepTimerClick?.let { onClick ->
                add(
                    MenuAction(
                        label = stringResource(R.string.sleep_timer),
                        icon = R.drawable.bedtime,
                        active = sleepTimerEnabled,
                        onClick = {
                            onDismiss()
                            onClick()
                        },
                    ),
                )
            }
            if (!isLocalMedia && !mediaMetadata.isPodcast) {
                add(
                    MenuAction(
                        label = stringResource(R.string.start_radio),
                        icon = R.drawable.radio,
                        onClick = {
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        },
                    ),
                )
            }
            if (
                !isLocalMedia &&
                isQueueTrigger != true &&
                archiveTuneCanvasEnabled &&
                !lowDataModeActive &&
                canvasNetworkAllowed &&
                playerDesignStyle != PlayerDesignStyle.V5
            ) {
                add(
                    MenuAction(
                        label = stringResource(R.string.refetch_canvas),
                        icon = R.drawable.sync,
                        busy = isCanvasArtworkRefetching,
                        enabled = !isCanvasArtworkRefetching,
                        onClick = {
                            coroutineScope.launch {
                                when (
                                    playerConnection.refetchCanvasArtwork(
                                        metadata = mediaMetadata,
                                        requireVertical = playerDesignStyle == PlayerDesignStyle.V7,
                                    )
                                ) {
                                    CanvasArtworkRefetchResult.Success -> onDismiss()
                                    CanvasArtworkRefetchResult.Failure -> {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.canvas_refetch_failed,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }

                                    CanvasArtworkRefetchResult.AlreadyRunning -> Unit
                                }
                            }
                        },
                    ),
                )
            }
            add(
                MenuAction(
                    label = stringResource(R.string.add_to_playlist),
                    icon = R.drawable.playlist_add,
                    onClick = { showChoosePlaylistDialog = true },
                ),
            )
            add(
                MenuAction(
                    label = stringResource(if (isInSpeedDial) R.string.remove_from_speed_dial else R.string.pin_to_speed_dial),
                    icon = if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                    active = isInSpeedDial,
                    onClick = {
                        val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                        onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                        onDismiss()
                    },
                ),
            )
            if (isLocalMedia) {
                add(
                    MenuAction(
                        label = stringResource(R.string.share),
                        icon = R.drawable.share,
                        onClick = {
                            shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
                            onDismiss()
                        },
                    ),
                )
            } else {
                add(
                    MenuAction(
                        label = stringResource(R.string.copy_link),
                        icon = R.drawable.link,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip =
                                ClipData.newPlainText(
                                    context.getString(R.string.copy_link),
                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    ),
                )
                add(
                    MenuAction(
                        label = stringResource(R.string.music_together),
                        icon = R.drawable.fire,
                        onClick = {
                            onDismiss()
                            playerBottomSheetState.collapseSoft()
                            navController.navigate("settings/music_together")
                        },
                    ),
                )
            }
            if (isQueueTrigger != true) {
                add(
                    MenuAction(
                        label = stringResource(R.string.aod_mode),
                        icon = R.drawable.bedtime,
                        active = aodFeatureEnabled,
                        onClick = {
                            if (!aodFeatureEnabled) {
                                onAodFeatureEnabledChange(true)
                            }
                            playerConnection.aodModeEnabled.value = true
                            onDismiss()
                        },
                    ),
                )
            }
        }

    MenuSheetLayout(
        hero = {
            MenuHero(
                title = nowPlayingTitle,
                style = MenuArtworkStyle.Song,
                thumbnails = playerThumbnails,
                placeholderIcon = R.drawable.music_note,
                overline = stringResource(R.string.now_playing),
                subtitle = nowPlayingSubtitle,
                meta = mediaMetadata.album?.title,
            )
        },
        leadingContent = {
            if (isQueueTrigger != true) {
                PlayerVolumeCard(
                    volume = deviceMusicVolumeController.volumeFraction,
                    onVolumeChange = onPlayerVolumeChange,
                )
            }
            MenuQuickActions(actions = quickActions)
        },
    ) {
        if (isQueueTrigger == true) {
            MenuSection(title = stringResource(R.string.queue)) {
                if (onPlayNextFromQueue != null) {
                    action(
                        title = stringResource(R.string.play_next),
                        icon = R.drawable.playlist_play,
                        onClick = {
                            onPlayNextFromQueue()
                            onDismiss()
                        },
                    )
                }
                if (onRemoveFromQueue != null) {
                    action(
                        title = stringResource(R.string.remove_from_queue),
                        icon = R.drawable.delete,
                        tone = MenuRowTone.Destructive,
                        onClick = {
                            onRemoveFromQueue()
                            onDismiss()
                        },
                    )
                }
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
                            onDismiss()
                            playerBottomSheetState.collapseSoft()
                            navController.navigateToArtist(splitArtists[0].originalArtist!!.id)
                        } else {
                            showSelectArtistDialog = true
                        }
                    },
                )
            }
            mediaMetadata.album?.let { album ->
                action(
                    title = stringResource(R.string.view_album),
                    icon = R.drawable.album,
                    navigates = true,
                    onClick = {
                        onDismiss()
                        playerBottomSheetState.collapseSoft()
                        navController.navigateToAlbum(album.id)
                    },
                )
            }
            action(
                title = stringResource(R.string.details),
                icon = R.drawable.info,
                navigates = true,
                onClick = {
                    onShowDetailsDialog()
                    onDismiss()
                },
            )
        }

        if (!isLocalMedia) {
            MenuSection(title = stringResource(R.string.menu_section_offline)) {
                downloadAction(
                    state = download?.state,
                    onDownload = {
                        database.transaction {
                            insert(mediaMetadata)
                        }
                        val downloadRequest =
                            DownloadRequest
                                .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                .setCustomCacheKey(mediaMetadata.id)
                                .setData(mediaMetadata.title.toByteArray())
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
                            mediaMetadata.id,
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
                            val url = "https://music.youtube.com/watch?v=${mediaMetadata.id}"
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
        }

        if (isQueueTrigger != true) {
            MenuSection(title = stringResource(R.string.menu_section_sound)) {
                action(
                    title = stringResource(R.string.equalizer),
                    icon = R.drawable.equalizer,
                    onClick = { showEqualizerDialog = true },
                )
                action(
                    title = stringResource(R.string.tempo_and_pitch),
                    icon = R.drawable.speed,
                    supporting =
                        "x${formatMultiplier(playbackParameters.speed)} • x${formatMultiplier(playbackParameters.pitch)}",
                    onClick = { showPitchTempoDialog = true },
                )
            }
        }
    }
}

@Composable
private fun PlayerVolumeCard(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeVolume = volume.coerceIn(0f, 1f)
    val isMuted = safeVolume <= 0f

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .fillMaxWidth()
                .menuEntrance(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MenuRowIcon(
                    icon = if (isMuted) R.drawable.volume_off else R.drawable.volume_up,
                    tone = if (isMuted) MenuRowTone.Neutral else MenuRowTone.Active,
                )

                Text(
                    text = stringResource(R.string.volume),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${(safeVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            VolumeSliderL(
                value = safeVolume,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeSliderL(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeValue = value.coerceIn(0f, 1f)
    var sliderValue by remember { mutableFloatStateOf(safeValue) }
    var isDragging by remember { mutableStateOf(false) }
    val volumeLabel = stringResource(R.string.volume)

    LaunchedEffect(safeValue) {
        if (!isDragging) sliderValue = safeValue
    }
    val sliderState =
        rememberControlledSliderState(
            value = sliderValue,
            valueRange = 0f..1f,
        )

    Slider(
        state = sliderState,
        onValueChange = { updated ->
            isDragging = true
            val coerced = updated.coerceIn(0f, 1f)
            sliderState.value = coerced
            sliderValue = coerced
            onValueChange(coerced)
        },
        onValueChangeFinished = { isDragging = false },
        modifier = modifier.semantics { contentDescription = volumeLabel },
        colors =
            SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
    )
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
    val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

    var tempo by remember {
        mutableFloatStateOf(initialSpeed.safeCoerceIn(TempoMin, TempoMax, fallback = 1f))
    }

    var pitch by remember {
        mutableFloatStateOf(initialPitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f))
    }

    var pitchMode by rememberSaveable {
        mutableStateOf(
            if (isPitchSemitoneAligned(pitch)) PitchMode.Semitones else PitchMode.Multiplier,
        )
    }

    val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
        playerConnection.player.playbackParameters =
            PlaybackParameters(
                speed.coerceIn(TempoMin, TempoMax),
                pitchMultiplier.coerceIn(PitchMin, PitchMax),
            )
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    pitch = 1f
                    applyPlaybackParameters(tempo, pitch)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.tempo),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "x${formatMultiplier(tempo)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        enabled = tempo > TempoMin,
                        onClick = {
                            tempo = (tempo - 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.remove),
                            contentDescription = null,
                        )
                    }

                    Slider(
                        value = multiplierToSlider(tempo),
                        onValueChange = { slider ->
                            val updated = sliderToMultiplier(slider).quantize(0.01f)
                            if (abs(updated - tempo) >= 0.005f) {
                                tempo = updated
                                applyPlaybackParameters(tempo, pitch)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(),
                    )

                    IconButton(
                        enabled = tempo < TempoMax,
                        onClick = {
                            tempo = (tempo + 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                    presets.forEach { preset ->
                        val selected = abs(tempo - preset) < 0.005f
                        FilterChip(
                            selected = selected,
                            onClick = {
                                tempo = preset
                                applyPlaybackParameters(tempo, pitch)
                            },
                            label = { Text("x${formatMultiplier(preset)}") },
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discover_tune),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.pitch),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text =
                            when (pitchMode) {
                                PitchMode.Semitones -> {
                                    val semitones = pitchToSemitones(pitch)
                                    "${if (semitones > 0) "+" else ""}$semitones"
                                }

                                PitchMode.Multiplier -> {
                                    "x${formatMultiplier(pitch)}"
                                }
                            },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = pitchMode == PitchMode.Semitones,
                        onClick = { pitchMode = PitchMode.Semitones },
                        label = { Text(stringResource(R.string.pitch_mode_semitones_short)) },
                    )
                    FilterChip(
                        selected = pitchMode == PitchMode.Multiplier,
                        onClick = { pitchMode = PitchMode.Multiplier },
                        label = { Text(stringResource(R.string.pitch_mode_multiplier_short)) },
                    )
                }

                when (pitchMode) {
                    PitchMode.Semitones -> {
                        val currentSemitones = pitchToSemitones(pitch)
                        Slider(
                            value = currentSemitones.toFloat(),
                            onValueChange = { slider ->
                                val semitones = slider.roundToInt().coerceIn(-12, 12)
                                val updated = semitonesToPitch(semitones)
                                if (abs(updated - pitch) >= 0.0005f) {
                                    pitch = updated
                                    applyPlaybackParameters(tempo, pitch)
                                }
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(-12, -7, -5, 0, 5, 7, 12)
                            presets.forEach { preset ->
                                val selected = currentSemitones == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = semitonesToPitch(preset)
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("${if (preset > 0) "+" else ""}$preset") },
                                )
                            }
                        }
                    }

                    PitchMode.Multiplier -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                enabled = pitch > PitchMin,
                                onClick = {
                                    pitch = (pitch - 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.remove),
                                    contentDescription = null,
                                )
                            }

                            Slider(
                                value = multiplierToSlider(pitch),
                                onValueChange = { slider ->
                                    val updated = sliderToMultiplier(slider).quantize(0.01f)
                                    if (abs(updated - pitch) >= 0.005f) {
                                        pitch = updated
                                        applyPlaybackParameters(tempo, pitch)
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(),
                            )

                            IconButton(
                                enabled = pitch < PitchMax,
                                onClick = {
                                    pitch = (pitch + 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add),
                                    contentDescription = null,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                            presets.forEach { preset ->
                                val selected = abs(pitch - preset) < 0.005f
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = preset
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("x${formatMultiplier(preset)}") },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

private enum class PitchMode {
    Semitones,
    Multiplier,
}

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.safeCoerceIn(
    min: Float,
    max: Float,
    fallback: Float,
): Float {
    val safe = if (this.isFinite()) this else fallback
    return safe.coerceIn(min, max)
}

private fun Float.quantize(step: Float): Float {
    if (step <= 0f) return this
    return (round(this / step) * step).coerceAtLeast(0f)
}

private fun pitchToSemitones(pitch: Float): Int {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    return (12f * log2(safePitch)).roundToInt().coerceIn(-12, 12)
}

private fun semitonesToPitch(semitones: Int): Float = 2f.pow(semitones.toFloat() / 12f).coerceIn(PitchMin, PitchMax)

private fun isPitchSemitoneAligned(pitch: Float): Boolean {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    val semitones = (12f * log2(safePitch)).roundToInt()
    val reconstructed = 2f.pow(semitones.toFloat() / 12f)
    return abs(reconstructed - pitch) < 0.0015f
}

private fun formatMultiplier(multiplier: Float): String = String.format("%.2f", multiplier)

private fun sliderToMultiplier(slider: Float): Float {
    val t = slider.coerceIn(0f, 1f)
    val y = (t - 0.5f) * 2f
    val curve = 2.2f
    val absY = abs(y).pow(curve)
    val shaped =
        when {
            y > 0f -> absY
            y < 0f -> -absY
            else -> 0f
        }
    val exponent = if (y < 0f) 2f * shaped else shaped
    return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
    val m = multiplier.coerceIn(TempoMin, TempoMax)
    val log = log2(m)
    val curve = 2.2f
    val shaped = if (m < 1f) (log / 2f) else log
    val absShaped = abs(shaped).pow(1f / curve)
    val y =
        when {
            shaped > 0f -> absShaped
            shaped < 0f -> -absShaped
            else -> 0f
        }
    return (0.5f + y / 2f).coerceIn(0f, 1f)
}
