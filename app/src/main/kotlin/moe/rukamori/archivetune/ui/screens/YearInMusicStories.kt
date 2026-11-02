/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.collect.ImmutableList
import kotlin.math.abs
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.recap.RecapAppearance
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.recap.RecapText
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun RecapStories(
    data: RecapData,
    selection: RecapSelection,
    text: RecapText,
    callbacks: RecapCallbacks,
    shareChooserOpen: Boolean,
) {
    val chapter = selection.chapter
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val index = data.chapters.indexOf(chapter).coerceAtLeast(0)
    var paused by rememberSaveable(data.year) { mutableStateOf(false) }
    var elapsed by rememberSaveable(data.year, chapter) { mutableLongStateOf(0L) }
    var pressing by remember(chapter) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(lifecycle, accessibility) {
        val observer = LifecycleEventObserver { _, event -> resumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED) }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        lifecycle.addObserver(observer)
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            accessibility.removeTouchExplorationStateChangeListener(listener)
        }
    }
    val scroll = key(chapter) { rememberScrollState() }
    val move = rememberUpdatedState<(Int) -> Unit> { direction ->
        data.chapters.getOrNull(index + direction)?.let(callbacks.chapter)
    }
    val duration = when (chapter) {
        RecapChapter.INTRO -> 2_000L
        RecapChapter.SUMMARY -> 12_000L
        RecapChapter.END -> 0L
        else -> 8_000L
    }
    val running = resumed && !paused && !pressing && !selection.sharing && !shareChooserOpen &&
        !touchExploration && !scroll.isScrollInProgress
    val disableAnimations by rememberPreference(DisableAnimationsKey, false)
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val motionEnabled = !disableAnimations && durationScale > 0f && !touchExploration
    var motionElapsed by rememberSaveable(data.year, chapter) {
        mutableLongStateOf(if (running && motionEnabled) 0L else 12_000L)
    }
    val motion = remember(chapter, motionEnabled) { { if (motionEnabled) motionElapsed else 12_000L } }
    LaunchedEffect(chapter, running, motionEnabled, durationScale) {
        if (!motionEnabled) {
            motionElapsed = 12_000L
            return@LaunchedEffect
        }
        if (!running) return@LaunchedEffect
        val startedAt = withFrameNanos { it }
        val beforeResume = motionElapsed
        val motionDuration = if (chapter == RecapChapter.END) 1_600L else 12_000L
        while (motionElapsed < motionDuration) {
            val now = withFrameNanos { it }
            motionElapsed = (beforeResume + ((now - startedAt) / 1_000_000.0 / durationScale).toLong())
                .coerceAtMost(motionDuration)
        }
    }
    LaunchedEffect(chapter, running) {
        if (!running || duration == 0L) return@LaunchedEffect
        val startedAt = withFrameNanos { it }
        val elapsedBeforeResume = elapsed
        while (elapsed < duration) {
            val now = withFrameNanos { it }
            elapsed = (elapsedBeforeResume + (now - startedAt) / 1_000_000L).coerceAtMost(duration)
        }
        move.value(1)
    }
    val intro = chapter == RecapChapter.INTRO
    val foreground = Color(if (intro) RecapAppearance.Ink else RecapAppearance.Foreground)
    val colors = remember(intro) {
        if (intro) lightColorScheme(primary = Color(RecapAppearance.Ink), onSurface = Color(RecapAppearance.Ink),
            onSurfaceVariant = Color(RecapAppearance.Ink), surface = Color(RecapAppearance.Cream))
        else darkColorScheme(primary = Color.White, onPrimary = Color(RecapAppearance.Background),
            surface = Color(RecapAppearance.Background), onSurface = Color(RecapAppearance.Foreground),
            onSurfaceVariant = Color(RecapAppearance.Secondary), surfaceContainerHigh = Color(RecapAppearance.Surface))
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            Color(RecapAppearance.Cream), Color(RecapAppearance.Mint),
        ))).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))) {
            RecapStoryBackground(chapter, Modifier.matchParentSize().graphicsLayer {
                val progress = (motion() / 12_000f).coerceIn(0f, 1f)
                scaleX = 1.04f + progress * 0.04f
                scaleY = scaleX
                translationX = (progress - 0.5f) * 12.dp.toPx()
            })
            Column(Modifier.align(Alignment.Center).widthIn(max = 1000.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End) {
                    if (!touchExploration && chapter != RecapChapter.END) {
                        RecapIcon(if (paused) R.drawable.play else R.drawable.pause,
                            if (paused) R.string.recap_resume_highlights else R.string.recap_pause_highlights, { paused = !paused })
                    }
                    FilledTonalIconButton(onClick = { callbacks.story(false) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = foreground.copy(alpha = 0.16f), contentColor = foreground)) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.close))
                    }
                }
                val progressDescription = stringResource(R.string.recap_progress, index + 1, data.chapters.size)
                if (chapter != RecapChapter.END) {
                    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp).semantics { contentDescription = progressDescription }) {
                        val gap = 4.dp.toPx()
                        val segmentWidth = ((size.width - gap * (data.chapters.size - 1)) / data.chapters.size).coerceAtLeast(0f)
                        data.chapters.indices.forEach { segment ->
                            val left = (if (rtl) data.chapters.lastIndex - segment else segment) * (segmentWidth + gap)
                            drawRect(foreground.copy(alpha = 0.28f), Offset(left, 0f), androidx.compose.ui.geometry.Size(segmentWidth, size.height))
                            val fill = when {
                                segment < index -> 1f
                                segment == index && duration > 0 -> elapsed.toFloat() / duration
                                segment == index -> 1f
                                else -> 0f
                            }
                            drawRect(foreground, Offset(left + if (rtl) segmentWidth * (1 - fill) else 0f, 0f),
                                androidx.compose.ui.geometry.Size(segmentWidth * fill, size.height))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(data.year.toString(), style = MaterialTheme.typography.labelLarge, color = foreground)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(R.drawable.small_icon), null, Modifier.size(16.dp), tint = foreground)
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = foreground)
                        }
                    }
                }
                val previousLabel = stringResource(R.string.recap_previous)
                val nextLabel = stringResource(R.string.recap_next)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(previousLabel) { move.value(-1); true })
                        if (index < data.chapters.lastIndex) add(CustomAccessibilityAction(nextLabel) { move.value(1); true })
                    }
                }.pointerInput(chapter, rtl, touchExploration) {
                    if (touchExploration) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressing = true
                        var position = down.position
                        var cancelled = false
                        var dragging = false
                        var releasedAt = down.uptimeMillis
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { cancelled = true; break }
                                position = change.position
                                releasedAt = change.uptimeMillis
                                val distance = position - down.position
                                if (change.isConsumed) { cancelled = true; break }
                                if (abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y)) {
                                    dragging = true
                                    change.consume()
                                } else if (abs(distance.y) > viewConfiguration.touchSlop && !dragging) {
                                    cancelled = true
                                    break
                                }
                            } while (event.changes.any { it.id == down.id && it.pressed })
                            if (!cancelled) {
                                val distance = position.x - down.position.x
                                if (dragging && abs(distance) > viewConfiguration.touchSlop * 3) {
                                    move.value(if ((distance < 0) != rtl) 1 else -1)
                                } else if (!dragging && releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    val previousSide = if (rtl) down.position.x > size.width * 0.65f else down.position.x < size.width * 0.35f
                                    move.value(if (previousSide) -1 else 1)
                                }
                            }
                        } finally {
                            pressing = false
                        }
                    }
                }) {
                    key(chapter) {
                        RecapScene(data, chapter, text, scroll, motion,
                            onReplay = { paused = false; callbacks.replay() }, onClose = { callbacks.story(false) })
                    }
                }
                val soundtrackError = selection.soundtrackFailed || selection.playback.failed ||
                    (!selection.soundtrackStarting && !selection.playback.recapQueue)
                if (soundtrackError) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recap_soundtrack_error), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        TextButton(callbacks.retrySoundtrack) { Text(stringResource(R.string.recap_retry_soundtrack)) }
                    }
                } else if (selection.soundtrackStarting || selection.playback.buffering) {
                    Text(stringResource(R.string.recap_soundtrack_loading), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoryCornerAction(if (selection.playback.muted) R.drawable.volume_off else R.drawable.volume_up,
                        if (selection.playback.muted) R.string.recap_unmute else R.string.recap_mute, callbacks.mute,
                        enabled = selection.playback.recapQueue)
                    Spacer(Modifier.weight(1f))
                    if (touchExploration) {
                        RecapIcon(if (rtl) R.drawable.arrow_forward else R.drawable.arrow_back,
                            R.string.recap_previous, { move.value(-1) }, index > 0)
                        RecapIcon(if (rtl) R.drawable.arrow_back else R.drawable.arrow_forward,
                            R.string.recap_next, { move.value(1) }, index < data.chapters.lastIndex)
                        Spacer(Modifier.weight(1f))
                    }
                    if (chapter != RecapChapter.INTRO && chapter != RecapChapter.END) {
                        if (selection.sharing) ShareProgress() else StoryCornerAction(R.drawable.share, R.string.share, callbacks.share)
                    } else Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun StoryCornerAction(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f),
            contentColor = Color.White)) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(20.dp))
    }
}

