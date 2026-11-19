/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
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

private val ContentMaxWidth = 720.dp

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

@Immutable
private data class RecapReel(val data: RecapData, val selection: RecapSelection)

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
    val overview = key(state.selection.year) { rememberLazyListState() }
    val disableAnimations by rememberPreference(DisableAnimationsKey, false)
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

    val reel = (state as? YearInMusicUiState.Success)?.storyData
        ?.takeIf { state.selection.story }?.let { RecapReel(it, state.selection) }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = reel,
            contentKey = { it != null },
            transitionSpec = {
                when {
                    disableAnimations -> EnterTransition.None togetherWith ExitTransition.None
                    targetState != null -> (slideInVertically(spring(dampingRatio = 0.88f, stiffness = 320f)) { it } +
                        fadeIn(tween(160))) togetherWith fadeOut(tween(200, delayMillis = 360))
                    else -> fadeIn(tween(1)) togetherWith (slideOutVertically(tween(340, easing = FastOutLinearInEasing)) { it } +
                        fadeOut(tween(240, delayMillis = 100)))
                }.apply { targetContentZIndex = if (targetState != null) 1f else 0f }
            },
            label = "recap_reel",
        ) { frame ->
            if (frame != null) RecapStories(frame.data, frame.selection, text, callbacks, shareChooserOpen)
            else RecapContent(state, overview, text, callbacks)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun RecapContent(
    state: YearInMusicUiState,
    listState: LazyListState,
    text: RecapText,
    callbacks: RecapCallbacks,
) {
    val success = state as? YearInMusicUiState.Success
    val selection = state.selection
    val periodData = if (selection.month == null) success?.data else (success?.monthState as? RecapMonthState.Success)?.data
    val disableAnimations by rememberPreference(DisableAnimationsKey, false)
    val animate = !disableAnimations
    val haze = rememberHazeState()
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val atmosphere = remember(selection.year) { RecapAppearance.atmosphere(selection.year).map(::Color) }
    val density = LocalDensity.current
    val collapseDistance = with(density) { 56.dp.toPx() }
    val fadeDistance = with(density) { 360.dp.toPx() }
    val collapse = remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val atmosphereFade = remember(listState, fadeDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 1) 1f
            else (listState.firstVisibleItemScrollOffset / fadeDistance + listState.firstVisibleItemIndex * 0.5f).coerceIn(0f, 1f)
        }
    }
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(surface)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Box(Modifier.fillMaxWidth().height(topInset + 440.dp).graphicsLayer { alpha = 1f - atmosphereFade.value }) {
                RecapAtmosphere(atmosphere, animate, Modifier.matchParentSize().graphicsLayer { alpha = if (dark) 0.5f else 0.85f })
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to surface.copy(alpha = 0f), 0.45f to surface.copy(alpha = 0.35f), 1f to surface,
                )))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topInset + 64.dp, bottom = bottomInset + 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                item("title", "title") { RecapTitle(selection, callbacks) }
                if (success != null) item("periods", "periods") { PeriodTabs(success.data, selection, text, callbacks.month) }
                when (state) {
                    is YearInMusicUiState.Loading -> item("status", "status") { RecapStatus(R.string.recap_loading, loading = true) }
                    is YearInMusicUiState.Empty -> item("status", "status") {
                        RecapStatus(R.string.year_in_music_empty_title, detail = stringResource(R.string.year_in_music_empty_subtitle, selection.year))
                    }
                    is YearInMusicUiState.Error -> item("status", "status") { RecapStatus(state.messageRes, retry = callbacks.retry) }
                    is YearInMusicUiState.Success -> if (selection.month == null) {
                        recapSections(state.data, selection.playback, text, callbacks, atmosphere, animate)
                    } else when (val month = state.monthState) {
                        RecapMonthState.Loading -> item("status", "status") { RecapStatus(R.string.recap_loading, loading = true) }
                        RecapMonthState.Empty -> item("status", "status") {
                            RecapStatus(R.string.recap_month_empty, detail = text.period(selection.year, selection.month))
                        }
                        is RecapMonthState.Error -> item("status", "status") { RecapStatus(month.messageRes, retry = callbacks.retryMonth) }
                        is RecapMonthState.Success -> recapSections(month.data, selection.playback, text, callbacks, atmosphere, animate)
                    }
                }
            }
        }
        RecapTopBar(
            collapse = { collapse.value }, haze = haze, accent = atmosphere[1],
            sharing = selection.sharing, shareEnabled = periodData != null, callbacks = callbacks,
        )
    }
}

