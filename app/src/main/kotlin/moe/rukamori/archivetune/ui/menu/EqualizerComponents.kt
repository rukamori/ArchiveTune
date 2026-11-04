/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

internal enum class FaderOrientation {
    Vertical,
    Horizontal,
}

@Composable
internal fun EqualizerFader(
    value: Int,
    valueRange: IntRange,
    label: String,
    valueDescription: String,
    enabled: Boolean,
    orientation: FaderOrientation,
    bipolar: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    dragStep: Int = 10,
    keyStep: Int = 100,
    resetValue: Int? = null,
    resetLabel: String? = null,
    onActiveChange: (Boolean) -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnActiveChange by rememberUpdatedState(onActiveChange)
    val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
    val zeroValue = if (bipolar) 0.coerceIn(valueRange.first, valueRange.last) else valueRange.first

    var dragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val targetFraction = (value - valueRange.first).toFloat() / span
    val fraction = remember { Animatable(targetFraction) }
    LaunchedEffect(targetFraction, dragging) {
        if (dragging) fraction.snapTo(targetFraction) else fraction.animateTo(targetFraction, motion.defaultSpatialSpec())
    }
    val handleThickness by animateDpAsState(
        targetValue = if (dragging) FaderHandleThicknessActive else FaderHandleThickness,
        animationSpec = motion.fastSpatialSpec(),
        label = "faderHandle",
    )
    val activeColor by animateColorAsState(
        if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.38f),
        motion.defaultEffectsSpec(),
        label = "faderActive",
    )
    val inactiveColor by animateColorAsState(
        if (enabled) colors.secondaryContainer else colors.onSurface.copy(alpha = 0.12f),
        motion.defaultEffectsSpec(),
        label = "faderInactive",
    )
    val tickColor by animateColorAsState(
        if (enabled) colors.outline else colors.onSurface.copy(alpha = 0.24f),
        motion.defaultEffectsSpec(),
        label = "faderTick",
    )

    fun emit(raw: Float) {
        val snapped = ((raw / dragStep).roundToInt() * dragStep).coerceIn(valueRange.first, valueRange.last)
        val previous = latestValue
        if (snapped == previous) return
        val crossedZero = bipolar && (previous - zeroValue).sign != (snapped - zeroValue).sign
        val hitEdge = (snapped == valueRange.first || snapped == valueRange.last)
        if (crossedZero || hitEdge) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        latestOnValueChange(snapped)
    }

    fun startDrag() {
        dragAccumulator = latestValue.toFloat()
        dragging = true
        latestOnActiveChange(true)
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        latestOnActiveChange(false)
        latestOnValueChangeFinished()
    }

    val gestureModifier =
        if (!enabled) {
            Modifier
        } else {
            Modifier
                .pointerInput(orientation, valueRange, layoutDirection) {
                    val length = if (orientation == FaderOrientation.Vertical) size.height else size.width
                    val perPixel = span / length.coerceAtLeast(1).toFloat()
                    if (orientation == FaderOrientation.Vertical) {
                        detectVerticalDragGestures(
                            onDragStart = { startDrag() },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        ) { change, delta ->
                            change.consume()
                            dragAccumulator = (dragAccumulator - delta * perPixel).coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat())
                            emit(dragAccumulator)
                        }
                    } else {
                        val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                        detectHorizontalDragGestures(
                            onDragStart = { startDrag() },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        ) { change, delta ->
                            change.consume()
                            dragAccumulator = (dragAccumulator + delta * direction * perPixel).coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat())
                            emit(dragAccumulator)
                        }
                    }
                }.then(
                    if (resetValue != null) {
                        Modifier.pointerInput(resetValue) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (latestValue != resetValue) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        latestOnValueChange(resetValue)
                                        latestOnValueChangeFinished()
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ).onKeyEvent { event ->
                    val increase =
                        when (event.key) {
                            Key.DirectionUp -> true
                            Key.DirectionDown -> false
                            Key.DirectionRight -> layoutDirection == LayoutDirection.Ltr
                            Key.DirectionLeft -> layoutDirection == LayoutDirection.Rtl
                            else -> return@onKeyEvent false
                        }
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val next = (latestValue + if (increase) keyStep else -keyStep).coerceIn(valueRange.first, valueRange.last)
                            if (next != latestValue) latestOnValueChange(next)
                            true
                        }

                        KeyEventType.KeyUp -> {
                            latestOnValueChangeFinished()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusable()
        }

    Canvas(
        modifier =
            modifier
                .semantics {
                    contentDescription = label
                    stateDescription = valueDescription
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = value.toFloat(),
                            range = valueRange.first.toFloat()..valueRange.last.toFloat(),
                        )
                    if (!enabled) {
                        disabled()
                    } else {
                        setProgress { target ->
                            val next = target.roundToInt().coerceIn(valueRange.first, valueRange.last)
                            if (next != latestValue) {
                                latestOnValueChange(next)
                                latestOnValueChangeFinished()
                            }
                            true
                        }
                        if (resetValue != null && resetLabel != null) {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(resetLabel) {
                                        latestOnValueChange(resetValue)
                                        latestOnValueChangeFinished()
                                        true
                                    },
                                )
                        }
                    }
                }.then(gestureModifier),
    ) {
        drawFader(
            orientation = orientation,
            rtl = layoutDirection == LayoutDirection.Rtl,
            fraction = fraction.value.coerceIn(0f, 1f),
            zeroFraction = if (bipolar) (zeroValue - valueRange.first).toFloat() / span else null,
            handleThickness = handleThickness.toPx(),
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            tickColor = tickColor,
        )
    }
}