private fun Modifier.storyEntrance(
    motion: () -> Long,
    delay: Int = 0,
    fromX: Float = 0f,
    initialScale: Float = 1f,
    drift: Float = 0f,
): Modifier = graphicsLayer {
    val elapsed = motion()
    val fraction = ((elapsed - delay) / 700f).coerceIn(0f, 1f)
    val progress = FastOutSlowInEasing.transform(fraction)
    alpha = fraction
    translationY = (1f - progress) * 28.dp.toPx()
    translationX = (1f - progress) * fromX.dp.toPx() +
        ((elapsed - delay - 700) / 10_000f).coerceIn(0f, 1f) * drift.dp.toPx()
    scaleX = initialScale + (1f - initialScale) * progress
    scaleY = scaleX
}

@Composable
private fun RecapStoryBackground(chapter: RecapChapter, modifier: Modifier = Modifier) {
    val colors = when (chapter) {
        RecapChapter.INTRO -> listOf(Color(RecapAppearance.Cream), Color(RecapAppearance.Mint))
        RecapChapter.TIME, RecapChapter.HABITS -> listOf(Color(0xFF54B9D2), Color(0xFF39899E), Color(0xFF182D2B))
        else -> listOf(Color(0xFF2851A0), Color(0xFF397CE4), Color(0xFF17292F))
    }
    Canvas(modifier.background(Brush.verticalGradient(colors))) {
        drawCircle(Brush.radialGradient(listOf(Color(RecapAppearance.Mint).copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.8f), radius = size.maxDimension * 0.65f),
            radius = size.maxDimension * 0.65f, center = Offset(size.width * 0.85f, size.height * 0.8f))
        drawCircle(Brush.radialGradient(listOf(Color(RecapAppearance.Cream).copy(alpha = 0.25f), Color.Transparent),
            center = Offset(0f, size.height * 0.25f), radius = size.maxDimension * 0.5f),
            radius = size.maxDimension * 0.5f, center = Offset(0f, size.height * 0.25f))
        if (chapter == RecapChapter.END) drawRect(Color.Black.copy(alpha = 0.2f))
    }
}

