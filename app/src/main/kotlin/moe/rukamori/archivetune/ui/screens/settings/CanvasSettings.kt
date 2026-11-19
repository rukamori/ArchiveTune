/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasHealth
import moe.rukamori.archivetune.canvas.CanvasHealthStatus
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.CanvasCacheOption
import moe.rukamori.archivetune.viewmodels.CanvasSettingsAction
import moe.rukamori.archivetune.viewmodels.CanvasSettingsDialog
import moe.rukamori.archivetune.viewmodels.CanvasSettingsState
import moe.rukamori.archivetune.viewmodels.CanvasSettingsUiModel
import moe.rukamori.archivetune.viewmodels.CanvasSettingsViewModel
import java.text.NumberFormat

private val CanvasContentMaxWidth = 840.dp
private val CanvasScreenGutter = 16.dp
private val CanvasStageShape = RoundedCornerShape(36.dp)
private val CanvasPanelShape = RoundedCornerShape(28.dp)
private val CanvasTileShape = RoundedCornerShape(24.dp)
private val CanvasReelWidth = 104.dp
private val CanvasGlyphSize = 48.dp
private val CanvasEntranceOffset = 32.dp
private const val CanvasEntranceStaggerMillis = 55L
private const val CanvasWideColumns = 4
private const val CanvasCompactColumns = 2
private val CanvasWideBreakpoint = 600.dp

private enum class CanvasProvider(
    val source: CanvasSource,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    BETTER_LYRICS(CanvasSource.BETTER_LYRICS, R.string.canvas_better_lyrics, R.drawable.lyrics),
    APPLE_MUSIC(CanvasSource.APPLE_MUSIC, R.string.canvas_apple_music, R.drawable.music_note),
    TIDAL(CanvasSource.TIDAL, R.string.canvas_tidal, R.drawable.slow_motion_video),
    SPOTIFY(CanvasSource.SPOTIFY, R.string.canvas_spotify, R.drawable.spotify),
}

@Composable
fun CanvasSettings(
    navController: NavController,
    viewModel: CanvasSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack: () -> Unit = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }
    val onBackToMain: () -> Unit = remember(navController) { { navController.backToMain() } }
    CanvasSettingsContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        onBackToMain = onBackToMain,
    )
}

@Composable
private fun CanvasSettingsContent(
    state: CanvasSettingsState,
    onAction: (CanvasSettingsAction) -> Unit,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.archivetune_canvas)) },
                subtitle = { Text(stringResource(R.string.canvas_settings_subtitle)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        onLongClick = onBackToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        val bottomPadding = insets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "canvasSettingsState",
            modifier = contentModifier,
        ) { screenState ->
            when (screenState) {
                CanvasSettingsState.Loading -> CanvasSettingsLoading()
                is CanvasSettingsState.Success ->
                    CanvasSettingsBody(
                        model = screenState.model,
                        onAction = onAction,
                        bottomPadding = bottomPadding,
                    )

                CanvasSettingsState.Empty ->
                    CanvasSettingsFailure(R.string.canvas_settings_load_failed, onAction, bottomPadding)

                is CanvasSettingsState.Error ->
                    CanvasSettingsFailure(screenState.messageRes, onAction, bottomPadding)
            }
        }
    }
}

