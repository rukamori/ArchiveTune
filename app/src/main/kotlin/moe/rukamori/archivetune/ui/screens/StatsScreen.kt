/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package moe.rukamori.archivetune.ui.screens

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.StatPeriod
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningSummary
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.StatsScreenState
import moe.rukamori.archivetune.viewmodels.StatsViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayingFlow: Flow<Boolean> =
        remember(playerConnection) { playerConnection?.isPlaying ?: flowOf(false) }
    val mediaMetadataFlow: Flow<MediaMetadata?> =
        remember(playerConnection) { playerConnection?.mediaMetadata ?: flowOf(null) }
    val isPlaying by isPlayingFlow.collectAsStateWithLifecycle(initialValue = false)
    val mediaMetadata by mediaMetadataFlow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isYearPickerOpen by viewModel.yearPickerOpen.collectAsStateWithLifecycle()

    val data =
        when (val state = screenState) {
            StatsScreenState.Loading -> {
                StatsStatusScreen(navController = navController, loading = true)
                return
            }

            StatsScreenState.Empty -> {
                StatsStatusScreen(navController = navController)
                return
            }

            is StatsScreenState.Error -> {
                StatsStatusScreen(
                    navController = navController,
                    errorMessage = stringResource(state.messageResId),
                    onRetry = viewModel::retry,
                )
                return
            }

            is StatsScreenState.Success -> {
                state.data
            }
        }

    val indexChips = data.selectedPeriodIndex
    val selectedOption = data.selectedOption
    val mostPlayedSongs = data.mostPlayedSongs
    val rankedSongs = data.visibleRankedSongs
    val mostPlayedArtists = data.mostPlayedArtists
    val mostPlayedAlbums = data.mostPlayedAlbums
    val firstEvent = data.firstEvent
    val listeningSummary = data.listeningSummary
    val songsById = remember(mostPlayedSongs) { mostPlayedSongs.associateBy { it.id } }
    val hasPeriodData = listeningSummary.totalPlayCount > 0 || rankedSongs.isNotEmpty()

    val coroutineScope = rememberCoroutineScope()
    val currentDate = remember { LocalDateTime.now() }
    val currentDayOfWeek = remember(currentDate) { currentDate.dayOfWeek.value % 7 }

    val availableYears =
        remember(currentDate, firstEvent) {
            val startYear = firstEvent?.event?.timestamp?.year ?: currentDate.year
            (currentDate.year downTo startYear).toList()
        }

    val weeklyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(first.event.timestamp.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")
                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)
                    val text =
                        when {
                            date.year != currentDate.year -> "$startDateFormatted, ${date.year} - $endDateFormatted, ${endDate.year}"
                            date.month != endDate.month -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    Pair(index, text)
                }.toList()
        }

    val monthlyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) { it.minusMonths(1) }
                .takeWhile { it.isAfter(first.event.timestamp.withDayOfMonth(1)) }
                .mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val text = if (date.year != currentDate.year) "${formatter.format(date)} ${date.year}" else formatter.format(date)
                    Pair(index, text)
                }.toList()
        }

    val yearlyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) { it.minusYears(1) }
                .takeWhile { it.isAfter(first.event.timestamp) }
                .mapIndexed { index, date -> Pair(index, "${date.year}") }
                .toList()
        }

    val continuousRanges =
        listOf(
            StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
            StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
            StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
            StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
            StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
            StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
        )

    val rangeChips =
        when (selectedOption) {
            OptionStats.WEEKS -> weeklyDates
            OptionStats.MONTHS -> monthlyDates
            OptionStats.YEARS -> yearlyDates
            OptionStats.CONTINUOUS -> continuousRanges
        }

    val modeOptions =
        listOf(
            OptionStats.CONTINUOUS to stringResource(R.string.continuous),
            OptionStats.WEEKS to stringResource(R.string.weeks),
            OptionStats.MONTHS to stringResource(R.string.months),
            OptionStats.YEARS to stringResource(R.string.years),
        )

    val selectedModeLabel = modeOptions.first { it.first == selectedOption }.second
    val selectedRangeLabel = rangeChips.firstOrNull { it.first == indexChips }?.second
    val periodSubtitle =
        if (selectedRangeLabel != null) joinByBullet(selectedModeLabel, selectedRangeLabel) else selectedModeLabel
    val periodKey = "${selectedOption.name}-$indexChips"

    val topSongPlays = rankedSongs.firstOrNull()?.songCountListened ?: 0
    val podiumSongs = rankedSongs.take(PODIUM_SIZE)
    val listedSongs = rankedSongs.drop(PODIUM_SIZE)

    val playSong: (SongWithStats) -> Unit = { song ->
        val songEntity = songsById[song.id]
        if (song.id == mediaMetadata?.id) {
            playerConnection?.player?.togglePlayPause()
        } else if (songEntity != null) {
            playerConnection?.playQueue(
                YouTubeQueue(
                    endpoint = WatchEndpoint(song.id),
                    preloadItem = songEntity.toMediaMetadata(),
                ),
            )
        }
    }

    val openSongMenu: (Song) -> Unit = { songEntity ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(
                originalSong = songEntity,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val openArtistMenu: (Artist) -> Unit = { artist ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            ArtistMenu(
                originalArtist = artist,
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val openAlbumMenu: (Album) -> Unit = { album ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            AlbumMenu(
                originalAlbum = album,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val motion = MaterialTheme.motionScheme
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    AnimatedContent(
                        targetState = periodSubtitle,
                        transitionSpec = {
                            (
                                fadeIn(motion.defaultEffectsSpec()) +
                                    slideInVertically(motion.defaultSpatialSpec()) { it / 2 }
                            ) togetherWith (
                                fadeOut(motion.fastEffectsSpec()) +
                                    slideOutVertically(motion.defaultSpatialSpec()) { -it / 2 }
                            )
                        },
                        label = "statsPeriodSubtitle",
                    ) { subtitle ->
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = viewModel::showYearPicker,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.auto_awesome),
                            contentDescription = stringResource(R.string.year_in_music),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                modifier =
                    Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = scaffoldPadding.calculateTopPadding()),
            ) {
                item(key = "periodDock", contentType = "periodDock") {
                    StatsPeriodDock(
                        modes = modeOptions,
                        selectedMode = selectedOption,
                        onModeSelected = viewModel::onOptionSelected,
                        ranges = rangeChips,
                        selectedRange = indexChips,
                        onRangeSelected = viewModel::onChipIndexChanged,
                        modifier = sectionMotion(),
                    )
                }

                if (!hasPeriodData) {
                    item(key = "periodEmpty", contentType = "periodEmpty") {
                        StatsPeriodEmptyCard(modifier = sectionMotion())
                    }
                } else {
                    item(key = "hero", contentType = "hero") {
                        StatsListeningHero(
                            summary = listeningSummary,
                            periodKey = periodKey,
                            rangeLabel = selectedRangeLabel,
                            modifier = sectionMotion(),
                        )
                    }

                    item(key = "metrics", contentType = "metrics") {
                        StatsMetricsGrid(
                            summary = listeningSummary,
                            modifier = sectionMotion(),
                        )
                    }

                    val topSong = rankedSongs.firstOrNull()
                    val topSongEntity = topSong?.let { songsById[it.id] }
                    val topArtist = mostPlayedArtists.firstOrNull()
                    if (topArtist != null || (topSong != null && topSongEntity != null)) {
                        item(key = "spotlights", contentType = "spotlights") {
                            StatsSpotlights(
                                topArtist = topArtist,
                                topSong = topSong,
                                topSongEntity = topSongEntity,
                                isTopSongActive = topSong != null && topSong.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onArtistClick = { artist -> navController.navigateToArtist(artist.id) },
                                onArtistLongClick = openArtistMenu,
                                onSongClick = playSong,
                                onSongLongClick = openSongMenu,
                                modifier = sectionMotion(),
                            )
                        }
                    }

                    if (mostPlayedArtists.any { (it.timeListened ?: 0) > 0 }) {
                        item(key = "artistShareHeader", contentType = "sectionHeader") {
                            StatsSectionHeader(
                                title = stringResource(R.string.stats_artist_breakdown),
                                modifier = sectionMotion(),
                            )
                        }
                        item(key = "artistShare", contentType = "artistShare") {
                            StatsArtistShareCard(
                                artists = mostPlayedArtists,
                                totalTimeListened = listeningSummary.totalTimeListened,
                                onArtistClick = { artist -> navController.navigateToArtist(artist.id) },
                                modifier = sectionMotion(),
                            )
                        }
                    }

                    if (data.listeningByDayOfWeek.isNotEmpty() || data.listeningByHour.isNotEmpty()) {
                        item(key = "rhythmHeader", contentType = "sectionHeader") {
                            StatsSectionHeader(
                                title = stringResource(R.string.stats_listening_patterns),
                                modifier = sectionMotion(),
                            )
                        }
                        item(key = "rhythm", contentType = "rhythm") {
                            StatsListeningRhythm(
                                daySlots = data.listeningByDayOfWeek,
                                hourSlots = data.listeningByHour,
                                currentDayOfWeek = currentDayOfWeek,
                                modifier = sectionMotion(),
                            )
                        }
                    }

                    if (rankedSongs.isNotEmpty()) {
                        item(key = "songsHeader", contentType = "sectionHeader") {
                            StatsSectionHeader(
                                title = stringResource(R.string.stats_top_songs),
                                count = data.rankedSongCount,
                                modifier = sectionMotion(),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.most_played_songs),
                                                items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                                            ),
                                        )
                                    },
                                    enabled = playerConnection != null && mostPlayedSongs.isNotEmpty(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.shuffle))
                                }
                            }
                        }

                        item(key = "songsPodium", contentType = "podium") {
                            StatsSongsPodium(
                                songs = podiumSongs,
                                songsById = songsById,
                                activeSongId = mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onSongClick = playSong,
                                onSongLongClick = openSongMenu,
                                modifier = sectionMotion(),
                            )
                        }

                        itemsIndexed(
                            items = listedSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "rankedSong" },
                        ) { index, song ->
                            val songEntity = songsById[song.id] ?: return@itemsIndexed
                            StatsRankedSongRow(
                                song = song,
                                songEntity = songEntity,
                                rank = index + PODIUM_SIZE + 1,
                                index = index,
                                count = listedSongs.size,
                                topSongPlays = topSongPlays,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onClick = { playSong(song) },
                                onLongClick = { openSongMenu(songEntity) },
                                modifier = sectionMotion(),
                            )
                        }

                        if (data.canExpandSongList) {
                            item(key = "songListExpansion", contentType = "sectionAction") {
                                StatsExpandSongsButton(
                                    expanded = data.isSongListExpanded,
                                    totalCount = data.rankedSongCount,
                                    onToggle = viewModel::toggleSongListExpanded,
                                    modifier = sectionMotion(),
                                )
                            }
                        }
                    }

                    if (mostPlayedArtists.isNotEmpty()) {
                        item(key = "artistsHeader", contentType = "sectionHeader") {
                            StatsSectionHeader(
                                title = stringResource(R.string.artists),
                                count = mostPlayedArtists.size,
                                modifier = sectionMotion(),
                            )
                        }
                        item(key = "artistsShelf", contentType = "artistsShelf") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = sectionMotion(),
                            ) {
                                itemsIndexed(
                                    items = mostPlayedArtists,
                                    key = { _, artist -> artist.id },
                                    contentType = { _, _ -> "artist" },
                                ) { index, artist ->
                                    StatsArtistOrb(
                                        artist = artist,
                                        rank = index + 1,
                                        onClick = { navController.navigateToArtist(artist.id) },
                                        onLongClick = { openArtistMenu(artist) },
                                        modifier = Modifier.animateItem(placementSpec = motion.defaultSpatialSpec()),
                                    )
                                }
                            }
                        }
                    }

                    if (mostPlayedAlbums.isNotEmpty()) {
                        item(key = "albumsHeader", contentType = "sectionHeader") {
                            StatsSectionHeader(
                                title = stringResource(R.string.albums),
                                count = mostPlayedAlbums.size,
                                modifier = sectionMotion(),
                            )
                        }
                        item(key = "albumsShelf", contentType = "albumsShelf") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = sectionMotion(),
                            ) {
                                itemsIndexed(
                                    items = mostPlayedAlbums,
                                    key = { _, album -> album.id },
                                    contentType = { _, _ -> "album" },
                                ) { index, album ->
                                    StatsAlbumTile(
                                        album = album,
                                        rank = index + 1,
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        onClick = { navController.navigateToAlbum(album.id) },
                                        onLongClick = { openAlbumMenu(album) },
                                        modifier = Modifier.animateItem(placementSpec = motion.defaultSpatialSpec()),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bottomSpacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (isYearPickerOpen) {
                StatsYearPickerDialog(
                    availableYears = availableYears,
                    selectedYear = currentDate.year,
                    onSelectYear = { year ->
                        viewModel.dismissYearPicker()
                        navController.navigate("year_in_music?year=$year")
                    },
                    onDismiss = viewModel::dismissYearPicker,
                )
            }
        }
    }
}