private fun DrawScope.drawFader(
    orientation: FaderOrientation,
    rtl: Boolean,
    fraction: Float,
    zeroFraction: Float?,
    handleThickness: Float,
    activeColor: Color,
    inactiveColor: Color,
    tickColor: Color,
) {
    val vertical = orientation == FaderOrientation.Vertical
    val length = if (vertical) size.height else size.width
    val cross = if (vertical) size.width else size.height
    val trackCross = cross * FaderTrackCrossFraction
    val trackStart = (cross - trackCross) / 2f
    val trackEnd = trackStart + trackCross
    val gap = FaderHandleGap.toPx()
    val inset = handleThickness / 2f
    val outerRadius = min(trackCross * 0.42f, FaderOuterCornerMax.toPx())
    val innerRadius = FaderInnerCorner.toPx()

    fun position(f: Float): Float {
        val usable = length - 2f * inset
        return when {
            vertical -> inset + usable * (1f - f)
            rtl -> inset + usable * (1f - f)
            else -> inset + usable * f
        }
    }

    val handle = position(fraction)
    val beforeHandle = handle - inset - gap
    val afterHandle = handle + inset + gap

    drawFaderSegment(vertical, 0f, beforeHandle, trackStart, trackEnd, outerRadius, innerRadius, inactiveColor)
    drawFaderSegment(vertical, afterHandle, length, trackStart, trackEnd, innerRadius, outerRadius, inactiveColor)

    if (zeroFraction != null) {
        val zero = position(zeroFraction)
        if (handle < zero) {
            drawFaderSegment(vertical, afterHandle, zero, trackStart, trackEnd, innerRadius, innerRadius, activeColor)
        } else if (handle > zero) {
            drawFaderSegment(vertical, zero, beforeHandle, trackStart, trackEnd, innerRadius, innerRadius, activeColor)
        }
        if (abs(handle - zero) > inset + gap * 2f) {
            val tickRadius = FaderTickRadius.toPx()
            val tickOffset = trackStart / 2f
            val firstTick = if (vertical) Offset(tickOffset, zero) else Offset(zero, tickOffset)
            val secondTick = if (vertical) Offset(size.width - tickOffset, zero) else Offset(zero, size.height - tickOffset)
            drawCircle(tickColor, tickRadius, firstTick)
            drawCircle(tickColor, tickRadius, secondTick)
        }
    } else {
        val fillsTowardEnd = vertical || rtl
        if (fillsTowardEnd) {
            drawFaderSegment(vertical, afterHandle, length, trackStart, trackEnd, innerRadius, outerRadius, activeColor)
        } else {
            drawFaderSegment(vertical, 0f, beforeHandle, trackStart, trackEnd, outerRadius, innerRadius, activeColor)
        }
    }

    drawFaderSegment(vertical, handle - inset, handle + inset, 0f, cross, inset, inset, activeColor)
}

