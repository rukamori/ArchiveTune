/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.recap.RecapAppearance
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapEntryKind
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.recap.RecapText
import moe.rukamori.archivetune.utils.rememberPreference

private class CircleSpot(val x: Float, val y: Float, val size: Float)

private val MonthCircles = listOf(
    CircleSpot(0.22f, 0.14f, 0.54f), CircleSpot(0.9f, 0.2f, 0.5f), CircleSpot(0.04f, 0.52f, 0.42f),
    CircleSpot(0.58f, 0.52f, 0.56f), CircleSpot(0.2f, 0.88f, 0.54f), CircleSpot(0.92f, 0.86f, 0.5f),
)

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
    val light = RecapAppearance.scene(chapter).light
    val foreground by animateColorAsState(Color(if (light) RecapAppearance.Ink else RecapAppearance.Foreground),
        tween(if (motionEnabled) 480 else 0), label = "recap_foreground")
    val page = MaterialTheme.colorScheme.surface
    val pageDark = page.luminance() < 0.5f
    val atmosphere = remember(data.year) { RecapAppearance.atmosphere(data.year).map(::Color) }
    val colors = remember(light) {
        if (light) lightColorScheme(primary = Color(RecapAppearance.Ink), onPrimary = Color.White,
            onSurface = Color(RecapAppearance.Ink), onSurfaceVariant = Color(RecapAppearance.Ink).copy(alpha = 0.7f),
            surface = Color.Transparent, surfaceContainerHigh = Color(RecapAppearance.Ink).copy(alpha = 0.08f))
        else darkColorScheme(primary = Color.White, onPrimary = Color(RecapAppearance.Ink),
            surface = Color.Transparent, onSurface = Color(RecapAppearance.Foreground),
            onSurfaceVariant = Color(RecapAppearance.Secondary), surfaceContainerHigh = Color(RecapAppearance.Surface))
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(page)) {
            RecapAtmosphere(atmosphere, motionEnabled, Modifier.matchParentSize().graphicsLayer { alpha = if (pageDark) 0.5f else 0.85f })
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f)))
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 6.dp).clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))) {
                AnimatedContent(chapter, transitionSpec = {
                    fadeIn(tween(if (motionEnabled) 560 else 0)) togetherWith fadeOut(tween(if (motionEnabled) 560 else 0))
                }, label = "recap_scene_background") { scene ->
                    RecapStoryBackground(scene, motionEnabled, Modifier.fillMaxSize())
                }
                Column(Modifier.align(Alignment.Center).widthIn(max = 1000.dp).fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.weight(1f))
                        if (!touchExploration && chapter != RecapChapter.END) {
                            StoryButton(if (paused) R.drawable.play else R.drawable.pause,
                                if (paused) R.string.recap_resume_highlights else R.string.recap_pause_highlights, { paused = !paused },
                                container = foreground.copy(alpha = 0.16f), content = foreground)
                        }
                        StoryButton(R.drawable.close, R.string.close, { callbacks.story(false) },
                            container = foreground.copy(alpha = 0.16f), content = foreground)
                    }
                    val progressDescription = stringResource(R.string.recap_progress, index + 1, data.chapters.size)
                    if (chapter != RecapChapter.END) {
                        Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp).semantics { contentDescription = progressDescription }) {
                            val gap = 5.dp.toPx()
                            val corner = CornerRadius(size.height / 2f)
                            val segmentWidth = ((size.width - gap * (data.chapters.size - 1)) / data.chapters.size).coerceAtLeast(0f)
                            data.chapters.indices.forEach { segment ->
                                val left = (if (rtl) data.chapters.lastIndex - segment else segment) * (segmentWidth + gap)
                                drawRoundRect(foreground.copy(alpha = 0.3f), Offset(left, 0f), Size(segmentWidth, size.height), corner)
                                val fill = when {
                                    segment < index -> 1f
                                    segment == index && duration > 0 -> elapsed.toFloat() / duration
                                    segment == index -> 1f
                                    else -> 0f
                                }
                                if (fill > 0f) drawRoundRect(foreground, Offset(left + if (rtl) segmentWidth * (1 - fill) else 0f, 0f),
                                    Size(segmentWidth * fill, size.height), corner)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.recap_reel_label, (data.year % 100).toString().padStart(2, '0')),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = foreground)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(painterResource(R.drawable.small_icon), null, Modifier.size(18.dp), tint = foreground)
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, color = foreground)
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
                            Text(stringResource(R.string.recap_soundtrack_error), Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.8f))
                            TextButton(callbacks.retrySoundtrack) {
                                Text(stringResource(R.string.recap_retry_soundtrack), color = foreground, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (selection.soundtrackStarting || selection.playback.buffering) {
                        Text(stringResource(R.string.recap_soundtrack_loading), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.8f))
                    }
                    CompositionLocalProvider(LocalContentColor provides foreground) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StoryButton(if (selection.playback.muted) R.drawable.volume_off else R.drawable.volume_up,
                                if (selection.playback.muted) R.string.recap_unmute else R.string.recap_mute, callbacks.mute,
                                container = Color.Black.copy(alpha = 0.32f), content = Color.White, enabled = selection.playback.recapQueue)
                            Spacer(Modifier.weight(1f))
                            if (touchExploration) {
                                RecapIcon(if (rtl) R.drawable.arrow_forward else R.drawable.arrow_back,
                                    R.string.recap_previous, { move.value(-1) }, index > 0)
                                RecapIcon(if (rtl) R.drawable.arrow_back else R.drawable.arrow_forward,
                                    R.string.recap_next, { move.value(1) }, index < data.chapters.lastIndex)
                                Spacer(Modifier.weight(1f))
                            }
                            if (chapter != RecapChapter.INTRO && chapter != RecapChapter.END) {
                                if (selection.sharing) ShareProgress()
                                else StoryButton(R.drawable.share, R.string.share, callbacks.share,
                                    container = Color.Black.copy(alpha = 0.32f), content = Color.White)
                            } else Spacer(Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryButton(icon: Int, label: Int, onClick: () -> Unit, container: Color, content: Color, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = container,
        contentColor = content.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
        }
    }
}

