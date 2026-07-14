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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp
private val NavigationIndicatorHorizontalInset = 8.dp
private val NavigationIndicatorVerticalInset = 12.dp

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
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val navigationShape =
        remember(isPairedWithMiniPlayer) {
            if (isPairedWithMiniPlayer) {
                RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 28.dp,
                    bottomEnd = 28.dp,
                )
            } else {
                null
            }
        } ?: MaterialTheme.shapes.extraLarge
    val navigationContainerColor =
        if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val motionScheme = MaterialTheme.motionScheme
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, defaultValue = false)
    val density = LocalDensity.current

    // Color of the custom sliding pill that sits behind the selected item.
    val indicatorColor =
        if (pureBlack) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer

    // The built-in per-item indicator just fades in place; hide it so our single pill can slide
    // between items instead. On pure-black we also pin the icon/label colors for contrast.
    val itemColors =
        if (pureBlack) {
            ShortNavigationBarItemDefaults.colors(
                selectedIndicatorColor = Color.Transparent,
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f),
            )
        } else {
            ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
        }

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    // Measured bounds of each item (x in root-space, width in px) and of the row container, used to
    // slide the pill to the exact position of the selected item regardless of layout/insets.
    val itemBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    var containerX by remember { mutableStateOf(0f) }

    val indicatorX = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }

    val selectedBounds = if (selectedIndex >= 0) itemBounds[selectedIndex] else null
    LaunchedEffect(selectedIndex, selectedBounds, containerX, disableAnimations) {
        val bounds = selectedBounds ?: return@LaunchedEffect
        val insetPx = with(density) { NavigationIndicatorHorizontalInset.toPx() }
        val targetX = (bounds.first - containerX) + insetPx
        val targetWidth = (bounds.second - insetPx * 2f).coerceAtLeast(0f)
        val firstPlacement = indicatorWidth.value == 0f
        if (disableAnimations || firstPlacement) {
            indicatorX.snapTo(targetX)
            indicatorWidth.snapTo(targetWidth)
        } else {
            // Run at a fixed motion scale so the slide stays lively even at 0.5x system scale.
            withContext(FullMotionDurationScale) {
                coroutineScope {
                    launch {
                        indicatorX.animateTo(
                            targetValue = targetX,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    launch {
                        indicatorWidth.animateTo(
                            targetValue = targetWidth,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
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
        Surface(
            modifier =
                Modifier
                    .widthIn(max = NavigationBarMaxWidth)
                    .fillMaxWidth()
                    .height(NavigationBarHeight),
            shape = navigationShape,
            color = navigationContainerColor,
            tonalElevation = NavigationBarDefaults.Elevation,
            shadowElevation = NavigationBarDefaults.Elevation,
        ) {
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
                            .onGloballyPositioned { containerX = it.positionInRoot().x },
                    contentAlignment = Alignment.Center,
                ) {
                    // Custom sliding pill indicator, drawn behind the items.
                    if (selectedIndex >= 0 && indicatorWidth.value > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(indicatorX.value.roundToInt(), 0) }
                                    .width(with(density) { indicatorWidth.value.toDp() })
                                    .fillMaxHeight()
                                    .padding(vertical = NavigationIndicatorVerticalInset)
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
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            itemBounds[index] =
                                                coordinates.positionInRoot().x to coordinates.size.width.toFloat()
                                        },
                                icon = {
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
                                },
                                label = {
                                    Text(
                                        text = stringResource(screen.titleId),
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
