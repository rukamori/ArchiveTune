/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.containerLabel
import moe.rukamori.archivetune.db.entities.formattedBitrate
import moe.rukamori.archivetune.db.entities.formattedFileSize
import moe.rukamori.archivetune.db.entities.formattedSampleRate
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.utils.makeTimeString
import java.text.DateFormat
import java.text.NumberFormat

internal val ExperimentalScreenGutter = 30.dp

private val LabStageCorner = 32.dp
private val LabGlyphSize = 76.dp
private val LabGlyphIconSize = 34.dp
private val LabHaloSize = 184.dp
private val BeaconGlyphSize = 40.dp
private val PanelCorner = 28.dp
private val PanelGlyphSize = 52.dp
private val TileCorner = 20.dp
private val ArtworkSize = 60.dp
private val BufferGaugeSize = 104.dp
private const val LabHaloRotationMillis = 24_000
private const val PanelGlyphRotationMillis = 14_000
private const val StatusPulseMillis = 900
private const val StatusPulseScale = 1.6f
private const val LabHaloAlpha = 0.09f
private const val NerdStatsSampleIntervalMillis = 500L
private const val PresenceClockIntervalMillis = 1_000L
private const val CopyFeedbackMillis = 2_000L
private const val HealthyBufferPercent = 70
private const val LowBufferPercent = 30

@Immutable
internal data class ExperimentBeacon(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val enabled: Boolean,
)

@Immutable
private data class PlaybackSample(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPercent: Int,
    val bufferedAheadMs: Long,
)

@Immutable
private data class NerdStat(
    @DrawableRes val icon: Int,
    val label: String,
    val value: String,
)

@Composable
internal fun ExperimentalLabStage(
    beacons: List<ExperimentBeacon>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val enabledCount = beacons.count { it.enabled }
    val enabledFraction = if (beacons.isEmpty()) 0f else enabledCount.toFloat() / beacons.size
    val morphProgress by animateFloatAsState(
        targetValue = enabledFraction,
        animationSpec = motion.slowSpatialSpec(),
        label = "labStageMorph",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val haloRotation = rememberSlowRotation(enabled = enabledCount > 0 && !animationsDisabled, periodMillis = LabHaloRotationMillis)
    val glyphContainer by animateColorAsState(
        targetValue = if (enabledCount > 0) colors.primaryContainer else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "labGlyphContainer",
    )
    val glyphContent by animateColorAsState(
        targetValue = if (enabledCount > 0) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "labGlyphContent",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LabStageCorner),
        color = colors.surfaceContainerHigh,
    ) {
        Box {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = LabHaloSize / 3, y = -LabHaloSize / 3)
                        .size(LabHaloSize)
                        .graphicsLayer { rotationZ = haloRotation.value }
                        .background(
                            color = colors.primary.copy(alpha = LabHaloAlpha),
                            shape = MaterialShapes.SoftBurst.toShape(),
                        ),
            )
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(LabGlyphSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = -haloRotation.value }
                                    .background(
                                        color = glyphContainer,
                                        shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)),
                                    ),
                        )
                        Icon(
                            painter = painterResource(R.drawable.experiment),
                            contentDescription = null,
                            tint = glyphContent,
                            modifier = Modifier.size(LabGlyphIconSize),
                        )
                    }
                    AnimatedContent(
                        targetState = enabledCount,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (
                                slideInVertically(motion.defaultSpatialSpec()) { it * direction / 2 } +
                                    fadeIn(motion.defaultEffectsSpec())
                            ) togetherWith (
                                slideOutVertically(motion.defaultSpatialSpec()) { -it * direction / 2 } +
                                    fadeOut(motion.fastEffectsSpec())
                            ) using SizeTransform(clip = false)
                        },
                        modifier = Modifier.weight(1f),
                        label = "labEnabledCount",
                    ) { count ->
                        Text(
                            text = stringResource(R.string.experimental_enabled_count, count, beacons.size),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = colors.onSurface,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.experimental_lab_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    beacons.forEach { beacon ->
                        ExperimentBeaconBadge(
                            beacon = beacon,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentBeaconBadge(
    beacon: ExperimentBeacon,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }
    val progress by animateFloatAsState(
        targetValue = if (beacon.enabled) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "beaconMorph",
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (beacon.enabled) colors.secondaryContainer else colors.surfaceContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "beaconSurface",
    )
    val glyphColor by animateColorAsState(
        targetValue = if (beacon.enabled) colors.primary else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "beaconGlyph",
    )
    val iconColor by animateColorAsState(
        targetValue = if (beacon.enabled) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "beaconIcon",
    )
    val label = stringResource(beacon.label)
    val stateText = stringResource(if (beacon.enabled) R.string.experiment_state_on else R.string.experiment_state_off)

    Surface(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = label
                stateDescription = stateText
            },
        shape = RoundedCornerShape(TileCorner),
        color = surfaceColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(BeaconGlyphSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = progress * 45f }
                            .background(glyphColor, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))),
                )
                Icon(
                    painter = painterResource(beacon.icon),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (beacon.enabled) colors.onSecondaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ExperimentalDiscordPanel(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isRunning by DiscordPresenceManager.isRunningFlow.collectAsStateWithLifecycle()
    val lastStart by DiscordPresenceManager.lastRpcStartTimeFlow.collectAsStateWithLifecycle()
    val lastEnd by DiscordPresenceManager.lastRpcEndTimeFlow.collectAsStateWithLifecycle()
    val start = lastStart
    val end = lastEnd
    val window = if (start != null && end != null && end > start) start to end else null

    DiagnosticsPanel(
        icon = R.drawable.discord,
        title = stringResource(R.string.discord_integration),
        subtitle =
            stringResource(
                if (isRunning) R.string.presence_manager_running else R.string.presence_manager_stopped,
            ),
        active = isRunning,
        glyphContainer = if (isRunning) colors.primaryContainer else colors.errorContainer,
        glyphContent = if (isRunning) colors.onPrimaryContainer else colors.onErrorContainer,
        status = {
            StatusPill(
                text = stringResource(if (isRunning) R.string.status_active else R.string.status_inactive),
                containerColor = if (isRunning) colors.primary else colors.error,
                contentColor = if (isRunning) colors.onPrimary else colors.onError,
                pulsing = isRunning,
            )
        },
        modifier = modifier,
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = window,
            contentKey = { it != null },
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                    SizeTransform(clip = false)
            },
            label = "discordPresenceWindow",
        ) { targetWindow ->
            if (targetWindow == null) {
                DiagnosticsNotice(
                    icon = R.drawable.timer,
                    text = stringResource(R.string.discord_debug_no_timestamps),
                )
            } else {
                PresenceWindow(
                    startMs = targetWindow.first,
                    endMs = targetWindow.second,
                    isRunning = isRunning,
                )
            }
        }
    }
}

