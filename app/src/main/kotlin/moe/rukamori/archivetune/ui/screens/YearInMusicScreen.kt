/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.recap.RecapAppearance
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapEntryKind
import moe.rukamori.archivetune.recap.RecapMenu
import moe.rukamori.archivetune.recap.RecapMonthState
import moe.rukamori.archivetune.recap.RecapPlayback
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.recap.RecapText
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.player.rememberOfflineArtworkImageRequest
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.RecapEvent
import moe.rukamori.archivetune.viewmodels.YearInMusicUiState
import moe.rukamori.archivetune.viewmodels.YearInMusicViewModel

@Immutable
internal data class RecapCallbacks(
    val back: () -> Unit,
    val yearPicker: (Boolean) -> Unit,
    val year: (Int) -> Unit,
    val story: (Boolean) -> Unit,
    val chapter: (RecapChapter) -> Unit,
    val month: (Int?) -> Unit,
    val entry: (RecapEntry) -> Unit,
    val menu: (RecapEntry) -> Unit,
    val play: () -> Unit,
    val share: () -> Unit,
    val retry: () -> Unit,
    val retryMonth: () -> Unit,
    val replay: () -> Unit,
    val mute: () -> Unit,
    val retrySoundtrack: () -> Unit,
)

@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val text = remember(context, configuration) { RecapText(context) }
    val player = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val menuState = LocalMenuState.current
    val menuScope = androidx.compose.runtime.rememberCoroutineScope()
    val font = LocalFontFamilyResolver.current.resolve(LocalArchiveTuneFontFamily.current).value as Typeface
    val snackbar = remember { SnackbarHostState() }
    val overview = key(state.selection.year, state.selection.month) { rememberLazyListState() }
    val yearSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var shareChooserOpen by rememberSaveable { mutableStateOf(false) }
    val callbacks = remember(viewModel, navController, font) {
        RecapCallbacks(
            back = { if (!viewModel.back()) navController.navigateUp() },
            yearPicker = viewModel::showYearPicker, year = { shareChooserOpen = false; viewModel.selectYear(it) },
            story = { shareChooserOpen = false; viewModel.showStory(it) },
            chapter = { shareChooserOpen = false; viewModel.selectChapter(it) },
            month = { shareChooserOpen = false; viewModel.selectMonth(it) },
            entry = viewModel::openEntry, menu = viewModel::showMenu, play = { viewModel.play() },
            share = { shareChooserOpen = true; viewModel.share(font) },
            retry = viewModel::reload, retryMonth = viewModel::reloadMonth,
            replay = viewModel::replayStory, mute = viewModel::toggleStoryMute, retrySoundtrack = viewModel::retrySoundtrack,
        )
    }

    DisposableEffect(viewModel, player, lifecycleOwner) {
        player?.let(viewModel::attachPlayer)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshCalendar()
            if (event == Lifecycle.Event.ON_RESUME) shareChooserOpen = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) viewModel.releaseStoryMute()
            player?.let(viewModel::detachPlayer)
        }
    }
    LaunchedEffect(viewModel, initialYear) { viewModel.initialize(initialYear) }
    DisposableEffect(navController, state.selection.story) {
        val entry = navController.currentBackStackEntry
        entry?.savedStateHandle?.set("recap_immersive", state.selection.story)
        onDispose { entry?.savedStateHandle?.set("recap_immersive", false) }
    }
    LaunchedEffect(state.selection.actionMessage) {
        state.selection.actionMessage?.let {
            shareChooserOpen = false
            snackbar.showSnackbar(context.getString(it))
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, navController, context, menuState, menuScope) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                if (event is RecapEvent.Share && !viewModel.isCurrentShare(event.request)) return@collect
                when (event) {
                    is RecapEvent.OpenArtist -> navController.navigateToArtist(event.id)
                    is RecapEvent.OpenAlbum -> navController.navigateToAlbum(event.id)
                    is RecapEvent.ShowMenu -> menuState.show {
                        when (val menu = event.menu) {
                            is RecapMenu.SongMenu -> SongMenu(originalSong = menu.song, navController = navController, onDismiss = menuState::dismiss)
                            is RecapMenu.ArtistMenu -> ArtistMenu(originalArtist = menu.artist, coroutineScope = menuScope, onDismiss = menuState::dismiss)
                            is RecapMenu.AlbumMenu -> AlbumMenu(originalAlbum = menu.album, navController = navController, onDismiss = menuState::dismiss)
                        }
                    }
                    is RecapEvent.Share -> try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, event.uri)
                            clipData = ClipData.newRawUri(null, event.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_summary)))
                    } catch (_: ActivityNotFoundException) {
                        shareChooserOpen = false
                        viewModel.shareLaunchFailed()
                    } catch (_: SecurityException) {
                        shareChooserOpen = false
                        viewModel.shareLaunchFailed()
                    }
                }
            }
        }
    }
    BackHandler(state.selection.month != null || state.selection.story || state.selection.yearPickerOpen, callbacks.back)

    Box(Modifier.fillMaxSize()) {
        val success = state as? YearInMusicUiState.Success
        val storyData = success?.storyData
        if (state.selection.story && storyData != null) {
            RecapStories(storyData, state.selection, text, callbacks, shareChooserOpen)
        } else {
            RecapContent(state, overview, text, callbacks)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
    if (state.selection.yearPickerOpen) {
        YearPicker(state.selection, yearSheet, callbacks)
    }
}

@Composable
private fun RecapContent(
    state: YearInMusicUiState,
    overview: LazyListState,
    text: RecapText,
    callbacks: RecapCallbacks,
) {
    val success = state as? YearInMusicUiState.Success
    val periodData = if (state.selection.month == null) success?.data else
        (success?.monthState as? RecapMonthState.Success)?.data
    val haze = rememberHazeState()
    val surface = MaterialTheme.colorScheme.surface
    val foreground = MaterialTheme.colorScheme.onSurface
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val backdropAlpha = if (surface.luminance() < 0.5f) 0.3f else 1f
    val glass = remember(surface, disableBlur) {
        HazeBlurStyle {
            blurEnabled(!disableBlur)
            blurRadius(24.dp)
            noiseFactor(0f)
            backgroundColor(surface)
        }
    }
    val glassModifier = Modifier.clip(CircleShape)
        .hazeBlur(input = HazeInput.Sources(haze), style = glass,
            performanceMode = HazePerformanceMode.Adaptive)
        .background(surface.copy(alpha = 0.35f))
    Box(Modifier.fillMaxSize().background(surface)) {
        Canvas(Modifier.fillMaxWidth().height(480.dp).hazeSource(haze)) {
            drawRect(surface)
            drawRect(Brush.radialGradient(
                listOf(Color(0xFFF2A52D).copy(alpha = backdropAlpha), Color(0xFFF2A52D).copy(alpha = 0f)),
                center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.85f,
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(RecapAppearance.Mint).copy(alpha = backdropAlpha), Color(RecapAppearance.Mint).copy(alpha = 0f)),
                center = Offset(size.width, size.height * 0.42f), radius = size.width * 0.7f,
            ))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, surface)))
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = LocalPlayerAwareWindowInsets.current,
            topBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
                    androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal,
                ))) {
                    Column(Modifier.widthIn(max = 728.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = callbacks.back, modifier = glassModifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.recap_back), tint = foreground)
                            }
                            if (state.selection.sharing) {
                                ShareProgress()
                            } else IconButton(onClick = callbacks.share, enabled = periodData != null,
                                modifier = glassModifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.share), stringResource(R.string.share),
                                    tint = foreground.copy(alpha = if (periodData != null) 1f else 0.38f))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.year_in_music), Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { callbacks.yearPicker(true) }, modifier = glassModifier) {
                                Text(state.selection.year.toString(), style = MaterialTheme.typography.titleMedium, color = foreground)
                                Icon(painterResource(R.drawable.expand_more), null, Modifier.size(20.dp), tint = foreground)
                            }
                        }
                        if (success != null) {
                            SecondaryScrollableTabRow(selectedTabIndex = state.selection.month ?: 0,
                                containerColor = Color.Transparent, contentColor = foreground,
                                edgePadding = 8.dp, divider = {}, indicator = {}) {
                                Tab(selected = state.selection.month == null, onClick = { callbacks.month(null) },
                                    selectedContentColor = foreground, unselectedContentColor = foreground.copy(alpha = 0.55f),
                                    text = { Text(state.selection.year.toString(), style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (state.selection.month == null) FontWeight.Bold else FontWeight.Medium) })
                                success.data.months.forEach { month ->
                                    Tab(selected = state.selection.month == month.month, onClick = { callbacks.month(month.month) },
                                        enabled = month.available, selectedContentColor = foreground,
                                        unselectedContentColor = foreground.copy(alpha = 0.55f),
                                        text = { Text(text.month(month.month, short = true), style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (state.selection.month == month.month) FontWeight.Bold else FontWeight.Medium) })
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    is YearInMusicUiState.Loading -> RecapStatus(R.string.recap_loading, loading = true)
                    is YearInMusicUiState.Empty -> RecapStatus(R.string.year_in_music_empty_title,
                        detail = stringResource(R.string.year_in_music_empty_subtitle, state.selection.year))
                    is YearInMusicUiState.Error -> RecapStatus(state.messageRes, retry = callbacks.retry)
                    is YearInMusicUiState.Success -> if (state.selection.month == null) {
                        RecapOverview(state.data, state.selection, overview, text, callbacks)
                    } else when (val month = state.monthState) {
                        RecapMonthState.Loading -> RecapStatus(R.string.recap_loading, loading = true)
                        RecapMonthState.Empty -> RecapStatus(R.string.recap_month_empty,
                            detail = text.period(state.selection.year, state.selection.month))
                        is RecapMonthState.Error -> RecapStatus(month.messageRes, retry = callbacks.retryMonth)
                        is RecapMonthState.Success -> RecapOverview(month.data, state.selection, overview, text, callbacks)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapOverview(
    data: RecapData,
    selection: RecapSelection,
    listState: LazyListState,
    text: RecapText,
    callbacks: RecapCallbacks,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (data.month == null) {
            item("reel", "hero") {
                Card(onClick = { callbacks.story(true) }, modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color(RecapAppearance.Sky))) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                        Color(RecapAppearance.Mint), Color(RecapAppearance.Sky), Color(RecapAppearance.Blue),
                    ))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(text.period(data), style = MaterialTheme.typography.labelLarge, color = Color(RecapAppearance.Ink))
                        RecapMosaic(data.songs, Modifier.fillMaxWidth().heightIn(max = 180.dp))
                        Text(stringResource(R.string.recap_hero_title), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Row(Modifier.clip(MaterialTheme.shapes.large).background(Color(RecapAppearance.Background).copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.play), null, Modifier.size(20.dp), tint = Color.White)
                            Text(stringResource(R.string.recap_highlights), color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            item("period", "heading") {
                val minutes = text.number(data.milliseconds / 60_000)
                val statement = stringResource(R.string.recap_month_listening, minutes, text.month(data.month))
                val emphasis = stringResource(R.string.recap_listening_minutes, minutes)
                val emphasisStart = statement.indexOf(emphasis)
                val foreground = MaterialTheme.colorScheme.onSurface
                Text(buildAnnotatedString {
                    append(statement)
                    if (emphasisStart >= 0) addStyle(SpanStyle(color = foreground), emphasisStart, emphasisStart + emphasis.length)
                }, Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).semantics { heading() },
                    color = foreground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (data.artists.isNotEmpty()) {
            item("artists", "artists") {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.top_artists))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(data.artists, key = { _, entry -> entry.id }, contentType = { _, _ -> "artist" }) { index, entry ->
                            Column(Modifier.width(136.dp).clip(MaterialTheme.shapes.medium)
                                .combinedClickable(onClick = { callbacks.entry(entry) }, onLongClick = { callbacks.menu(entry) })
                                .padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecapArtwork(entry, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(stringResource(R.string.recap_ranked_artist, text.number(index.toLong() + 1), entry.title),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(text.minutes(entry.milliseconds), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text.plays(entry.plays), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RecapIcon(R.drawable.more_horiz, R.string.more, { callbacks.menu(entry) })
                            }
                        }
                    }
                }
            }
        }
        rankings(data, selection.playback, text, callbacks)
        item("totals", "totals") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                SectionTitle(stringResource(R.string.recap_recorded_listening))
                RecapTotals(data, text)
            }
        }
        if (data.month == null) item("months", "months") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                SectionTitle(stringResource(R.string.recap_months))
                MonthlyListening(data, text, callbacks.month)
            }
        }
        item("habits", "habits") {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                SectionTitle(stringResource(R.string.recap_habits))
                ListeningHabits(data, text)
            }
        }
        item("attribution", "attribution") {
            Text(stringResource(R.string.recap_ranked_by_time), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

private fun LazyListScope.rankings(data: RecapData, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    if (data.songs.isNotEmpty()) {
        item("songs_heading", "heading") {
            Row(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.top_songs), Modifier.weight(1f))
                TextButton(callbacks.play, enabled = playback.available) { Text(stringResource(R.string.play_all)) }
            }
        }
        itemsIndexed(data.songs, key = { _, entry -> "song_${entry.id}" }, contentType = { _, _ -> "ranking" }) { index, entry ->
            RankedEntry(entry, index + 1, playback, text, callbacks)
        }
    }
    if (data.albums.isNotEmpty()) {
        item("albums_heading", "heading") { SectionTitle(stringResource(R.string.albums), Modifier.widthIn(max = 680.dp).fillMaxWidth()) }
        itemsIndexed(data.albums, key = { _, entry -> "album_${entry.id}" }, contentType = { _, _ -> "ranking" }) { index, entry ->
            RankedEntry(entry, index + 1, playback, text, callbacks)
        }
    }
}

@Composable
private fun RankedEntry(entry: RecapEntry, rank: Int, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    val active = entry.kind == RecapEntryKind.SONG && playback.currentId == entry.id
    val onClick = remember(entry, callbacks) { { callbacks.entry(entry) } }
    val onLongClick = remember(entry, callbacks) { { callbacks.menu(entry) } }
    Row(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = entry.kind != RecapEntryKind.SONG || playback.available,
                onClick = onClick, onLongClick = onLongClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            RecapArtwork(entry, Modifier.size(56.dp))
            if (active) Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(if (playback.playing) R.drawable.pause else R.drawable.play),
                    stringResource(if (playback.playing) R.string.recap_pause else R.string.play), Modifier.size(24.dp), tint = Color.White)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (playback.available) RecapIcon(R.drawable.more_horiz, R.string.more, onLongClick)
    }
}

