/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from Flamingo (yos.music.player) ui/widgets/YosLyricView.kt — GPLv3,
 * https://github.com/shouryadixitisverycool/Flamingo
 *
 * Apple-Music-style karaoke lyric view: per-character gradient wipe via
 * TextMeasurer/drawText, distance-based per-line blur (up to 8dp), interlude
 * countdown dots, 1600ms auto-scroll resume, golden-ratio scroll anchor,
 * 550ms yosEasing line spacing animation. Data arrives through
 * [FlamingoLyricsData] instead of Flamingo's global view-model singletons.
 */

package moe.rukamori.archivetune.ui.player.flamingo

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

val flamingoEasing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)

/**
 * Main lyric text style, ported from Flamingo's top-level MainTextStyle
 * (30.5sp / 40.5sp lineHeight, user-configurable weight and line balance).
 */
fun flamingoMainTextStyle(
    fontWeight: FontWeight,
    lineBalance: Boolean,
): TextStyle = TextStyle(
    fontSize = 30.5.sp,
    lineHeight = 40.5.sp,
    fontWeight = fontWeight,
    letterSpacing = 0.05.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    lineBreak = LineBreak(
        strategy = if (lineBalance) LineBreak.Strategy.Balanced else LineBreak.Strategy.Simple,
        LineBreak.Strictness.Default,
        LineBreak.WordBreak.Default,
    ),
)

/** Flamingo's lyric font weight setting names mapped to FontWeight. */
fun flamingoFontWeightFromName(name: String): FontWeight = when (name) {
    "Thin" -> FontWeight.Thin
    "ExtraLight" -> FontWeight.ExtraLight
    "Light" -> FontWeight.Light
    "Regular" -> FontWeight.Normal
    "Medium" -> FontWeight.Medium
    "SemiBold" -> FontWeight.SemiBold
    "Bold" -> FontWeight.Bold
    "ExtraBold" -> FontWeight.ExtraBold
    "Black" -> FontWeight.Black
    else -> FontWeight.ExtraBold
}

/**
 * Ported from Flamingo's YosLyricView.
 */