private fun DrawScope.drawFaderSegment(
    vertical: Boolean,
    from: Float,
    to: Float,
    crossStart: Float,
    crossEnd: Float,
    radiusFrom: Float,
    radiusTo: Float,
    color: Color,
) {
    val length = to - from
    if (length <= 0.5f) return
    val crossSize = crossEnd - crossStart
    val limit = min(length / 2f, crossSize / 2f)
    val startCorner = CornerRadius(min(radiusFrom, limit))
    val endCorner = CornerRadius(min(radiusTo, limit))
    val rect =
        if (vertical) {
            RoundRect(
                left = crossStart,
                top = from,
                right = crossEnd,
                bottom = to,
                topLeftCornerRadius = startCorner,
                topRightCornerRadius = startCorner,
                bottomRightCornerRadius = endCorner,
                bottomLeftCornerRadius = endCorner,
            )
        } else {
            RoundRect(
                left = from,
                top = crossStart,
                right = to,
                bottom = crossEnd,
                topLeftCornerRadius = startCorner,
                topRightCornerRadius = endCorner,
                bottomRightCornerRadius = endCorner,
                bottomLeftCornerRadius = startCorner,
            )
        }
    drawPath(Path().apply { addRoundRect(rect) }, color)
}

@Composable
internal fun EqualizerResponseCurve(
    levelsMb: List<Int>,
    minimumMb: Int,
    maximumMb: Int,
    enabled: Boolean,
    highlightedIndex: Int?,
    topLabel: String,
    bottomLabel: String,
    contentDescription: String,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val count = levelsMb.size
    val levels = remember(count) { List(count) { Animatable(levelsMb[it].toFloat()) } }
    val highlights = remember(count) { List(count) { Animatable(0f) } }
    val levelSpec = motion.defaultSpatialSpec<Float>()
    val highlightSpec = motion.fastSpatialSpec<Float>()
    LaunchedEffect(levels, levelsMb) {
        levelsMb.forEachIndexed { index, target ->
            launch { levels[index].animateTo(target.toFloat(), levelSpec) }
        }
    }
    LaunchedEffect(highlights, highlightedIndex) {
        highlights.forEachIndexed { index, animatable ->
            launch { animatable.animateTo(if (index == highlightedIndex) 1f else 0f, highlightSpec) }
        }
    }
    val engagement by animateFloatAsState(if (enabled) 1f else 0f, motion.slowSpatialSpec(), label = "curveEngagement")
    val curveColor by animateColorAsState(
        if (enabled) colors.primary else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(),
        label = "curveColor",
    )
    val gridColor = colors.outlineVariant
    val labelColor = colors.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val topLayout = remember(textMeasurer, topLabel, labelStyle) { textMeasurer.measure(topLabel, labelStyle) }
    val bottomLayout = remember(textMeasurer, bottomLabel, labelStyle) { textMeasurer.measure(bottomLabel, labelStyle) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val span = (maximumMb - minimumMb).coerceAtLeast(1).toFloat()

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        if (count == 0) return@Canvas
        val labelGap = 4.dp.toPx()
        val top = topLayout.size.height + labelGap
        val bottom = size.height - bottomLayout.size.height - labelGap
        val plotHeight = (bottom - top).coerceAtLeast(1f)

        fun y(levelMb: Float): Float = top + plotHeight * (1f - ((levelMb - minimumMb) / span).coerceIn(-0.08f, 1.08f))

        fun x(index: Int): Float {
            val ltr = (index + 0.5f) * size.width / count
            return if (rtl) size.width - ltr else ltr
        }
        val zeroY = y(0f.coerceIn(minimumMb.toFloat(), maximumMb.toFloat()))
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        drawLine(gridColor.copy(alpha = 0.6f), Offset(0f, top), Offset(size.width, top), 1.dp.toPx(), pathEffect = dash)
        drawLine(gridColor.copy(alpha = 0.6f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx(), pathEffect = dash)
        for (index in 0 until count) {
            drawLine(gridColor.copy(alpha = 0.35f), Offset(x(index), top), Offset(x(index), bottom), 1.dp.toPx())
        }
        drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), 1.5.dp.toPx())

        val labelX = if (rtl) size.width - topLayout.size.width.toFloat() else 0f
        drawText(topLayout, labelColor, Offset(labelX, 0f))
        val bottomLabelX = if (rtl) size.width - bottomLayout.size.width.toFloat() else 0f
        drawText(bottomLayout, labelColor, Offset(bottomLabelX, size.height - bottomLayout.size.height))

        if (engagement < 0.999f) {
            val ghost = curvePoints(count, ::x) { y(levels[it].value) }
            drawPath(
                path = smoothCurve(ghost, size.width, rtl),
                color = labelColor.copy(alpha = 0.55f * (1f - engagement)),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash, cap = StrokeCap.Round),
            )
        }

        val points = curvePoints(count, ::x) { y(levels[it].value * engagement) }
        val curve = smoothCurve(points, size.width, rtl)
        val fill =
            Path().apply {
                addPath(curve)
                lineTo(if (rtl) 0f else size.width, zeroY)
                lineTo(if (rtl) size.width else 0f, zeroY)
                close()
            }
        val zeroStop = ((zeroY) / size.height).coerceIn(0.01f, 0.99f)
        drawPath(
            path = fill,
            brush =
                Brush.verticalGradient(
                    0f to curveColor.copy(alpha = 0.34f),
                    zeroStop to curveColor.copy(alpha = 0.04f),
                    1f to curveColor.copy(alpha = 0.34f),
                ),
        )
        drawPath(
            path = curve,
            color = curveColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEachIndexed { index, point ->
            val emphasis = highlights[index].value.coerceAtLeast(0f)
            if (emphasis > 0.01f) {
                drawCircle(curveColor.copy(alpha = 0.18f * emphasis.coerceAtMost(1f)), 16.dp.toPx() * emphasis, point)
            }
            drawCircle(surfaceColor, 6.dp.toPx() + 3.dp.toPx() * emphasis, point)
            drawCircle(curveColor, 4.dp.toPx() + 3.dp.toPx() * emphasis, point)
        }
    }
}

