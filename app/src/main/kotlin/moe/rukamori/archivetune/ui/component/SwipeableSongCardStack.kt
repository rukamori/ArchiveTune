/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class DiscoverCardState(
    val songs: List<SongItem>,
    val currentIndex: Int,
    val isPlayingPreview: Boolean = false,
)

@Composable
fun SwipeableSongCardStack(
    state: DiscoverCardState,
    onSwipeLeft: (SongItem) -> Unit,
    onSwipeRight: (SongItem) -> Unit,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = state.songs
    val currentIndex = state.currentIndex

    if (currentIndex >= songs.size) {
        EmptyStackCard(modifier = modifier)
        return
    }

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val currentSong = songs[currentIndex]
    val nextSong = songs.getOrNull(currentIndex + 1)
    val afterNextSong = songs.getOrNull(currentIndex + 2)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val cardHeight = (maxHeight - 12.dp).coerceAtLeast(120.dp)
                val cardWidth = (cardHeight * 0.70f).coerceAtMost(maxWidth - 24.dp)
                val dragRotation = (offsetX.value / 35f).coerceIn(-15f, 15f)

                // Calculate real-time smooth expansion for background cards as top card is swiped
                val dragProgress = (abs(offsetX.value) / 350f).coerceIn(0f, 1f)

                val nextCardScale = 0.94f + (0.06f * dragProgress)
                val nextCardOffsetY = (10f * (1f - dragProgress)).dp

                val afterNextCardScale = 0.88f + (0.06f * dragProgress)
                val afterNextCardOffsetY = (20f - (10f * dragProgress)).dp

                // Background 2nd card down
                afterNextSong?.let { song ->
                    SongCardItem(
                        song = song,
                        isPlaying = false,
                        isTopCard = false,
                        modifier =
                            Modifier
                                .size(cardWidth, cardHeight)
                                .scale(afterNextCardScale)
                                .offset(y = afterNextCardOffsetY),
                    )
                }

                // Background 1st card down
                nextSong?.let { song ->
                    SongCardItem(
                        song = song,
                        isPlaying = false,
                        isTopCard = false,
                        modifier =
                            Modifier
                                .size(cardWidth, cardHeight)
                                .scale(nextCardScale)
                                .offset(y = nextCardOffsetY),
                    )
                }

                // Pulsing Background Aura Glow behind active card when playing
                if (state.isPlayingPreview) {
                    val infiniteTransition = rememberInfiniteTransition(label = "background_aura_glow")
                    val auraAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 0.90f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "auraAlpha",
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(cardWidth + 40.dp, cardHeight + 40.dp)
                                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                                .rotate(dragRotation)
                                .clip(RoundedCornerShape(36.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = auraAlpha),
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = auraAlpha * 0.6f),
                                                Color.Transparent,
                                            ),
                                    ),
                                )
                                .blur(32.dp),
                    )
                }

                // Top active swipeable card
                var lastDragDeltaX = remember { 0f }

                SongCardItem(
                    song = currentSong,
                    isPlaying = state.isPlayingPreview,
                    isTopCard = true,
                    onClick = onTogglePreview,
                    modifier =
                        Modifier
                            .size(cardWidth, cardHeight)
                            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                            .rotate(dragRotation)
                            .pointerInput(currentSong.id) {
                                detectDragGestures(
                                    onDragCancel = {
                                        scope.launch {
                                            launch { offsetX.animateTo(0f, tween(120)) }
                                            launch { offsetY.animateTo(0f, tween(120)) }
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val x = offsetX.value
                                            val y = offsetY.value
                                            val fastFlickRight = x > 70f && lastDragDeltaX > 6f
                                            val fastFlickLeft = x < -70f && lastDragDeltaX < -6f

                                            when {
                                                x > 100f || fastFlickRight -> {
                                                    offsetX.animateTo(1400f, tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                    onSwipeRight(currentSong)
                                                    offsetX.snapTo(0f)
                                                    offsetY.snapTo(0f)
                                                }

                                                x < -100f || fastFlickLeft -> {
                                                    offsetX.animateTo(-1400f, tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                    onSwipeLeft(currentSong)
                                                    offsetX.snapTo(0f)
                                                    offsetY.snapTo(0f)
                                                }

                                                else -> {
                                                    val isTap = abs(x) < 12f && abs(y) < 12f
                                                    if (isTap) {
                                                        onTogglePreview()
                                                    }
                                                    launch { offsetX.animateTo(0f, tween(120)) }
                                                    launch { offsetY.animateTo(0f, tween(120)) }
                                                }
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        lastDragDeltaX = dragAmount.x
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                            offsetY.snapTo(offsetY.value + (dragAmount.y * 0.3f))
                                        }
                                    },
                                )
                            },
                    overlayBadge = {
                        when {
                            offsetX.value > 50f -> {
                                SwipeBadge(
                                    text = "LIKE",
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.align(Alignment.TopStart),
                                )
                            }

                            offsetX.value < -50f -> {
                                SwipeBadge(
                                    text = "SKIP",
                                    color = Color(0xFFF44336),
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                    },
                )
            }

            // Action controls below card stack: 3 buttons (Skip, Play/Pause, Like)
            DiscoverControlBar(
                isPlayingPreview = state.isPlayingPreview,
                onSkip = {
                    scope.launch {
                        launch { offsetY.animateTo(40f, tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        offsetX.animateTo(-1400f, tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        onSwipeLeft(currentSong)
                        offsetX.snapTo(0f)
                        offsetY.snapTo(0f)
                    }
                },
                onTogglePreview = onTogglePreview,
                onLike = {
                    scope.launch {
                        launch { offsetY.animateTo(40f, tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        offsetX.animateTo(1400f, tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        onSwipeRight(currentSong)
                        offsetX.snapTo(0f)
                        offsetY.snapTo(0f)
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp, top = 4.dp),
            )

        }

        // Dynamic Edge Glow Gradients positioned ON TOP of the card stack
        val rightGlowAlpha = (offsetX.value / 250f).coerceIn(0f, 0.85f)
        val leftGlowAlpha = (-offsetX.value / 250f).coerceIn(0f, 0.85f)

        if (rightGlowAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(140.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF4CAF50).copy(alpha = rightGlowAlpha),
                                    ),
                            ),
                        ),
            )
        }

        if (leftGlowAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(140.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFF44336).copy(alpha = leftGlowAlpha),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )
        }
    }
}

@Composable
private fun SongCardItem(
    song: SongItem,
    isPlaying: Boolean,
    isTopCard: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    overlayBadge: @Composable (BoxScope.() -> Unit)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow_transition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glow_alpha",
    )

    val cardBorderModifier =
        if (isPlaying && isTopCard) {
            Modifier.border(
                width = 3.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                shape = RoundedCornerShape(24.dp),
            )
        } else {
            Modifier
        }

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .then(cardBorderModifier)
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Paused Dim Overlay on Top Card
            if (isTopCard && !isPlaying) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                )

                // Translucent Pause Icon in Center
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.pause),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Gradient scrim for text readability
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.95f),
                                    ),
                                startY = 200f,
                            ),
                        ),
            )

            // Song Info at Card Bottom
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                song.album?.name?.let { albumName ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            overlayBadge?.invoke(this)
        }
    }
}

@Composable
private fun SwipeBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier =
            modifier
                .padding(20.dp)
                .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DiscoverControlBar(
    isPlayingPreview: Boolean,
    onSkip: () -> Unit,
    onTogglePreview: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Skip Button (Left - Red X)
        IconButton(
            onClick = onSkip,
            modifier =
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
        }

        // 2. Play / Pause Button (Center - Play/Pause)
        IconButton(
            onClick = onTogglePreview,
            modifier =
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (isPlayingPreview) R.drawable.pause else R.drawable.play,
                    ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }

        // 3. Like & Add to Discover Playlist Button (Right - Green Heart)
        IconButton(
            onClick = onLike,
            modifier =
                Modifier
                    .size(56.dp)
                    .background(Color(0xFF4CAF50), CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun EmptyStackCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Discover completato!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tutte le canzoni salvate sono pronte nella tua playlist Discover Settimanale.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