@Composable
private fun RecapScene(
    data: RecapData,
    chapter: RecapChapter,
    text: RecapText,
    scroll: androidx.compose.foundation.ScrollState,
    motion: () -> Long,
    onReplay: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        val hasArt = chapter !in listOf(RecapChapter.INTRO, RecapChapter.END, RecapChapter.MONTHS, RecapChapter.SUMMARY)
        if (chapter == RecapChapter.MONTHS) {
            val months = data.months.filter { it.available && it.milliseconds > 0 }
            val monthType = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineLarge
                else MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 72.sp)
            Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                months.chunked(if (landscape) 4 else 2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { month ->
                            Box(Modifier.weight(1f).aspectRatio(1f).semantics(mergeDescendants = true) {
                                contentDescription = listOfNotNull(text.month(month.month), month.artist?.title,
                                    text.minutes(month.milliseconds)).joinToString(", ")
                            }) {
                                month.artist?.let { RecapArtwork(it, Modifier.fillMaxSize().clearAndSetSemantics { }
                                    .storyEntrance(motion, delay = (month.month - 1) * 70, initialScale = 0.72f)) }
                                Text(text.month(month.month, short = true), Modifier.align(Alignment.Center)
                                    .storyEntrance(motion, delay = 180 + (month.month - 1) * 70, fromX = if (month.month % 2 == 0) 36f else -36f)
                                    .clearAndSetSemantics { }, color = Color.White,
                                    style = monthType.copy(
                                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 12f)),
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                        repeat((if (landscape) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else if (chapter == RecapChapter.SUMMARY) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth().height(if (landscape) 140.dp else 180.dp).clearAndSetSemantics { }) {
                    data.songs.forEachIndexed { index, entry ->
                        RecapArtwork(entry, Modifier.weight(1f).fillMaxHeight()
                            .storyEntrance(motion, delay = index * 90, initialScale = 1.12f), radius = 4)
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    SceneText(data, chapter, text, motion, onReplay, onClose)
                }
            }
        } else if (landscape && hasArt) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                SceneArtwork(data, chapter, motion, Modifier.weight(1f))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SceneText(data, chapter, text, motion, onReplay, onClose)
                }
            }
        } else if (chapter == RecapChapter.HABITS || chapter == RecapChapter.TIME) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SceneText(data, chapter, text, motion, onReplay, onClose)
                }
                StoryArtworkWall(data, motion, Modifier.fillMaxWidth())
            }
        } else {
            val artFirst = chapter in listOf(RecapChapter.TRACK, RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS)
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                if (hasArt && artFirst) SceneArtwork(data, chapter, motion, Modifier.fillMaxWidth())
                SceneText(data, chapter, text, motion, onReplay, onClose)
                if (hasArt && !artFirst) SceneArtwork(data, chapter, motion, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StoryArtworkWall(data: RecapData, motion: () -> Long, modifier: Modifier = Modifier) {
    val artwork = (data.albums + data.songs).distinctBy { it.artwork ?: it.id }
    if (artwork.isEmpty()) return
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { column ->
                    RecapArtwork(artwork[(row * 3 + column) % artwork.size],
                        Modifier.weight(1f).aspectRatio(1f).storyEntrance(motion,
                            delay = 220 + row * 140 + column * 80, fromX = if (row % 2 == 0) -36f else 36f,
                            initialScale = 0.9f, drift = if (row % 2 == 0) 12f else -12f), radius = 4)
                }
            }
        }
    }
}