@Composable
private fun CanvasSettingsBody(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
    bottomPadding: Dp,
) {
    val onEnabled: (Boolean) -> Unit =
        remember(onAction) { { enabled -> onAction(CanvasSettingsAction.SetEnabled(enabled)) } }
    val onSourceSelected: (CanvasSource) -> Unit =
        remember(onAction) { { source -> onAction(CanvasSettingsAction.SelectSource(source)) } }
    val onWifiOnly: (Boolean) -> Unit =
        remember(onAction) { { wifiOnly -> onAction(CanvasSettingsAction.SetWifiOnly(wifiOnly)) } }
    val onCacheLimitSelected: (CanvasCacheOption) -> Unit =
        remember(onAction) { { option -> onAction(CanvasSettingsAction.SetCacheLimit(option.limitMb)) } }
    val onRefresh = remember(onAction) { { onAction(CanvasSettingsAction.RefreshHealth) } }
    val onClear = remember(onAction) { { onAction(CanvasSettingsAction.ShowClearCache) } }

    val configuration = model.configuration
    val overallHealth =
        remember(model.health, configuration.enabled) { model.health.overall(configuration.enabled) }
    val motionEnabled = rememberCanvasMotionEnabled()
    val entrance = remember(motionEnabled) { CanvasEntranceTracker(motionEnabled) }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding =
            PaddingValues(
                start = CanvasScreenGutter,
                end = CanvasScreenGutter,
                top = 8.dp,
                bottom = bottomPadding + SettingsDimensions.ScreenBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "stage", contentType = "stage") {
            CanvasStage(
                enabled = configuration.enabled,
                status = overallHealth,
                busy = model.busy,
                motionEnabled = motionEnabled,
                onEnabledChange = onEnabled,
                modifier = Modifier.canvasSection().canvasEntrance("stage", 0, entrance),
            )
        }

        item(key = "wifi", contentType = "toggle") {
            CanvasWifiCard(
                checked = configuration.wifiOnly,
                waitingForWifi = overallHealth == CanvasHealth.WIFI_REQUIRED,
                enabled = !model.busy,
                onCheckedChange = onWifiOnly,
                modifier = Modifier.canvasSection().canvasEntrance("wifi", 1, entrance),
            )
        }

        item(key = "sourceHeader", contentType = "header") {
            CanvasSectionTitle(
                textRes = R.string.canvas_source,
                modifier =
                    Modifier
                        .canvasSection()
                        .padding(top = 16.dp)
                        .canvasEntrance("sourceHeader", 2, entrance),
            )
        }

        item(key = "sources", contentType = "sources") {
            CanvasSourcePicker(
                selected = configuration.source,
                health = model.health,
                checking = model.health.checking,
                canRefresh = model.canRefreshHealth && !model.busy,
                enabled = !model.busy,
                motionEnabled = motionEnabled,
                onSelect = onSourceSelected,
                onRefresh = onRefresh,
                modifier = Modifier.canvasSection().canvasEntrance("sources", 3, entrance),
            )
        }

        item(key = "cacheHeader", contentType = "header") {
            CanvasSectionTitle(
                textRes = R.string.canvas_cache,
                modifier =
                    Modifier
                        .canvasSection()
                        .padding(top = 16.dp)
                        .canvasEntrance("cacheHeader", 4, entrance),
            )
        }

        item(key = "cache", contentType = "cache") {
            CanvasCachePanel(
                model = model,
                onCacheLimitSelected = onCacheLimitSelected,
                onClear = onClear,
                modifier = Modifier.canvasSection().canvasEntrance("cache", 5, entrance),
            )
        }
    }

    CanvasSettingsDialogs(
        model = model,
        onAction = onAction,
    )
}

private fun Modifier.canvasSection(): Modifier = widthIn(max = CanvasContentMaxWidth).fillMaxWidth()

@Composable
private fun CanvasSectionTitle(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
    )
}