@Composable
internal fun RecapArtwork(entry: RecapEntry, modifier: Modifier = Modifier, radius: Int = 12) {
    val request = rememberOfflineArtworkImageRequest(entry.artwork)
    val shape = remember(entry.kind, radius) { if (entry.kind == RecapEntryKind.ARTIST) CircleShape else RoundedCornerShape(radius.dp) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.small_icon), null, Modifier.fillMaxSize(0.38f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(request, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun RecapTotals(data: RecapData, text: RecapText) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(text.number(data.milliseconds / 60_000), style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Text(stringResource(R.string.year_in_music_minutes_unit), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text(text.number(data.plays), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.year_in_music_plays_label), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(text.number(data.distinctTracks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.recap_distinct_tracks), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun MonthlyListening(data: RecapData, text: RecapText, onMonth: (Int?) -> Unit) {
    val peak = remember(data.months) { data.months.maxOf { it.milliseconds }.coerceAtLeast(1L) }
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.surfaceContainerHigh
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        items(data.months, key = { it.month }, contentType = { "month" }) { month ->
            val description = stringResource(R.string.recap_month_accessibility, text.month(month.month),
                if (month.available) text.minutes(month.milliseconds) else stringResource(R.string.recap_future_month))
            val click = remember(month.month, onMonth) { { onMonth(month.month) } }
            Column(Modifier.width(60.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
                enabled = month.available, onClick = click).padding(vertical = 8.dp).semantics(mergeDescendants = true) {
                contentDescription = description
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(22.dp).height(112.dp).clip(RoundedCornerShape(5.dp)).background(secondaryColor),
                    contentAlignment = Alignment.BottomCenter) {
                    if (month.available && month.milliseconds > 0) {
                        Box(Modifier.fillMaxWidth().height((112f * month.milliseconds / peak).coerceAtLeast(3f).dp)
                            .background(selectedColor))
                    }
                }
                Text(text.month(month.month, short = true), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelSmall,
                    color = if (month.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ListeningHabits(data: RecapData, text: RecapText) {
    val facts = remember(data.habits, data.month, text) { text.facts(data) }
    Column {
        facts.forEachIndexed { index, fact ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(fact.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fact.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun YearPicker(selection: RecapSelection, sheetState: SheetState, callbacks: RecapCallbacks) {
    ModalBottomSheet(onDismissRequest = { callbacks.yearPicker(false) }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            item("title", "heading") { SectionTitle(stringResource(R.string.recap_choose_year)) }
            items(selection.years, key = { it }, contentType = { "year" }) { year ->
                TextButton(onClick = { callbacks.year(year) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (year == selection.year) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun RecapStatus(message: Int, modifier: Modifier = Modifier.fillMaxSize(), detail: String? = null,
                        loading: Boolean = false, retry: (() -> Unit)? = null) {
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, false)
    Box(modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) {
                if (disableAnimations) Icon(painterResource(R.drawable.small_icon), null, Modifier.size(32.dp))
                else LoadingIndicator()
            }
            Text(stringResource(message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (retry != null) TextButton(retry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
internal fun RecapIcon(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
    }
}

@Composable
internal fun ShareProgress() {
    val description = stringResource(R.string.recap_preparing_share)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
    }
}
