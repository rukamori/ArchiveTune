/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ImmersiveDetailColors
import moe.rukamori.archivetune.ui.component.rememberImmersiveDetailColors
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.makeTimeString

@Immutable
private class TopResultLink(
    @DrawableRes val iconRes: Int,
    val text: String,
    val actionLabel: String?,
    val onClick: (() -> Unit)?,
)

@Immutable
private class TopResultFact(
    @DrawableRes val iconRes: Int,
    val text: String,
)

@Immutable
private class TopResultAction(
    @DrawableRes val iconRes: Int,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    pureBlack: Boolean,
    navController: NavController,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val animationsDisabled = LocalAnimationsDisabled.current
    val colors = rememberImmersiveDetailColors(item.thumbnail)
    val hasMenu = item.hasSearchResultMenu()
    val menuLabel = stringResource(R.string.more_options)
    val typeLabel =
        if (item is SongItem && item.isMusicVideo()) {
            stringResource(R.string.search_type_video)
        } else {
            searchResultTypeLabel(item)
        }
    val viewArtistLabel = stringResource(R.string.view_artist)
    val viewAlbumLabel = stringResource(R.string.view_album)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.search_pause)
    val shuffleLabel = stringResource(R.string.shuffle)
    val radioLabel = stringResource(R.string.search_action_radio)
    val openLabel = stringResource(R.string.search_action_open)
    val playNextLabel = stringResource(R.string.play_next)
    val queuedNextLabel = stringResource(R.string.search_queued_next)
    var queuedNext by remember(item.id) { mutableStateOf(false) }

    val openArtist: (String) -> Unit = { id ->
        navController.navigateToArtist(id)
        onLeave()
    }
    val openAlbum: (String) -> Unit = { id ->
        navController.navigateToAlbum(id)
        onLeave()
    }
    val links = topResultLinks(item, viewArtistLabel, viewAlbumLabel, openArtist, openAlbum)
    val facts = topResultFacts(item)
    val actions =
        when (item) {
            is SongItem -> {
                val showPause = isActive && isPlaying
                listOf(
                    TopResultAction(
                        iconRes = if (showPause) R.drawable.pause else R.drawable.play,
                        label = if (showPause) pauseLabel else playLabel,
                        onClick = onClick,
                    ),
                    TopResultAction(
                        iconRes = if (queuedNext) R.drawable.check else R.drawable.playlist_play,
                        label = if (queuedNext) queuedNextLabel else playNextLabel,
                        enabled = !queuedNext && !isActive,
                        onClick = {
                            playerConnection.playNext(item.toMediaItem())
                            queuedNext = true
                        },
                    ),
                )
            }

            is AlbumItem -> {
                listOf(
                    TopResultAction(R.drawable.play, playLabel) {
                        playerConnection.playQueue(YouTubeAlbumRadio(item.playlistId))
                        onLeave()
                    },
                    TopResultAction(R.drawable.album, viewAlbumLabel, onClick = onClick),
                )
            }

            is ArtistItem -> {
                val primary =
                    item.shuffleEndpoint?.let { endpoint ->
                        TopResultAction(R.drawable.shuffle, shuffleLabel) {
                            playerConnection.playQueue(YouTubeQueue(endpoint))
                            onLeave()
                        }
                    } ?: item.playEndpoint?.let { endpoint ->
                        TopResultAction(R.drawable.play, playLabel) {
                            playerConnection.playQueue(YouTubeQueue(endpoint))
                            onLeave()
                        }
                    }
                val radio =
                    item.radioEndpoint?.let { endpoint ->
                        TopResultAction(R.drawable.radio, radioLabel) {
                            playerConnection.playQueue(YouTubeQueue(endpoint))
                            onLeave()
                        }
                    }
                listOfNotNull(primary, radio).ifEmpty {
                    listOf(TopResultAction(R.drawable.artist, viewArtistLabel, onClick = onClick))
                }
            }

            is PlaylistItem -> {
                listOfNotNull(
                    item.playEndpoint?.let { endpoint ->
                        TopResultAction(R.drawable.play, playLabel) {
                            playerConnection.playQueue(YouTubeQueue.playlist(endpoint))
                            onLeave()
                        }
                    },
                    item.shuffleEndpoint?.let { endpoint ->
                        TopResultAction(R.drawable.shuffle, shuffleLabel) {
                            playerConnection.playQueue(YouTubeQueue.shuffledPlaylist(item.id, endpoint))
                            onLeave()
                        }
                    },
                ).ifEmpty {
                    listOf(TopResultAction(R.drawable.queue_music, openLabel, onClick = onClick))
                }
            }

            is PodcastItem -> {
                listOf(TopResultAction(R.drawable.navigate_next, openLabel, onClick = onClick))
            }

            is EpisodeItem -> {
                listOf(TopResultAction(R.drawable.play, playLabel, onClick = onClick))
            }
        }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) TopResultPressedScale else 1f,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "topResultScale",
    )
    val containerColor = if (pureBlack) lerp(Color.Black, colors.accent, PureBlackTint) else colors.backdrop

    Surface(
        shape = TopResultShape,
        color = containerColor,
        contentColor = colors.content,
        border = if (isActive) BorderStroke(2.dp, colors.accent) else null,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(TopResultShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClickLabel = if (hasMenu) menuLabel else null,
                    onLongClick =
                        if (hasMenu) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMenu()
                            }
                        } else {
                            null
                        },
                ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                TopResultArtwork(
                    item = item,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    haloColor = colors.accent.copy(alpha = HaloAlpha),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accent.copy(alpha = TonalAlpha),
                            contentColor = colors.content,
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        if (item.explicit) {
                            Icon(
                                painter = painterResource(R.drawable.explicit),
                                contentDescription = stringResource(R.string.search_explicit),
                                tint = colors.contentVariant,
                                modifier =
                                    Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (hasMenu) {
                            IconButton(onClick = onMenu) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = menuLabel,
                                    tint = colors.contentVariant,
                                )
                            }
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (facts.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            facts.forEach { fact ->
                                TopResultFactItem(fact = fact, colors = colors)
                            }
                        }
                    }
                }
            }

            if (links.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    links.forEach { link ->
                        TopResultLinkPill(link = link, colors = colors)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (links.isEmpty()) 16.dp else 8.dp),
            ) {
                actions.forEachIndexed { index, action ->
                    val content: @Composable () -> Unit = {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = action.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index == 0) {
                        Button(
                            onClick = action.onClick,
                            enabled = action.enabled,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
                                ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            content()
                        }
                    } else {
                        FilledTonalButton(
                            onClick = action.onClick,
                            enabled = action.enabled,
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.accent.copy(alpha = TonalAlpha),
                                    contentColor = colors.content,
                                    disabledContainerColor = colors.accent.copy(alpha = DisabledTonalAlpha),
                                    disabledContentColor = colors.contentVariant,
                                ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopResultArtwork(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    haloColor: Color,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val spinning = isActive && isPlaying && !animationsDisabled
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(tween(HaloSpinMillis, easing = LinearEasing)),
            )
        }
    }
    val haloShape =
        when (item) {
            is ArtistItem -> MaterialShapes.Cookie12Sided
            is AlbumItem -> MaterialShapes.Sunny
            is PlaylistItem -> MaterialShapes.Clover4Leaf
            else -> MaterialShapes.Cookie9Sided
        }.toShape()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(TopResultHaloSize),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation.value }
                    .background(haloColor, haloShape),
        )
        SearchResultThumbnail(
            item = item,
            isActive = isActive,
            isPlaying = isPlaying,
            size = TopResultArtworkSize,
            cornerRadius = 22.dp,
        )
    }
}