@Composable
private fun CanvasStage(
    enabled: Boolean,
    status: CanvasHealth,
    busy: Boolean,
    motionEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        animationSpec = motion.slowEffectsSpec(),
        label = "canvasStageContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.onPrimaryContainer else colorScheme.onSurface,
        animationSpec = motion.slowEffectsSpec(),
        label = "canvasStageContent",
    )

    Surface(
        shape = CanvasStageShape,
        color = containerColor,
        contentColor = contentColor,
        modifier =
            modifier
                .clip(CanvasStageShape)
                .toggleable(
                    value = enabled,
                    enabled = !busy,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            CanvasLoopReel(
                enabled = enabled,
                motionEnabled = motionEnabled,
                modifier = Modifier.width(CanvasReelWidth),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                CanvasStatusPill(status = status)
                Text(
                    text = stringResource(R.string.canvas_enable),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.archivetune_canvas_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    enabled = !busy,
                    thumbContent =
                        if (enabled) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun CanvasLoopReel(
    enabled: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val fan by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = motion.slowSpatialSpec(),
        label = "canvasReelFan",
    )
    val frameColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.primary else colorScheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasReelFrame",
    )
    val glyphColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasReelGlyph",
    )
    val frameShape = RoundedCornerShape(22.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .aspectRatio(9f / 15f)
                .clearAndSetSemantics {},
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(vertical = 10.dp)
                    .graphicsLayer {
                        rotationZ = -12f * fan
                        translationX = -14.dp.toPx() * fan
                    }.clip(frameShape)
                    .background(colorScheme.tertiary.copy(alpha = 0.28f + 0.22f * fan)),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(vertical = 5.dp)
                    .graphicsLayer {
                        rotationZ = 7f * fan
                        translationX = 10.dp.toPx() * fan
                    }.clip(frameShape)
                    .background(colorScheme.secondary.copy(alpha = 0.24f + 0.26f * fan)),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(frameShape)
                    .background(frameColor),
        ) {
            if (enabled && motionEnabled) {
                CanvasLoopGlyphAnimated(color = glyphColor)
            } else {
                CanvasLoopGlyph(
                    rotation = 0f,
                    morphProgress = 0f,
                    playhead = 0.35f,
                    color = glyphColor,
                )
            }
        }
    }
}

@Composable
private fun CanvasLoopGlyphAnimated(color: Color) {
    val transition = rememberInfiniteTransition(label = "canvasLoop")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 14_000, easing = LinearEasing)),
        label = "canvasLoopRotation",
    )
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "canvasLoopMorph",
    )
    val playhead by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4_800, easing = LinearEasing)),
        label = "canvasLoopPlayhead",
    )
    CanvasLoopGlyph(
        rotation = rotation,
        morphProgress = morphProgress,
        playhead = playhead,
        color = color,
    )
}

@Composable
private fun CanvasLoopGlyph(
    rotation: Float,
    morphProgress: Float,
    playhead: Float,
    color: Color,
) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = rotation }
                        .clip(EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)))
                        .background(color.copy(alpha = 0.22f)),
            )
            Icon(
                painter = painterResource(R.drawable.motion_photos_on),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.24f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playhead.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

@Composable
private fun CanvasStatusPill(
    status: CanvasHealth,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "canvasStatusPill",
        ) { health ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CanvasHealthIndicator(health = health, size = 16.dp)
                Text(
                    text = stringResource(health.labelResource()),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CanvasHealthIndicator(
    health: CanvasHealth,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        if (health == CanvasHealth.CHECKING) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            val dotColor by animateColorAsState(
                targetValue = health.toneColor(),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "canvasHealthDot",
            )
            Box(
                modifier =
                    Modifier
                        .size(size * 0.6f)
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(dotColor),
            )
        }
    }
}

@Composable
private fun CanvasWifiCard(
    checked: Boolean,
    waitingForWifi: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Clover4Leaf, MaterialShapes.Flower) }
    val morphProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "canvasWifiMorph",
    )
    val glyphColor by animateColorAsState(
        targetValue = if (checked) colorScheme.tertiary else colorScheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasWifiGlyph",
    )
    val glyphContent by animateColorAsState(
        targetValue = if (checked) colorScheme.onTertiary else colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasWifiGlyphContent",
    )

    Surface(
        shape = CanvasPanelShape,
        color = colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .clip(CanvasPanelShape)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            CanvasShapedGlyph(
                shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)),
                iconRes = R.drawable.wifi_proxy,
                containerColor = glyphColor,
                contentColor = glyphContent,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.canvas_wifi_only),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.canvas_wifi_only_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(
                    visible = waitingForWifi,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
                ) {
                    Text(
                        text = stringResource(R.string.canvas_health_wifi_required),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun CanvasSourcePicker(
    selected: CanvasSource,
    health: CanvasHealthStatus,
    checking: Boolean,
    canRefresh: Boolean,
    enabled: Boolean,
    motionEnabled: Boolean,
    onSelect: (CanvasSource) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.selectableGroup(),
    ) {
        CanvasAllSourcesTile(
            selected = selected == CanvasSource.ALL,
            health = health,
            enabled = enabled,
            onClick = { onSelect(CanvasSource.ALL) },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.canvas_provider_health),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { heading() },
            )
            CanvasRefreshButton(
                checking = checking,
                enabled = canRefresh,
                motionEnabled = motionEnabled,
                onClick = onRefresh,
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= CanvasWideBreakpoint) CanvasWideColumns else CanvasCompactColumns
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CanvasProvider.entries.chunked(columns).forEach { rowProviders ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                    ) {
                        rowProviders.forEach { provider ->
                            CanvasProviderTile(
                                provider = provider,
                                health = health.of(provider),
                                selected = selected == provider.source,
                                enabled = enabled,
                                onClick = { onSelect(provider.source) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            )
                        }
                        repeat(columns - rowProviders.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selected == CanvasSource.SPOTIFY,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.canvas_spotify_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CanvasRefreshButton(
    checking: Boolean,
    enabled: Boolean,
    motionEnabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
    ) {
        if (checking && motionEnabled) {
            val transition = rememberInfiniteTransition(label = "canvasRefresh")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = LinearEasing)),
                label = "canvasRefreshRotation",
            )
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = stringResource(R.string.refresh),
                modifier = Modifier.graphicsLayer { rotationZ = -rotation },
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = stringResource(R.string.refresh),
            )
        }
    }
}

