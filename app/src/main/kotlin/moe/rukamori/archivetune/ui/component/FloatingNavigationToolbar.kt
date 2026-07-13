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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.ui.screens.Screens

private val NavigationBarMaxWidth = 420.dp
private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp

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
    Box(
        modifier = modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .widthIn(max = NavigationItemsMaxWidth)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(vertical = NavigationItemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEach { screen ->
                            val selected = isSelected(screen)
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
                                modifier = Modifier.weight(1f),
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
