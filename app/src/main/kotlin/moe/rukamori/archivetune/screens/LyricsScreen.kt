/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel

/**
 * 全画面の同期歌詞。
 *
 * 歌詞そのものと取得は既存の [LyricsRenderViewModel] に任せる（歌詞取得まわりは
 * 触らない方針）。ここに足すのは表示と同期だけ。
 *
 * 同期は行単位。再生位置が進むと「開始時刻が最も新しい行」がアクティブになり、
 * その行だけ白く大きく、それ以外は薄くなる。行が変わったら自動で中央へ送る。
 *
 * 行をタップするとその行の先頭へシークする。歌詞側からも再生へ戻せる。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val viewModel: LyricsRenderViewModel = hiltViewModel()
    val metrics = appleMetrics()
    val type = appleType()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val renderState by viewModel.state.collectAsState()

    val mediaId = mediaMetadata?.id

    var position by remember(mediaId) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var pendingSeek by remember(mediaId) { mutableStateOf<Long?>(null) }

    // 歌詞の読み込みは曲ごとに1回だけ。duration が後から確定し直しても
    // 再バインドすると取得がやり直されるため、1曲で固定する。
    LaunchedEffect(mediaId) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        val resolved =
            mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L)
                ?: playerConnection.player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                ?: 0L
        viewModel.bind(mediaId, resolved)
    }

    LaunchedEffect(mediaId, playbackState) {
        var unmatchedTicks = 0
        while (isActive) {
            delay(100)
            val realPosition = playerConnection.player.currentPosition
            val target = pendingSeek
            when {
                target == null -> {
                    position = realPosition
                    unmatchedTicks = 0
                }

                abs(realPosition - target) < 500L || ++unmatchedTicks > 25 -> {
                    pendingSeek = null
                    unmatchedTicks = 0
                    position = realPosition
                }

                else -> Unit
            }
        }
    }

    fun seekTo(ms: Long) {
        pendingSeek = ms
        position = ms
        playerConnection.player.seekTo(ms)
        if (playbackState == Player.STATE_READY) playerConnection.player.playWhenReady = true
    }

    val backdrop = rememberArtworkBackdrop(mediaId = mediaId, thumbnailUrl = mediaMetadata?.thumbnailUrl)

    val lines =
        when (val prepared = renderState) {
            is LyricsRenderScreenState.Success -> prepared.lyrics.lines
            else -> emptyList()
        }

    // 再生位置以下の最後の行。まだ1行目より前なら0をアクティブにする（イントロ扱い）。
    val currentIndex =
        remember(position, lines) {
            if (lines.isEmpty()) {
                -1
            } else {
                lines.indexOfLast { it.startMs <= position }.coerceAtLeast(0)
            }
        }

    val listState = rememberLazyListState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(currentIndex, viewportHeight) {
        if (currentIndex !in lines.indices || viewportHeight <= 0) return@LaunchedEffect
        val offset = with(density) { -(viewportHeight / 2) }
        listState.animateScrollToItem(currentIndex, offset)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(backdrop.top, backdrop.bottom),
                    ),
                )
                .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = metrics.gutter.dp - metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
        }

        LargeTitleHeader(
            title = stringResource(R.string.lyrics),
            subtitle = mediaMetadata?.title,
        )

        when (val state = renderState) {
            is LyricsRenderScreenState.Loading -> {
                LoadingState(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Empty -> {
                EmptyState(
                    title = stringResource(R.string.lyrics),
                    message = stringResource(R.string.lyrics_not_found),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Error -> {
                ErrorState(
                    title = stringResource(R.string.lyrics),
                    message = stringResource(R.string.lyrics_not_found),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { viewportHeight = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(
                        items = state.lyrics.lines,
                        key = { index, _ -> index },
                    ) { index, line ->
                        val isCurrent = index == currentIndex
                        val color by
                            animateColorAsState(
                                targetValue =
                                    if (isCurrent) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.34f)
                                    },
                                animationSpec = tween(320),
                                label = "lyricLine",
                            )

                        val text = line.text.ifEmpty {
                            if (line.isInstrumental) "—" else ""
                        }
                        if (text.isNotEmpty()) {
                            Text(
                                text = text,
                                style =
                                    if (isCurrent) {
                                        type.title3
                                    } else {
                                        type.body
                                    },
                                color = color,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { seekTo(line.startMs) }
                                        .padding(
                                            horizontal = metrics.gutter.dp,
                                            vertical = metrics.sm.dp,
                                        ),
                            )
                        }

                        if (line.translation != null) {
                            Text(
                                text = line.translation.orEmpty(),
                                style = type.subhead,
                                color = Color.White.copy(alpha = if (isCurrent) 0.72f else 0.28f),
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = metrics.gutter.dp,
                                            end = metrics.gutter.dp,
                                            bottom = metrics.sm.dp,
                                        ),
                            )
                        }
                    }
                }
            }
        }
    }
}
