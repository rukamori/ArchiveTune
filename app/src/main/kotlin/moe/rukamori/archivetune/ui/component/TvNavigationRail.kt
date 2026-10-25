/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.screens.Screens

@Composable
fun TvNavigationRail(
    items: List<Screens>,
    selectedItemRoute: String?,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    railToContentFocusRequester: FocusRequester? = null,
    onItemClick: (Screens) -> Unit,
) {
    LaunchedEffect(firstItemFocusRequester) {
        firstItemFocusRequester?.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, screen ->
                val isFirst = index == 0
                val isLast = index == items.lastIndex

                TvNavigationRailItem(
                    screen = screen,
                    selected = selectedItemRoute == screen.route,
                    modifier =
                        Modifier
                            .then(
                                if (isFirst && firstItemFocusRequester != null) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (isLast && contentFocusRequester != null) {
                                    Modifier.focusProperties {
                                        right = contentFocusRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (isFirst && railToContentFocusRequester != null) {
                                    Modifier.focusProperties {
                                        left = railToContentFocusRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    onClick = { onItemClick(screen) },
                )
            }
        }
    }
}

@Composable
private fun TvNavigationRailItem(
    screen: Screens,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "",
    )
    val containerColor by animateColorAsState(
        targetValue =
            when {
                focused -> MaterialTheme.colorScheme.secondaryContainer
                selected -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        label = "",
    )
    val contentColor by animateColorAsState(
        targetValue =
            when {
                focused -> MaterialTheme.colorScheme.onSecondaryContainer
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "",
    )

    Column(
        modifier =
            modifier
                .scale(scale)
                .width(104.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(containerColor)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key(KEYCODE_DPAD_CENTER.toLong()),
                        Key(KEYCODE_ENTER.toLong()),
                        Key(KEYCODE_NUMPAD_ENTER.toLong()),
                        -> {
                            onClick()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter =
                    painterResource(
                        if (selected || focused) screen.iconIdActive else screen.iconIdInactive,
                    ),
                contentDescription = stringResource(screen.titleId),
                tint = contentColor,
            )
        }
        Text(
            text = stringResource(screen.titleId),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
