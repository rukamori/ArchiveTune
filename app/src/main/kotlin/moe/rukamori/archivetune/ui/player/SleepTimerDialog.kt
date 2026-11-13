/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Date
import kotlin.math.roundToInt

private const val SleepTimerMinMinutes = 5
private const val SleepTimerMaxMinutes = 120
private const val SleepTimerStepMinutes = 5
private val SleepTimerPresets = listOf(15, 30, 45, 60, 90)

private enum class SleepTimerMode { Duration, EndOfSong }

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    initialValue: Float = 30f,
) {
    var sleepTimerValue by rememberSaveable { mutableFloatStateOf(initialValue) }

    SleepTimerDialog(
        value = sleepTimerValue,
        onValueChange = { sleepTimerValue = it },
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onEndOfSong = onEndOfSong,
    )
}

@Composable
fun SleepTimerDialog(
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
) {
    val minutes = snapSleepTimerMinutes(value)
    var mode by rememberSaveable { mutableStateOf(SleepTimerMode.Duration) }
    val reduceMotion = rememberReducedMotion()
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val latestMinutes by rememberUpdatedState(minutes)

    fun feedback(type: HapticFeedbackType) {
        if (enableHapticFeedback) haptic.performHapticFeedback(type)
    }

    fun selectMinutes(target: Int) {
        val snapped = snapSleepTimerMinutes(target.toFloat())
        if (snapped != latestMinutes) {
            feedback(HapticFeedbackType.SegmentTick)
            onValueChange(snapped.toFloat())
        }
    }

    fun selectMode(target: SleepTimerMode) {
        if (mode != target) {
            mode = target
            feedback(HapticFeedbackType.ToggleOn)
        }
    }

    val entranceScale = remember { Animatable(if (reduceMotion) 1f else 0.88f) }
    val entranceAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { entranceScale.animateTo(1f, motion.defaultSpatialSpec()) }
        entranceAlpha.animateTo(1f, motion.defaultEffectsSpec())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .navigationBarsPadding(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = entranceScale.value
                            scaleY = entranceScale.value
                            alpha = entranceAlpha.value
                        },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    SleepTimerHeader(
                        mode = mode,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    SleepTimerModeSwitch(
                        mode = mode,
                        onModeChange = ::selectMode,
                    )

                    Spacer(Modifier.height(8.dp))

                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            (
                                fadeIn(motion.defaultEffectsSpec()) +
                                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.82f)
                            ) togetherWith (
                                fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.82f)
                            ) using SizeTransform(clip = false)
                        },
                        contentAlignment = Alignment.Center,
                        label = "sleepTimerHero",
                        modifier = Modifier.fillMaxWidth(),
                    ) { target ->
                        when (target) {
                            SleepTimerMode.Duration -> SleepTimerDurationDial(minutes = minutes)
                            SleepTimerMode.EndOfSong -> SleepTimerEndOfSongHero(reduceMotion = reduceMotion)
                        }
                    }

                    AnimatedVisibility(
                        visible = mode == SleepTimerMode.Duration,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        SleepTimerDurationControls(
                            minutes = minutes,
                            onMinutesChange = ::selectMinutes,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Button(
                            onClick = {
                                feedback(HapticFeedbackType.Confirm)
                                when (mode) {
                                    SleepTimerMode.Duration -> onConfirm(minutes)
                                    SleepTimerMode.EndOfSong -> onEndOfSong()
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.bedtime),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.sleep_timer_start),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerHeader(
    mode: SleepTimerMode,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val morphProgress =
        animateFloatAsState(
            targetValue = if (mode == SleepTimerMode.EndOfSong) 1f else 0f,
            animationSpec = motion.slowSpatialSpec(),
            label = "sleepTimerEmblemMorph",
        )
    val rotation = rememberAmbientRotation(reduceMotion, durationMillis = 18_000, label = "sleepTimerEmblemRotation")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp),
        ) {
            MorphBlob(
                morph = morph,
                progress = morphProgress,
                rotation = rotation,
                color = colors.primaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                painter = painterResource(R.drawable.bedtime),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = stringResource(R.string.sleep_timer),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

@Composable
private fun SleepTimerModeSwitch(
    mode: SleepTimerMode,
    onModeChange: (SleepTimerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        ToggleButton(
            checked = mode == SleepTimerMode.Duration,
            onCheckedChange = { onModeChange(SleepTimerMode.Duration) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = colors,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.timer),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.sleep_timer_mode_timer),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ToggleButton(
            checked = mode == SleepTimerMode.EndOfSong,
            onCheckedChange = { onModeChange(SleepTimerMode.EndOfSong) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = colors,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.end_of_song),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SleepTimerDurationDial(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) } }
    val progress by animateFloatAsState(
        targetValue = minutes / SleepTimerMaxMinutes.toFloat(),
        animationSpec = motion.defaultSpatialSpec(),
        label = "sleepTimerDialProgress",
    )
    val endTime = rememberSleepTimerEndTime(minutes)
    val endsAt = stringResource(R.string.sleep_timer_ends_at, endTime)
    val unit = pluralStringResource(R.plurals.sleep_timer_minutes_unit, minutes)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
    ) {
        CircularWavyProgressIndicator(
            progress = { progress },
            color = colors.primary,
            trackColor = colors.secondaryContainer,
            stroke = stroke,
            trackStroke = stroke,
            modifier = Modifier.size(216.dp).clearAndSetSemantics {},
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = minutes,
                transitionSpec = {
                    val rising = targetState > initialState
                    (
                        slideInVertically(motion.defaultSpatialSpec()) { height -> if (rising) height / 2 else -height / 2 } +
                            fadeIn(motion.defaultEffectsSpec())
                    ) togetherWith (
                        slideOutVertically(motion.fastSpatialSpec()) { height -> if (rising) -height / 2 else height / 2 } +
                            fadeOut(motion.fastEffectsSpec())
                    ) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                label = "sleepTimerMinutes",
            ) { value ->
                Text(
                    text = "%d".format(value),
                    style = MaterialTheme.typography.displayLargeEmphasized,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = CircleShape,
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
            ) {
                Text(
                    text = endsAt,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SleepTimerEndOfSongHero(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst) }
    val pulse =
        if (reduceMotion) {
            remember { mutableFloatStateOf(0f) }
        } else {
            rememberInfiniteTransition(label = "sleepTimerEndOfSong").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "sleepTimerEndOfSongMorph",
            )
        }
    val rotation = rememberAmbientRotation(reduceMotion, durationMillis = 24_000, label = "sleepTimerEndOfSongRotation")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(184.dp),
        ) {
            MorphBlob(
                morph = morph,
                progress = pulse,
                rotation = rotation,
                color = colors.tertiaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = colors.onTertiaryContainer,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.end_of_song),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = colors.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 128.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sleep_timer_end_of_song_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SleepTimerDurationControls(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val minutesLabel = pluralStringResource(R.plurals.minute, minutes, minutes)
    val decreaseLabel = pluralStringResource(R.plurals.sleep_timer_decrease, SleepTimerStepMinutes, SleepTimerStepMinutes)
    val increaseLabel = pluralStringResource(R.plurals.sleep_timer_increase, SleepTimerStepMinutes, SleepTimerStepMinutes)
    val presetColors =
        ToggleButtonDefaults.colors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
        )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalIconButton(
                onClick = { onMinutesChange(minutes - SleepTimerStepMinutes) },
                enabled = minutes > SleepTimerMinMinutes,
            ) {
                Icon(painter = painterResource(R.drawable.remove), contentDescription = decreaseLabel)
            }
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onMinutesChange(it.roundToInt()) },
                valueRange = SleepTimerMinMinutes.toFloat()..SleepTimerMaxMinutes.toFloat(),
                steps = (SleepTimerMaxMinutes - SleepTimerMinMinutes) / SleepTimerStepMinutes - 1,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { stateDescription = minutesLabel },
            )
            FilledTonalIconButton(
                onClick = { onMinutesChange(minutes + SleepTimerStepMinutes) },
                enabled = minutes < SleepTimerMaxMinutes,
            ) {
                Icon(painter = painterResource(R.drawable.add), contentDescription = increaseLabel)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SleepTimerPresets.forEach { preset ->
                val presetLabel = pluralStringResource(R.plurals.minute, preset, preset)
                ToggleButton(
                    checked = minutes == preset,
                    onCheckedChange = { onMinutesChange(preset) },
                    colors = presetColors,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = presetLabel },
                ) {
                    Text(
                        text = "%d".format(preset),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphBlob(
    morph: Morph,
    progress: State<Float>,
    rotation: State<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .graphicsLayer { rotationZ = rotation.value }
                .drawBehind {
                    drawOutline(
                        outline = EditorialMorphShape(morph, progress.value).createOutline(size, layoutDirection, this),
                        color = color,
                    )
                },
    )
}

@Composable
private fun rememberAmbientRotation(
    reduceMotion: Boolean,
    durationMillis: Int,
    label: String,
): State<Float> =
    if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
            label = label,
        )
    }

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
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

private fun snapSleepTimerMinutes(value: Float): Int {
    val stepped = (value / SleepTimerStepMinutes).roundToInt() * SleepTimerStepMinutes
    return stepped.coerceIn(SleepTimerMinMinutes, SleepTimerMaxMinutes)
}