@Composable
private fun CanvasAllSourcesTile(
    selected: Boolean,
    health: CanvasHealthStatus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasAllContainer",
    )
    val contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface
    val morph = remember { Morph(MaterialShapes.Pill, MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "canvasAllMorph",
    )

    Surface(
        shape = CanvasPanelShape,
        color = containerColor,
        contentColor = contentColor,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CanvasPanelShape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            CanvasShapedGlyph(
                shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)),
                iconRes = R.drawable.all_inclusive,
                containerColor = if (selected) colorScheme.secondary else colorScheme.surfaceContainerHighest,
                contentColor = if (selected) colorScheme.onSecondary else colorScheme.onSurfaceVariant,
                rotation = 45f * morphProgress,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.canvas_source_all),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.canvas_source_all_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .padding(top = 6.dp)
                            .clearAndSetSemantics {},
                ) {
                    CanvasProvider.entries.forEach { provider ->
                        CanvasHealthIndicator(health = health.of(provider), size = 14.dp)
                    }
                }
            }
            CanvasSelectionBadge(selected = selected)
        }
    }
}

@Composable
private fun CanvasProviderTile(
    provider: CanvasProvider,
    health: CanvasHealth,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasProviderContainer",
    )
    val contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface
    val morph = remember(provider) { Morph(provider.restingShape(), MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "canvasProviderMorph",
    )

    Surface(
        shape = CanvasTileShape,
        color = containerColor,
        contentColor = contentColor,
        modifier =
            modifier
                .clip(CanvasTileShape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                CanvasShapedGlyph(
                    shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)),
                    iconRes = provider.iconRes,
                    containerColor = if (selected) colorScheme.secondary else colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) colorScheme.onSecondary else colorScheme.onSurfaceVariant,
                    rotation = 30f * morphProgress,
                )
                Spacer(Modifier.weight(1f))
                CanvasSelectionBadge(selected = selected)
            }
            Text(
                text = stringResource(provider.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CanvasHealthIndicator(health = health, size = 16.dp)
                AnimatedContent(
                    targetState = health,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically { it / 2 })
                            .togetherWith(fadeOut(motion.fastEffectsSpec()) + slideOutVertically { -it / 2 })
                    },
                    label = "canvasProviderHealth",
                ) { value ->
                    Text(
                        text = stringResource(value.labelResource()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(28.dp),
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(MaterialShapes.Cookie6Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasCachePanel(
    model: CanvasSettingsUiModel,
    onCacheLimitSelected: (CanvasCacheOption) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val limitMb = model.configuration.cacheLimitMb
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val progress by animateFloatAsState(
        targetValue = model.cacheProgress,
        animationSpec = motion.slowEffectsSpec(),
        label = "canvasCacheProgress",
    )
    val nearlyFull = limitMb > 0 && model.cacheProgress >= 0.9f
    val meterColor by animateColorAsState(
        targetValue = if (nearlyFull) colorScheme.error else colorScheme.primary,
        animationSpec = motion.defaultEffectsSpec(),
        label = "canvasCacheMeter",
    )

    Surface(
        shape = CanvasPanelShape,
        color = colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = model.cacheSize,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 })
                            .togetherWith(fadeOut(motion.fastEffectsSpec()) + slideOutVertically { -it / 2 })
                    },
                    label = "canvasCacheSize",
                    modifier = Modifier.weight(1f),
                ) { size ->
                    Text(
                        text = stringResource(R.string.size_used, size),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(16.dp))
                CanvasShapedGlyph(
                    shape = MaterialShapes.Puffy.toShape(),
                    iconRes = R.drawable.storage,
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                )
            }

            AnimatedVisibility(
                visible = model.busy || limitMb > 0,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.busy) {
                        LinearWavyProgressIndicator(
                            color = colorScheme.primary,
                            trackColor = colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            color = meterColor,
                            trackColor = colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = percentFormat.format(model.cacheProgress.toDouble()),
                                style = MaterialTheme.typography.labelLarge,
                                color = meterColor,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = model.cacheLimit,
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.canvas_cache_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.max_cache_size),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .semantics { heading() },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
            ) {
                model.cacheOptions.values.forEach { option ->
                    val checked = option.limitMb == limitMb
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { if (!checked) onCacheLimitSelected(option) },
                        enabled = !model.busy,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .semantics { role = Role.RadioButton },
                    ) {
                        AnimatedVisibility(
                            visible = checked,
                            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                        ) {
                            Row {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            }
                        }
                        Text(
                            text = option.displayName(),
                            maxLines = 1,
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onClear,
                enabled = !model.busy,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .heightIn(min = 56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.clear_canvas_cache))
            }
        }
    }
}