@Composable
private fun LazyItemScope.sectionMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
private fun rememberIntegerFormat(): NumberFormat {
    val locale = currentLocale()
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

@Composable
private fun rememberHourLabeler(): (Int) -> String {
    val context = LocalContext.current
    val locale = currentLocale()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "ha")
        val formatter = SimpleDateFormat(pattern, locale)
        val calendar = Calendar.getInstance()
        val labeler: (Int) -> String = { hour ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, 0)
            formatter.format(calendar.time)
        }
        labeler
    }
}

private fun fullDayName(
    slot: Int,
    locale: Locale,
): String = DayOfWeek.of(if (slot == 0) 7 else slot).getDisplayName(TextStyle.FULL, locale)

@Composable
private fun StatsStatusScreen(
    navController: NavController,
    loading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats)) },
                subtitle = {
                    Text(
                        text = stringResource(R.string.settings_stats_subtitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                ContainedLoadingIndicator(modifier = Modifier.size(64.dp))
            } else {
                val isError = errorMessage != null
                Column(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatsStateEmblem(
                        iconRes = if (isError) R.drawable.error else R.drawable.graphic_eq,
                        shape = if (isError) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.SoftBurst.toShape(),
                        containerColor =
                            if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        contentColor =
                            if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        size = 120.dp,
                    )
                    Text(
                        text = errorMessage ?: stringResource(R.string.stats_empty_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    if (!isError) {
                        Text(
                            text = stringResource(R.string.stats_empty_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (onRetry != null) {
                        Button(
                            onClick = onRetry,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsStateEmblem(
    @DrawableRes iconRes: Int,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
) {
    val motion = MaterialTheme.motionScheme
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, motion.slowSpatialSpec()) }
    Box(
        modifier =
            Modifier
                .size(size)
                .graphicsLayer {
                    val scale = 0.6f + 0.4f * entrance.value
                    scaleX = scale
                    scaleY = scale
                    rotationZ = (1f - entrance.value) * -45f
                }.background(containerColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.36f),
        )
    }
}

@Composable
private fun StatsPeriodDock(
    modes: List<Pair<OptionStats, String>>,
    selectedMode: OptionStats,
    onModeSelected: (OptionStats) -> Unit,
    ranges: List<Pair<Int, String>>,
    selectedRange: Int,
    onRangeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.calendar_today),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.stats_time_range),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    val checked = mode == selectedMode
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onModeSelected(mode) },
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                    ) {
                        Text(
                            text = label,
                            style =
                                if (checked) {
                                    MaterialTheme.typography.labelLargeEmphasized
                                } else {
                                    MaterialTheme.typography.labelLarge
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedMode to ranges,
                contentKey = { it.first },
                transitionSpec = {
                    val from = modes.indexOfFirst { it.first == initialState.first }
                    val to = modes.indexOfFirst { it.first == targetState.first }
                    val direction = if (to >= from) 1 else -1
                    (
                        slideInHorizontally(motion.defaultSpatialSpec()) { width -> direction * width / 3 } +
                            fadeIn(motion.defaultEffectsSpec())
                    ) togetherWith (
                        slideOutHorizontally(motion.defaultSpatialSpec()) { width -> -direction * width / 3 } +
                            fadeOut(motion.fastEffectsSpec())
                    )
                },
                label = "statsRangeRow",
            ) { (_, targetRanges) ->
                StatsRangeRow(
                    ranges = targetRanges,
                    selectedRange = selectedRange,
                    onRangeSelected = onRangeSelected,
                )
            }
        }
    }
}

@Composable
private fun StatsRangeRow(
    ranges: List<Pair<Int, String>>,
    selectedRange: Int,
    onRangeSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex = ranges.indexOfFirst { it.first == selectedRange }
    LaunchedEffect(selectedIndex, ranges.size) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = ranges,
            key = { it.first },
            contentType = { "range" },
        ) { (value, label) ->
            val checked = value == selectedRange
            ToggleButton(
                checked = checked,
                onCheckedChange = { onRangeSelected(value) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = label,
                    style =
                        if (checked) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatsPeriodEmptyCard(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsStateEmblem(
                iconRes = R.drawable.history,
                shape = MaterialShapes.SoftBurst.toShape(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 88.dp,
            )
            Text(
                text = stringResource(R.string.stats_period_empty_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.stats_period_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatsListeningHero(
    summary: ListeningSummary,
    periodKey: String,
    rangeLabel: String?,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val integerFormat = rememberIntegerFormat()
    val totalMinutes = summary.totalTimeListened / 60_000L
    val animatedMinutes = remember { Animatable(0f) }
    LaunchedEffect(totalMinutes) {
        animatedMinutes.animateTo(totalMinutes.toFloat(), motion.slowEffectsSpec())
    }

    var morphTurns by remember { mutableIntStateOf(0) }
    LaunchedEffect(periodKey) { morphTurns += 1 }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(
        targetValue = if (morphTurns % 2 == 1) 1f else 0f,
        animationSpec = motion.slowSpatialSpec(),
        label = "heroMorph",
    )
    val accentRotation by animateFloatAsState(
        targetValue = morphTurns * 40f,
        animationSpec = motion.slowSpatialSpec(),
        label = "heroRotation",
    )

    val totalLabel = stringResource(R.string.stats_total_time_listened)
    val minutesUnit = stringResource(R.string.year_in_music_minutes_unit)
    val exactTime = makeTimeString(summary.totalTimeListened)
    val formattedMinutes = integerFormat.format(totalMinutes)
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 56.dp, y = (-48).dp)
                        .size(220.dp)
                        .graphicsLayer { rotationZ = accentRotation }
                        .background(
                            color = primary.copy(alpha = 0.16f),
                            shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f)),
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-28).dp, y = 20.dp)
                        .size(72.dp)
                        .graphicsLayer { rotationZ = -accentRotation * 0.5f }
                        .background(tertiary.copy(alpha = 0.18f), MaterialShapes.Sunny.toShape()),
            )
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .clearAndSetSemantics {
                            contentDescription =
                                listOfNotNull(rangeLabel, totalLabel, "$formattedMinutes $minutesUnit", exactTime)
                                    .joinToString(", ")
                        },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (rangeLabel != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            text = rangeLabel,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.timer),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = totalLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = integerFormat.format(animatedMinutes.value.roundToLong()),
                    style = MaterialTheme.typography.displayLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = joinByBullet(minutesUnit, exactTime),
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun StatsMetricsGrid(
    summary: ListeningSummary,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val metrics =
        listOf(
            StatsMetric(
                value = summary.totalPlayCount,
                label = stringResource(R.string.stats_total_plays),
                iconRes = R.drawable.play,
                shape = MaterialShapes.Cookie6Sided.toShape(),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            StatsMetric(
                value = summary.uniqueSongsCount,
                label = stringResource(R.string.stats_unique_songs),
                iconRes = R.drawable.music_note,
                shape = MaterialShapes.Clover4Leaf.toShape(),
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            ),
            StatsMetric(
                value = summary.uniqueArtistsCount,
                label = stringResource(R.string.stats_unique_artists),
                iconRes = R.drawable.artist,
                shape = MaterialShapes.Cookie4Sided.toShape(),
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
            ),
            StatsMetric(
                value = summary.uniqueAlbumsCount,
                label = stringResource(R.string.stats_unique_albums),
                iconRes = R.drawable.album,
                shape = MaterialShapes.Pentagon.toShape(),
                containerColor = colors.tertiary,
                contentColor = colors.onTertiary,
            ),
        )

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMetrics.forEach { metric ->
                        StatsMetricTile(
                            metric = metric,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

private class StatsMetric(
    val value: Int,
    val label: String,
    @DrawableRes val iconRes: Int,
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun StatsMetricTile(
    metric: StatsMetric,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val integerFormat = rememberIntegerFormat()
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(metric.value) {
        animatedValue.animateTo(metric.value.toFloat(), motion.slowEffectsSpec())
    }
    val formattedValue = integerFormat.format(metric.value)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .clearAndSetSemantics { contentDescription = "${metric.label}: $formattedValue" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(metric.containerColor, metric.shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(metric.iconRes),
                    contentDescription = null,
                    tint = metric.contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = integerFormat.format(animatedValue.value.roundToLong()),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatsSpotlights(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: Song?,
    isTopSongActive: Boolean,
    isPlaying: Boolean,
    onArtistClick: (Artist) -> Unit,
    onArtistLongClick: (Artist) -> Unit,
    onSongClick: (SongWithStats) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val wide = maxWidth >= 600.dp
        val artistCard: @Composable (Modifier) -> Unit = { cardModifier ->
            if (topArtist != null) {
                StatsArtistSpotlight(
                    artist = topArtist,
                    onClick = { onArtistClick(topArtist) },
                    onLongClick = { onArtistLongClick(topArtist) },
                    modifier = cardModifier,
                )
            }
        }
        val songCard: @Composable (Modifier) -> Unit = { cardModifier ->
            if (topSong != null && topSongEntity != null) {
                StatsSongSpotlight(
                    song = topSong,
                    songEntity = topSongEntity,
                    isActive = isTopSongActive,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(topSong) },
                    onLongClick = { onSongLongClick(topSongEntity) },
                    modifier = cardModifier,
                )
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (topArtist != null) {
                    artistCard(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (topSong != null && topSongEntity != null) {
                    songCard(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                artistCard(Modifier.fillMaxWidth())
                songCard(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatsArtistSpotlight(
    artist: Artist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatsShapedArtwork(
                    url = artist.artist.thumbnailUrl,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    placeholderRes = R.drawable.artist,
                    modifier = Modifier.size(92.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stats_favourite_artist),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                    )
                    Text(
                        text = artist.artist.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsStatPill(
                    iconRes = R.drawable.play,
                    text = pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                )
                StatsStatPill(
                    iconRes = R.drawable.timer,
                    text = makeTimeString(artist.timeListened?.toLong()),
                )
            }
        }
    }
}

@Composable
private fun StatsSongSpotlight(
    song: SongWithStats,
    songEntity: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistNames = remember(songEntity) { songEntity.artists.joinToString { it.name } }
    val showPause = isActive && isPlaying
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.large,
                    maxSizePx = 400,
                    modifier = Modifier.size(92.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stats_favourite_song),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                    )
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (artistNames.isNotEmpty()) {
                        Text(
                            text = artistNames,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatsStatPill(
                        iconRes = R.drawable.play,
                        text = pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                    )
                    StatsStatPill(
                        iconRes = R.drawable.timer,
                        text = makeTimeString(song.timeListened),
                    )
                }
                FilledIconButton(
                    onClick = onClick,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                ) {
                    Icon(
                        painter = painterResource(if (showPause) R.drawable.pause else R.drawable.play),
                        contentDescription =
                            stringResource(if (showPause) R.string.stats_pause_song else R.string.play),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsStatPill(
    @DrawableRes iconRes: Int,
    text: String,
) {
    if (text.isEmpty()) return
    Surface(
        shape = CircleShape,
        color = LocalContentColor.current.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatsShapedArtwork(
    url: String?,
    shape: Shape,
    @DrawableRes placeholderRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(placeholderRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StatsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val integerFormat = rememberIntegerFormat()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 20.dp, top = 24.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = integerFormat.format(count),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun StatsArtistShareCard(
    artists: List<Artist>,
    totalTimeListened: Long,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        remember(artists) {
            artists.filter { (it.timeListened ?: 0) > 0 }.take(ARTIST_SHARE_COUNT)
        }
    if (entries.isEmpty()) return

    val motion = MaterialTheme.motionScheme
    val locale = currentLocale()
    val percentFormat = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val visibleTime = remember(entries) { entries.sumOf { it.timeListened?.toLong() ?: 0L } }
    val ribbonDenominator = max(totalTimeListened, visibleTime).coerceAtLeast(1L).toFloat()
    val shareDenominator = (if (totalTimeListened > 0L) totalTimeListened else visibleTime).coerceAtLeast(1L).toFloat()
    val segmentColors = artistShareColors(entries.size)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val animatedFractions =
        entries.map { artist ->
            key(artist.id) {
                val target = ((artist.timeListened ?: 0).toFloat() / ribbonDenominator).coerceIn(0f, 1f)
                val animated by animateFloatAsState(
                    targetValue = target,
                    animationSpec = motion.slowSpatialSpec(),
                    label = "artistShare",
                )
                animated.coerceIn(0f, 1f)
            }
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(28.dp),
            ) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                val gap = 4.dp.toPx()
                var start = 0f
                animatedFractions.forEachIndexed { index, fraction ->
                    val segmentWidth = fraction * size.width
                    val drawnWidth = segmentWidth - gap
                    if (drawnWidth > 0f) {
                        drawRoundRect(
                            color = segmentColors[index],
                            topLeft = Offset(start, 0f),
                            size = Size(drawnWidth.coerceAtLeast(size.height.coerceAtMost(segmentWidth)), size.height),
                            cornerRadius = radius,
                        )
                    }
                    start += segmentWidth
                }
            }

            Column {
                entries.forEachIndexed { index, artist ->
                    val share = ((artist.timeListened ?: 0).toFloat() / shareDenominator).coerceIn(0f, 1f)
                    StatsShareLegendRow(
                        artist = artist,
                        color = segmentColors[index],
                        shareText = percentFormat.format(share),
                        onClick = { onArtistClick(artist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsShareLegendRow(
    artist: Artist,
    color: Color,
    shareText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 6.dp, height = 32.dp)
                    .background(color, CircleShape),
        )
        StatsShapedArtwork(
            url = artist.artist.thumbnailUrl,
            shape = CircleShape,
            placeholderRes = R.drawable.artist,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.artist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = makeTimeString(artist.timeListened?.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = shareText,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}

@Composable
private fun artistShareColors(count: Int): List<Color> {
    val colors = MaterialTheme.colorScheme
    val palette =
        listOf(
            colors.primary,
            colors.tertiary,
            colors.secondary,
            colors.primary.copy(alpha = 0.55f),
            colors.tertiary.copy(alpha = 0.55f),
        )
    return List(count) { index -> palette[index % palette.size] }
}

@Composable
private fun StatsListeningRhythm(
    daySlots: List<ListeningBySlot>,
    hourSlots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (maxWidth >= 720.dp && daySlots.isNotEmpty() && hourSlots.isNotEmpty()) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatsWeekRhythmCard(
                    slots = daySlots,
                    currentDayOfWeek = currentDayOfWeek,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                StatsHourClockCard(
                    slots = hourSlots,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (daySlots.isNotEmpty()) {
                    StatsWeekRhythmCard(
                        slots = daySlots,
                        currentDayOfWeek = currentDayOfWeek,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (hourSlots.isNotEmpty()) {
                    StatsHourClockCard(
                        slots = hourSlots,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCardTitle(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsWeekRhythmCard(
    slots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    val dayLabels =
        listOf(
            R.string.day_sun,
            R.string.day_mon,
            R.string.day_tue,
            R.string.day_wed,
            R.string.day_thu,
            R.string.day_fri,
            R.string.day_sat,
        )
    val locale = currentLocale()
    val motion = MaterialTheme.motionScheme
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { (slots.maxOfOrNull { it.timeListened } ?: 0L).coerceAtLeast(1L) }
    val peakSlot = remember(slots) { slots.filter { it.timeListened > 0 }.maxByOrNull { it.timeListened }?.slot }
    val primary = MaterialTheme.colorScheme.primary
    val emptyBar = MaterialTheme.colorScheme.surfaceContainerHighest
    val sunnyShape = MaterialShapes.Sunny.toShape()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsCardTitle(
                title = stringResource(R.string.stats_listening_by_day),
                trailing = peakSlot?.let { stringResource(R.string.stats_peak_hour, fullDayName(it, locale)) },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                for (day in 0..6) {
                    val time = slotMap[day]?.timeListened ?: 0L
                    val fraction = time.toFloat() / maxTime
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = motion.defaultSpatialSpec(),
                        label = "dayBar$day",
                    )
                    val isPeak = day == peakSlot
                    val isToday = day == currentDayOfWeek
                    val barColor =
                        when {
                            time <= 0L -> emptyBar
                            isPeak -> primary
                            else -> primary.copy(alpha = 0.24f + 0.46f * fraction)
                        }
                    val description =
                        joinByBullet(fullDayName(day, locale), makeTimeString(time).ifEmpty { null })
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .width(30.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(30.dp)
                                        .heightIn(min = 30.dp)
                                        .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                                        .background(barColor, CircleShape),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (isPeak) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(top = 3.dp)
                                                .size(24.dp)
                                                .background(MaterialTheme.colorScheme.tertiary, sunnyShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.star),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(dayLabels[day]),
                            style =
                                if (isToday) {
                                    MaterialTheme.typography.labelMediumEmphasized
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                            color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(4.dp)
                                    .background(if (isToday) primary else Color.Transparent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHourClockCard(
    slots: List<ListeningBySlot>,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val hourLabel = rememberHourLabeler()
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { (slots.maxOfOrNull { it.timeListened } ?: 0L).coerceAtLeast(1L) }
    val peak = remember(slots) { slots.filter { it.timeListened > 0 }.maxByOrNull { it.timeListened } }
    val peakLabel = peak?.let { hourLabel(it.slot) }

    val primary = MaterialTheme.colorScheme.primary
    val emptySpoke = MaterialTheme.colorScheme.outlineVariant
    val centerDisc = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val quarterLabels =
        remember(textMeasurer, hourLabel, labelStyle) {
            listOf(0, 6, 12, 18).map { hour -> hour to textMeasurer.measure(hourLabel(hour), labelStyle) }
        }

    val animatedFractions =
        (0..23).map { hour ->
            val target = (slotMap[hour]?.timeListened ?: 0L).toFloat() / maxTime
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = motion.slowSpatialSpec(),
                label = "hourSpoke$hour",
            )
            animated.coerceIn(0f, 1.08f)
        }

    val chartTitle = stringResource(R.string.stats_listening_by_hour)
    val mostActiveLabel = stringResource(R.string.stats_most_active)
    val chartDescription =
        if (peakLabel != null) {
            joinByBullet(chartTitle, stringResource(R.string.stats_peak_hour, peakLabel))
        } else {
            chartTitle
        }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsCardTitle(title = chartTitle)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clearAndSetSemantics { contentDescription = chartDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension / 2f
                        val labelBand = 22.dp.toPx()
                        val outer = radius - labelBand
                        val inner = outer * 0.5f
                        val spokeWidth = ((2f * PI.toFloat() * inner) / 24f) * 0.6f

                        drawCircle(color = centerDisc, radius = inner - spokeWidth, center = center)

                        animatedFractions.forEachIndexed { hour, fraction ->
                            val angle = (hour / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                            val direction = Offset(cos(angle), sin(angle))
                            val startRadius = inner + spokeWidth / 2f
                            val reach = (outer - inner) * fraction
                            val endRadius = max(startRadius, inner + reach - spokeWidth / 2f)
                            val isPeak = peak != null && hour == peak.slot
                            val spokeColor =
                                when {
                                    fraction <= 0f -> emptySpoke
                                    isPeak -> primary
                                    else -> primary.copy(alpha = 0.28f + 0.42f * fraction.coerceAtMost(1f))
                                }
                            drawLine(
                                color = spokeColor,
                                start = center + direction * startRadius,
                                end = center + direction * endRadius,
                                strokeWidth = spokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }

                        quarterLabels.forEach { (hour, layout) ->
                            val angle = (hour / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                            val labelCenter = center + Offset(cos(angle), sin(angle)) * (radius - labelBand / 2f)
                            drawText(
                                textLayoutResult = layout,
                                topLeft =
                                    Offset(
                                        labelCenter.x - layout.size.width / 2f,
                                        labelCenter.y - layout.size.height / 2f,
                                    ),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mostActiveLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedContent(
                            targetState = peakLabel,
                            transitionSpec = {
                                (
                                    fadeIn(motion.defaultEffectsSpec()) +
                                        slideInVertically(motion.defaultSpatialSpec()) { it / 2 }
                                ) togetherWith (
                                    fadeOut(motion.fastEffectsSpec()) +
                                        slideOutVertically(motion.defaultSpatialSpec()) { -it / 2 }
                                )
                            },
                            label = "peakHour",
                        ) { label ->
                            Text(
                                text = label ?: "–",
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                color = primary,
                                maxLines = 1,
                            )
                        }
                        if (peak != null) {
                            Text(
                                text = makeTimeString(peak.timeListened),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSongsPodium(
    songs: List<SongWithStats>,
    songsById: Map<String, Song>,
    activeSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongWithStats) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = songs.mapNotNull { song -> songsById[song.id]?.let { song to it } }
    if (entries.isEmpty()) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.forEachIndexed { index, (song, songEntity) ->
            StatsPodiumCard(
                song = song,
                rank = index + 1,
                isActive = song.id == activeSongId,
                isPlaying = isPlaying,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(songEntity) },
                modifier =
                    Modifier
                        .weight(if (index == 0) 1.15f else 1f)
                        .fillMaxHeight(),
            )
        }
        repeat(PODIUM_SIZE - entries.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatsPodiumCard(
    song: SongWithStats,
    rank: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isChampion = rank == 1
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (isChampion) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (isChampion) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
    ) {
        Column(
            modifier =
                Modifier
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.large,
                    maxSizePx = 400,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                )
                StatsRankBadge(
                    rank = rank,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-4).dp),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatsRankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val isChampion = rank == 1
    val rankDescription = stringResource(R.string.stats_rank_description, rank)
    Box(
        modifier =
            modifier
                .size(if (isChampion) 38.dp else 32.dp)
                .background(
                    color = if (isChampion) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    shape = if (isChampion) MaterialShapes.Sunny.toShape() else MaterialShapes.Cookie6Sided.toShape(),
                ).semantics { contentDescription = rankDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = if (isChampion) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun StatsRankedSongRow(
    song: SongWithStats,
    songEntity: Song,
    rank: Int,
    index: Int,
    count: Int,
    topSongPlays: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val artistNames = remember(songEntity) { songEntity.artists.joinToString { it.name } }
    val playShare = if (topSongPlays > 0) song.songCountListened.toFloat() / topSongPlays else 0f
    val animatedShare by animateFloatAsState(
        targetValue = playShare.coerceIn(0f, 1f),
        animationSpec = motion.defaultSpatialSpec(),
        label = "songPlayShare",
    )
    val shareColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 1.dp),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.medium,
                    maxSizePx = 200,
                    modifier = Modifier.size(52.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text =
                    joinByBullet(
                        artistNames.ifEmpty { null },
                        pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = makeTimeString(song.timeListened),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Canvas(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .height(6.dp),
                ) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color = trackColor, cornerRadius = radius)
                    val fillWidth = size.width * animatedShare
                    if (fillWidth > 0f) {
                        drawRoundRect(
                            color = shareColor,
                            size = Size(fillWidth.coerceAtLeast(size.height), size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        },
    ) {
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsExpandSongsButton(
    expanded: Boolean,
    totalCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "expandIcon",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilledTonalButton(
            onClick = onToggle,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(ButtonDefaults.IconSize)
                        .graphicsLayer { rotationZ = iconRotation },
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text =
                    if (expanded) {
                        stringResource(R.string.stats_show_top_songs)
                    } else {
                        stringResource(R.string.stats_show_all_songs, totalCount)
                    },
            )
        }
    }
}

@Composable
private fun StatsArtistOrb(
    artist: Artist,
    rank: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isChampion = rank == 1
    Column(
        modifier =
            modifier
                .width(120.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(modifier = Modifier.size(108.dp)) {
            StatsShapedArtwork(
                url = artist.artist.thumbnailUrl,
                shape = if (isChampion) MaterialShapes.Cookie9Sided.toShape() else CircleShape,
                placeholderRes = R.drawable.artist,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                shape = CircleShape,
                color = if (isChampion) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                contentColor = if (isChampion) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Text(
                    text = stringResource(R.string.stats_rank, rank),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.artist.name,
            style = MaterialTheme.typography.titleSmallEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = makeTimeString(artist.timeListened?.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatsAlbumTile(
    album: Album,
    rank: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playCount = album.songCountListened ?: 0
    Column(
        modifier =
            modifier
                .width(156.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            ItemThumbnail(
                thumbnailUrl = album.album.thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = MaterialTheme.shapes.large,
                maxSizePx = 400,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_rank, rank),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.album.title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                joinByBullet(
                    pluralStringResource(R.plurals.n_time, playCount, playCount),
                    makeTimeString(album.timeListened).ifEmpty { null },
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsYearPickerDialog(
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.auto_awesome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.year_in_music),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_year_picker_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableYears.forEach { year ->
                        if (year == selectedYear) {
                            Button(
                                onClick = { onSelectYear(year) },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onSelectYear(year) },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(text = year.toString())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
    )
}

private const val PODIUM_SIZE = 3
private const val ARTIST_SHARE_COUNT = 5

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