@Composable
private fun PresenceWindow(
    startMs: Long,
    endMs: Long,
    isRunning: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val now by rememberWallClock(key = startMs to endMs)
    val totalMs = endMs - startMs
    val elapsedMs = (now - startMs).coerceIn(0L, totalMs)
    val fraction by animateFloatAsState(
        targetValue = elapsedMs.toFloat() / totalMs,
        animationSpec = motion.defaultSpatialSpec(),
        label = "presenceWindowProgress",
    )
    val clockFormat = rememberClockFormatter()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.discord_presence_window),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        LinearWavyProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (isRunning) colors.primary else colors.outline,
            trackColor = colors.surfaceContainerHighest,
            amplitude = { if (isRunning && !animationsDisabled) 1f else 0f },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonospaceValue(text = makeTimeString(elapsedMs))
            MonospaceValue(text = makeTimeString(totalMs))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NerdStatTile(
                stat =
                    NerdStat(
                        icon = R.drawable.play,
                        label = stringResource(R.string.last_start),
                        value = clockFormat.format(startMs),
                    ),
                modifier = Modifier.weight(1f),
            )
            NerdStatTile(
                stat =
                    NerdStat(
                        icon = R.drawable.pause,
                        label = stringResource(R.string.last_end),
                        value = clockFormat.format(endMs),
                    ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ExperimentalNerdStatsPanel(
    playerConnection: PlayerConnection?,
    modifier: Modifier = Modifier,
) {
    if (playerConnection == null) {
        val colors = MaterialTheme.colorScheme
        DiagnosticsPanel(
            icon = R.drawable.graphic_eq,
            title = stringResource(R.string.nerd_stats),
            subtitle = stringResource(R.string.real_time_playback_stats),
            active = false,
            glyphContainer = colors.surfaceContainerHighest,
            glyphContent = colors.onSurfaceVariant,
            status = {
                StatusPill(
                    text = stringResource(R.string.playback_state_idle),
                    containerColor = colors.surfaceContainerHighest,
                    contentColor = colors.onSurfaceVariant,
                    pulsing = false,
                )
            },
            modifier = modifier,
        ) {
            DiagnosticsNotice(
                icon = R.drawable.music_note,
                text = stringResource(R.string.no_track_playing),
            )
        }
        return
    }

    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val format by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()
    val sample by rememberPlaybackSample(playerConnection.player)

    val stateText =
        stringResource(
            when (playbackState) {
                Player.STATE_IDLE -> R.string.playback_state_idle
                Player.STATE_BUFFERING -> R.string.playback_state_buffering
                Player.STATE_READY -> R.string.playback_state_ready
                Player.STATE_ENDED -> R.string.playback_state_ended
                else -> R.string.playback_state_unknown
            },
        )
    val (stateContainer, stateContent) =
        when (playbackState) {
            Player.STATE_READY ->
                if (isPlaying) colors.primary to colors.onPrimary else colors.secondaryContainer to colors.onSecondaryContainer
            Player.STATE_BUFFERING -> colors.tertiary to colors.onTertiary
            Player.STATE_IDLE -> colors.surfaceContainerHighest to colors.onSurfaceVariant
            Player.STATE_ENDED -> colors.secondaryContainer to colors.onSecondaryContainer
            else -> colors.error to colors.onError
        }

    DiagnosticsPanel(
        icon = R.drawable.graphic_eq,
        title = stringResource(R.string.nerd_stats),
        subtitle = stringResource(R.string.real_time_playback_stats),
        active = isPlaying,
        glyphContainer = if (isPlaying) colors.primaryContainer else colors.secondaryContainer,
        glyphContent = if (isPlaying) colors.onPrimaryContainer else colors.onSecondaryContainer,
        status = {
            StatusPill(
                text = stateText,
                containerColor = stateContainer,
                contentColor = stateContent,
                pulsing = (playbackState == Player.STATE_READY && isPlaying) || playbackState == Player.STATE_BUFFERING,
            )
        },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = mediaMetadata,
            contentKey = { it?.id },
            transitionSpec = {
                (
                    slideInVertically(motion.defaultSpatialSpec()) { it / 4 } +
                        fadeIn(motion.defaultEffectsSpec())
                ) togetherWith fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
            },
            label = "nerdStatsTrack",
        ) { metadata ->
            if (metadata == null) {
                DiagnosticsNotice(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.no_track_playing),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NerdTrackStrip(metadata = metadata)
                    MediaIdRow(mediaId = metadata.id)
                    BufferHealthBlock(sample = sample, isPlaying = isPlaying)
                    FormatStatsBlock(
                        format = format,
                        speed = playbackParameters.speed,
                        pitch = playbackParameters.pitch,
                    )
                }
            }
        }
    }
}

