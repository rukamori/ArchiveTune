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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.Screens

val DPAD_CENTER_KEY = Key(KEYCODE_DPAD_CENTER.toLong())
val ENTER_KEY = Key(KEYCODE_ENTER.toLong())
val NUMPAD_ENTER_KEY = Key(KEYCODE_NUMPAD_ENTER.toLong())

/**
 * A reusable D-pad focus handler for Android TV.
 *
 * Applies focus requester, focusable, and D-pad key event handling to [modifier],
 * then emits [content] within that modifier.
 */
@Composable
fun TVFocusHandler(
    focusRequester: FocusRequester,
    onDpadUp: () -> Unit = {},
    onDpadDown: () -> Unit = {},
    onDpadLeft: () -> Unit = {},
    onDpadRight: () -> Unit = {},
    onDpadCenter: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    modifier
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

            when (keyEvent.key) {
                Key.DirectionUp -> {
                    onDpadUp()
                    true
                }
                Key.DirectionDown -> {
                    onDpadDown()
                    true
                }
                Key.DirectionLeft -> {
                    onDpadLeft()
                    true
                }
                Key.DirectionRight -> {
                    onDpadRight()
                    true
                }
                DPAD_CENTER_KEY, ENTER_KEY, NUMPAD_ENTER_KEY -> {
                    onDpadCenter()
                    true
                }
                Key.Back -> {
                    onBack()
                    true
                }
                else -> false
            }
        }

    content()
}

/**
 * A wrapper for list/grid items that makes them D-pad navigable on TV.
 * Provides visual focus indication and D-pad click handling.
 */
@Composable
fun TVFocusableItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focused: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor = when {
        focused -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val contentColor = when {
        focused -> MaterialTheme.colorScheme.onSecondaryContainer
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    DPAD_CENTER_KEY, ENTER_KEY, NUMPAD_ENTER_KEY -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/**
 * A TV-optimized navigation rail item with D-pad support and visual focus indication.
 */
@Composable
fun TVNavigationRailItemEnhanced(
    screen: Screens,
    selected: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale = if (focused || selected) 1.06f else 1f

    val containerColor = when {
        focused -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val contentColor = when {
        focused -> MaterialTheme.colorScheme.onSecondaryContainer
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    DPAD_CENTER_KEY, ENTER_KEY, NUMPAD_ENTER_KEY -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                role = Role.Tab,
                onClick = onClick,
            )
            .size(110.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .background(containerColor)
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A `combinedClickable` variant that also responds to D-pad center/enter on TV.
 * Replaces `.focusable().combinedClickable(...)` for TV-navigable items.
 */
fun Modifier.tvCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = this
    .focusable()
    .combinedClickable(
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
    )
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (!enabled) return@onKeyEvent false
        when (keyEvent.key) {
            DPAD_CENTER_KEY, ENTER_KEY, NUMPAD_ENTER_KEY -> {
                onClick()
                true
            }
            else -> false
        }
    }

/**
 * Adds D-pad directional navigation within a section container.
 * Use on the section container to allow D-pad up/down/left/right to move focus between items.
 */
fun Modifier.tvSectionFocus(
    onDpadUp: () -> Unit = {},
    onDpadDown: () -> Unit = {},
    onDpadLeft: () -> Unit = {},
    onDpadRight: () -> Unit = {},
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (keyEvent.key) {
            Key.DirectionUp -> {
                onDpadUp()
                true
            }
            Key.DirectionDown -> {
                onDpadDown()
                true
            }
            Key.DirectionLeft -> {
                onDpadLeft()
                true
            }
            Key.DirectionRight -> {
                onDpadRight()
                true
            }
            else -> false
        }
    }

/**
 * Adds D-pad center/enter click support to a modifier.
 * On TV, `combinedClickable` does not handle key events, so this ensures D-pad center/enter
 * triggers the click action. Safe to use on non-TV devices.
 */
fun Modifier.tvDpadClickable(onClick: () -> Unit): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (keyEvent.key) {
            DPAD_CENTER_KEY, ENTER_KEY, NUMPAD_ENTER_KEY -> {
                onClick()
                true
            }
            else -> false
        }
    }