@Composable
fun FlamingoLyricView(
    lyricsData: FlamingoLyricsData?,
    liveTimeLambda: () -> Int,
    onSeek: (Int) -> Unit,
    translationLambda: () -> Boolean,
    blurLambda: () -> Boolean,
    noLrcText: String,
    weightLambda: () -> Boolean,
    mainTextStyle: TextStyle,
    modifier: Modifier,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val mainTextBasicColor = Color(0xFFF2F2F2)
    val subTextBasicColor = Color(0xFF919191)

    val lrcEntries = lyricsData?.lrcEntries.orEmpty()
    val lineEndTimes = lyricsData?.lineEndTimes.orEmpty()
    val lineTransliterations = lyricsData?.lineTransliterations.orEmpty()
    val lineSubtitles = lyricsData?.lineSubtitles.orEmpty()
    val isTtmlLyrics = lyricsData?.isTtmlLyrics == true
    val otherSideForLines = lyricsData?.otherSideForLines.orEmpty()

    if (lrcEntries.isEmpty() || otherSideForLines.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight(if (weightLambda()) 0.56f else 1f)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    onBackClick()
                },
        ) {
            Text(
                text = noLrcText,
                fontSize = 18.sp,
                color = mainTextBasicColor,
            )
        }
    } else {
        val scrollState = rememberLazyListState()
        val currentLyricIndex = remember { mutableIntStateOf(-1) }
        val ttmlLiveTime = remember { mutableIntStateOf(liveTimeLambda()) }

        FlamingoWrapper {
            LaunchedEffect(isTtmlLyrics, lrcEntries) {
                while (isTtmlLyrics) {
                    ttmlLiveTime.intValue = liveTimeLambda()
                    delay(10L)
                }
            }
        }

        // Non-TTML: advance the active line from playback position (port of
        // Flamingo's MediaController.updateLyricsRunnable writer, which updated
        // MainViewModelObject.syncLyricIndex for status-bar lyrics).
        FlamingoWrapper {
            LaunchedEffect(isTtmlLyrics, lrcEntries) {
                if (isTtmlLyrics) return@LaunchedEffect
                while (true) {
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        line.first().first > liveTime
                    }
                    if (nextIndex != -1 && nextIndex - 1 != currentLyricIndex.intValue) {
                        currentLyricIndex.intValue = nextIndex - 1
                    } else if (nextIndex == -1 && currentLyricIndex.intValue != lrcEntries.size - 1) {
                        currentLyricIndex.intValue = lrcEntries.size - 1
                    }
                    delay(100)
                }
            }
        }

        val focusedLyricIndices = remember(
            "FlamingoLyricView_focusedLyricIndices",
            isTtmlLyrics,
            lrcEntries,
            lineEndTimes,
        ) {
            derivedStateOf {
                if (!isTtmlLyrics) {
                    return@derivedStateOf listOf(currentLyricIndex.intValue)
                }

                val liveTime = ttmlLiveTime.intValue
                val activeIndices = lrcEntries.mapIndexedNotNull { index, line ->
                    val lineStart = line.firstOrNull()?.first ?: return@mapIndexedNotNull null
                    val lineEnd = lineEndTimes.getOrNull(index)
                        ?: lrcEntries.getOrNull(index + 1)?.firstOrNull()?.first
                        ?: lineStart

                    if (liveTime >= lineStart && liveTime < lineEnd.coerceAtLeast(lineStart + 1f)) {
                        index
                    } else {
                        null
                    }
                }

                activeIndices.ifEmpty {
                    if (currentLyricIndex.intValue >= 0) listOf(currentLyricIndex.intValue) else emptyList()
                }
            }
        }

        val focusedLyricAnchorIndex = remember(
            "FlamingoLyricView_focusedLyricAnchorIndex",
            focusedLyricIndices,
        ) {
            derivedStateOf {
                focusedLyricIndices.value.firstOrNull() ?: currentLyricIndex.intValue
            }
        }

        val blankSpacer: (LazyListScope.() -> Unit) = {
            item {
                Box(
                    modifier = Modifier
                        .height(70.dp),
                ) {
                }
            }
        }

        val enableLyricScroll = remember { mutableStateOf(true) }

        val height = rememberSaveable(key = "FlamingoLyricView_height") { mutableIntStateOf(0) }

        val targetWeight = 0.0618f
        val targetOffset = rememberSaveable(height.intValue, key = "FlamingoLyricView_targetOffset") {
            height.intValue * targetWeight
        }

        val space = 0.dp

        val measurer = rememberTextMeasurer(
            cacheSize = 32,
        )

        val visibleItems = remember("FlamingoLyricView_visibleItems") {
            derivedStateOf {
                scrollState.layoutInfo.visibleItemsInfo
            }
        }
        val targetItem = remember("FlamingoLyricView_targetItem") {
            derivedStateOf {
                visibleItems.value.find {
                    it.index == focusedLyricAnchorIndex.value + 1
                }
            }
        }
        val currentOffset = remember("FlamingoLyricView_currentOffset", targetOffset) {
            derivedStateOf {
                targetItem.value?.offset ?: targetOffset.toInt()
            }
        }
        val scrollDistance = remember("FlamingoLyricView_scrollDistance", targetOffset) {
            derivedStateOf {
                currentOffset.value - targetOffset
            }
        }
        val nowFirst = remember("FlamingoLyricView_nowFirst") {
            derivedStateOf {
                scrollState.firstVisibleItemIndex
            }
        }
        val supportBlur = rememberSaveable(key = "FlamingoLyricView_supportBlur") {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }

        val isUserScrolling = remember { mutableStateOf(false) }
        val nestedScrollConnection = remember {
            @Stable
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    isUserScrolling.value = true
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    isUserScrolling.value = false
                    return super.onPostFling(consumed, available)
                }
            }
        }

        FlamingoWrapper {
            LaunchedEffect(isUserScrolling.value) {
                if (isUserScrolling.value) {
                    enableLyricScroll.value = false
                } else {
                    delay(1600)
                    enableLyricScroll.value = true
                }
            }
        }

        FlamingoWrapper {
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        onBackClick()
                    }
                    .nestedScroll(nestedScrollConnection)
                    .onSizeChanged {
                        if (height.intValue == 0 && it.height != 0) {
                            height.intValue = it.height
                        }
                    },
            ) {
                blankSpacer()
                itemsIndexed(
                    items = lrcEntries,
                    key = { _, lines -> lines },
                ) { index, lines ->
                    val isCurrent = remember(lines) {
                        derivedStateOf {
                            focusedLyricIndices.value.contains(index)
                        }
                    }

                    val isTop = remember(lines) {
                        derivedStateOf {
                            index == (focusedLyricAnchorIndex.value - 1)
                        }
                    }

                    val showStateAnimation = remember(index) {
                        derivedStateOf {
                            (focusedLyricAnchorIndex.value in scrollState.layoutInfo.visibleItemsInfo.map { it.index - 1 } && focusedLyricAnchorIndex.value >= 0) && enableLyricScroll.value
                        }
                    }

                    val isLyricEmpty = rememberSaveable(lines) {
                        mutableStateOf(
                            lines.all { it.second.isBlank() },
                        )
                    }

                    key(lines) {
                        val translation = remember(
                            index,
                            lines,
                            isTtmlLyrics,
                            translationLambda(),
                            lineTransliterations,
                            lineSubtitles,
                        ) {
                            if (isTtmlLyrics) {
                                val secondaryText = if (translationLambda()) {
                                    lineSubtitles.getOrNull(index)
                                } else {
                                    lineTransliterations.getOrNull(index)
                                }
                                secondaryText?.ifBlank { null }
                            } else {
                                val str = lines.last().second
                                str.ifBlank { null }
                            }
                        }

                        val blur = remember(index) {
                            derivedStateOf {
                                if (!showStateAnimation.value || focusedLyricIndices.value.contains(index) || !blurLambda() || !supportBlur) {
                                    0f
                                } else {
                                    (abs(index - focusedLyricAnchorIndex.value) * 2.5f).coerceAtMost(
                                        8f,
                                    )
                                }
                            }
                        }

                        val otherSide = remember(index) {
                            otherSideForLines.getOrElse(index) { false }
                        }

                        FlamingoWrapper {
                            LyricItem(
                                isCurrentLambda = {
                                    isCurrent.value
                                },
                                isTopLambda = {
                                    isTop.value
                                },
                                mainLyric = lines.dropLast(1),
                                translation = translation,
                                showTranslation = if (isTtmlLyrics) translation != null else translationLambda(),
                                subTextSize = 16,
                                blur = { blur.value },
                                mainTextBasicColor = mainTextBasicColor,
                                subTextBasicColor = subTextBasicColor,
                                otherSide = otherSide,
                                liveTimeLambda = liveTimeLambda,
                                measurer = measurer,
                                mainTextStyle = mainTextStyle,
                                isLyricEmpty = { isLyricEmpty.value },
                                nextTime = {
                                    if (index + 1 > lrcEntries.size - 1) {
                                        0f
                                    } else {
                                        lrcEntries[(index + 1)].first().first
                                    }
                                },
                            ) {
                                FlamingoHaptics.doubleClick(context)
                                currentLyricIndex.intValue = index
                                onSeek(lines.first().first.toInt())
                            }
                        }
                    }

                    key(index) {
                        FlamingoWrapper {
                            val show = remember(index) {
                                derivedStateOf { !isLyricEmpty.value || isCurrent.value }
                            }

                            val thisScrollDistance = if (targetItem.value != null) {
                                floatToDp(scrollDistance.value / (visibleItems.value.size))
                            } else {
                                0.dp
                            }

                            val thisTargetHeight = remember(index) {
                                mutableStateOf(space)
                            }

                            FlamingoWrapper {
                                LaunchedEffect(focusedLyricAnchorIndex.value) {
                                    if (visibleItems.value.isEmpty()) {
                                        return@LaunchedEffect
                                    }
                                    if (index >= focusedLyricAnchorIndex.value - 1 && showStateAnimation.value && show.value) {
                                        val weight =
                                            (1f - ((index - (nowFirst.value)) / visibleItems.value.size))
                                        delay((550 * (1f - weight)).toLong())
                                        thisTargetHeight.value =
                                            (thisScrollDistance * weight).plus(space)
                                        delay(
                                            ((550 / 1.95f) * weight).toLong(),
                                        )
                                        thisTargetHeight.value = space
                                    } else if (show.value) {
                                        thisTargetHeight.value = space
                                    } else {
                                        thisTargetHeight.value = 0.dp
                                    }
                                }
                            }

                            val offset = animateDpAsState(
                                targetValue = thisTargetHeight.value,
                                animationSpec = if (thisTargetHeight.value == 0.dp || thisTargetHeight.value == space) {
                                    androidx.compose.animation.core.spring(
                                        stiffness = 105F,
                                        dampingRatio = 1f,
                                        visibilityThreshold = 0.0001.dp,
                                    )
                                } else {
                                    tween(
                                        durationMillis = 550,
                                        easing = flamingoEasing,
                                    )
                                },
                            )

                            FlamingoWrapper {
                                Spacer(modifier = Modifier.height(offset.value))
                            }
                        }
                    }
                }
                blankSpacer()
                item("extra_blank") {
                    Spacer(modifier = Modifier.height(500.dp))
                }
            }
        }

        FlamingoWrapper {
            LaunchedEffect(focusedLyricAnchorIndex.value, translationLambda()) {
                try {
                    if (enableLyricScroll.value) {
                        if (
                            try {
                                if (focusedLyricAnchorIndex.value - 1 < 0) {
                                    false
                                } else {
                                    lrcEntries[(focusedLyricAnchorIndex.value - 1)][1].second.isBlank()
                                }
                            } catch (_: Exception) {
                                false
                            }
                        ) {
                            return@LaunchedEffect
                        }

                        if (targetItem.value != null) {
                            scrollState.animateScrollBy(
                                scrollDistance.value,
                                animationSpec = tween(
                                    durationMillis = 550,
                                    easing = flamingoEasing,
                                ),
                            )
                        } else {
                            scrollState.animateScrollToItem(
                                index = (focusedLyricAnchorIndex.value + 1).coerceAtLeast(0),
                                scrollOffset = -targetOffset.toInt(),
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        FlamingoWrapper {
            LaunchedEffect(lrcEntries) {
                currentLyricIndex.intValue = -1
            }
        }

        FlamingoWrapper {
            LaunchedEffect(Unit) {
                try {
                    if (currentLyricIndex.intValue != -1) {
                        return@LaunchedEffect
                    }
                    val liveTime = liveTimeLambda()
                    val nextIndex = lrcEntries.indexOfFirst { line ->
                        line.first().first > liveTime
                    }

                    if (nextIndex != -1 && nextIndex - 1 != currentLyricIndex.intValue) {
                        scrollState.scrollToItem(
                            index = (nextIndex).coerceAtLeast(0),
                            scrollOffset = -targetOffset.toInt(),
                        )
                        currentLyricIndex.intValue = nextIndex - 1
                    } else if (nextIndex == -1 && currentLyricIndex.intValue != lrcEntries.size - 1) {
                        scrollState.scrollToItem(
                            index = (lrcEntries.size).coerceAtLeast(0),
                            scrollOffset = -targetOffset.toInt(),
                        )
                        currentLyricIndex.intValue = lrcEntries.size - 1
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

@Composable
private fun floatToDp(value: Float): Dp {
    val density = LocalDensity.current
    return (value / density.density).dp
}

val easing: Easing = EaseInOutQuad

@Composable
private fun LazyItemScope.Line(
    lines: List<Pair<Float, String>>,
    style: TextStyle,
    measurer: TextMeasurer,
    modifier: Modifier,
    viewAlign: Alignment.Horizontal,
    draw: CacheDrawScope.(Constraints, TextLayoutResult) -> DrawResult,
) = FlamingoWrapper {
    val styledString = remember(style, lines) {
        buildString {
            lines.forEach { char ->
                if (char.second.isNotEmpty()) {
                    append(char.second)
                }
            }
        }
    }

    Column(
        horizontalAlignment = viewAlign,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        SubcomposeLayout(modifier = modifier) { constraints ->

            val measureResult = measurer.measure(
                text = styledString,
                style = style,
                constraints = Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                ),
                layoutDirection = LayoutDirection.Ltr,
            )

            val height = (style.lineHeight * measureResult.lineCount)

            val width = runCatching {
                (0 until measureResult.lineCount).maxOf {
                    measureResult.getBoundingBox(
                        measureResult.getLineEnd(it, visibleEnd = true) - 1,
                    ).right
                }
            }.getOrDefault(constraints.maxWidth.toFloat())

            val content = subcompose(lines) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawWithCache { draw(constraints, measureResult) },
                )
            }.first()

            val placeable = content.measure(
                Constraints.fixed(width.roundToInt(), height.roundToPx()),
            )

            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }
}

@Composable
private fun LazyItemScope.LyricItem(
    isCurrentLambda: () -> Boolean,
    isTopLambda: () -> Boolean,
    mainLyric: List<Pair<Float, String>>,
    translation: String?,
    showTranslation: Boolean,
    subTextSize: Int,
    blur: () -> Float,
    mainTextBasicColor: Color,
    subTextBasicColor: Color,
    measurer: TextMeasurer,
    mainTextStyle: TextStyle,
    isLyricEmpty: () -> Boolean,
    nextTime: () -> Float,
    otherSide: Boolean,
    liveTimeLambda: () -> Int,
    onClick: () -> Unit,
) {
    val viewAlign = if (otherSide) Alignment.End else Alignment.Start

    val focusedColor = Color(0xFFFFFFFF)
    val unfocusedColor = Color(0x2EFFFFFF)

    val unfocusedSolidBrush = SolidColor(unfocusedColor)

    val isNotOneByOne = rememberSaveable(mainLyric) {
        mutableStateOf(
            mainLyric.all { it.first == mainLyric.firstOrNull()?.first },
        )
    }

    val liveTime = remember(mainLyric) { mutableIntStateOf(liveTimeLambda()) }

    FlamingoWrapper {
        val launch = remember(mainLyric) {
            derivedStateOf {
                isLyricEmpty() || !isNotOneByOne.value
            }
        }
        if (launch.value) {
            LaunchedEffect(Unit) {
                while (true) {
                    withContext(Dispatchers.Main) {
                        liveTime.intValue = liveTimeLambda()
                    }
                    delay(10L)
                }
            }
        }
    }

    FlamingoWrapper {
        Column(
            Modifier
                .padding(horizontal = 9.dp),
            horizontalAlignment = viewAlign,
        ) {
            val otherSideAnimate = if (otherSide) {
                TransformOrigin(1f, 0.25f)
            } else {
                TransformOrigin(0f, 0.25f)
            }

            val otherSideTransformOrigin =
                if (otherSide) {
                    TransformOrigin(1f, 0.5f)
                } else {
                    TransformOrigin(0f, 0.5f)
                }

            val tweenSpecWithDelay: AnimationSpec<Float> = remember(mainLyric) {
                TweenSpec(
                    durationMillis = 270,
                    easing = flamingoEasing,
                    delay = 110,
                )
            }

            val tweenSpecWithoutDelay: AnimationSpec<Float> = remember(mainLyric) {
                TweenSpec(durationMillis = 300, easing = flamingoEasing, delay = 45)
            }

            val scale = animateFloatAsState(
                targetValue = if (isCurrentLambda()) 1.005f else 1f,
                animationSpec = if (isCurrentLambda()) tweenSpecWithDelay else tweenSpecWithoutDelay,
            )

            val cardPadding = if (otherSide) {
                Modifier.padding(start = 28.dp)
            } else {
                Modifier.padding(end = 28.dp)
            }

            if (isLyricEmpty()) {
                Column(Modifier.animateContentSize()) {
                    val percent = remember(mainLyric) {
                        derivedStateOf {
                            val m = mainLyric.first().first
                            ((liveTime.intValue - m).coerceAtLeast(0f) / (nextTime() - m))
                                .coerceAtMost(1f)
                        }
                    }
                    val show = remember(mainLyric) {
                        derivedStateOf { (isLyricEmpty() && isCurrentLambda() && percent.value != 0f) }
                    }
                    AnimatedVisibility(
                        show.value,
                        enter = fadeIn(
                            animationSpec = TweenSpec(
                                durationMillis = 550,
                                easing = flamingoEasing,
                                delay = 300,
                            ),
                        ) + scaleIn(
                            initialScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = TweenSpec(
                                durationMillis = 550,
                                easing = flamingoEasing,
                                delay = 300,
                            ),
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.85f,
                            transformOrigin = otherSideAnimate,
                            animationSpec = TweenSpec(
                                durationMillis = 340,
                                easing = flamingoEasing,
                            ),
                        ),
                    ) {
                        FlamingoWrapper {
                            LyricCard(
                                { scale.value },
                                cardPadding,
                                otherSideTransformOrigin,
                                viewAlign,
                            ) {
                                Column(
                                    Modifier
                                        .padding(start = 20.dp, end = 20.dp)
                                        .padding(top = 8.dp, bottom = 10.dp),
                                    horizontalAlignment = viewAlign,
                                ) {
                                    CountdownAnimation(
                                        { percent.value },
                                        colorLambda = { mainTextBasicColor },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                FlamingoWrapper {
                    LyricCard(
                        { scale.value },
                        cardPadding,
                        otherSideTransformOrigin,
                        viewAlign,
                    ) {
                        val blurValue = animateDpAsState(
                            blur().dp,
                            SnapSpec(delay = if (isTopLambda()) 260 else 0),
                        )

                        val blurModifier = remember(mainLyric) {
                            derivedStateOf {
                                val thisBlur = blur()
                                if (thisBlur == 0f) {
                                    Modifier
                                } else {
                                    Modifier.blur(
                                        blurValue.value,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                    )
                                }
                            }
                        }

                        FlamingoWrapper {
                            Column(
                                Modifier
                                    .then(blurModifier.value)
                                    .fillMaxWidth(),
                                horizontalAlignment = viewAlign,
                            ) {
                                val textAlign = if (otherSide) TextAlign.End else TextAlign.Start

                                val alphaTweenSpecWithDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        TweenSpec(
                                            durationMillis = 350,
                                            easing = flamingoEasing,
                                            delay = 145,
                                        )
                                    }

                                val alphaTweenSpecWithoutDelay: AnimationSpec<Float> =
                                    remember(mainLyric) {
                                        TweenSpec(
                                            durationMillis = 350,
                                            easing = flamingoEasing,
                                            delay = 80,
                                        )
                                    }

                                FlamingoWrapper {
                                    val thisAlphaAnimated = animateFloatAsState(
                                        targetValue = if (isCurrentLambda()) 1f else 0.14f,
                                        animationSpec = if (isCurrentLambda()) alphaTweenSpecWithDelay else alphaTweenSpecWithoutDelay,
                                    )

                                    val thisAlpha = remember(mainLyric) {
                                        derivedStateOf {
                                            if (isNotOneByOne.value) {
                                                thisAlphaAnimated.value
                                            } else {
                                                1f
                                            }
                                        }
                                    }

                                    val otherSidePadding = remember(mainLyric) {
                                        derivedStateOf {
                                            if (otherSide) {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = if (mainLyric.last().second.endsWith("：")) 3.dp else 20.dp,
                                                )
                                            } else {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp,
                                                )
                                            }
                                        }
                                    }

                                    val showHighLight = remember(mainLyric, translation) {
                                        derivedStateOf {
                                            if (isNotOneByOne.value) {
                                                true
                                            } else {
                                                val highlightIndex = (mainLyric.size - if (translation != null && mainLyric.size >= 3) 3 else 1)
                                                    .coerceIn(0, mainLyric.lastIndex)
                                                liveTime.intValue >= mainLyric[highlightIndex].first
                                            }
                                        }
                                    }

                                    Line(
                                        lines = mainLyric,
                                        style = if (otherSide) mainTextStyle.copy(textAlign = TextAlign.End) else mainTextStyle,
                                        measurer = measurer,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                this.alpha = thisAlpha.value
                                                compositingStrategy =
                                                    CompositingStrategy.ModulateAlpha
                                            }
                                            .padding(vertical = 4.dp)
                                            .then(otherSidePadding.value)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                            ) {
                                                onClick()
                                            },
                                        viewAlign = viewAlign,
                                    ) { _, measureResult ->

                                        if (isNotOneByOne.value) {
                                            // Not word-by-word: always fully highlighted
                                            return@Line onDrawWithContent {
                                                drawText(
                                                    textLayoutResult = measureResult,
                                                    color = focusedColor,
                                                )
                                            }
                                        }

                                        if (!isCurrentLambda()) {
                                            // Word-by-word but not the current line
                                            if (showHighLight.value) {
                                                return@Line onDrawWithContent {
                                                    drawText(
                                                        textLayoutResult = measureResult,
                                                        color = focusedColor,
                                                        topLeft = Offset(0F, -4F),
                                                    )
                                                }
                                            } else {
                                                return@Line onDrawWithContent {
                                                    drawText(
                                                        textLayoutResult = measureResult,
                                                        color = unfocusedColor,
                                                    )
                                                }
                                            }
                                        }

                                        // Word-by-word karaoke sweep below

                                        var sum = 0
                                        var lastTime = 0f

                                        val wordsToDraw = arrayListOf<DrawWord>()

                                        var averageTime = 0f

                                        lastTime = mainLyric.first().first

                                        mainLyric.fastForEachIndexed { wordIndex, word ->

                                            val thisWord = word.second

                                            if (thisWord.isEmpty()) {
                                                return@fastForEachIndexed
                                            }

                                            averageTime = (word.first - lastTime) / thisWord.length

                                            val thisWordGroupLastTime = if (wordIndex - 1 < 0) {
                                                mainLyric.first().first
                                            } else {
                                                mainLyric[(wordIndex - 1)].first
                                            }
                                            val groupPercent =
                                                if ((word.first - thisWordGroupLastTime) == 0f) {
                                                    0f
                                                } else {
                                                    ((liveTime.intValue - thisWordGroupLastTime).coerceAtLeast(
                                                        0f,
                                                    ) / (word.first - thisWordGroupLastTime)).coerceIn(
                                                        0f,
                                                        1f,
                                                    )
                                                }
                                            val easedPercent = easing.transform(groupPercent.coerceIn(0f, 1f))
                                            val topLeftWeight = 4 * easedPercent

                                            thisWord.forEach { char ->

                                                val charWord = char.toString()

                                                val layout = measurer.measure(
                                                    text = charWord,
                                                    style = if (otherSide) mainTextStyle.copy(
                                                        textAlign = TextAlign.End,
                                                    ) else mainTextStyle,
                                                    constraints = measureResult.layoutInput.constraints,
                                                )

                                                val thisWordLastTime = lastTime
                                                val thisWordAverageTime = averageTime

                                                wordsToDraw += DrawWord(
                                                    time = lastTime + averageTime,
                                                    word = charWord,
                                                    layout = layout,
                                                    topLeft = measureResult.getBoundingBox(
                                                        sum.coerceAtMost(
                                                            mainLyric.sumOf { it.second.length } - 1,
                                                        ).coerceAtLeast(0),
                                                    ).topLeft.minus(
                                                        Offset(
                                                            0F,
                                                            topLeftWeight,
                                                        ),
                                                    ),
                                                    brush = { px, percent ->
                                                        if (thisWord == " ") {
                                                            return@DrawWord unfocusedSolidBrush
                                                        }

                                                        val beforeColor = if (percent <= -0.5f) {
                                                            unfocusedColor
                                                        } else {
                                                            focusedColor
                                                        }

                                                        val afterColor = if (percent >= 1f) {
                                                            focusedColor
                                                        } else {
                                                            unfocusedColor
                                                        }
                                                        Brush.horizontalGradient(
                                                            0f to beforeColor,
                                                            (percent - px).coerceIn(0f, 1f) to beforeColor,
                                                            (percent + px).coerceIn(0f, 1f) to afterColor,
                                                        )
                                                    },
                                                    percent = {
                                                        if (thisWord == " ") {
                                                            return@DrawWord 0f
                                                        }

                                                        ((liveTime.intValue - thisWordLastTime) / thisWordAverageTime)
                                                    },
                                                ).also {
                                                    sum += charWord.length
                                                    lastTime += averageTime
                                                }
                                            }
                                        }

                                        onDrawBehind {
                                            wordsToDraw.fastForEach { l ->
                                                drawText(
                                                    textLayoutResult = l.layout,
                                                    topLeft = l.topLeft,
                                                    brush = l.brush(
                                                        0.3f,
                                                        l.percent(),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                FlamingoWrapper {
                                    AnimatedVisibility(showTranslation && translation != null) {
                                        translation?.let {
                                            val translationAlpha = animateFloatAsState(
                                                targetValue = if (isCurrentLambda()) 0.5f else 0.14f,
                                                animationSpec = if (isCurrentLambda()) alphaTweenSpecWithDelay else alphaTweenSpecWithoutDelay,
                                            )

                                            val translationOtherSidePadding = if (otherSide) {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp,
                                                )
                                            } else {
                                                Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp,
                                                )
                                            }

                                            Text(
                                                text = it,
                                                fontSize = subTextSize.sp,
                                                color = subTextBasicColor,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        this.alpha = translationAlpha.value
                                                        compositingStrategy =
                                                            CompositingStrategy.ModulateAlpha
                                                    }
                                                    .then(translationOtherSidePadding)
                                                    .padding(top = 5.dp),
                                                lineHeight = (subTextSize + 5).sp,
                                                letterSpacing = 0.3.sp,
                                                textAlign = textAlign,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricCard(
    scale: () -> Float,
    cardPadding: Modifier,
    otherSideTransformOrigin: TransformOrigin,
    viewAlign: Alignment.Horizontal,
    content: @Composable () -> Unit,
) =
    FlamingoWrapper {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val scaleValue = scale()
                    scaleX = scaleValue
                    scaleY = scaleValue
                    transformOrigin = otherSideTransformOrigin
                }
                .fillMaxWidth()
                .then(cardPadding)
                .padding(top = 9.dp, bottom = 9.dp),
            horizontalAlignment = viewAlign,
        ) {
            content()
        }
    }

@Composable
fun CountdownAnimation(progress: () -> Float, colorLambda: () -> Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1800, easing = flamingoEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = 0.8f
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 5.dp),
        ) {
            for (i in 1..3) {
                val average = 1f / 3f
                val beforePadding = (i - 1) * average
                val thisPercent = (progress() - beforePadding) / ((i * average) - beforePadding)
                val alpha = 0.2f + (0.8f * thisPercent).coerceIn(0f, 0.8f)

                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            colorLambda().copy(alpha = alpha),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Stable
private data class DrawWord(
    val time: Float,
    val word: String,
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val brush: (px: Float, percent: Float) -> Brush,
    val percent: () -> Float,
)