@Composable
private fun TopResultFactItem(
    fact: TopResultFact,
    colors: ImmersiveDetailColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(fact.iconRes),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = fact.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TopResultLinkPill(
    link: TopResultLink,
    colors: ImmersiveDetailColors,
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                painter = painterResource(link.iconRes),
                contentDescription = link.actionLabel,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = link.text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LinkTextMaxWidth),
            )
        }
    }
    val onClick = link.onClick
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.accent.copy(alpha = TonalAlpha),
            contentColor = colors.content,
        ) {
            content()
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.accent.copy(alpha = DisabledTonalAlpha),
                contentColor = colors.content,
            ) {
                content()
            }
        }
    }
}

private fun topResultLinks(
    item: YTItem,
    viewArtistLabel: String,
    viewAlbumLabel: String,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
): List<TopResultLink> {
    fun artistLink(
        name: String,
        id: String?,
    ) = TopResultLink(
        iconRes = R.drawable.artist,
        text = name,
        actionLabel = id?.let { viewArtistLabel },
        onClick = id?.let { artistId -> { onArtist(artistId) } },
    )

    return when (item) {
        is SongItem -> {
            item.artists.take(MaxArtistLinks).map { artistLink(it.name, it.id) } +
                listOfNotNull(
                    item.album?.let { album ->
                        TopResultLink(
                            iconRes = R.drawable.album,
                            text = album.name,
                            actionLabel = viewAlbumLabel,
                            onClick = { onAlbum(album.id) },
                        )
                    },
                )
        }

        is AlbumItem -> {
            item.artists.orEmpty().take(MaxArtistLinks).map { artistLink(it.name, it.id) }
        }

        is PlaylistItem -> {
            listOfNotNull(item.author?.let { artistLink(it.name, it.id) })
        }

        is PodcastItem -> {
            listOfNotNull(item.author?.let { artistLink(it.name, null) })
        }

        is EpisodeItem -> {
            listOfNotNull(
                item.podcast?.let {
                    TopResultLink(iconRes = R.drawable.mic, text = it.name, actionLabel = null, onClick = null)
                },
            )
        }

        is ArtistItem -> {
            emptyList()
        }
    }.filter { it.text.isNotBlank() }
}