@Composable
private fun CanvasShapedGlyph(
    shape: Shape,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(CanvasGlyphSize),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation }
                    .clip(shape)
                    .background(containerColor),
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun CanvasCacheOption.displayName(): String =
    when (limitMb) {
        0 -> stringResource(R.string.disable)
        -1 -> stringResource(R.string.unlimited)
        else -> formattedSize
    }

@Composable
private fun CanvasSettingsDialogs(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
) {
    val onDismiss = remember(onAction) { { onAction(CanvasSettingsAction.DismissDialog) } }
    val onConfirmClear = remember(onAction) { { onAction(CanvasSettingsAction.ClearCache) } }
    when (model.dialog) {
        CanvasSettingsDialog.CLEAR_CACHE -> {
            val colorScheme = MaterialTheme.colorScheme
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    CanvasShapedGlyph(
                        shape = MaterialShapes.SoftBurst.toShape(),
                        iconRes = R.drawable.delete,
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.clear_canvas_cache),
                        textAlign = TextAlign.Center,
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.clear_canvas_cache_dialog),
                            textAlign = TextAlign.Center,
                        )
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = stringResource(R.string.size_used, model.cacheSize),
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmClear,
                        enabled = !model.busy,
                        shapes = ButtonDefaults.shapes(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = colorScheme.error,
                                contentColor = colorScheme.onError,
                            ),
                    ) {
                        Text(stringResource(R.string.clear))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun CanvasSettingsLoading() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        LoadingIndicator(modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun CanvasSettingsFailure(
    @StringRes messageRes: Int,
    onAction: (CanvasSettingsAction) -> Unit,
    bottomPadding: Dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val onRetry = remember(onAction) { { onAction(CanvasSettingsAction.Retry) } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = CanvasScreenGutter)
                .padding(bottom = bottomPadding),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Puffy.toShape())
                    .background(colorScheme.errorContainer),
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.retry))
        }
    }
}

@Stable
private class CanvasEntranceTracker(private val animate: Boolean) {
    private val revealed = HashSet<String>()

    fun claim(key: String): Boolean = animate && revealed.add(key)
}

