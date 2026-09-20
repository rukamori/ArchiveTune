/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsSourceFormat
import moe.rukamori.archivetune.lyrics.LyricsSyncType
import moe.rukamori.archivetune.lyrics.LyricsTextDirection
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.lyrics.toLyricsEntries
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 250.0
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 250.0
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.08
private const val SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS = 2.0
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

@Composable
fun LyricsEnhanced(
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val animationsDisabled = LocalAnimationsDisabled.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackParameters by playerConnection.playbackParameters.collectAsState()

    val preparedLyrics = (lyricsState as? LyricsRenderScreenState.Success)?.lyrics
    val preferences = preparedLyrics?.preferences
    val lyricsClick = preferences?.clickEnabled ?: true
    val lyricsTextSize = preferences?.textSizeSp ?: 26f
    val lyricsLineBlurPreference = preferences?.lineBlurEnabled ?: true

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val textColor =
        textColorOverride ?: if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.White
        }
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedLineKeys = remember { mutableStateListOf<String>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 5
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val showTranslations =
        preparedLyrics?.lines?.any { line -> line.translation != null } == true
    val showPhonetics =
        preparedLyrics?.lines?.any { line -> line.romanizedText != null || line.phonetics.isNotEmpty() } == true
    val baseLayoutDirection = LocalLayoutDirection.current
    val lyricsLayoutDirection =
        remember(preparedLyrics?.lines, baseLayoutDirection) {
            if (preparedLyrics?.lines?.firstOrNull { line -> line.text.isNotBlank() }?.direction == LyricsTextDirection.RTL) {
                LayoutDirection.Rtl
            } else {
                baseLayoutDirection
            }
        }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, preparedLyrics?.lines) {
            mediaMetadata?.id.orEmpty() to preparedLyrics?.lines?.map { line -> line.id }
        }

    val isSynced = preparedLyrics?.syncType != null && preparedLyrics.syncType != LyricsSyncType.PLAIN
    val isWordSyncedFormat = preparedLyrics?.syncType == LyricsSyncType.WORD

    val lyricsEntries: List<LyricsEntry> =
        remember(preparedLyrics) {
            preparedLyrics?.toLyricsEntries().orEmpty()
        }

    val romanizationMap =
        remember(preparedLyrics) {
            preparedLyrics
                ?.lines
                ?.mapIndexed { index, line ->
                    val phonetics = MutableList<String?>(line.main.words.size) { null }
                    line.phonetics.forEach { phonetic ->
                        if (phonetic.wordIndex in phonetics.indices) {
                            phonetics[phonetic.wordIndex] = phonetic.text
                        }
                    }
                    index to if (phonetics.any { it != null }) phonetics else listOf(line.romanizedText)
                }?.toMap()
                .orEmpty()
        }
    val syncedLyrics =
        remember(lyricsEntries, isWordSyncedFormat, romanizationMap) {
            buildSyncedLyrics(lyricsEntries, isWordSyncedFormat, romanizationMap)
        }

    val leadMs =
        when {
            preparedLyrics?.sourceFormat == LyricsSourceFormat.TTML -> WORD_SYNC_LEAD_MS
            isWordSyncedFormat -> WORD_SYNC_LEAD_MS
            else -> LRC_LEAD_MS
        }

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(playbackParameters.speed)
    val playbackPositionMs =
        remember(player) {
            mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
        }
    val listState = key(lyricsSessionKey) { rememberLazyListState() }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(player, lyricsSessionKey, animationsDisabled, playbackParameters.speed) {
        var smoothedPositionMs = player.currentPosition.coerceAtLeast(0L).toDouble()
        var previousFrameNanos = 0L
        while (isActive) {
            val sliderPosition = latestSliderPositionProvider.value()
            val rawPosition = (sliderPosition ?: player.currentPosition).coerceAtLeast(0L)
            if (sliderPosition != null || !player.isPlaying || animationsDisabled) {
                smoothedPositionMs = rawPosition.toDouble()
                previousFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                if (sliderPosition == null) {
                    delay(100L)
                } else {
                    withFrameNanos { }
                }
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (previousFrameNanos == 0L) {
                    previousFrameNanos = frameNanos
                    smoothedPositionMs = rawPosition.toDouble()
                } else {
                    val elapsedMs =
                        ((frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000.0) *
                            latestPlaybackSpeed.value.toDouble()
                    val projectedPositionMs = smoothedPositionMs + elapsedMs
                    val driftMs = rawPosition.toDouble() - projectedPositionMs

                    smoothedPositionMs =
                        if (
                            driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS ||
                            driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS
                        ) {
                            rawPosition.toDouble()
                        } else {
                            val correctionMs =
                                (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).coerceIn(
                                    -SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS,
                                    SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS,
                                )
                            projectedPositionMs + correctionMs
                        }
                    previousFrameNanos = frameNanos
                }

                val nextPosition = smoothedPositionMs.roundToLong().coerceAtLeast(0L)

                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }
    }

    val playbackSyncPosition: () -> Int =
        remember {
            {
                (
                    playbackPositionMs.longValue +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val normalTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = lyricsTextSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
        )
    val accompanimentTextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = (lyricsTextSize * 0.82f).sp,
            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.titleLarge.fontFamily,
        )
    val phoneticTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = (lyricsTextSize * 0.55f).sp,
            fontWeight = FontWeight.Normal,
        )
    val plainLyrics =
        remember(lyricsEntries, isSynced) {
            PlainLyrics(
                items =
                    if (isSynced) {
                        emptyList()
                    } else {
                        lyricsEntries.mapIndexedNotNull { index, entry ->
                            val text = entry.text.trim()
                            if (text.isBlank()) {
                                null
                            } else {
                                val selectionId = "plain:$index:${text.hashCode()}"
                                PlainLyricLine(
                                    itemId = "$selectionId#$index",
                                    selectionId = selectionId,
                                    text = text,
                                )
                            }
                        }
                    },
            )
        }
    val selectionLines =
        remember(isSynced, syncedLyrics, plainLyrics) {
            if (isSynced) {
                syncedLyrics.lines.mapIndexedNotNull { index, line ->
                    val text = line.lineText()
                    if (text.isBlank()) {
                        null
                    } else {
                        val selectionId = line.selectionKey(text)
                        LyricSelectionLine(
                            itemId = "$selectionId#$index",
                            selectionId = selectionId,
                            text = text,
                        )
                    }
                }
            } else {
                plainLyrics.items.map { line ->
                    LyricSelectionLine(
                        itemId = line.itemId,
                        selectionId = line.selectionId,
                        text = line.text,
                    )
                }
            }
        }
    val selectedLineKeySnapshot = selectedLineKeys.toList()
    val selectedLineKeySet = remember(selectedLineKeySnapshot) { selectedLineKeySnapshot.toSet() }
    val dismissSelection = {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }
    val toggleSelectedLine: (String) -> Unit = { lineKey ->
        if (selectedLineKeys.contains(lineKey)) {
            selectedLineKeys.remove(lineKey)
            if (selectedLineKeys.isEmpty()) isSelectionModeActive = false
        } else if (selectedLineKeys.size < maxSelectionLimit) {
            selectedLineKeys.add(lineKey)
        } else {
            showMaxSelectionToast = true
        }
    }
    val shareSelectedLyrics: () -> Unit = {
        val metadata = mediaMetadata
        if (metadata != null) {
            val selectedLyricsText =
                selectionLines
                    .filter { line -> line.selectionId in selectedLineKeySet }
                    .joinToString("\n") { line -> line.text }
            if (selectedLyricsText.isNotBlank()) {
                shareDialogData =
                    Triple(
                        selectedLyricsText,
                        metadata.title,
                        metadata.artists.joinToString { it.name },
                    )
                showShareDialog = true
            }
        }
        dismissSelection()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
    ) {
        when {
            lyricsState is LyricsRenderScreenState.Loading -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            lyricsState !is LyricsRenderScreenState.Success -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            isSynced && syncedLyrics.lines.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced && plainLyrics.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced -> {
                PlainLyricsView(
                    lines = plainLyrics,
                    listState = listState,
                    selectedLineKeys = selectedLineKeySet,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                    onLineClicked = { lineKey ->
                        if (isSelectionModeActive) toggleSelectedLine(lineKey)
                    },
                    onLinePressed = { lineKey ->
                        if (!isSelectionModeActive) {
                            isSelectionModeActive = true
                            if (!selectedLineKeys.contains(lineKey)) {
                                selectedLineKeys.add(lineKey)
                            }
                        } else if (!selectedLineKeys.contains(lineKey)) {
                            toggleSelectedLine(lineKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val lyricsViewportOffset = remember(maxHeight) { maxHeight * 0.38f }

                    CompositionLocalProvider(LocalLayoutDirection provides lyricsLayoutDirection) {
                        key(lyricsSessionKey, syncedLyrics) {
                            KaraokeLyricsView(
                                listState = listState,
                                lyrics = syncedLyrics,
                                currentPosition = playbackSyncPosition,
                                onLineClicked = { line ->
                                    if (isSelectionModeActive) {
                                        toggleSelectedLine(line.selectionKey())
                                    } else if (lyricsClick && isSynced && line.start > 0) {
                                        player.seekTo(line.start.toLong())
                                    }
                                },
                                onLinePressed = { line ->
                                    val lineKey = line.selectionKey()
                                    if (!isSelectionModeActive) {
                                        isSelectionModeActive = true
                                        if (!selectedLineKeys.contains(lineKey)) {
                                            selectedLineKeys.add(lineKey)
                                        }
                                    } else if (!selectedLineKeys.contains(lineKey)) {
                                        toggleSelectedLine(lineKey)
                                    }
                                },
                                textColor = textColor,
                                normalLineTextStyle = normalTextStyle,
                                accompanimentLineTextStyle = accompanimentTextStyle,
                                phoneticTextStyle = phoneticTextStyle,
                                blendMode = BlendMode.SrcOver,
                                useBlurEffect = lyricsLineBlur,
                                showTranslation = showTranslations,
                                showPhonetic = showPhonetics,
                                offset = lyricsViewportOffset,
                                keepAliveZone = 72.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (isSelectionModeActive && selectionLines.isNotEmpty()) {
        LyricsSelectionBottomSheet(
            lines = selectionLines,
            selectedLineKeys = selectedLineKeySet,
            onToggleLine = toggleSelectedLine,
            onDismissRequest = dismissSelection,
            onShareSelected = shareSelectedLyrics,
        )
    }

    if (showShareDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = LyricsSharePayload(lyricsText, songTitle, artists),
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareDialogData = Triple(lyricsText, songTitle, artists)
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = LyricsSharePayload(lyricsText, songTitle, artists),
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

@Immutable
private data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

@Immutable
private data class PlainLyricLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
)

@Immutable
private data class LyricSelectionLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
)

@Composable
private fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedLineKeys: Set<String>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding =
        remember {
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines.items,
            key = { line -> line.itemId },
            contentType = { "plain_lyric_line" },
        ) { line ->
            PlainLyricLineItem(
                line = line,
                selected = line.selectionId in selectedLineKeys,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
            )
        }
    }
}

@Composable
private fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            textColor
        }

    Text(
        text = line.text,
        style = textStyle,
        color = contentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(
                    onClick = { onLineClicked(line.selectionId) },
                    onLongClick = { onLinePressed(line.selectionId) },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LyricsSelectionBottomSheet(
    lines: List<LyricSelectionLine>,
    selectedLineKeys: Set<String>,
    onToggleLine: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onShareSelected: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstSelectedIndex =
        remember(lines, selectedLineKeys) {
            lines
                .indexOfFirst { line -> line.selectionId in selectedLineKeys }
                .coerceAtLeast(0)
        }
    val sheetListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = firstSelectedIndex,
        )
    val selectedCount = selectedLineKeys.size

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_element, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.close))
                }
            }

            LazyColumn(
                state = sheetListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = lines,
                    key = { line -> line.itemId },
                    contentType = { "lyric_selection_line" },
                ) { line ->
                    LyricsSelectionLineItem(
                        line = line,
                        selected = line.selectionId in selectedLineKeys,
                        onClick = { onToggleLine(line.selectionId) },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = onShareSelected,
                    enabled = selectedCount > 0,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.share_selected))
                }
            }
        }
    }
}

@Composable
private fun LyricsSelectionLineItem(
    line: LyricSelectionLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

private fun ISyncedLine.lineText(): String =
    when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }

private fun ISyncedLine.selectionKey(text: String = lineText()): String = "$start:$end:${text.hashCode()}"

private fun List<WordTimestamp>.toKaraokeSyllables(phonetics: List<String?>): List<KaraokeSyllable> =
    mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val end = word.endTime.toMilliseconds()

        KaraokeSyllable(
            content = word.text,
            start = start,
            end = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phonetics.getOrNull(index),
        )
    }

private fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

private fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    isWordSynced: Boolean,
    romanizationMap: Map<Int, List<String?>>,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()

    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        if (isWordSynced && entry.words != null) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words!!.filter { !it.isBackground }
            val bgWords = entry.words!!.filter { it.isBackground }
            val alignment =
                when (entry.agent?.lowercase()) {
                    "v2" -> KaraokeAlignment.End
                    else -> KaraokeAlignment.Start
                }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words!!
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val mainSyllables = wordsForMain.toKaraokeSyllables(wordPhonetics)

            val lineStart = entry.time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val lineEnd =
                if (entry.durationMs > 0L) {
                    (entry.time + entry.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                } else {
                    mainSyllables.maxOf(KaraokeSyllable::end)
                }
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    val bgSyllables = bgWords.toKaraokeSyllables(emptyList())
                    val bgStart = bgSyllables.minOf(KaraokeSyllable::start)
                    val bgEnd = bgSyllables.maxOf(KaraokeSyllable::end)
                    if (bgEnd > bgStart) {
                        listOf(
                            KaraokeLine.AccompanimentKaraokeLine(
                                syllables = bgSyllables,
                                translation = null,
                                alignment = alignment,
                                start = bgStart,
                                end = bgEnd,
                                phonetic = null,
                            ),
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = translation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = null,
                    accompanimentLines = accompanimentLines,
                ),
            )
        } else {
            val nextEntry = entries.getOrNull(index + 1)
            val lineEnd =
                if (entry.durationMs > 0L) {
                    (entry.time + entry.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                } else if (nextEntry != null && nextEntry.time > entry.time) {
                    val gap = nextEntry.time - entry.time
                    if (gap > 3000L) {
                        minOf((nextEntry.time - 1L).toInt(), (entry.time + 4000L).toInt())
                            .coerceAtLeast(entry.time.toInt() + 1)
                    } else {
                        (nextEntry.time - 1L).coerceAtLeast(entry.time + 1L).toInt()
                    }
                } else {
                    (entry.time + 4000L).toInt()
                }
            lines.add(
                buildLineSyncedLrcLine(
                    entry = entry,
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                ),
            )
        }
    }

    return SyncedLyrics(lines = lines)
}

private fun buildLineSyncedLrcLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
): ISyncedLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedRomanizedText == null) {
        return SyncedLine(
            content = entry.text,
            translation = translation,
            start = start,
            end = end,
        )
    }

    val syllables =
        buildWrappingKaraokeSyllables(
            content = entry.text,
            romanizedText = normalizedRomanizedText,
            start = start,
            end = end,
        )

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = translation,
        alignment = KaraokeAlignment.Start,
        start = start,
        end = end,
    )
}

private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices =
        contentUnits.indices.filter { index ->
            contentUnits[index].any(Char::isLetterOrDigit)
        }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}