private fun topResultFacts(item: YTItem): List<TopResultFact> =
    when (item) {
        is SongItem -> {
            listOfNotNull(
                item.duration?.let { TopResultFact(R.drawable.timer, makeTimeString(it * 1000L)) },
                item.viewCountText?.let { TopResultFact(R.drawable.trending_up, it) },
            )
        }

        is AlbumItem -> {
            listOfNotNull(item.year?.let { TopResultFact(R.drawable.calendar_today, it.toString()) })
        }

        is ArtistItem -> {
            listOfNotNull(
                item.monthlyListenerCountText?.let { TopResultFact(R.drawable.graphic_eq, it) },
                item.subscriberCountText?.let { TopResultFact(R.drawable.person, it) },
            )
        }

        is PlaylistItem -> {
            listOfNotNull(item.songCountText?.let { TopResultFact(R.drawable.queue_music, it) })
        }

        is PodcastItem -> {
            emptyList()
        }

        is EpisodeItem -> {
            listOfNotNull(
                item.dateText?.let { TopResultFact(R.drawable.calendar_today, it) },
                item.durationText?.let { TopResultFact(R.drawable.timer, it) },
            )
        }
    }.filter { it.text.isNotBlank() }

private fun SongItem.isMusicVideo(): Boolean {
    val type = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    return type == MUSIC_VIDEO_TYPE_OMV || type == MUSIC_VIDEO_TYPE_UGC
}

private val TopResultShape = RoundedCornerShape(28.dp)
private val TopResultArtworkSize = 112.dp
private val TopResultHaloSize = 128.dp
private val LinkTextMaxWidth = 200.dp
private const val MaxArtistLinks = 3
private const val TopResultPressedScale = 0.98f
private const val HaloAlpha = 0.32f
private const val TonalAlpha = 0.18f
private const val DisabledTonalAlpha = 0.10f
private const val PureBlackTint = 0.14f
private const val HaloSpinMillis = 14000
