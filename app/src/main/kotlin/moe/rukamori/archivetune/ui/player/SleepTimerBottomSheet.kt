/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.SleepTimerFadeOutKey
import moe.rukamori.archivetune.constants.SleepTimerFinishSongKey
import moe.rukamori.archivetune.playback.SleepTimer
import moe.rukamori.archivetune.ui.component.BackAwareModalBottomSheet
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Date
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SleepTimerMinMinutes = 1
private const val SleepTimerMaxMinutes = 120
private const val SleepTimerHapticStepMinutes = 5
private const val SleepTimerTickStepMinutes = 5
private const val SleepTimerMajorTickMinutes = 15
private const val SleepTimerGlyphPlaceholder = ""
private val SleepTimerPresets = listOf(5, 10, 15, 30, 45, 60, 90)
private val SleepTimerDialMarks = listOf(5, 15, 30, 45, 60, 90, 120)
private val SleepTimerMono = FontFamily.Monospace
private val DialStrokeWidth = 12.dp
private val DialThumbRadius = 14.dp
private val DialThumbHalo = 4.dp
private val DialThumbCoreRadius = 3.5.dp
private val DialMarkInset = 18.dp
private val DialTouchSlack = 32.dp
private const val DialThumbPressedScale = 1.18f
private const val DialInnerTouchFraction = 0.55f

