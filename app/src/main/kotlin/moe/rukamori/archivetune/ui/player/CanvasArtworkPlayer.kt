/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L
@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying)

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .header("User-Agent", CanvasPlaybackUserAgent)
                                .build(),
                        )
                    }

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    chain.proceed(
                        StreamClientUtils
                            .applyRequestProfile(
                                request.newBuilder(),
                                requestProfile,
                            ).build(),
                    )
                }.build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }
    val renderersFactory =
        remember(context) {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }
    val exoPlayer =
        remember(initial, mediaSourceFactory, renderersFactory) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .build()
                .apply {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = isPlaying
                }
        }

    LaunchedEffect(isPlaying) {
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary) return@LaunchedEffect

        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary) {
            delay(CanvasPlaybackStallCheckIntervalMs)

            val currentPosition = exoPlayer.currentPosition
            val playbackState = exoPlayer.playbackState
            val positionAdvanced = currentPosition != lastPosition
            val isActivelyRendering =
                playbackState == Player.STATE_READY &&
                    exoPlayer.isPlaying &&
                    positionAdvanced

            stalledForMs =
                if (isActivelyRendering) {
                    0L
                } else {
                    stalledForMs + CanvasPlaybackStallCheckIntervalMs
                }

            if (stalledForMs >= CanvasPlaybackStallTimeoutMs) {
                currentUrl = fallback
                isVideoReady = false
                return@LaunchedEffect
            }

            lastPosition = currentPosition
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                    exoPlayer.setCanvasPlayback(shouldPlay)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed")
                    val next =
                        when (currentUrl) {
                            primary -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    if (shouldPlay) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!shouldPlay) return
                    exoPlayer.setCanvasPlayback(isPlaying = true)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (shouldPlay && !playWhenReady) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (shouldPlay && !isPlaying) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    ContentFrame(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop

        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit

        else -> ContentScale.Fit
    }

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "CanvasPlayback"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
