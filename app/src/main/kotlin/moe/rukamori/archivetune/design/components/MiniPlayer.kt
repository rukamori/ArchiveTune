/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.extensions.togglePlayPause

/**
 * Apple Music 風のミニプレイヤー。
 *
 * 浮遊する角丸の面に、アートワーク・曲名・アーティスト・再生ボタンを並べる。
 *
 * - **タップ** → 全画面プレイヤーへ
 * - **横スワイプ** → 前の曲／次の曲（しきい値まで引っ張ると発火し、指を離すと戻る）
 * - 再生／一時停止は `PlayerConnection.isPlaying` と同期
 *
 * 再生中の曲が1つもないときは描画しない。ダミーの曲を並べることはない。
 *
 * @param onOpenPlayer 全画面プレイヤーを開く。
 */
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val mediaMetadata by connection.mediaMetadata.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    val metadata = mediaMetadata ?: return

    val type = appleType()
    val metrics = appleMetrics()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(18.dp)

    // スワイプによる横移動。しきい値を超えると曲が変わり、離すとばねで戻る。
    var rawOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = rawOffsetPx,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "miniPlayerSwipe",
    )
    val thresholdPx = with(density) { 72.dp.toPx() }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffsetPx },
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        GlassSurface(shape = shape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(metrics.miniPlayerHeight.dp)
                        .pointerInput(metadata.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { rawOffsetPx = 0f },
                                onDragEnd = { rawOffsetPx = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    rawOffsetPx =
                                        (rawOffsetPx + dragAmount)
                                            .coerceIn(-thresholdPx * 1.2f, thresholdPx * 1.2f)
                                    if (abs(rawOffsetPx) >= thresholdPx) {
                                        // 右へ送ると前の曲、左へ送ると次の曲。
                                        if (rawOffsetPx > 0f) {
                                            connection.seekToPrevious()
                                        } else {
                                            connection.seekToNext()
                                        }
                                        rawOffsetPx = 0f
                                    }
                                },
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenPlayer() }
                        .padding(start = metrics.md.dp, end = metrics.sm.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = metadata.thumbnailUrl,
                    contentDescription = metadata.title,
                    cornerRadius = (metrics.artworkCorner - 2).coerceAtLeast(4).dp,
                    modifier = Modifier.size(46.dp),
                )

                Spacer(Modifier.width(metrics.md.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = metadata.title,
                        style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = applePalette().onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata.artists.joinToString(", ") { it.name },
                        style = type.footnote,
                        color = applePalette().labelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(metrics.sm.dp))

                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { connection.player.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = applePalette().onBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}