@Composable
fun SleepTimerBottomSheet(
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onStart: (minutes: Int, finishCurrentSong: Boolean, fadeOut: Boolean) -> Unit,
) {
    val minutes = snapSleepTimerMinutes(value)
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    var finishCurrentSong by rememberPreference(SleepTimerFinishSongKey, false)
    var fadeOut by rememberPreference(SleepTimerFadeOutKey, true)
    var starting by remember { mutableStateOf(false) }
    val latestMinutes by rememberUpdatedState(minutes)
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    fun feedback(type: HapticFeedbackType) {
        if (enableHapticFeedback) haptic.performHapticFeedback(type)
    }

    fun selectMinutes(target: Int) {
        val clamped = target.coerceIn(SleepTimerMinMinutes, SleepTimerMaxMinutes)
        if (clamped != latestMinutes) {
            val segment =
                clamped % SleepTimerHapticStepMinutes == 0 ||
                    clamped == SleepTimerMinMinutes ||
                    clamped == SleepTimerMaxMinutes
            feedback(if (segment) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick)
        }
        onValueChange(clamped.toFloat())
    }

    fun hideSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) latestOnDismiss()
        }
    }

    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
        ) {
            SleepTimerSheetHeader(progress = minutes / SleepTimerMaxMinutes.toFloat())

            Spacer(Modifier.height(20.dp))

            SleepTimerDial(
                minutes = minutes,
                haloColor = colors.surfaceContainerLow,
                onMinutesChange = ::selectMinutes,
                modifier =
                    Modifier
                        .fillMaxWidth(0.68f)
                        .widthIn(max = 320.dp)
                        .aspectRatio(1f),
            )

            Spacer(Modifier.height(20.dp))

            SleepTimerStopsAt(minutes = minutes)

            Spacer(Modifier.height(20.dp))

            SleepTimerPresetRow(
                minutes = minutes,
                onSelect = ::selectMinutes,
            )

            Spacer(Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SleepTimerOptionRow(
                    icon = R.drawable.music_note,
                    title = stringResource(R.string.sleep_timer_finish_song),
                    subtitle = stringResource(R.string.sleep_timer_finish_song_description),
                    checked = finishCurrentSong,
                    onCheckedChange = { checked ->
                        feedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        finishCurrentSong = checked
                    },
                )
                SleepTimerOptionRow(
                    icon = R.drawable.volume_up,
                    title = stringResource(R.string.sleep_timer_fade_out),
                    subtitle =
                        stringResource(
                            R.string.sleep_timer_fade_out_description,
                            (SleepTimer.FadeOutDurationMs / 1_000L).toInt(),
                        ),
                    checked = fadeOut,
                    onCheckedChange = { checked ->
                        feedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        fadeOut = checked
                    },
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = ::hideSheet,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.primary,
                        ),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = {
                        if (!starting) {
                            starting = true
                            feedback(HapticFeedbackType.Confirm)
                            onStart(minutes, finishCurrentSong, fadeOut)
                            hideSheet()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_start),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerSheetHeader(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val locale = LocalConfiguration.current.locales[0]
    val moonTilt by animateFloatAsState(
        targetValue = -28f * progress,
        animationSpec = motion.defaultSpatialSpec(),
        label = "sleepTimerMoonTilt",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_playback_title).uppercase(locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
        ) {
            Icon(
                painter = painterResource(R.drawable.bedtime),
                contentDescription = null,
                tint = colors.primary,
                modifier =
                    Modifier
                        .size(26.dp)
                        .graphicsLayer { rotationZ = moonTilt },
            )
        }
    }
}

@Composable
private fun SleepTimerDial(
    minutes: Int,
    haloColor: Color,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val latestOnMinutesChange by rememberUpdatedState(onMinutesChange)
    val latestMinutes by rememberUpdatedState(minutes)
    var dragging by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = minutes / SleepTimerMaxMinutes.toFloat(),
        animationSpec = if (dragging) snap() else motion.defaultSpatialSpec(),
        label = "sleepTimerDialProgress",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) DialThumbPressedScale else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "sleepTimerDialThumbScale",
    )
    val description = stringResource(R.string.sleep_timer_duration)
    val minutesState = pluralStringResource(R.plurals.minute, minutes, minutes)
    val trackColor = colors.surfaceContainerHighest
    val tickColor = colors.onSurfaceVariant
    val activeColor = colors.primary
    val thumbCoreColor = colors.onPrimary

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .semantics {
                    contentDescription = description
                    stateDescription = minutesState
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = minutes.toFloat(),
                            range = SleepTimerMinMinutes.toFloat()..SleepTimerMaxMinutes.toFloat(),
                            steps = SleepTimerMaxMinutes - SleepTimerMinMinutes - 1,
                        )
                    setProgress { target ->
                        latestOnMinutesChange(target.roundToInt())
                        true
                    }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = dialTrackRadius(size.width.toFloat(), size.height.toFloat())
                        val distance = (down.position - center).getDistance()
                        if (distance < radius * DialInnerTouchFraction || distance > radius + DialTouchSlack.toPx()) {
                            return@awaitEachGesture
                        }
                        down.consume()
                        dragging = true
                        var last = latestMinutes
                        val first = minutesForOffset(down.position - center, previous = null)
                        if (first != last) {
                            last = first
                            latestOnMinutesChange(first)
                        }
                        try {
                            drag(down.id) { change ->
                                val next = minutesForOffset(change.position - center, previous = last)
                                if (next != last) {
                                    last = next
                                    latestOnMinutesChange(next)
                                }
                                change.consume()
                            }
                        } finally {
                            dragging = false
                        }
                    }
                }.drawBehind {
                    val strokePx = DialStrokeWidth.toPx()
                    val radius = dialTrackRadius(size.width, size.height)
                    val dialCenter = center
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        center = dialCenter,
                        style = Stroke(width = strokePx),
                    )
                    var tick = 0
                    while (tick < SleepTimerMaxMinutes) {
                        val major = tick % SleepTimerMajorTickMinutes == 0
                        val angle = tick / SleepTimerMaxMinutes.toFloat() * 2f * PI.toFloat()
                        val direction = Offset(sin(angle), -cos(angle))
                        val length = strokePx * if (major) 0.9f else 0.5f
                        drawLine(
                            color = tickColor.copy(alpha = if (major) 0.7f else 0.4f),
                            start = dialCenter + direction * (radius - length / 2f),
                            end = dialCenter + direction * (radius + length / 2f),
                            strokeWidth = (if (major) 2.dp else 1.5.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                        tick += SleepTimerTickStepMinutes
                    }
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = dialCenter - Offset(radius, radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    val thumbAngle = progress * 2f * PI.toFloat()
                    val thumbCenter = dialCenter + Offset(sin(thumbAngle), -cos(thumbAngle)) * radius
                    val thumbRadius = DialThumbRadius.toPx() * thumbScale
                    drawCircle(color = haloColor, radius = thumbRadius + DialThumbHalo.toPx(), center = thumbCenter)
                    drawCircle(color = activeColor, radius = thumbRadius, center = thumbCenter)
                    drawCircle(color = thumbCoreColor, radius = DialThumbCoreRadius.toPx(), center = thumbCenter)
                },
    ) {
        SleepTimerDialMarks(color = colors.onSurfaceVariant)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            RollingText(
                text = "%d".format(minutes),
                order = minutes,
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum",
                    ),
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.sleep_timer_minutes_short),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = SleepTimerMono, letterSpacing = 1.sp),
                color = colors.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SleepTimerDialMarks(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelSmall.copy(fontFamily = SleepTimerMono)
    Layout(
        content = {
            SleepTimerDialMarks.forEach { mark ->
                Text(
                    text = dialMarkLabel(mark),
                    style = style,
                    color = color,
                    maxLines = 1,
                )
            }
        },
        modifier = modifier.fillMaxSize().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val placeables = measurables.map { it.measure(Constraints()) }
        val radius =
            dialTrackRadius(width.toFloat(), height.toFloat()) -
                DialStrokeWidth.toPx() / 2f -
                DialMarkInset.toPx()
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val angle = SleepTimerDialMarks[index] / SleepTimerMaxMinutes.toFloat() * 2f * PI.toFloat()
                val x = width / 2f + sin(angle) * radius - placeable.width / 2f
                val y = height / 2f - cos(angle) * radius - placeable.height / 2f
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

@Composable
private fun SleepTimerStopsAt(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val endTime = rememberSleepTimerEndTime(minutes)
    val fullText = stringResource(R.string.sleep_timer_ends_at, endTime)
    val template = stringResource(R.string.sleep_timer_ends_at, SleepTimerGlyphPlaceholder)
    val prefix = template.substringBefore(SleepTimerGlyphPlaceholder).trim().uppercase(locale)
    val suffix = template.substringAfter(SleepTimerGlyphPlaceholder, "").trim().uppercase(locale)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = SleepTimerMono, letterSpacing = 1.sp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = fullText },
    ) {
        if (prefix.isNotEmpty()) {
            Text(text = prefix, style = labelStyle, color = colors.onSurfaceVariant, maxLines = 1)
        }
        RollingText(
            text = endTime,
            order = minutes,
            style = labelStyle.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
        )
        if (suffix.isNotEmpty()) {
            Text(text = suffix, style = labelStyle, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SleepTimerPresetRow(
    minutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val presetColors =
        ToggleButtonDefaults.colors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            checkedContainerColor = colors.primary,
            checkedContentColor = colors.onPrimary,
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SleepTimerPresets.forEach { preset ->
                val accessibilityLabel = pluralStringResource(R.plurals.minute, preset, preset)
                ToggleButton(
                    checked = minutes == preset,
                    onCheckedChange = { onSelect(preset) },
                    colors = presetColors,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier =
                        Modifier
                            .heightIn(min = 40.dp)
                            .semantics { contentDescription = accessibilityLabel },
                ) {
                    Text(
                        text = presetLabel(preset),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = SleepTimerMono),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerOptionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val iconTint by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "sleepTimerOptionIconTint",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    ).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHighest),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Immutable
private data class RollingGlyph(
    val char: Char,
    val order: Int,
)

@Composable
private fun RollingText(
    text: String,
    order: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .animateContentSize(motion.fastSpatialSpec())
                .clipToBounds(),
    ) {
        val length = text.length
        text.forEachIndexed { index, char ->
            key(length - index) {
                AnimatedContent(
                    targetState = RollingGlyph(char, order),
                    contentKey = { it.char },
                    transitionSpec = {
                        val rising = targetState.order >= initialState.order
                        (
                            slideInVertically(motion.defaultSpatialSpec()) { height -> if (rising) height else -height } +
                                fadeIn(motion.defaultEffectsSpec())
                        ) togetherWith (
                            slideOutVertically(motion.fastSpatialSpec()) { height -> if (rising) -height else height } +
                                fadeOut(motion.fastEffectsSpec())
                        ) using SizeTransform(clip = true)
                    },
                    contentAlignment = Alignment.Center,
                    label = "sleepTimerRollingGlyph",
                ) { glyph ->
                    Text(
                        text = glyph.char.toString(),
                        style = style,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun dialMarkLabel(minutes: Int): String =
    if (minutes % 60 == 0) {
        stringResource(R.string.sleep_timer_hours_compact, minutes / 60)
    } else {
        "%d".format(minutes)
    }

@Composable
private fun presetLabel(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.sleep_timer_minutes_compact, remainder)
        remainder == 0 -> stringResource(R.string.sleep_timer_hours_compact, hours)
        else -> stringResource(R.string.library_duration_hours_minutes, hours, remainder)
    }
}

@Composable
private fun rememberSleepTimerEndTime(minutes: Int): String {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay(60_000L - current % 60_000L)
        }
    }
    return remember(now, minutes, formatter) {
        formatter.format(Date(now + minutes * 60_000L))
    }
}

private fun Density.dialTrackRadius(
    width: Float,
    height: Float,
): Float = min(width, height) / 2f - (DialThumbRadius.toPx() * DialThumbPressedScale + DialThumbHalo.toPx())

private fun minutesForOffset(
    offset: Offset,
    previous: Int?,
): Int {
    val fullTurn = 2f * PI.toFloat()
    val angle = (atan2(offset.x, -offset.y) + fullTurn) % fullTurn
    val raw = (angle / fullTurn * SleepTimerMaxMinutes).roundToInt()
    val candidate =
        if (raw == 0 || raw == SleepTimerMaxMinutes) {
            if (previous != null && previous <= SleepTimerMaxMinutes / 2) SleepTimerMinMinutes else SleepTimerMaxMinutes
        } else {
            raw
        }
    if (previous != null) {
        val quarter = SleepTimerMaxMinutes / 4
        if (previous >= SleepTimerMaxMinutes - quarter && candidate <= quarter) return SleepTimerMaxMinutes
        if (previous <= quarter && candidate >= SleepTimerMaxMinutes - quarter) return SleepTimerMinMinutes
    }
    return candidate.coerceIn(SleepTimerMinMinutes, SleepTimerMaxMinutes)
}

private fun snapSleepTimerMinutes(value: Float): Int = value.roundToInt().coerceIn(SleepTimerMinMinutes, SleepTimerMaxMinutes)
