/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.Artwork
import moe.rukamori.archivetune.design.components.Scrubber
import moe.rukamori.archivetune.design.components.SongMenuSheet
import moe.rukamori.archivetune.design.components.formatDuration
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode

/**
 * 全画面プレイヤー（Now Playing）。
 *
 * 背面はジャケットから抜いた2色のグラデーション。ジャケットが変わると
 * [androidx.compose.animation.animateColorAsState] で色が溶けて入れ替わる。
 *
 * 構成は上から下へ：
 * 下向き矢印／アルバム名／メニュー → ジャケット → 曲名 → スクラブバー →
 * 再生時間 → 搬送系 → 歌詞・音量・キュー。
 *
 * @param onDismiss 下向き矢印。プレイヤーを閉じる。
 * @param onOpenLyrics 歌詞ボタン。
 * @param onOpenQueue キューボタン。
 * 右上の「…」はこの中で曲メニュー（[SongMenuSheet]）を開く。
 */
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    // シャッフル・リピートの現在値。再生エンジン側（PlayerConnection）の状態をそのまま映す。
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val mediaId = mediaMetadata?.id

    // 右上の「…」で開く曲メニュー。曲が入れ替わったら閉じておく。
    var showSongMenu by remember(mediaId) { mutableStateOf(false) }

    var position by remember(mediaId) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var duration by remember(mediaId) {
        mutableLongStateOf(
            playerConnection.player.duration
                .takeIf { it > 0L && it != C.TIME_UNSET }
                ?: ((mediaMetadata?.duration ?: 0).toLong() * 1000L),
        )
    }
    // ユーザーがシーク先をまだプレイヤーが迎えていないとき、表示位置を保持する。
    var pendingSeek by remember(mediaId) { mutableStateOf<Long?>(null) }
    // ドラッグ中はポーリングが表示を上書きしないようにする。
    var scrubbing by remember(mediaId) { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(playerConnection.player.volume) }

    LaunchedEffect(mediaId, playbackState) {
        var unmatchedTicks = 0
        while (isActive) {
            delay(100)
            volume = playerConnection.player.volume

            if (scrubbing) continue

            val realPosition = playerConnection.player.currentPosition
            val target = pendingSeek
            when {
                target == null -> {
                    position = realPosition
                    unmatchedTicks = 0
                }

                abs(realPosition - target) < 500L || ++unmatchedTicks > 25 -> {
                    // 追いついた、または2.5秒たって追いつかない＝シーク自体が
                    // 無視された（バッファ切れ・エラー等）。表示を実測へ戻す。
                    pendingSeek = null
                    unmatchedTicks = 0
                    position = realPosition
                }

                else -> Unit // まだ届かない。表示した位置のまま待つ。
            }

            val playerDuration = playerConnection.player.duration
            if (playerDuration > 0L && playerDuration != C.TIME_UNSET) {
                duration = playerDuration
            } else {
                mediaMetadata?.duration?.takeIf { it > 0 }?.let { duration = it.toLong() * 1000L }
            }
        }
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0L, duration.coerceAtLeast(0L))
        position = clamped
        pendingSeek = clamped
        playerConnection.player.seekTo(clamped)
        if (playbackState == Player.STATE_READY) playerConnection.player.playWhenReady = true
    }

    val backdrop = rememberArtworkBackdrop(mediaId = mediaId, thumbnailUrl = mediaMetadata?.thumbnailUrl)

    // 背景の色は曲ごとに変わる。切り替わりは溶けるように行う。
    val animatedTop by animateColorAsState(targetValue = backdrop.top, animationSpec = tween(650), label = "backdropTop")
    val animatedBottom by animateColorAsState(targetValue = backdrop.bottom, animationSpec = tween(650), label = "backdropBottom")

    // 停止中はジャケットがわずかに沈む。動いているときと同じ表情にしない。
    val artworkScale by animateFloatAsState(targetValue = if (isPlaying) 1f else 0.965f, animationSpec = tween(260), label = "artworkScale")

    val title = mediaMetadata?.title.orEmpty()
    val artistNames = mediaMetadata?.artists.orEmpty().joinToString(", ") { it.name }
    val albumTitle = mediaMetadata?.album?.title.orEmpty()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(animatedTop, animatedBottom),
                    ),
                )
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = metrics.gutter.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 上端：閉じる／アルバム名／メニュー ──────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (albumTitle.isNotEmpty()) {
                    Text(
                        text = albumTitle,
                        style = type.footnote,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PlayerIconButton(onClick = { showSongMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = stringResource(R.string.more),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ── ジャケット ─────────────────────────────────────
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .widthIn(max = 380.dp)
                        .aspectRatio(1f)
                        // 影は内側に置き、外側の graphicsLayer でまとめて縮める。
                        // 逆にすると停止中にジャケットだけ縮んで影が残り、
                        // 周囲に暗い輪郭が残る。
                        .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(metrics.artworkCorner.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        ),
            )
        }

        // ── 曲名・アーティスト ──────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.md.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = type.title2,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (artistNames.isNotEmpty()) {
                Text(
                    text = artistNames,
                    style = type.callout,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                )
            }
        }

        // ── スクラブバーと時間 ──────────────────────────────
        Scrubber(
            positionMs = position,
            durationMs = duration,
            onSeek = { seekTo(it) },
            onDragStateChange = { scrubbing = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.md.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration((position / 1000L).toInt()) ?: "0:00",
                style = type.caption,
                color = Color.White.copy(alpha = 0.60f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration((duration / 1000L).toInt()) ?: "0:00",
                style = type.caption,
                color = Color.White.copy(alpha = 0.60f),
            )
        }

        // ── 搬送系 ───────────────────────────────────────
        // Apple Music 同様、再生ボタンの外側にシャッフルとリピートを並べる。
        // 有効なときだけアクセント色を付け、無効時は控えめな白で控える。
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerIconButton(
                onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
            ) {
                Icon(
                    painter = painterResource(if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle),
                    contentDescription =
                        stringResource(if (shuffleEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on),
                    tint = if (shuffleEnabled) palette.accent else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(26.dp),
                )
            }

            PlayerIconButton(onClick = { playerConnection.seekToPrevious() }) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.widget_previous),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            PlayPauseButton(isPlaying = isPlaying, onClick = { playerConnection.player.togglePlayPause() })

            PlayerIconButton(onClick = { playerConnection.seekToNext() }) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            PlayerIconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                Icon(
                    painter =
                        painterResource(
                            when {
                                repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                repeatMode == Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> R.drawable.repeat
                            },
                        ),
                    contentDescription =
                        stringResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) palette.accent else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // ── 歌詞・音量・キュー ──────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(onClick = onOpenLyrics) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = stringResource(R.string.lyrics),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Icon(
                painter = painterResource(if (volume <= 0.01f) R.drawable.volume_off else R.drawable.volume_up),
                contentDescription = stringResource(R.string.volume),
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(17.dp),
            )

            Scrubber(
                positionMs = (volume.coerceIn(0f, 1f) * 1000f).roundToLong(),
                durationMs = 1000L,
                onSeek = { ms ->
                    val newVolume = (ms / 1000f).coerceIn(0f, 1f)
                    volume = newVolume
                    playerConnection.player.volume = newVolume
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = metrics.sm.dp),
            )

            PlayerIconButton(onClick = onOpenQueue) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = stringResource(R.string.queue),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    // 右上の「…」から一覧と同じ曲メニューを開く。全画面プレイヤーから
    // アーティストやアルバムの画面へは移動しないため、再生に絞る操作だけ出す。
    mediaMetadata?.let { song ->
        if (showSongMenu) {
            SongMenuSheet(
                song = song,
                onDismiss = { showSongMenu = false },
                onPlayNext = {
                    playerConnection.playNext(song.toMediaItem())
                    showSongMenu = false
                },
                onAddToQueue = {
                    playerConnection.addToQueue(song.toMediaItem())
                    showSongMenu = false
                },
                onPlayRadio = {
                    playerConnection.playRadioFrom(song)
                    showSongMenu = false
                },
            )
        }
    }
}

/** 再生・一時停止の大きな円ボタン。白い面に濃いアイコンを載せる。 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .background(Color.White, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
            contentDescription = if (isPlaying) stringResource(R.string.widget_pause) else stringResource(R.string.play),
            tint = Color.Black,
            modifier = Modifier.size(if (isPlaying) 30.dp else 28.dp),
        )
    }
}

/** 枠を作らないアイコンボタン。タップ領域だけ確保し、面は出さない。 */
@Composable
private fun PlayerIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