private inline fun curvePoints(
    count: Int,
    x: (Int) -> Float,
    y: (Int) -> Float,
): List<Offset> = List(count) { Offset(x(it), y(it)) }

private fun smoothCurve(
    points: List<Offset>,
    width: Float,
    rtl: Boolean,
): Path {
    val path = Path()
    val startX = if (rtl) width else 0f
    val endX = if (rtl) 0f else width
    path.moveTo(startX, points.first().y)
    path.lineTo(points.first().x, points.first().y)
    for (index in 0 until points.lastIndex) {
        val p0 = points[max(index - 1, 0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[min(index + 2, points.lastIndex)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    path.lineTo(endX, points.last().y)
    return path
}

@Composable
internal fun EqualizerPowerBadge(
    active: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(if (active) 1f else 0f, motion.slowSpatialSpec(), label = "powerBadgeMorph")
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        motion.defaultEffectsSpec(),
        label = "powerBadgeContainer",
    )
    val content by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.defaultEffectsSpec(),
        label = "powerBadgeContent",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = progress * 90f }
                    .background(container, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))),
        )
        Icon(
            painter = painterResource(R.drawable.graphic_eq),
            contentDescription = null,
            tint = content,
            modifier =
                Modifier
                    .size(size * 0.46f)
                    .graphicsLayer {
                        val scale = 0.9f + 0.1f * progress
                        scaleX = scale
                        scaleY = scale
                    },
        )
    }
}

@Stable
internal class EqualizerEntranceChoreography {
    private val played = mutableSetOf<String>()

    fun claim(key: String): Boolean = played.add(key)
}

@Composable
internal fun Modifier.equalizerEntrance(
    choreography: EqualizerEntranceChoreography,
    key: String,
    order: Int,
): Modifier {
    val shouldPlay = remember(choreography, key) { choreography.claim(key) }
    val progress = remember(choreography, key) { Animatable(if (shouldPlay) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((order * EntranceStaggerMillis * scale).toLong())
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * EntranceOffset.toPx()
        val scale = 0.96f + 0.04f * value
        scaleX = scale
        scaleY = scale
    }
}

private val FaderHandleThickness = 4.dp
private val FaderHandleThicknessActive = 7.dp
private val FaderHandleGap = 4.dp
private val FaderInnerCorner = 2.dp
private val FaderOuterCornerMax = 16.dp
private val FaderTickRadius = 2.dp
private const val FaderTrackCrossFraction = 0.6f
private const val EntranceStaggerMillis = 55L
private val EntranceOffset = 36.dp
