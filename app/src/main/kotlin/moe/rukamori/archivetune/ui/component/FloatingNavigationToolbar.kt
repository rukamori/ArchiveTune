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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.Screens

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val hasOverflowAction =
        (onShuffleClick != null && shuffleIconRes != null) ||
            onMusicRecognitionClick != null ||
            onMusicTogetherClick != null
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
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    (if (hasOverflowAction) Modifier.weight(1f) else Modifier.widthIn(max = 420.dp).fillMaxWidth())
                        .height(72.dp),
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
                                    } else {
                                        onItemClick(screen, selected)
                                    }
                                }
                            }

                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = onClick,
                            icon = {
                                Icon(
                                    painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                                    contentDescription = null,
                                )
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

            if (hasOverflowAction) {
                NavigationOverflowFab(
                    pureBlack = pureBlack,
                    onShuffleClick = onShuffleClick,
                    shuffleIconRes = shuffleIconRes,
                    shuffleContentDescription = shuffleContentDescription,
                    onMusicRecognitionClick = onMusicRecognitionClick,
                    musicRecognitionContentDescription = musicRecognitionContentDescription,
                    onMusicTogetherClick = onMusicTogetherClick,
                )
            }
        }
    }
}

@Composable
private fun NavigationOverflowFab(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Box {
        FloatingActionButton(
            onClick = { fabMenuExpanded = !fabMenuExpanded },
            modifier = Modifier.size(56.dp),
            containerColor = floatingToolbarFabContainerColor(),
            contentColor = floatingToolbarFabContentColor(),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more),
            )
        }

        DropdownMenu(
            expanded = fabMenuExpanded,
            onDismissRequest = { fabMenuExpanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicRecognitionClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.mic),
                                contentDescription =
                                    musicRecognitionContentDescription.ifEmpty {
                                        stringResource(R.string.music_recognition)
                                    },
                            )
                        }
                    }
                },
                enabled = onMusicRecognitionClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        disabledLeadingIconColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    ),
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicTogetherClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.multi_user),
                                contentDescription = null,
                            )
                        }
                    }
                },
                enabled = onMusicTogetherClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        disabledLeadingIconColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    ),
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        fabMenuExpanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                            contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(shuffleIconRes),
                                    contentDescription =
                                        shuffleContentDescription.ifEmpty {
                                            stringResource(R.string.shuffle)
                                        },
                                )
                            }
                        }
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                            leadingIconColor =
                                if (pureBlack) {
                                    Color.White.copy(
                                        alpha = 0.82f,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                )
            }
        }
    }
}

@Composable
private fun floatingToolbarFabContainerColor(): Color = MaterialTheme.colorScheme.primary

@Composable
private fun floatingToolbarFabContentColor(): Color = MaterialTheme.colorScheme.onPrimary

@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
