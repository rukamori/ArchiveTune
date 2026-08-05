/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Shared flat slider + volume row for the "Apple Music" player design.
 *
 * The player and the lyrics screen each used to carry their own volume slider — one a hand-drawn
 * Canvas with no thumb, the other an M3 Slider with a zero-size invisible thumb. They looked
 * subtly different and neither animated, so the fill snapped between values. Both now share this
 * component so the two screens read as one control.
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

private val AppleMusicSliderTouchHeight = 26.dp
private val AppleMusicSliderIdleTrackHeight = 6.dp
private val AppleMusicSliderPressedTrackHeight = 10.dp
private val AppleMusicVolumeIconSize = 18.dp

/**
 * Flat Apple-Music-style slider: rounded track, no thumb, tap or drag anywhere to set the value.
 *
 * The track grows while held and settles back when released, which is what gives the control its
 * "soft" feel. The fill is animated so a value arriving from outside (hardware volume keys, or
 * playback progress) glides instead of jumping, but it snaps to the finger while dragging — an
 * animated fill during a drag would lag behind the touch and feel broken.
 */
@Composable
internal fun AppleMusicFlatSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onFractionChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    idleTrackHeight: Dp = AppleMusicSliderIdleTrackHeight,
    pressedTrackHeight: Dp = AppleMusicSliderPressedTrackHeight,
    trackColor: Color = Color.White.copy(alpha = 0.28f),
    fillColor: Color = Color.White,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }

    // The gesture blocks below are keyed on `enabled` alone, so anything they capture would
    // otherwise be frozen at the composition that installed them. The seek bar's callback closes
    // over the track duration, which changes on every song change -- a captured copy would keep
    // converting taps against the previous song's length and seek to the wrong spot. Keying the
    // pointerInput on the callbacks instead would cancel an in-flight drag whenever the position
    // updated, so read them through rememberUpdatedState.
    val currentOnFractionChange by rememberUpdatedState(onFractionChange)
    val currentOnFractionChangeFinished by rememberUpdatedState(onFractionChangeFinished)

    val target = (if (dragging) dragFraction else fraction).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "appleMusicSliderFraction",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) pressedTrackHeight else idleTrackHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "appleMusicSliderTrackHeight",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0.85f,
        label = "appleMusicSliderFillAlpha",
    )

    Box(
        modifier =
            modifier
                .height(AppleMusicSliderTouchHeight)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        // Not setting `dragging` here: a tap has no press/release span to animate
                        // over, so the fill should spring to the new value from wherever it is. It
                        // reaches us through the caller's state on the next frame, so dragFraction
                        // deliberately stays untouched -- writing it while dragging is false would
                        // leave a stale value that the next real drag animates away from.
                        val tapped = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnFractionChange(tapped)
                        currentOnFractionChangeFinished()
                    }
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            currentOnFractionChange(dragFraction)
                        },
                        onDragEnd = {
                            dragging = false
                            currentOnFractionChangeFinished()
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            currentOnFractionChange(dragFraction)
                        },
                    )
                }.drawBehind {
                    val height = trackHeight.toPx()
                    val top = (size.height - height) / 2f
                    val radius = CornerRadius(height / 2f)
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, height),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = fillColor.copy(alpha = fillColor.alpha * fillAlpha),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * animatedFraction, height),
                        cornerRadius = radius,
                    )
                },
    )
}

/**
 * Volume row used by both the Apple Music player and its lyrics screen.
 *
 * The left glyph switches to the muted speaker at zero so silence is visible at a glance; neither
 * screen used to indicate mute at all.
 */
@Composable
internal fun AppleMusicVolumeRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White.copy(alpha = 0.55f),
    trackColor: Color = Color.White.copy(alpha = 0.28f),
    fillColor: Color = Color.White,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val muted = volume <= 0.001f
        Icon(
            painter =
                painterResource(
                    if (muted) R.drawable.player_volume_off else R.drawable.player_volume_min,
                ),
            contentDescription =
                stringResource(
                    if (muted) R.string.muted else R.string.minimum_volume,
                ),
            tint = iconTint,
            modifier = Modifier.size(AppleMusicVolumeIconSize),
        )
        Spacer(Modifier.width(12.dp))
        AppleMusicFlatSlider(
            fraction = volume,
            onFractionChange = onVolumeChange,
            trackColor = trackColor,
            fillColor = fillColor,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.player_volume_up),
            contentDescription = stringResource(R.string.maximum_volume),
            tint = iconTint,
            modifier = Modifier.size(AppleMusicVolumeIconSize),
        )
    }
}
