/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.LyricsLineAlignment
import java.util.Locale
import kotlin.math.roundToInt

enum class LyricsSingerSide(
    val bias: Float,
) {
    LEFT(-1f),
    CENTER(0f),
    RIGHT(1f),
}

@Immutable
data class LyricsSingerCue(
    val label: String,
    val side: LyricsSingerSide,
)

fun lyricsSingerSide(
    alignment: LyricsLineAlignment,
    startIsRight: Boolean,
): LyricsSingerSide =
    when (alignment) {
        LyricsLineAlignment.CENTER -> LyricsSingerSide.CENTER
        LyricsLineAlignment.START -> if (startIsRight) LyricsSingerSide.RIGHT else LyricsSingerSide.LEFT
        LyricsLineAlignment.END -> if (startIsRight) LyricsSingerSide.LEFT else LyricsSingerSide.RIGHT
    }

@Composable
fun rememberLyricsSingerLabelFormatter(): (List<String>) -> String? {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(locale) {
        val formatter = ListFormatter.getInstance(locale)
        val formatLabel: (List<String>) -> String? = { singers ->
            when (singers.size) {
                0 -> null
                1 -> singers.first()
                else -> formatter.format(singers)
            }
        }
        formatLabel
    }
}

@Composable
fun LyricsSingerBubble(
    cue: LyricsSingerCue?,
    hazeState: HazeState,
    contentColor: Color,
    isPlaying: Boolean,
    bubbleBottomProvider: () -> Int?,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val retainedCue = remember { mutableStateOf(cue) }
    SideEffect {
        if (cue != null) retainedCue.value = cue
    }
    val displayedCue = cue ?: retainedCue.value
    val biasState: State<Float> =
        animateFloatAsState(
            targetValue = displayedCue?.side?.bias ?: LyricsSingerSide.CENTER.bias,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "lyricsSingerBubbleBias",
        )

    Layout(
        modifier = modifier,
        content = {
            AnimatedVisibility(
                visible = cue != null,
                enter =
                    fadeIn(motionScheme.fastEffectsSpec()) +
                        scaleIn(
                            animationSpec = motionScheme.fastSpatialSpec(),
                            initialScale = BUBBLE_ENTER_SCALE,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ),
                exit =
                    fadeOut(motionScheme.fastEffectsSpec()) +
                        scaleOut(
                            animationSpec = motionScheme.fastSpatialSpec(),
                            targetScale = BUBBLE_EXIT_SCALE,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ),
                label = "lyricsSingerBubbleVisibility",
            ) {
                displayedCue?.let { visibleCue ->
                    SingerBubblePill(
                        label = visibleCue.label,
                        hazeState = hazeState,
                        contentColor = contentColor,
                        isPlaying = isPlaying,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val containerWidth = constraints.maxWidth
        val containerHeight = constraints.maxHeight
        val placeable =
            measurables.firstOrNull()?.measure(
                Constraints(maxWidth = (containerWidth * BUBBLE_MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(0)),
            )
        layout(containerWidth, containerHeight) {
            if (placeable == null || placeable.width == 0) return@layout
            val bubbleBottom = bubbleBottomProvider() ?: return@layout
            val travel = (containerWidth - placeable.width).coerceAtLeast(0)
            val x = (travel * (biasState.value + 1f) / 2f).roundToInt()
            val y =
                (bubbleBottom - placeable.height)
                    .coerceIn(0, (containerHeight - placeable.height).coerceAtLeast(0))
            placeable.place(x, y)
        }
    }
}

@Composable
private fun SingerBubblePill(
    label: String,
    hazeState: HazeState,
    contentColor: Color,
    isPlaying: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val lightContent = contentColor.luminance() > 0.5f
    val glassBase = if (lightContent) Color.Black else Color.White
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
    val blurStyle =
        remember(glassBase, lightContent) {
            HazeBlurStyle {
                backgroundColor(glassBase)
                blurRadius(24.dp)
                noiseFactor(0.12f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(glassBase.copy(alpha = if (lightContent) 0.26f else 0.42f)),
                    ),
                )
                fallbackColorEffect(HazeColorEffect.tint(glassBase.copy(alpha = if (lightContent) 0.5f else 0.72f)))
            }
        }
    val edgeBrush =
        remember(contentColor) {
            Brush.verticalGradient(
                listOf(
                    contentColor.copy(alpha = 0.3f),
                    contentColor.copy(alpha = 0.06f),
                ),
            )
        }
    val description = stringResource(R.string.lyrics_now_singing, label)

    Box(
        modifier =
            Modifier
                .heightIn(min = 30.dp)
                .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .hazeBlur(
                        input = hazeInput,
                        style = blurStyle,
                        expandLayerBounds = true,
                    ).border(width = 1.dp, brush = edgeBrush, shape = CircleShape),
        )
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingingIndicator(
                color = contentColor.copy(alpha = 0.86f),
                animating = isPlaying,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (
                        fadeIn(motionScheme.defaultEffectsSpec()) +
                            slideInVertically(motionScheme.defaultSpatialSpec()) { height -> height / 2 }
                    ) togetherWith (
                        fadeOut(motionScheme.fastEffectsSpec()) +
                            slideOutVertically(motionScheme.fastSpatialSpec()) { height -> -height / 2 }
                    ) using SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
                },
                contentAlignment = Alignment.CenterStart,
                label = "lyricsSingerBubbleLabel",
            ) { singerLabel ->
                Text(
                    text = singerLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SingingIndicator(
    color: Color,
    animating: Boolean,
) {
    val phases: List<State<Float>>? =
        if (animating) {
            val transition = rememberInfiniteTransition(label = "lyricsSingingIndicator")
            SINGING_BAR_DURATIONS_MS.map { durationMs ->
                transition.animateFloat(
                    initialValue = SINGING_BAR_MIN_FRACTION,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Reverse),
                    label = "lyricsSingingBar",
                )
            }
        } else {
            null
        }

    Canvas(modifier = Modifier.size(width = 12.dp, height = 12.dp)) {
        val barWidth = 2.5.dp.toPx()
        val gap = (size.width - barWidth * SINGING_BAR_DURATIONS_MS.size) / (SINGING_BAR_DURATIONS_MS.size - 1)
        SINGING_BAR_DURATIONS_MS.indices.forEach { index ->
            val fraction = phases?.get(index)?.value ?: SINGING_BAR_REST_FRACTIONS[index]
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * (barWidth + gap), y = (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private const val BUBBLE_MAX_WIDTH_FRACTION = 0.72f
private const val BUBBLE_ENTER_SCALE = 0.82f
private const val BUBBLE_EXIT_SCALE = 0.9f
private const val SINGING_BAR_MIN_FRACTION = 0.3f
private val SINGING_BAR_DURATIONS_MS = listOf(420, 560, 380)
private val SINGING_BAR_REST_FRACTIONS = listOf(0.45f, 0.75f, 0.55f)
