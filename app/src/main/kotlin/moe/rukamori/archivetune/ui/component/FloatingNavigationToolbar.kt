/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.toImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.FloatingNavigationBarMaxWidth
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.NavigationBarStyle
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * Shared handle for the frosted navigation bar: the app records its content into [layer] each
 * frame, and the bar draws that layer (offset by the recorded content's root position) behind a
 * blur so the pixels underneath show through frosted.
 */
class NavigationBarBackdrop(
    val layer: GraphicsLayer,
) {
    var contentOffsetInRoot: Offset = Offset.Zero
}

/**
 * The app-content capture used for frosted-glass surfaces (navigation bar, mini player). Null when
 * no frosted surface is enabled or the device cannot blur (below Android 12).
 */
val LocalNavigationBarBackdrop = compositionLocalOf<NavigationBarBackdrop?> { null }

private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp

// Frosted nav-bar backdrop blur radius, in px (RenderEffect works in raw pixels).
private const val FrostedNavBarBlurRadiusPx = 60f

// How much of the blurred backdrop shows through the opaque bar. The bar is always drawn on a
// fully opaque surface and the blurred content is composited on top at this alpha, so page
// brightness can only ever modulate the bar by this fraction — it reads the same over any screen,
// and if the backdrop layer has nothing under the bar the result is simply a solid bar.
private const val FrostedNavBarOverlayAlpha = 0.30f

// The sliding pill wraps just the icon (like the stock indicator), so the label sits below it,
// outside the bubble. These are the standard Material3 active-indicator dimensions.
private val NavigationIndicatorWidth = 56.dp
private val NavigationIndicatorHeight = 32.dp

// The floating pill uses a larger, softer blob around the selected icon (label stays outside),
// tinted with the accent color like the reference bar.
private val FloatingNavigationIndicatorWidth = 64.dp
private val FloatingNavigationIndicatorHeight = 42.dp

/**
 * Forces the signature navigation-bar motion (the sliding pill + icon pop) to always run at its
 * full, intended duration, ignoring the system "Animator duration scale" developer setting. Users
 * who run the OS at 0.5x still get expressive tab-switch motion, while the in-app "disable
 * animations" toggle continues to fully bypass these animations.
 */
private object FullMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    style: NavigationBarStyle = NavigationBarStyle.DEFAULT,
    frostedBlur: Boolean = false,
    frostedBackdrop: NavigationBarBackdrop? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val isFloating = style == NavigationBarStyle.FLOATING
    val navigationShape =
        remember(isPairedWithMiniPlayer, isFloating) {
            when {
                // A detached pill keeps the full radius; it never docks with the mini player.
                isFloating -> RoundedCornerShape(percent = 50)
                isPairedWithMiniPlayer ->
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = 28.dp,
                        bottomEnd = 28.dp,
                    )
                else -> null
            }
        } ?: MaterialTheme.shapes.extraLarge
    // True backdrop blur on Android 12+ uses RenderEffect (hardware-accelerated, every frame).
    // Below API 31, RenderEffect is unavailable — the pre-S path falls back to a periodically
    // captured + CPU-blurred bitmap (see [rememberPreSFrostedBitmap]) so the frosted setting
    // still has a visible effect on older devices instead of degrading to a plain solid bar.
    val canBlurBackdrop = frostedBlur && frostedBackdrop != null
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val navigationContainerColor =
        if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val motionScheme = MaterialTheme.motionScheme
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, defaultValue = false)
    val (hideNavigationLabels) = rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)
    val density = LocalDensity.current

    // Color of the custom sliding pill that sits behind the selected item's icon. The floating
    // pill uses a translucent accent blob with accent-tinted icon/label (reference-bar look); the
    // docked bar keeps the stock secondary-container treatment.
    val indicatorColor =
        when {
            isFloating -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            pureBlack -> Color.White.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    val indicatorWidth = if (isFloating) FloatingNavigationIndicatorWidth else NavigationIndicatorWidth
    val indicatorHeight = if (isFloating) FloatingNavigationIndicatorHeight else NavigationIndicatorHeight

    // The built-in per-item indicator just fades in place; hide it so our single pill can slide
    // between items instead. On pure-black we also pin the icon/label colors for contrast.
    val itemColors =
        when {
            isFloating ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            pureBlack ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f),
                )
            else -> ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
        }

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    // Measured center of each item's icon (root-space) and the row container's top-left, so the pill
    // can slide to the exact icon position regardless of layout/insets. Only the icon is tracked so
    // the bubble hugs the icon and leaves the text label outside of it.
    val iconCenters = remember { mutableStateMapOf<Int, Offset>() }
    var containerPos by remember { mutableStateOf(Offset.Zero) }

    val indicatorX = remember { Animatable(0f) }
    var indicatorY by remember { mutableStateOf(0f) }
    var indicatorPlaced by remember { mutableStateOf(false) }

    val selectedCenter = if (selectedIndex >= 0) iconCenters[selectedIndex] else null
    LaunchedEffect(selectedIndex, selectedCenter, containerPos, disableAnimations, indicatorWidth, indicatorHeight) {
        val center = selectedCenter ?: return@LaunchedEffect
        val widthPx = with(density) { indicatorWidth.toPx() }
        val heightPx = with(density) { indicatorHeight.toPx() }
        val targetX = (center.x - containerPos.x) - widthPx / 2f
        // All icons share a row, so Y is constant; compute it directly (no animation needed).
        indicatorY = (center.y - containerPos.y) - heightPx / 2f
        val firstPlacement = !indicatorPlaced
        if (disableAnimations || firstPlacement) {
            indicatorX.snapTo(targetX)
            indicatorPlaced = true
        } else {
            // Run at a fixed motion scale so the slide stays lively even at 0.5x system scale.
            withContext(FullMotionDurationScale) {
                indicatorX.animateTo(
                    targetValue = targetX,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        var barPositionInRoot by remember { mutableStateOf(Offset.Zero) }
        Surface(
            modifier =
                Modifier
                    .widthIn(max = if (isFloating) FloatingNavigationBarMaxWidth else NavigationBarMaxWidth)
                    .fillMaxWidth()
                    .height(NavigationBarHeight)
                    .onGloballyPositioned { barPositionInRoot = it.positionInRoot() },
            shape = navigationShape,
            color = navigationContainerColor,
            tonalElevation = NavigationBarDefaults.Elevation,
            shadowElevation = if (isFloating) 8.dp else NavigationBarDefaults.Elevation,
        ) {
            if (canBlurBackdrop && frostedBackdrop != null) {
                if (isPreS) {
                    // Pre-Android 12: RenderEffect is unavailable. Capture the app-content
                    // GraphicsLayer periodically (see [rememberPreSFrostedBitmap]), blur it on
                    // the CPU via ImageBlurUtils, and composite the result on top of the opaque
                    // bar at the same bounded alpha as the S+ path. The bar surface's shape
                    // already clips its content, so the overlay is correctly clipped to the
                    // pill shape.
                    val blurredBitmap = rememberPreSFrostedBitmap(
                        backdrop = frostedBackdrop,
                        blurRadiusPx = FrostedNavBarBlurRadiusPx,
                    )
                    if (blurredBitmap != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = FrostedNavBarOverlayAlpha
                                        clip = true
                                    }.drawBehind {
                                        val offset = frostedBackdrop.contentOffsetInRoot - barPositionInRoot
                                        translate(offset.x, offset.y) {
                                            drawImage(blurredBitmap)
                                        }
                                    },
                        )
                    }
                } else {
                    // Frosted glass on top of an always-opaque bar: the app content captured this frame
                    // is shifted so the region underneath lines up, blurred, and composited at a bounded
                    // alpha. Page brightness can only modulate the bar by that fraction, so the bar
                    // looks the same over every screen — and if the captured layer has nothing under
                    // the bar, the result is simply the solid bar (never see-through).
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect =
                                        BlurEffect(
                                            radiusX = FrostedNavBarBlurRadiusPx,
                                            radiusY = FrostedNavBarBlurRadiusPx,
                                            edgeTreatment = TileMode.Clamp,
                                        )
                                    alpha = FrostedNavBarOverlayAlpha
                                    clip = true
                                }.drawBehind {
                                    val offset = frostedBackdrop.contentOffsetInRoot - barPositionInRoot
                                    translate(offset.x, offset.y) {
                                        drawLayer(frostedBackdrop.layer)
                                    }
                                },
                    )
                }
            }
            ShortNavigationBar(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0),
                arrangement = ShortNavigationBarArrangement.EqualWeight,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { containerPos = it.positionInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    // Custom sliding pill indicator, drawn behind the icons (label stays outside it).
                    if (selectedIndex >= 0 && indicatorPlaced) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset(indicatorX.value.roundToInt(), indicatorY.roundToInt()) }
                                    .width(indicatorWidth)
                                    .height(indicatorHeight)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(indicatorColor),
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .widthIn(max = NavigationItemsMaxWidth)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(vertical = NavigationItemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, screen ->
                            val selected = isSelected(screen)
                            // Tactile "pop": when an item becomes selected, its icon springs
                            // from 80% up past 100% and settles back, giving the bottom pill a
                            // lively bounce on every tab switch (skipped when animations are off).
                            val iconScale = remember(screen) { Animatable(1f) }
                            LaunchedEffect(selected, disableAnimations) {
                                if (disableAnimations) {
                                    iconScale.snapTo(1f)
                                } else if (selected) {
                                    iconScale.snapTo(0.8f)
                                    // Fixed motion scale keeps the pop expressive at 0.5x too.
                                    withContext(FullMotionDurationScale) {
                                        iconScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                ),
                                        )
                                    }
                                } else {
                                    iconScale.snapTo(1f)
                                }
                            }
                            val onDoubleClick =
                                remember(screen, onSearchItemDoubleClick) {
                                    if (screen == Screens.Search) onSearchItemDoubleClick else null
                                }
                            val lastClickTime = remember(screen) { mutableLongStateOf(0L) }
                            val onClick =
                                remember(screen, selected, onItemClick, onDoubleClick) {
                                    {
                                        val currentTime = SystemClock.uptimeMillis()
                                        val isDoubleClick =
                                            onDoubleClick != null &&
                                                currentTime - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                                        lastClickTime.longValue = if (isDoubleClick) 0L else currentTime
                                        if (isDoubleClick) {
                                            onDoubleClick?.invoke()
                                            Unit
                                        } else {
                                            onItemClick(screen, selected)
                                        }
                                    }
                                }

                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = onClick,
                                colors = itemColors,
                                modifier = Modifier.weight(1f),
                                icon = {
                                    // Measure the icon's own bounds so the pill hugs only the icon.
                                    Box(
                                        modifier =
                                            Modifier.onGloballyPositioned { coordinates ->
                                                val pos = coordinates.positionInRoot()
                                                iconCenters[index] =
                                                    Offset(
                                                        pos.x + coordinates.size.width / 2f,
                                                        pos.y + coordinates.size.height / 2f,
                                                    )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Crossfade(
                                            targetState = selected,
                                            animationSpec = motionScheme.fastEffectsSpec(),
                                            label = "navigationItemIcon",
                                        ) { isSelected ->
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                    ),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.graphicsLayer {
                                                        scaleX = iconScale.value
                                                        scaleY = iconScale.value
                                                    },
                                            )
                                        }
                                    }
                                },
                                label = if (hideNavigationLabels) {
                                    null
                                } else {
                                    {
                                        Text(
                                            text = stringResource(screen.titleId),
                                            maxLines = 1,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pre-Android 12 (pre-S) fallback for the frosted-glass surfaces (navigation bar, mini player).
 *
 * On Android 12+ the frosted effect uses [BlurEffect] (RenderEffect, hardware-accelerated, every
 * frame). Below API 31 RenderEffect is unavailable, so the previous implementation silently fell
 * back to a plain opaque surface — the frosted setting appeared to do nothing on older devices.
 *
 * This helper restores a real frosted effect on pre-S by periodically capturing the app-content
 * [GraphicsLayer] (the same one the S+ path uses) to a [Bitmap], running it through
 * [ImageBlurUtils.blur] (a pure-CPU stack blur that needs no RenderEffect), and publishing the
 * result as an [ImageBitmap] the caller draws with the same offset/alpha as the S+ path.
 *
 * Throttling: the capture+blur runs on [Dispatchers.Default] every [updateIntervalMs] (default
 * ~5 fps). Frosted glass doesn't need 60 fps — the underlying content rarely changes faster than
 * that, and a full-screen stack blur every frame would tank pre-S hardware. The first frame is
 * captured immediately so the bar isn't transparent for a full interval on first composition.
 *
 * Returns `null` while the layer has no size (before first `record { ... }`) or if the capture
 * fails — the caller should keep the opaque base surface in that case.
 */
@Composable
internal fun rememberPreSFrostedBitmap(
    backdrop: NavigationBarBackdrop?,
    blurRadiusPx: Float,
    updateIntervalMs: Long = 200L,
): ImageBitmap? {
    if (backdrop == null) return null
    var blurred by remember(backdrop, blurRadiusPx, updateIntervalMs) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(backdrop, blurRadiusPx, updateIntervalMs) {
        // First frame: capture immediately so the bar isn't opaque-only for a full interval.
        while (isActive) {
            val layer = backdrop.layer
            val w = layer.size.width
            val h = layer.size.height
            if (w > 0 && h > 0) {
                try {
                    val next = withContext(Dispatchers.Default) {
                        val imageBitmap = layer.toImageBitmap()
                        val androidBmp: Bitmap = imageBitmap.asAndroidBitmap()
                        val blurredBmp = ImageBlurUtils.blur(androidBmp, blurRadiusPx)
                        blurredBmp.asImageBitmap()
                    }
                    blurred = next
                } catch (_: Throwable) {
                    // Capture or blur failed (e.g. OOM, native crash on some devices) — keep the
                    // previous frame; the opaque base surface is still visible underneath.
                }
            }
            delay(updateIntervalMs)
        }
    }
    return blurred
}