@Composable
private fun Modifier.canvasEntrance(
    key: String,
    order: Int,
    tracker: CanvasEntranceTracker,
): Modifier {
    val shouldAnimate = remember(key, tracker) { tracker.claim(key) }
    val progress = remember(key, tracker) { Animatable(if (shouldAnimate) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val offsetPx = with(LocalDensity.current) { CanvasEntranceOffset.toPx() }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            delay(order * CanvasEntranceStaggerMillis)
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * offsetPx
    }
}

@Composable
private fun rememberCanvasMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

private fun CanvasHealthStatus.of(provider: CanvasProvider): CanvasHealth =
    when (provider) {
        CanvasProvider.BETTER_LYRICS -> betterLyrics
        CanvasProvider.APPLE_MUSIC -> appleMusic
        CanvasProvider.TIDAL -> tidal
        CanvasProvider.SPOTIFY -> spotify
    }

private fun CanvasHealthStatus.overall(enabled: Boolean): CanvasHealth {
    if (!enabled) return CanvasHealth.DISABLED
    val active = CanvasProvider.entries.map { of(it) }.filter { it != CanvasHealth.NOT_SELECTED }
    return when {
        active.isEmpty() -> CanvasHealth.NOT_CHECKED
        CanvasHealth.CHECKING in active -> CanvasHealth.CHECKING
        CanvasHealth.AVAILABLE in active -> CanvasHealth.AVAILABLE
        CanvasHealth.OFFLINE in active -> CanvasHealth.OFFLINE
        CanvasHealth.WIFI_REQUIRED in active -> CanvasHealth.WIFI_REQUIRED
        CanvasHealth.LOW_DATA_MODE in active -> CanvasHealth.LOW_DATA_MODE
        CanvasHealth.UNAVAILABLE in active -> CanvasHealth.UNAVAILABLE
        CanvasHealth.NOT_CONNECTED in active -> CanvasHealth.NOT_CONNECTED
        else -> CanvasHealth.NOT_CHECKED
    }
}

private fun CanvasProvider.restingShape(): RoundedPolygon =
    when (this) {
        CanvasProvider.BETTER_LYRICS -> MaterialShapes.Cookie6Sided
        CanvasProvider.APPLE_MUSIC -> MaterialShapes.Clover4Leaf
        CanvasProvider.TIDAL -> MaterialShapes.PixelCircle
        CanvasProvider.SPOTIFY -> MaterialShapes.Cookie9Sided
    }

@Composable
private fun CanvasHealth.toneColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when (this) {
        CanvasHealth.AVAILABLE -> colorScheme.primary
        CanvasHealth.UNAVAILABLE,
        CanvasHealth.NOT_CONNECTED,
        -> colorScheme.error

        CanvasHealth.OFFLINE,
        CanvasHealth.WIFI_REQUIRED,
        CanvasHealth.LOW_DATA_MODE,
        -> colorScheme.tertiary

        CanvasHealth.CHECKING,
        CanvasHealth.NOT_CHECKED,
        CanvasHealth.NOT_SELECTED,
        CanvasHealth.DISABLED,
        -> colorScheme.outline
    }
}

@StringRes
private fun CanvasHealth.labelResource(): Int = when (this) {
    CanvasHealth.NOT_CHECKED -> R.string.canvas_health_not_checked
    CanvasHealth.CHECKING -> R.string.canvas_health_checking
    CanvasHealth.AVAILABLE -> R.string.canvas_health_available
    CanvasHealth.UNAVAILABLE -> R.string.canvas_health_unavailable
    CanvasHealth.NOT_CONNECTED -> R.string.spotify_not_connected
    CanvasHealth.NOT_SELECTED -> R.string.canvas_health_not_selected
    CanvasHealth.DISABLED -> R.string.canvas_health_disabled
    CanvasHealth.OFFLINE -> R.string.canvas_health_offline
    CanvasHealth.WIFI_REQUIRED -> R.string.canvas_health_wifi_required
    CanvasHealth.LOW_DATA_MODE -> R.string.canvas_health_low_data
}