private fun springCurve(t: Float): Float = if (t >= 1f) 1f else 1f - exp(-6.5f * t) * cos(9.5f * t)

private fun Modifier.storyEntrance(
    motion: () -> Long,
    delay: Int = 0,
    fromX: Float = 0f,
    fromY: Float = 32f,
    initialScale: Float = 1f,
    blur: Float = 14f,
    drift: Float = 0f,
): Modifier = graphicsLayer {
    val elapsed = motion()
    val fraction = ((elapsed - delay) / 900f).coerceIn(0f, 1f)
    val progress = springCurve(fraction)
    alpha = (fraction * 2.4f).coerceIn(0f, 1f)
    translationY = (1f - progress) * fromY.dp.toPx()
    translationX = (1f - progress) * fromX.dp.toPx() +
        ((elapsed - delay - 900) / 10_000f).coerceIn(0f, 1f) * drift.dp.toPx()
    scaleX = initialScale + (1f - initialScale) * progress
    scaleY = scaleX
    val radius = (1f - fraction * 1.6f).coerceIn(0f, 1f) * blur.dp.toPx()
    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) {
        BlurEffect(radius, radius, TileMode.Decal)
    } else null
}

@Composable
private fun RecapStoryBackground(chapter: RecapChapter, animate: Boolean, modifier: Modifier = Modifier) {
    val scene = RecapAppearance.scene(chapter)
    val base = remember(scene) { scene.base.map(::Color) }
    val glow = remember(scene) { scene.glow.map { Color(it).copy(alpha = 0.6f) } }
    Box(modifier.background(Brush.verticalGradient(base))) {
        RecapAtmosphere(glow, animate, Modifier.matchParentSize())
        if (!scene.light) Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f),
        )))
    }
}

