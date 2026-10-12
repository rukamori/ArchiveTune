/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.applePalette

/**
 * 再生位置を示すスクラブバー。
 *
 * Material の `Slider` は親指のつまみが大きく、画面の部品として浮いて見えるため
 * ここでは自前で描いている。狙いは3点。
 *
 *  - トラックは細い丸端の帯。掴んでいる間だけ太くなる（触っている感触を出す）
 *  - つまみは帯と同じ色。先端に溶け込ませ、独立した部品として立たせない
 *  - タップ位置へ即シークでき、押したまま動かせば追従する
 *
 * ドラッグ中は [onDragStateChange] へ通知する。呼び出し側はここで表示位置を
 * 自らの値に固定し（`sliderPosition`）、離すと再生中の位置へ戻す。
 *
 * **キーについて**：`pointerInput` のキーにはドラッグ状態を入れないこと。
 * `onDragStart` で状態を書き換えるとその書き換えがキー変更になり、
 * ジェスチャ本体が即座にキャンセルされてドラッグが一回で終わってしまう。
 * 可変値は `rememberUpdatedState` 経由で読み、キーは付けない。
 *
 * @param positionMs 再生位置。ドラッグ中は呼び出し側が追従した値を入れる。
 * @param durationMs 合計時間。0以下ならシークを受付けず、つまみも描かない。
 * @param onSeek 決定位置（ミリ秒）。ドラッグ中は連続、タップは一回。
 */
@Composable
fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChange: (Boolean) -> Unit = {},
) {
    val palette = applePalette()

    // ポインタ入力のラムダは初回だけ構築されるため、後から変わる値は
    // State 経由で読む。これでラムダが掴んだ参照でも常に最新値になる。
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val currentDurationMs by rememberUpdatedState(durationMs)

    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val trackFraction =
        if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
    val fraction = (dragFraction ?: trackFraction).coerceIn(0f, 1f)

    val trackThickness by
        animateDpAsState(
            targetValue = if (dragging) 6.dp else 4.dp,
            label = "scrubberTrack",
        )
    val thumbRadius by
        animateDpAsState(
            targetValue = if (dragging) 7.dp else 5.dp,
            label = "scrubberThumb",
        )

    val seekEnabled = durationMs > 0L

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { widthPx = it.width }
                // ドラッグ。座標は pointerInput の空間で来るため view 座標の変換は不要。
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (currentDurationMs <= 0L || widthPx <= 0) return@detectDragGestures
                            dragging = true
                            currentOnDragStateChange(true)
                            val f = (offset.x / widthPx).coerceIn(0f, 1f)
                            dragFraction = f
                            currentOnSeek((f * currentDurationMs).toLong())
                        },
                        onDragEnd = {
                            dragging = false
                            dragFraction = null
                            currentOnDragStateChange(false)
                        },
                        onDragCancel = {
                            dragging = false
                            dragFraction = null
                            currentOnDragStateChange(false)
                        },
                    ) { change, _ ->
                        if (currentDurationMs <= 0L || widthPx <= 0) return@detectDragGestures
                        change.consume()
                        val f = (change.position.x / widthPx).coerceIn(0f, 1f)
                        dragFraction = f
                        currentOnSeek((f * currentDurationMs).toLong())
                    }
                }
                // タップは一回だけ。位置を固定しないので、実際の再生位置が
                // 追いつくまでのわずかな間だけ遅れて動く（その方が跳ねない）。
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (currentDurationMs <= 0L || widthPx <= 0) return@detectTapGestures
                        val f = (offset.x / widthPx).coerceIn(0f, 1f)
                        currentOnSeek((f * currentDurationMs).toLong())
                    }
                },
    ) {
        if (!seekEnabled) return@Canvas

        val centerY = size.height / 2f
        val thicknessPx = trackThickness.toPx()
        val topY = centerY - thicknessPx / 2f
        val radius = thicknessPx / 2f
        val centerX = size.width * fraction

        drawRoundRect(
            color = palette.fill,
            topLeft = Offset(0f, topY),
            size = Size(size.width, thicknessPx),
            cornerRadius = CornerRadius(radius, radius),
        )
        if (centerX > 0.5f) {
            drawRoundRect(
                color = palette.accent,
                topLeft = Offset(0f, topY),
                size = Size(centerX, thicknessPx),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        drawCircle(
            color = palette.accent,
            radius = thumbRadius.toPx(),
            center = Offset(centerX, centerY),
        )
    }
}
