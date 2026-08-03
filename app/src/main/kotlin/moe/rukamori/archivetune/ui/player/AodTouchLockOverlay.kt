/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.constants.AodUnlockMethod
import kotlin.math.roundToInt

@Composable
fun AodSlideToLockButton(
    accentColor: Color,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var slideOffsetPx by remember { mutableFloatStateOf(0f) }
    val maxSlidePx = 360f

    val animatedSlideOffset by animateFloatAsState(
        targetValue = slideOffsetPx,
        animationSpec = tween(durationMillis = if (slideOffsetPx == 0f) 200 else 0),
        label = "lockSlideOffset",
    )

    Box(
        modifier = modifier
            .width(260.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (slideOffsetPx >= maxSlidePx * 0.70f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLock()
                        }
                        slideOffsetPx = 0f
                    },
                    onDragCancel = { slideOffsetPx = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        slideOffsetPx = (slideOffsetPx + dragAmount).coerceIn(0f, maxSlidePx)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Slide to Lock >>>",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedSlideOffset.roundToInt(), 0) }
                .padding(3.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun AodTouchLockOverlay(
    isLocked: Boolean,
    unlockMethod: AodUnlockMethod,
    accentColor: Color,
    onUnlock: () -> Unit,
    onAuthenticateBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLocked) return

    val haptic = LocalHapticFeedback.current
    var slideOffsetPx by remember { mutableFloatStateOf(0f) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val animatedSlideOffset by animateFloatAsState(
        targetValue = slideOffsetPx,
        animationSpec = tween(durationMillis = if (slideOffsetPx == 0f) 200 else 0),
        label = "slideOffset",
    )

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val holdDurationMs = 1500L
            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                if (holdProgress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                    break
                }
                delay(16L)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                detectTapGestures { /* consume tap */ }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {},
                    onHorizontalDrag = { _, _ -> },
                    onDragEnd = {},
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {},
                    onVerticalDrag = { _, _ -> }, // swallows notification shade swipe-down
                    onDragEnd = {},
                )
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .fillMaxWidth(),
        ) {
            when (unlockMethod) {
                AodUnlockMethod.SLIDE -> {
                    val maxSlidePx = 360f
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (slideOffsetPx >= maxSlidePx * 0.70f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onUnlock()
                                        }
                                        slideOffsetPx = 0f
                                    },
                                    onDragCancel = { slideOffsetPx = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        slideOffsetPx = (slideOffsetPx + dragAmount).coerceIn(0f, maxSlidePx)
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Slide to Unlock >>>",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(animatedSlideOffset.roundToInt(), 0) }
                                .padding(4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Unlock",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                AodUnlockMethod.FINGERPRINT -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAuthenticateBiometric()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.25f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Unlock",
                                tint = accentColor,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap fingerprint to unlock",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }

                AodUnlockMethod.HOLD -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            },
                    ) {
                        if (holdProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { holdProgress },
                                modifier = Modifier.size(72.dp),
                                color = accentColor,
                                strokeWidth = 4.dp,
                            )
                        }
                        Icon(
                            imageVector = if (holdProgress >= 1f) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Hold to Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hold for 1.5s to unlock",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