@Composable
private fun SceneArtwork(data: RecapData, chapter: RecapChapter, motion: () -> Long, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (chapter) {
            RecapChapter.TIME, RecapChapter.HABITS -> StoryArtworkWall(data, motion, Modifier.widthIn(max = 520.dp).fillMaxWidth())
            RecapChapter.TRACK -> data.songs.firstOrNull()?.let {
                RecapArtwork(it, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
                    .storyEntrance(motion, initialScale = 0.82f), radius = 20)
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
                val entry = when (chapter) {
                    RecapChapter.ARTISTS -> data.artists.firstOrNull()
                    RecapChapter.ALBUMS -> data.albums.firstOrNull()
                    else -> data.songs.firstOrNull()
                }
                entry?.let { RecapArtwork(it, Modifier.size(132.dp).storyEntrance(motion, initialScale = 0.8f), radius = 16) }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SceneText(data: RecapData, chapter: RecapChapter, text: RecapText, motion: () -> Long, onReplay: () -> Unit, onClose: () -> Unit) {
    when (chapter) {
        RecapChapter.INTRO -> Text(stringResource(R.string.recap_story_intro), Modifier.fillMaxWidth()
            .storyEntrance(motion, initialScale = 0.92f).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        RecapChapter.TIME -> {
            SceneHeading(stringResource(R.string.recap_time_statement), Modifier.storyEntrance(motion))
            Text(text.number(data.milliseconds / 60_000), Modifier.storyEntrance(motion, delay = 160, initialScale = 0.9f),
                style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.year_in_music_minutes_unit), Modifier.storyEntrance(motion, delay = 260), style = MaterialTheme.typography.titleLarge)
            Text(text.plays(data.plays) + " · " + stringResource(R.string.recap_distinct_tracks) + " " + text.number(data.distinctTracks),
                modifier = Modifier.storyEntrance(motion, delay = 340), style = MaterialTheme.typography.bodyMedium)
        }
        RecapChapter.TRACK -> data.songs.firstOrNull()?.let {
            Text(text.chapterTitle(chapter), Modifier.storyEntrance(motion, delay = 160), style = MaterialTheme.typography.labelLarge)
            SceneHeading(it.title, Modifier.storyEntrance(motion, delay = 240))
            Text(it.subtitle, Modifier.storyEntrance(motion, delay = 320), style = MaterialTheme.typography.titleMedium)
            Text(text.minutes(it.milliseconds) + " · " + text.plays(it.plays), Modifier.storyEntrance(motion, delay = 400),
                style = MaterialTheme.typography.bodyLarge)
        }
        RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
            SceneHeading(text.chapterTitle(chapter), Modifier.storyEntrance(motion, delay = 120))
            val entries = when (chapter) {
                RecapChapter.ARTISTS -> data.artists
                RecapChapter.ALBUMS -> data.albums
                else -> data.songs
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEachIndexed { index, entry ->
                    Row(Modifier.storyEntrance(motion, delay = 240 + index * 110, fromX = 24f),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text.number(index.toLong() + 1), Modifier.widthIn(min = 24.dp), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        RecapChapter.MONTHS -> Unit
        RecapChapter.HABITS -> {
            val days = text.days(data.habits.longestStreak)
            val statement = stringResource(R.string.recap_streak_statement, days)
            val start = statement.indexOf(days)
            Text(buildAnnotatedString {
                append(statement)
                if (start >= 0) addStyle(SpanStyle(color = Color.White), start, start + days.length)
            }, Modifier.fillMaxWidth().storyEntrance(motion, delay = 80).semantics { heading() },
                color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold)
        }
        RecapChapter.SUMMARY -> {
            Text(data.year.toString(), Modifier.storyEntrance(motion, delay = 220, initialScale = 0.92f),
                style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.displayLarge
                else MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp))
            val facts = buildList {
                data.albums.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_album), it.title, it.subtitle)) }
                data.songs.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_song), it.title, it.subtitle)) }
                add(Triple(stringResource(R.string.year_in_music_minutes_unit), text.number(data.milliseconds / 60_000), null))
                data.artists.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_artist), it.title, null)) }
            }
            facts.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEachIndexed { column, (label, value, subtitle) ->
                        SummaryFact(label, value, subtitle, Modifier.weight(1f)
                            .storyEntrance(motion, delay = 420 + rowIndex * 180 + column * 100))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        RecapChapter.END -> {
            Text(stringResource(R.string.recap_story_end), Modifier.fillMaxWidth().padding(bottom = 32.dp)
                .storyEntrance(motion, initialScale = 0.94f).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            TextButton(onReplay, modifier = Modifier.fillMaxWidth().storyEntrance(motion, delay = 300)) {
                Icon(painterResource(R.drawable.replay), null, Modifier.size(20.dp))
                Text(stringResource(R.string.recap_replay), Modifier.padding(start = 8.dp))
            }
            TextButton(onClose, modifier = Modifier.fillMaxWidth().storyEntrance(motion, delay = 440)) {
                Text(stringResource(R.string.recap_return_overview))
            }
        }
    }
}

@Composable
private fun SceneHeading(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold)
}

@Composable
private fun SummaryFact(label: String, value: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun RecapMosaic(entries: ImmutableList<RecapEntry>, modifier: Modifier = Modifier) {
    val groups = remember(entries) { entries.take(5).chunked(3) }
    if (groups.isEmpty()) return
    Column(modifier.aspectRatio(1.75f).clip(MaterialTheme.shapes.medium).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry -> RecapArtwork(entry, Modifier.weight(1f).fillMaxHeight(), radius = 8) }
            }
        }
    }
}
