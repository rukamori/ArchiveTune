/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.rememberControlledSliderState
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.toColorScheme
import java.util.Locale

private val StageTones = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95)

internal fun Color.toHexRgb(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

internal fun Color.readableContent(): Color = if (luminance() > 0.42f) Color(0xDE000000) else Color.White

internal fun ThemeSeedPalette.sameSeedsAs(other: ThemeSeedPalette): Boolean =
    primary.toArgb() == other.primary.toArgb() &&
        secondary.toArgb() == other.secondary.toArgb() &&
        tertiary.toArgb() == other.tertiary.toArgb() &&
        neutral.toArgb() == other.neutral.toArgb()

@Composable
internal fun rememberSeedColorScheme(
    palette: ThemeSeedPalette,
    isDark: Boolean,
): ColorScheme {
    val latestPalette by rememberUpdatedState(palette)
    val latestDark by rememberUpdatedState(isDark)
    val initial = remember { palette.toColorScheme(isDark) }
    val scheme by produceState(initialValue = initial) {
        snapshotFlow { latestPalette to latestDark }
            .conflate()
            .collect { (seeds, dark) ->
                value = withContext(Dispatchers.Default) { seeds.toColorScheme(dark) }
            }
    }
    return scheme
}

@Composable
internal fun ConnectedChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcons: List<Int?> = emptyList(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(index) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.secondary,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
            ) {
                leadingIcons.getOrNull(index)?.let { icon ->
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(text = label, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun MorphSwatch(
    color: Color,
    morphed: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    restPolygon: RoundedPolygon = MaterialShapes.Circle,
    morphPolygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    turnDegrees: Float = 40f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme
    val morph = remember(restPolygon, morphPolygon) { Morph(restPolygon, morphPolygon) }
    val progress by animateFloatAsState(
        targetValue = if (morphed) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "swatchMorph",
    )
    val fill by animateColorAsState(
        targetValue = color,
        animationSpec = motion.defaultEffectsSpec(),
        label = "swatchFill",
    )
    val shape = EditorialMorphShape(morph, progress.coerceIn(0f, 1f))
    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer { rotationZ = progress * turnDegrees }
                .clip(shape)
                .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer { rotationZ = -progress * turnDegrees },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
internal fun TonalStrip(
    seed: Color,
    modifier: Modifier = Modifier,
) {
    val tones = remember(seed.toArgb()) {
        val hct = seed.toHct()
        StageTones.map { tone -> hct.withTone(tone.toDouble()).toColor() }
    }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(CircleShape)
                .clearAndSetSemantics {},
    ) {
        tones.forEachIndexed { index, tone ->
            val animated by animateColorAsState(tone, spec, label = "tone$index")
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .background(animated),
            )
        }
    }
}

@Composable
internal fun ThemeStage(
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()

    @Composable
    fun animated(target: Color, label: String): Color = animateColorAsState(target, spec, label = label).value

    val surface = animated(scheme.surface, "stageSurface")
    val onSurface = animated(scheme.onSurface, "stageOnSurface")
    val onSurfaceVariant = animated(scheme.onSurfaceVariant, "stageOnSurfaceVariant")
    val surfaceContainer = animated(scheme.surfaceContainer, "stageSurfaceContainer")
    val surfaceContainerHigh = animated(scheme.surfaceContainerHigh, "stageSurfaceContainerHigh")
    val outlineVariant = animated(scheme.outlineVariant, "stageOutlineVariant")
    val primary = animated(scheme.primary, "stagePrimary")
    val onPrimary = animated(scheme.onPrimary, "stageOnPrimary")
    val primaryContainer = animated(scheme.primaryContainer, "stagePrimaryContainer")
    val onPrimaryContainer = animated(scheme.onPrimaryContainer, "stageOnPrimaryContainer")
    val secondaryContainer = animated(scheme.secondaryContainer, "stageSecondaryContainer")
    val onSecondaryContainer = animated(scheme.onSecondaryContainer, "stageOnSecondaryContainer")
    val tertiaryContainer = animated(scheme.tertiaryContainer, "stageTertiaryContainer")
    val onTertiaryContainer = animated(scheme.onTertiaryContainer, "stageOnTertiaryContainer")

    val previewDescription = stringResource(R.string.preview)
    val artworkShape = MaterialShapes.Cookie9Sided.toShape()
    val playShape = MaterialShapes.Cookie4Sided.toShape()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(surface)
                .border(1.dp, outlineVariant, RoundedCornerShape(28.dp))
                .padding(14.dp)
                .clearAndSetSemantics { contentDescription = previewDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(surfaceContainerHigh),
            )
            StageRowBar(color = onSurface, fraction = 0.42f, height = 10.dp)
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(primaryContainer)
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(artworkShape)
                        .background(tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = onTertiaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StageBar(color = onPrimaryContainer, fraction = 0.78f, height = 9.dp)
                StageBar(color = onPrimaryContainer.copy(alpha = 0.55f), fraction = 0.5f, height = 7.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(onPrimaryContainer.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.42f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(primary),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(playShape)
                        .background(primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StagePill(container = secondaryContainer, content = onSecondaryContainer)
            StagePill(container = tertiaryContainer, content = onTertiaryContainer)
            StagePill(container = Color.Transparent, content = onSurfaceVariant, outline = outlineVariant)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(R.drawable.library_music, R.drawable.search, R.drawable.favorite).forEachIndexed { index, icon ->
                val selected = index == 0
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) secondaryContainer else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (selected) onSecondaryContainer else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StageRowBar(
    color: Color,
    fraction: Float,
    height: Dp,
) {
    Box(
        Modifier
            .weight(fraction)
            .height(height)
            .clip(CircleShape)
            .background(color),
    )
    if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StageBar(
    color: Color,
    fraction: Float,
    height: Dp,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun StagePill(
    container: Color,
    content: Color,
    outline: Color? = null,
) {
    val pill = RoundedCornerShape(50)
    Box(
        modifier =
            Modifier
                .clip(pill)
                .background(container)
                .then(if (outline != null) Modifier.border(1.dp, outline, pill) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(7.dp)
                .clip(CircleShape)
                .background(content),
        )
    }
}

@Composable
internal fun ColorChannelSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackColors: List<Color>,
    thumbColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    val sliderState = rememberControlledSliderState(value = value, valueRange = valueRange)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLargeEmphasized,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { next ->
                sliderState.value = next
                onValueChange(next)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            thumb = {
                Box(
                    Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(thumbColor),
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(pill)
                        .background(Brush.horizontalGradient(trackColors))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, pill),
                )
            },
        )
    }
}