@Composable
private fun NerdTrackStrip(metadata: MediaMetadata) {
    val colors = MaterialTheme.colorScheme
    val artworkShape = MaterialShapes.Cookie12Sided.toShape()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(ArtworkSize)
                    .clip(artworkShape)
                    .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            metadata.thumbnailUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.track_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = metadata.artists.joinToString { it.name }
            if (artists.isNotBlank()) {
                Text(
                    text = artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaIdRow(mediaId: String) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(mediaId) { mutableStateOf(false) }
    val mediaIdLabel = stringResource(R.string.media_id_label)
    val copiedMessage = stringResource(R.string.media_id_copied)

    LaunchedEffect(copied) {
        if (copied) {
            delay(CopyFeedbackMillis)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(TileCorner),
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.token),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = mediaIdLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = mediaId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(mediaIdLabel, mediaId)))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                        copied = true
                    }
                },
            ) {
                AnimatedContent(
                    targetState = copied,
                    transitionSpec = {
                        fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    },
                    label = "mediaIdCopyIcon",
                ) { isCopied ->
                    Icon(
                        painter = painterResource(if (isCopied) R.drawable.check else R.drawable.copy),
                        contentDescription =
                            stringResource(if (isCopied) R.string.media_id_copied else R.string.copy_media_id),
                        tint = if (isCopied) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BufferHealthBlock(
    sample: PlaybackSample,
    isPlaying: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val bufferProgress by animateFloatAsState(
        targetValue = sample.bufferedPercent / 100f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "bufferHealth",
    )
    val positionProgress by animateFloatAsState(
        targetValue = if (sample.durationMs > 0) sample.positionMs.toFloat() / sample.durationMs else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "playbackPosition",
    )
    val healthColor by animateColorAsState(
        targetValue =
            when {
                sample.bufferedPercent > HealthyBufferPercent -> colors.primary
                sample.bufferedPercent > LowBufferPercent -> colors.tertiary
                else -> colors.error
            },
        animationSpec = motion.defaultEffectsSpec(),
        label = "bufferHealthColor",
    )
    val percentText = stringResource(R.string.percentage_format, sample.bufferedPercent)

    Surface(
        shape = RoundedCornerShape(TileCorner),
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(BufferGaugeSize)
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    progress = { bufferProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = healthColor,
                    trackColor = colors.surfaceContainerHighest,
                )
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = colors.onSurface,
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.buffer_health_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { stateDescription = percentText },
                )
                Text(
                    text = stringResource(R.string.nerd_stats_buffer_ahead, makeTimeString(sample.bufferedAheadMs)),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(2.dp))
                if (sample.durationMs > 0) {
                    LinearWavyProgressIndicator(
                        progress = { positionProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.secondary,
                        trackColor = colors.surfaceContainerHighest,
                        amplitude = { if (isPlaying && !animationsDisabled) 1f else 0f },
                    )
                }
                MonospaceValue(
                    text =
                        if (sample.durationMs > 0) {
                            stringResource(
                                R.string.nerd_stats_position,
                                makeTimeString(sample.positionMs),
                                makeTimeString(sample.durationMs),
                            )
                        } else {
                            makeTimeString(sample.positionMs)
                        },
                )
            }
        }
    }
}

@Composable
private fun FormatStatsBlock(
    format: FormatEntity?,
    speed: Float,
    pitch: Float,
) {
    val motion = MaterialTheme.motionScheme
    val multiplierFormat = rememberDecimalFormatter(minFraction = 1, maxFraction = 2)
    val loudnessFormat = rememberDecimalFormatter(minFraction = 1, maxFraction = 1)
    val playbackStats =
        listOf(
            NerdStat(
                icon = R.drawable.slow_motion_video,
                label = stringResource(R.string.playback_speed_label),
                value = stringResource(R.string.nerd_stats_multiplier_value, multiplierFormat.format(speed)),
            ),
            NerdStat(
                icon = R.drawable.tune,
                label = stringResource(R.string.pitch),
                value = stringResource(R.string.nerd_stats_multiplier_value, multiplierFormat.format(pitch)),
            ),
        )

    AnimatedContent(
        targetState = format,
        contentKey = { it?.id },
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false)
        },
        label = "nerdStatsFormat",
    ) { targetFormat ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (targetFormat == null) {
                Surface(
                    shape = RoundedCornerShape(TileCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContainedLoadingIndicator(modifier = Modifier.size(44.dp))
                        Text(
                            text = stringResource(R.string.loading_format),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NerdStatGrid(stats = playbackStats)
            } else {
                NerdStatGrid(stats = formatStats(targetFormat, loudnessFormat) + playbackStats)
            }
        }
    }
}

@Composable
private fun formatStats(
    format: FormatEntity,
    loudnessFormat: NumberFormat,
): List<NerdStat> {
    val codec = format.codecLabel()
    val container = format.containerLabel()
    val codecValue =
        when {
            codec.isBlank() -> stringResource(R.string.unknown_codec)
            container.isBlank() || container.equals(codec, ignoreCase = true) -> codec
            else -> "$codec · $container"
        }
    return buildList {
        add(NerdStat(R.drawable.graphic_eq, stringResource(R.string.codec_label), codecValue))
        add(
            NerdStat(
                R.drawable.speed,
                stringResource(R.string.bitrate_label),
                format.formattedBitrate() ?: stringResource(R.string.unknown_bitrate),
            ),
        )
        add(
            NerdStat(
                R.drawable.waves,
                stringResource(R.string.sample_rate_label),
                format.formattedSampleRate() ?: stringResource(R.string.unknown_sample_rate),
            ),
        )
        add(
            NerdStat(
                R.drawable.storage,
                stringResource(R.string.content_length_label),
                if (format.contentLength > 0) format.formattedFileSize() else stringResource(R.string.unknown_content_length),
            ),
        )
        format.loudnessDb?.let { loudness ->
            add(
                NerdStat(
                    R.drawable.volume_up,
                    stringResource(R.string.loudness),
                    stringResource(R.string.nerd_stats_loudness_value, loudnessFormat.format(loudness)),
                ),
            )
        }
        add(NerdStat(R.drawable.info, stringResource(R.string.nerd_stats_itag_label), format.itag.toString()))
    }
}

@Composable
private fun NerdStatGrid(stats: List<NerdStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { stat ->
                    NerdStatTile(stat = stat, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NerdStatTile(
    stat: NerdStat,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = RoundedCornerShape(TileCorner),
        color = colors.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(stat.icon),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = stat.value,
                transitionSpec = {
                    (
                        slideInVertically(motion.fastSpatialSpec()) { it / 2 } +
                            fadeIn(motion.fastEffectsSpec())
                    ) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "nerdStatValue",
            ) { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    active: Boolean,
    glyphContainer: Color,
    glyphContent: Color,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelCorner),
        color = colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiagnosticsGlyph(
                    icon = icon,
                    active = active,
                    containerColor = glyphContainer,
                    contentColor = glyphContent,
                    size = PanelGlyphSize,
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) { heading() },
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status()
            }
            content()
        }
    }
}

@Composable
private fun DiagnosticsGlyph(
    @DrawableRes icon: Int,
    active: Boolean,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = motion.slowSpatialSpec(),
        label = "diagnosticsGlyphMorph",
    )
    val container by animateColorAsState(containerColor, motion.defaultEffectsSpec(), label = "diagnosticsGlyphContainer")
    val content by animateColorAsState(contentColor, motion.defaultEffectsSpec(), label = "diagnosticsGlyphContent")
    val rotation = rememberSlowRotation(enabled = active && !animationsDisabled, periodMillis = PanelGlyphRotationMillis)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation.value }
                    .background(container, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))),
        )
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    pulsing: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val container by animateColorAsState(containerColor, motion.defaultEffectsSpec(), label = "statusPillContainer")
    val content by animateColorAsState(contentColor, motion.defaultEffectsSpec(), label = "statusPillContent")
    val pulse: State<Float> =
        if (pulsing && !animationsDisabled) {
            rememberInfiniteTransition(label = "statusPillPulse").animateFloat(
                initialValue = 1f,
                targetValue = StatusPulseScale,
                animationSpec = infiniteRepeatable(tween(StatusPulseMillis), RepeatMode.Reverse),
                label = "statusPillPulseScale",
            )
        } else {
            remember { mutableFloatStateOf(1f) }
        }

    Surface(
        shape = CircleShape,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = pulse.value
                            scaleY = pulse.value
                            alpha = 2f - pulse.value
                        }.background(content, CircleShape),
            )
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                        SizeTransform(clip = false)
                },
                label = "statusPillText",
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsNotice(
    @DrawableRes icon: Int,
    text: String,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(TileCorner),
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(colors.surfaceContainerHighest, MaterialShapes.Cookie6Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonospaceValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun rememberSlowRotation(
    enabled: Boolean,
    periodMillis: Int,
): State<Float> =
    if (enabled) {
        rememberInfiniteTransition(label = "experimentalRotation").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
            label = "experimentalRotationAngle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

@Composable
private fun rememberPlaybackSample(player: Player): State<PlaybackSample> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = player.toPlaybackSample(), player, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = player.toPlaybackSample()
                delay(NerdStatsSampleIntervalMillis)
            }
        }
    }
}

private fun Player.toPlaybackSample(): PlaybackSample =
    PlaybackSample(
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
        bufferedPercent = bufferedPercentage.coerceIn(0, 100),
        bufferedAheadMs = totalBufferedDuration.coerceAtLeast(0L),
    )

@Composable
private fun rememberWallClock(key: Any?): State<Long> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = System.currentTimeMillis(), key, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(PresenceClockIntervalMillis)
            }
        }
    }
}

@Composable
private fun rememberClockFormatter(): DateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateFormat.getTimeInstance(DateFormat.MEDIUM, locale) }
}

@Composable
private fun rememberDecimalFormatter(
    minFraction: Int,
    maxFraction: Int,
): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, minFraction, maxFraction) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
        }
    }
}