private fun LazyListScope.recapSections(
    data: RecapData,
    playback: RecapPlayback,
    text: RecapText,
    callbacks: RecapCallbacks,
    atmosphere: List<Color>,
    animate: Boolean,
) {
    if (data.month == null) item("highlights", "highlights") {
        HighlightCard(atmosphere, animate, Modifier.then(revealModifier(animate)), onPlay = { callbacks.story(true) })
    }
    item("statement_${data.month ?: 0}", "statement") { ListeningStatement(data, text, revealModifier(animate)) }
    if (data.artists.isNotEmpty()) {
        item("artists_heading", "heading") {
            SectionTitle(stringResource(R.string.recap_top_artists_period, text.period(data.year, data.month)))
        }
        item("artists", "artists") {
            LazyRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().then(revealModifier(animate)),
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(data.artists, key = { _, entry -> entry.id }, contentType = { _, _ -> "artist" }) { index, entry ->
                    ArtistCard(entry, index + 1, text, callbacks)
                }
            }
        }
    }
    if (data.songs.isNotEmpty()) {
        item("songs_heading", "heading") {
            SectionTitle(stringResource(R.string.recap_top_songs_period, text.period(data.year, data.month))) {
                TextButton(callbacks.play, enabled = playback.available) {
                    Icon(painterResource(R.drawable.play), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.play_all), Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        itemsIndexed(data.songs, key = { _, entry -> "song_${entry.id}" }, contentType = { _, _ -> "song" }) { index, entry ->
            SongRow(entry, index + 1, index == data.songs.lastIndex, playback, text, callbacks)
        }
    }
    if (data.albums.isNotEmpty()) {
        item("albums_heading", "heading") {
            SectionTitle(stringResource(R.string.recap_top_albums_period, text.period(data.year, data.month)))
        }
        item("albums", "albums") {
            LazyRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().then(revealModifier(animate)),
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(data.albums, key = { _, entry -> entry.id }, contentType = { _, _ -> "album" }) { index, entry ->
                    AlbumCard(entry, index + 1, text, callbacks)
                }
            }
        }
    }
    if (data.month == null) {
        item("months_heading", "heading") { SectionTitle(stringResource(R.string.recap_months)) }
        item("months", "months") { MonthShelf(data, text, callbacks.month, revealModifier(animate)) }
    }
    item("habits_heading", "heading") { SectionTitle(stringResource(R.string.recap_habits)) }
    item("habits", "habits") { ListeningHabits(data, text, revealModifier(animate)) }
    item("attribution", "attribution") {
        Text(stringResource(R.string.recap_ranked_by_time), Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecapTopBar(
    collapse: () -> Float,
    haze: HazeState,
    accent: Color,
    sharing: Boolean,
    shareEnabled: Boolean,
    callbacks: RecapCallbacks,
) {
    val surface = MaterialTheme.colorScheme.surface
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val glass = remember(surface, disableBlur) {
        HazeBlurStyle {
            blurEnabled(!disableBlur)
            blurRadius(24.dp)
            noiseFactor(0f)
            backgroundColor(surface)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = collapse() }
            .hazeBlur(input = HazeInput.Sources(haze), style = glass, performanceMode = HazePerformanceMode.Adaptive)
            .background(surface.copy(alpha = if (disableBlur) 0.96f else 0.72f)))
        Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(R.drawable.arrow_back, R.string.recap_back, callbacks.back,
                container = surface.copy(alpha = 0.88f), content = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.year_in_music), Modifier.weight(1f).padding(horizontal = 12.dp)
                .graphicsLayer { alpha = ((collapse() - 0.5f) * 2f).coerceIn(0f, 1f) }.clearAndSetSemantics { },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sharing) ShareProgress()
            else GlassButton(R.drawable.share, R.string.share, callbacks.share, enabled = shareEnabled,
                container = accent.copy(alpha = if (shareEnabled) 0.92f else 0.4f), content = Color(RecapAppearance.Ink))
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = collapse() },
            thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GlassButton(icon: Int, label: Int, onClick: () -> Unit, container: Color, content: Color, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = container,
        contentColor = content.copy(alpha = if (enabled) 1f else 0.38f), shadowElevation = 3.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
        }
    }
}

@Composable
private fun RecapTitle(selection: RecapSelection, callbacks: RecapCallbacks) {
    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.year_in_music), Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2)
        Box {
            val chooseYear = stringResource(R.string.recap_choose_year)
            Surface(onClick = { callbacks.yearPicker(true) }, shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.semantics { contentDescription = chooseYear }) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selection.year.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(painterResource(R.drawable.expand_more), null, Modifier.size(22.dp))
                }
            }
            DropdownMenu(expanded = selection.yearPickerOpen, onDismissRequest = { callbacks.yearPicker(false) },
                shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(chooseYear, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                selection.years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString(), fontWeight = if (year == selection.year) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { callbacks.year(year) },
                        trailingIcon = if (year == selection.year) {
                            { Icon(painterResource(R.drawable.check), null, Modifier.size(20.dp)) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodTabs(data: RecapData, selection: RecapSelection, text: RecapText, onMonth: (Int?) -> Unit) {
    val tabs = remember(data.months) { listOf<Int?>(null) + data.months.map { it.month } }
    val selectedIndex = tabs.indexOf(selection.month).coerceAtLeast(0)
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    val disableAnimations by rememberPreference(DisableAnimationsKey, false)
    LaunchedEffect(selectedIndex) {
        val target = (selectedIndex - 1).coerceAtLeast(0)
        if (disableAnimations) rowState.scrollToItem(target) else rowState.animateScrollToItem(target)
    }
    LazyRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(bottom = 8.dp).selectableGroup(), state = rowState,
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(tabs, key = { it ?: 0 }, contentType = { "period" }) { month ->
            val available = month == null || data.months.firstOrNull { it.month == month }?.available == true
            val selected = selection.month == month
            val onSurface = MaterialTheme.colorScheme.onSurface
            val color by animateColorAsState(when {
                selected -> onSurface
                available -> onSurface.copy(alpha = 0.34f)
                else -> onSurface.copy(alpha = 0.14f)
            }, label = "period_color")
            val label = if (month == null) data.year.toString() else text.month(month, short = true)
            Text(label, Modifier.clip(RoundedCornerShape(12.dp))
                .selectable(selected = selected, enabled = available, role = Role.Tab, onClick = { onMonth(month) })
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .semantics { if (month != null) contentDescription = text.month(month) },
                color = color, style = MaterialTheme.typography.titleLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HighlightCard(colors: List<Color>, animate: Boolean, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    val emphasis = stringResource(R.string.recap_hero_emphasis)
    val statement = stringResource(R.string.recap_hero_statement, emphasis)
    val action = stringResource(R.string.recap_highlights)
    BoxWithConstraints(modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(minOf(maxWidth, 440.dp)).clip(RoundedCornerShape(24.dp)).background(colors[2])
            .clickable(onClickLabel = action, role = Role.Button, onClick = onPlay), contentAlignment = Alignment.Center) {
            RecapAtmosphere(colors.reversed(), animate, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.1f)))
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val start = statement.indexOf(emphasis)
                Text(buildAnnotatedString {
                    append(statement)
                    if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White), start, start + emphasis.length)
                }, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.16f), blurRadius = 16f)),
                    modifier = Modifier.semantics { heading() })
                Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.24f)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.play), null, Modifier.size(18.dp), tint = Color.White)
                    Text(action, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ListeningStatement(data: RecapData, text: RecapText, modifier: Modifier = Modifier) {
    val minutes = text.number(data.milliseconds / 60_000)
    val statement = stringResource(R.string.recap_month_listening, minutes,
        data.month?.let { text.month(it) } ?: data.year.toString())
    val emphasis = stringResource(R.string.recap_listening_minutes, minutes)
    val start = statement.indexOf(emphasis)
    val foreground = MaterialTheme.colorScheme.onSurface
    Text(buildAnnotatedString {
        append(statement)
        if (start >= 0) addStyle(SpanStyle(color = foreground), start, start + emphasis.length)
    }, modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        color = foreground.copy(alpha = 0.4f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
private fun ArtistCard(entry: RecapEntry, rank: Int, text: RecapText, callbacks: RecapCallbacks) {
    val more = stringResource(R.string.more)
    val description = stringResource(R.string.recap_ranked_artist, text.number(rank.toLong()), entry.title) +
        ", " + text.minutes(entry.milliseconds)
    Box(Modifier.width(232.dp).aspectRatio(0.78f).clip(RoundedCornerShape(20.dp))
        .combinedClickable(onClick = { callbacks.entry(entry) }, onLongClick = { callbacks.menu(entry) })
        .semantics(mergeDescendants = true) {
            contentDescription = description
            customActions = listOf(CustomAccessibilityAction(more) { callbacks.menu(entry); true })
        }) {
        RecapArtwork(entry, Modifier.matchParentSize(), shape = RectangleShape)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.32f), 0.28f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f),
        )))
        Text(text.number(rank.toLong()), Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp).clearAndSetSemantics { },
            color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(entry.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(text.minutes(entry.milliseconds), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SongRow(entry: RecapEntry, rank: Int, last: Boolean, playback: RecapPlayback, text: RecapText, callbacks: RecapCallbacks) {
    val active = playback.currentId == entry.id
    val onClick = remember(entry, callbacks) { { callbacks.entry(entry) } }
    val onLongClick = remember(entry, callbacks) { { callbacks.menu(entry) } }
    Row(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
            .combinedClickable(enabled = playback.available, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.number(rank.toLong()), Modifier.width(28.dp), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            RecapArtwork(entry, Modifier.size(52.dp), radius = 6)
            if (active) Box(Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(if (playback.playing) R.drawable.pause else R.drawable.play),
                    stringResource(if (playback.playing) R.string.recap_pause else R.string.play), Modifier.size(22.dp), tint = Color.White)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(Modifier.heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text(text.minutes(entry.milliseconds), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text.plays(entry.plays), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (playback.available) RecapIcon(R.drawable.more_horiz, R.string.more, onLongClick)
                else Spacer(Modifier.width(12.dp))
            }
            if (!last) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AlbumCard(entry: RecapEntry, rank: Int, text: RecapText, callbacks: RecapCallbacks) {
    val more = stringResource(R.string.more)
    Column(Modifier.width(164.dp).clip(RoundedCornerShape(12.dp))
        .combinedClickable(onClick = { callbacks.entry(entry) }, onLongClick = { callbacks.menu(entry) })
        .semantics(mergeDescendants = true) {
            customActions = listOf(CustomAccessibilityAction(more) { callbacks.menu(entry); true })
        }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            RecapArtwork(entry, Modifier.fillMaxWidth().aspectRatio(1f), radius = 12)
            Text(text.number(rank.toLong()), Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 9.dp, vertical = 2.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text.minutes(entry.milliseconds), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthShelf(data: RecapData, text: RecapText, onMonth: (Int?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(data.months, key = { it.month }, contentType = { "month" }) { month ->
            val description = stringResource(R.string.recap_month_accessibility, text.month(month.month),
                if (month.available) text.minutes(month.milliseconds) else stringResource(R.string.recap_future_month))
            val click = remember(month.month, onMonth) { { onMonth(month.month) } }
            Column(Modifier.width(112.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = month.available, onClick = click)
                .padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = description }
                .graphicsLayer { alpha = if (month.available) 1f else 0.45f },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) {
                    month.artist?.let { RecapArtwork(it, Modifier.matchParentSize().clearAndSetSemantics { }) }
                    if (month.artist == null) Text(text.month(month.month, short = true), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text.month(month.month), Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (month.available) text.minutes(month.milliseconds) else stringResource(R.string.recap_future_month),
                    Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ListeningHabits(data: RecapData, text: RecapText, modifier: Modifier = Modifier) {
    val plays = stringResource(R.string.year_in_music_plays_label)
    val tracks = stringResource(R.string.recap_distinct_tracks)
    val facts = remember(data, text, plays, tracks) {
        text.facts(data).map { it.label to it.value } +
            listOf(plays to text.number(data.plays), tracks to text.number(data.distinctTracks))
    }
    Column(modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)) {
        facts.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            if (index < facts.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RecapStatus(message: Int, detail: String? = null, loading: Boolean = false, retry: (() -> Unit)? = null) {
    Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().heightIn(min = 320.dp).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(stringResource(message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (retry != null) TextButton(retry) { Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun revealModifier(animate: Boolean): Modifier {
    var revealed by rememberSaveable { mutableStateOf(!animate) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (revealed) return@LaunchedEffect
        delay(40)
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
        revealed = true
    }
    return Modifier.graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * 32.dp.toPx()
    }
}

@Composable
internal fun RecapAtmosphere(colors: List<Color>, animate: Boolean, modifier: Modifier = Modifier, base: Color? = null) {
    val phase = if (animate) {
        rememberInfiniteTransition("recap_atmosphere").animateFloat(0f, 1f,
            infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "recap_atmosphere_phase")
    } else null
    Canvas(modifier) {
        if (base != null) drawRect(base)
        val turn = (phase?.value ?: 0.12f) * 2f * PI.toFloat()
        colors.forEachIndexed { index, color ->
            val angle = turn + index * 1.9f
            val center = Offset(size.width * (0.5f + 0.4f * cos(angle)), size.height * (0.45f + 0.32f * sin(angle * 0.7f + index)))
            val radius = size.maxDimension * (0.55f + 0.07f * index)
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}

@Composable
internal fun RecapArtwork(entry: RecapEntry, modifier: Modifier = Modifier, radius: Int = 12, shape: Shape? = null) {
    val request = rememberOfflineArtworkImageRequest(entry.artwork)
    val resolved = shape ?: remember(entry.kind, radius) {
        if (entry.kind == RecapEntryKind.ARTIST) CircleShape else RoundedCornerShape(radius.dp)
    }
    Box(modifier.clip(resolved).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.small_icon), null, Modifier.fillMaxSize(0.38f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(request, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
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