@Composable
private fun RecapScene(
    data: RecapData,
    chapter: RecapChapter,
    text: RecapText,
    scroll: ScrollState,
    motion: () -> Long,
    onReplay: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        when (chapter) {
            RecapChapter.INTRO -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recap_story_intro), Modifier.storyEntrance(motion, initialScale = 0.94f, blur = 20f)
                    .semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            }
            RecapChapter.TIME, RecapChapter.HABITS -> {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (chapter == RecapChapter.TIME) {
                            val minutes = text.number(data.milliseconds / 60_000)
                            StoryStatement(stringResource(R.string.recap_month_listening, minutes, data.year.toString()),
                                stringResource(R.string.recap_listening_minutes, minutes), motion)
                            Text(text.plays(data.plays) + " · " + stringResource(R.string.recap_distinct_tracks) + " " +
                                text.number(data.distinctTracks), Modifier.storyEntrance(motion, delay = 420),
                                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.72f))
                        } else {
                            val days = text.days(data.habits.longestStreak)
                            StoryStatement(stringResource(R.string.recap_streak_statement, days), days, motion)
                        }
                    }
                    StoryArtworkWall(data, motion, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
            }
            RecapChapter.TRACK -> data.songs.firstOrNull()?.let { entry ->
                val content: @Composable () -> Unit = {
                    Text(text.chapterTitle(chapter), Modifier.storyEntrance(motion), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.72f))
                    Text(entry.title, Modifier.storyEntrance(motion, delay = 320).semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White,
                        textAlign = if (landscape) TextAlign.Start else TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (entry.subtitle.isNotBlank()) Text(entry.subtitle, Modifier.storyEntrance(motion, delay = 400),
                        style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.72f),
                        textAlign = if (landscape) TextAlign.Start else TextAlign.Center)
                    Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), Modifier.storyEntrance(motion, delay = 480),
                        style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f))
                }
                if (landscape) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        HeroArtwork(entry, motion, Modifier.weight(1f).fillMaxHeight(0.8f))
                        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        HeroArtwork(entry, motion, Modifier.fillMaxWidth(0.78f).padding(bottom = 18.dp))
                        content()
                    }
                }
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
                val entries = when (chapter) {
                    RecapChapter.ARTISTS -> data.artists
                    RecapChapter.ALBUMS -> data.albums
                    else -> data.songs
                }
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight).padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    Text(text.chapterTitle(chapter), Modifier.padding(bottom = 8.dp).storyEntrance(motion, blur = 18f).semantics { heading() },
                        style = storyDisplay(MaterialTheme.typography.displaySmall), fontWeight = FontWeight.Bold, color = Color.White)
                    entries.forEachIndexed { position, entry -> RankedStoryRow(entry, position + 1, text, motion, 240 + position * 110) }
                }
            }
            RecapChapter.MONTHS -> MonthWall(data, text, motion)
            RecapChapter.SUMMARY -> Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth().height(if (landscape) 150.dp else 220.dp).clearAndSetSemantics { }) {
                    data.songs.take(5).forEachIndexed { position, entry ->
                        RecapArtwork(entry, Modifier.weight(1f).fillMaxHeight()
                            .storyEntrance(motion, delay = position * 90, fromY = -48f, initialScale = 1.08f, blur = 0f), shape = RectangleShape)
                    }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    val yearSize = with(LocalDensity.current) { 96.dp.toSp() }
                    Text(data.year.toString(), Modifier.storyEntrance(motion, delay = 260, initialScale = 0.9f, blur = 22f),
                        color = Color.White, style = TextStyle(fontSize = yearSize, lineHeight = yearSize,
                            fontWeight = FontWeight.Bold, letterSpacing = (-2).sp))
                    val facts = buildList {
                        data.albums.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_album), it.title, it.subtitle)) }
                        data.songs.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_song), it.title, it.subtitle)) }
                        add(Triple(stringResource(R.string.year_in_music_minutes_unit), text.number(data.milliseconds / 60_000), null))
                        data.artists.firstOrNull()?.let { add(Triple(stringResource(R.string.recap_top_artist), it.title, null)) }
                    }
                    facts.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEachIndexed { column, (label, value, subtitle) ->
                                SummaryFact(label, value, subtitle, Modifier.weight(1f)
                                    .storyEntrance(motion, delay = 460 + rowIndex * 180 + column * 100))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            RecapChapter.END -> Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.recap_story_end), Modifier.storyEntrance(motion, initialScale = 0.94f, blur = 20f)
                    .semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(28.dp))
                TextButton(onReplay, modifier = Modifier.storyEntrance(motion, delay = 300)) {
                    Icon(painterResource(R.drawable.replay), null, Modifier.size(20.dp), tint = Color.White)
                    Text(stringResource(R.string.recap_replay), Modifier.padding(start = 8.dp), color = Color.White,
                        style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClose, modifier = Modifier.storyEntrance(motion, delay = 420)) {
                    Text(stringResource(R.string.recap_return_overview), color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun storyDisplay(style: TextStyle): TextStyle =
    if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineLarge else style

@Composable
private fun StoryStatement(statement: String, emphasis: String, motion: () -> Long) {
    val start = statement.indexOf(emphasis)
    Text(buildAnnotatedString {
        append(statement)
        if (start >= 0) addStyle(SpanStyle(color = Color.White), start, start + emphasis.length)
    }, Modifier.fillMaxWidth().storyEntrance(motion, delay = 80, blur = 18f).semantics { heading() },
        color = Color.White.copy(alpha = 0.6f), style = storyDisplay(MaterialTheme.typography.displaySmall),
        fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HeroArtwork(entry: RecapEntry, motion: () -> Long, modifier: Modifier) {
    RecapArtwork(entry, modifier.widthIn(max = 340.dp).aspectRatio(1f)
        .storyEntrance(motion, delay = 120, initialScale = 0.84f, blur = 0f)
        .shadow(28.dp, if (entry.kind == RecapEntryKind.ARTIST) CircleShape else RoundedCornerShape(14.dp)), radius = 14)
}

@Composable
private fun RankedStoryRow(entry: RecapEntry, rank: Int, text: RecapText, motion: () -> Long, delay: Int) {
    Row(Modifier.storyEntrance(motion, delay = delay, fromX = 40f, fromY = 0f, blur = 8f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text.number(rank.toLong()), Modifier.widthIn(min = 28.dp), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        RecapArtwork(entry, Modifier.size(if (rank == 1) 76.dp else 60.dp), radius = 8)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = if (rank == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StoryArtworkWall(data: RecapData, motion: () -> Long, modifier: Modifier = Modifier) {
    val artwork = remember(data) { (data.albums + data.songs).distinctBy { it.artwork ?: it.id } }
    if (artwork.isEmpty()) return
    BoxWithConstraints(modifier.clipToBounds().clearAndSetSemantics { }) {
        val tile = maxWidth * 0.3f
        val gap = 14.dp
        val rowWidth = tile * 5 + gap * 4
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(3) { row ->
                val direction = if (row % 2 == 0) -1f else 1f
                Row(Modifier.requiredWidth(rowWidth).graphicsLayer {
                    val drift = (motion() / 12_000f).coerceIn(0f, 1f) * tile.toPx() * 0.9f
                    translationX = (if (row % 2 == 0) 0.25f else -0.35f) * tile.toPx() + direction * drift
                }, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(5) { column ->
                        RecapArtwork(artwork[(row * 5 + column + row) % artwork.size],
                            Modifier.width(tile).aspectRatio(1f).storyEntrance(motion,
                                delay = 200 + row * 130 + column * 60, fromX = 28f * -direction, initialScale = 0.92f, blur = 0f),
                            radius = 6)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthWall(data: RecapData, text: RecapText, motion: () -> Long) {
    val months = remember(data) { data.months.filter { it.available && it.milliseconds > 0 } }
    val description = months.joinToString("; ") { month ->
        listOfNotNull(text.month(month.month), month.artist?.title, text.minutes(month.milliseconds)).joinToString(", ")
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = description }) {
        val circles = remember(months) { months.mapNotNull { it.artist }.distinctBy { it.id }.take(MonthCircles.size) }
        circles.forEachIndexed { position, artist ->
            val spot = MonthCircles[position]
            val diameter = maxWidth * spot.size
            RecapArtwork(artist, Modifier.size(diameter)
                .offset(x = maxWidth * spot.x - diameter / 2, y = maxHeight * spot.y - diameter / 2)
                .storyEntrance(motion, delay = position * 140, fromY = 0f, initialScale = 0.6f, blur = 0f,
                    drift = if (position % 2 == 0) 14f else -14f)
                .clearAndSetSemantics { }, shape = CircleShape)
        }
        val names = remember(months, text) { months.map { text.month(it.month, short = true) } }
        if (names.isNotEmpty()) {
            val rows = 4
            val fontSize = with(LocalDensity.current) { (maxHeight / 4.6f).coerceIn(48.dp, 128.dp).toSp() }
            val width = maxWidth
            Column(Modifier.fillMaxSize().clearAndSetSemantics { }, verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(rows) { row ->
                    val line = remember(names, row) {
                        List(6) { names[(row * 3 + it) % names.size] }.joinToString(" ")
                    }
                    val direction = if (row % 2 == 0) -1f else 1f
                    Text(line, Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer {
                            val drift = (motion() / 12_000f).coerceIn(0f, 1f) * width.toPx() * 0.22f
                            translationX = (if (row % 2 == 0) 0f else -0.42f) * width.toPx() + direction * drift
                        }
                        .storyEntrance(motion, delay = 220 + row * 120, fromX = 90f * -direction, fromY = 0f, blur = 10f),
                        color = Color.White, softWrap = false, maxLines = 1,
                        style = TextStyle(fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp))
                }
            }
        }
    }
}

@Composable
private fun SummaryFact(label: String, value: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle.uppercase(LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
