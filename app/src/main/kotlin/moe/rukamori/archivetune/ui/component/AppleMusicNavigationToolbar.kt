/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.screens.Screens

@Composable
internal fun AppleMusicNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    hazeState: HazeState,
    modifier: Modifier,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?,
) {
    val groupedItems = remember(items) { items.filterNot { it == Screens.Search } }
    val hasSearch = Screens.Search in items
    val shape = remember { RoundedCornerShape(percent = 50) }
    val selectedIndex = groupedItems.indexOfFirst(isSelected)
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val indicatorPosition = animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = if (animationsDisabled) snap() else motionScheme.fastSpatialSpec(),
        label = "appleMusicSelectionPosition",
    )
    val indicatorAlpha = animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else motionScheme.fastEffectsSpec(),
        label = "appleMusicSelectionAlpha",
    )
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = NavigationBarMaxWidth)
                .fillMaxWidth()
                .height(NavigationBarHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (groupedItems.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape)) {
                    FloatingBarGlassBackground(
                        hazeState = hazeState,
                        shape = shape,
                        pureBlack = pureBlack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                            .drawWithCache {
                                val itemWidth = size.width / groupedItems.size
                                val radius = CornerRadius(size.height / 2f)
                                onDrawBehind {
                                    val position = indicatorPosition.value.coerceIn(0f, groupedItems.lastIndex.toFloat())
                                    val left = if (layoutDirection == LayoutDirection.Ltr) {
                                        position * itemWidth
                                    } else {
                                        size.width - (position + 1f) * itemWidth
                                    }
                                    drawRoundRect(
                                        color = indicatorColor,
                                        topLeft = Offset(left, 0f),
                                        size = Size(itemWidth, size.height),
                                        cornerRadius = radius,
                                        alpha = indicatorAlpha.value,
                                    )
                                }
                            },
                    ) {
                        groupedItems.forEach { screen ->
                            key(screen.route) {
                                AppleMusicNavigationItem(
                                    screen = screen,
                                    selected = isSelected(screen),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onItemClick = onItemClick,
                                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                                )
                            }
                        }
                    }
                }
            }
            if (hasSearch) {
                Box(modifier = Modifier.size(NavigationBarHeight).clip(shape)) {
                    FloatingBarGlassBackground(
                        hazeState = hazeState,
                        shape = shape,
                        pureBlack = pureBlack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppleMusicNavigationItem(
                        screen = Screens.Search,
                        selected = isSelected(Screens.Search),
                        modifier = Modifier.fillMaxSize(),
                        onItemClick = onItemClick,
                        onSearchItemDoubleClick = onSearchItemDoubleClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleMusicNavigationItem(
    screen: Screens,
    selected: Boolean,
    modifier: Modifier,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val tint = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = if (animationsDisabled) snap() else motionScheme.fastEffectsSpec(),
        label = "appleMusicItemTint",
    )
    val scale = animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = if (animationsDisabled) snap() else motionScheme.fastSpatialSpec(),
        label = "appleMusicItemScale",
    )
    val shape = remember { RoundedCornerShape(percent = 50) }
    val onClick = rememberNavigationItemClick(screen, selected, onItemClick, onSearchItemDoubleClick)
    val title = stringResource(screen.titleId)
    Box(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = if (animationsDisabled) snap() else motionScheme.fastEffectsSpec(),
            label = "appleMusicItemIcon",
        ) { active ->
            Icon(
                painter = painterResource(if (active) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = null,
                tint = tint.value,
                modifier = Modifier.size(32.dp).graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            )
        }
    }
}
