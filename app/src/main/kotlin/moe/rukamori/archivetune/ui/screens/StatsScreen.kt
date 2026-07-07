/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningSummary
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ChoiceChipsRow
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalAlbumsGrid
import moe.rukamori.archivetune.ui.component.LocalArtistsGrid
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.StatsScreenState
import moe.rukamori.archivetune.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
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
                StatsStatusScreen(
                    navController = navController,
                    loading = true,
                )
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

            is StatsScreenState.Success -> state.data
        }

    val indexChips = data.selectedPeriodIndex
    val mostPlayedSongs = data.mostPlayedSongs
    val mostPlayedSongsStats = data.visibleRankedSongs
    val mostPlayedArtists = data.mostPlayedArtists
    val mostPlayedAlbums = data.mostPlayedAlbums
    val firstEvent = data.firstEvent
    val selectedOption = data.selectedOption
    val listeningByHour = data.listeningByHour
    val listeningByDayOfWeek = data.listeningByDayOfWeek
    val listeningSummary = data.listeningSummary
    val songsById = remember(mostPlayedSongs) { mostPlayedSongs.associateBy { it.id } }

    val coroutineScope = rememberCoroutineScope()
    val currentDate = remember { LocalDateTime.now() }

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
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showYearPicker,
                        onLongClick = {},
                    ) {
                        Icon(
                            painterResource(R.drawable.auto_awesome),
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
            item(key = "rangeControls", contentType = "controls") {
                StatsFilterPanel(modifier = Modifier.animateItem()) {
                    ChoiceChipsRow(
                        chips =
                            when (selectedOption) {
                                OptionStats.WEEKS -> weeklyDates
                                OptionStats.MONTHS -> monthlyDates
                                OptionStats.YEARS -> yearlyDates
                                OptionStats.CONTINUOUS ->
                                    listOf(
                                        StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
                                        StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
                                        StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
                                        StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
                                        StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
                                        StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                                    )
                            },
                        options =
                            listOf(
                                OptionStats.CONTINUOUS to stringResource(R.string.continuous),
                                OptionStats.WEEKS to stringResource(R.string.weeks),
                                OptionStats.MONTHS to stringResource(R.string.months),
                                OptionStats.YEARS to stringResource(R.string.years),
                            ),
                        selectedOption = selectedOption,
                        onSelectionChange = viewModel::onOptionSelected,
                        currentValue = indexChips,
                        onValueUpdate = viewModel::onChipIndexChanged,
                    )
                }
            }

            item(key = "overview", contentType = "overview") {
                StatsSummarySection(
                    summary = listeningSummary,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "artistDistribution", contentType = "insights") {
                if (mostPlayedArtists.isNotEmpty()) {
                    Column(modifier = Modifier.animateItem()) {
                        StatsSectionHeader(
                            title = stringResource(R.string.stats_artist_breakdown),
                            supportingText = mostPlayedArtists.take(5).size.toString(),
                        )
                        SegmentedArtistChart(
                            artists = mostPlayedArtists.take(5),
                            totalTimeListened = listeningSummary.totalTimeListened,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            item(key = "spotlights", contentType = "spotlights") {
                val topSong = mostPlayedSongsStats.firstOrNull()
                StatsHighlightsSection(
                    topArtist = mostPlayedArtists.firstOrNull(),
                    topSong = topSong,
                    topSongEntity =
                        topSong?.let { rankedSong ->
                            mostPlayedSongs.firstOrNull { it.id == rankedSong.id }
                        },
                    navController = navController,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "listeningPatterns", contentType = "insights") {
                StatsListeningPatterns(
                    daySlots = listeningByDayOfWeek,
                    hourSlots = listeningByHour,
                    currentDayOfWeek = remember { LocalDateTime.now().dayOfWeek.value % 7 },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "mostPlayedSongsHeader", contentType = "sectionHeader") {
                StatsSongsHeader(
                    title = stringResource(R.string.stats_top_songs),
                    count = data.rankedSongCount,
                    shuffleEnabled = playerConnection != null && mostPlayedSongs.isNotEmpty(),
                    onShuffle = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = context.getString(R.string.most_played_songs),
                                items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                            ),
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }

            val visibleRankedSongs = mostPlayedSongsStats

            itemsIndexed(
                items = visibleRankedSongs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "ranked_song" },
            ) { index, song ->
                val songEntity = songsById[song.id] ?: return@itemsIndexed
                RankedSongItem(
                    song = song,
                    rank = index + 1,
                    count = visibleRankedSongs.size,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    onClick = {
                        if (song.id == mediaMetadata?.id) {
                            playerConnection?.player?.togglePlayPause()
                        } else {
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    endpoint = WatchEndpoint(song.id),
                                    preloadItem = songEntity.toMediaMetadata(),
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = songEntity,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }

            if (data.canExpandSongList) {
                item(key = "songListExpansion", contentType = "sectionAction") {
                    TextButton(
                        onClick = viewModel::toggleSongListExpanded,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .animateItem(),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (data.isSongListExpanded) {
                                        R.drawable.expand_less
                                    } else {
                                        R.drawable.expand_more
                                    },
                                ),
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                if (data.isSongListExpanded) {
                                    stringResource(R.string.stats_show_top_songs)
                                } else {
                                    stringResource(R.string.stats_show_all_songs, data.rankedSongCount)
                                },
                        )
                    }
                }
            }

            item(key = "mostPlayedArtists", contentType = "sectionHeader") {
                StatsSectionHeader(
                    title = stringResource(R.string.artists),
                    supportingText = mostPlayedArtists.size.toString(),
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "artistsShelf", contentType = "artists_shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = mostPlayedArtists,
                        key = { artist -> artist.id },
                        contentType = { "artist" },
                    ) { artist ->
                        LocalArtistsGrid(
                            title = artist.artist.name,
                            subtitle =
                                joinByBullet(
                                    pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                                    makeTimeString(artist.timeListened?.toLong()),
                                ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier =
                                Modifier
                                    .width(164.dp)
                                    .combinedClickable(
                                        onClick = { navController.navigate("artist/${artist.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                        )
                    }
                }
            }

            item(key = "mostPlayedAlbumsHeader", contentType = "sectionHeader") {
                StatsSectionHeader(
                    title = stringResource(R.string.albums),
                    supportingText = mostPlayedAlbums.size.toString(),
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "albumsRow", contentType = "albums_row") {
                if (mostPlayedAlbums.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = mostPlayedAlbums,
                            key = { _, album -> album.id },
                            contentType = { _, _ -> "album_grid" },
                        ) { index, album ->
                            val playCount = album.songCountListened ?: 0
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle =
                                    joinByBullet(
                                        pluralStringResource(R.plurals.n_time, playCount, playCount),
                                        makeTimeString(album.timeListened?.toLong()),
                                    ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                    Modifier
                                        .width(172.dp)
                                        .combinedClickable(
                                            onClick = { navController.navigate("album/${album.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }
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
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
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
                LoadingIndicator(modifier = Modifier.size(48.dp))
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text =
                            if (errorMessage == null) {
                                stringResource(R.string.stats_empty_title)
                            } else {
                                errorMessage
                            },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (errorMessage == null) {
                        Text(
                            text = stringResource(R.string.stats_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (onRetry != null) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsFilterPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_time_range),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun StatsSongsHeader(
    title: String,
    count: Int,
    shuffleEnabled: Boolean,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = shuffleEnabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
}

@Composable
private fun StatsListeningPatterns(
    daySlots: List<ListeningBySlot>,
    hourSlots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    if (daySlots.isEmpty() && hourSlots.isEmpty()) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_listening_patterns),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        BoxWithConstraints {
            if (maxWidth >= 720.dp && daySlots.isNotEmpty() && hourSlots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListeningByDayChart(
                        slots = daySlots,
                        currentDayOfWeek = currentDayOfWeek,
                        modifier = Modifier.weight(1f),
                    )
                    ListeningByHourChart(
                        slots = hourSlots,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (daySlots.isNotEmpty()) {
                        ListeningByDayChart(
                            slots = daySlots,
                            currentDayOfWeek = currentDayOfWeek,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hourSlots.isNotEmpty()) {
                        ListeningByHourChart(
                            slots = hourSlots,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSectionHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RankedSongItem(
    song: SongWithStats,
    rank: Int,
    count: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowShapes = ListItemDefaults.segmentedShapes(index = rank - 1, count = count)
    val click = remember(song.id, onClick) { onClick }
    val longClick = remember(song.id, onLongClick) { onLongClick }

    SegmentedListItem(
        onClick = click,
        onLongClick = longClick,
        shapes = rowShapes,
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (rank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text =
                    joinByBullet(
                        pluralStringResource(
                            R.plurals.n_time,
                            song.songCountListened,
                            song.songCountListened,
                        ),
                        makeTimeString(song.timeListened),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun StatsYearPickerDialog(
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.year_in_music),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    items = availableYears,
                    key = { year -> year },
                    contentType = { "year_chip" },
                ) { year ->
                    val isSelected = year == selectedYear
                    Text(
                        text = year.toString(),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    },
                                ).clickable { onSelectYear(year) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
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

@Composable
private fun StatsSummarySection(
    summary: ListeningSummary,
    modifier: Modifier = Modifier,
) {
    if (summary.totalPlayCount == 0) return

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
            val expanded = maxWidth >= 680.dp
            if (expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatsListeningTimeHero(
                        summary = summary,
                        modifier = Modifier.weight(1.2f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatsListeningTimeHero(summary = summary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsListeningTimeHero(
    summary: ListeningSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_total_time_listened),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = makeTimeString(summary.totalTimeListened) ?: "-",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: Song?,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_artist),
                mainText = topArtist.artist.name,
                subText = "${topArtist.songCount} ${stringResource(
                    R.string.songs,
                ).lowercase()} • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                useCircleShape = true,
                onClick = { navController.navigate("artist/${topArtist.id}") },
            )
        }
        if (topSong != null && topSongEntity != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_song),
                mainText = topSong.title,
                subText = "${pluralStringResource(
                    R.plurals.n_time,
                    topSong.songCountListened,
                    topSong.songCountListened,
                )} • ${makeTimeString(topSong.timeListened)}",
                imageUrl = topSong.thumbnailUrl,
                useCircleShape = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    useCircleShape: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(if (useCircleShape) CircleShape else MaterialTheme.shapes.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SegmentedArtistChart(
    artists: List<Artist>,
    totalTimeListened: Long,
    modifier: Modifier = Modifier,
) {
    val visibleArtistTime = remember(artists) { artists.sumOf { it.timeListened?.toLong() ?: 0L } }
    val displayTotalTime =
        remember(totalTimeListened, visibleArtistTime) {
            totalTimeListened.takeIf { it > 0L } ?: visibleArtistTime
        }
    if (visibleArtistTime == 0L) return

    val segmentData =
        remember(artists, visibleArtistTime) {
            val rawSegments =
                artists.mapNotNull { artist ->
                    val time = artist.timeListened?.toLong() ?: 0L
                    if (time <= 0L) return@mapNotNull null
                    artist to (time.toFloat() / visibleArtistTime) * 360f
                }

            if (rawSegments.isEmpty()) {
                emptyList()
            } else {
                val topArtistId = rawSegments.maxByOrNull { it.second }?.first?.id
                val retainedSegments =
                    rawSegments
                        .filter { (_, sweep) -> sweep >= 1f }
                        .ifEmpty { listOf(rawSegments.maxBy { it.second }) }
                val retainedSweep = retainedSegments.sumOf { it.second.toDouble() }.toFloat()
                val remainderSweep = (360f - retainedSweep).coerceAtLeast(0f)
                val completedSegments =
                    retainedSegments.map { (artist, sweep) ->
                        artist to
                            if (artist.id == topArtistId) {
                                sweep + remainderSweep
                            } else {
                                sweep
                            }
                    }

                var startAngle = -90f
                completedSegments.map { (artist, sweep) ->
                    Triple(artist, startAngle, sweep).also {
                        startAngle += sweep
                    }
                }
            }
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val segmentColors =
        remember(primaryColor, segmentData.size) {
            createDistinctArtistColors(
                seedColor = primaryColor,
                count = segmentData.size,
            )
        }

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(140.dp)
                        .drawWithCache {
                            val strokeWidth = size.width * 0.18f
                            val inset = strokeWidth / 2f
                            val arcRect =
                                Rect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                )
                            onDrawBehind {
                                segmentData.forEachIndexed { i, (_, startAngle, sweep) ->
                                    val gapDeg = if (segmentData.size > 1) 2f else 0f
                                    drawArc(
                                        color = segmentColors[i % segmentColors.size],
                                        startAngle = startAngle + gapDeg / 2f,
                                        sweepAngle = (sweep - gapDeg).coerceAtLeast(0f),
                                        useCenter = false,
                                        topLeft = arcRect.topLeft,
                                        size = Size(arcRect.width, arcRect.height),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                            }
                        },
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                segmentData.forEachIndexed { i, (artist, _, sweep) ->
                    val percentage = (sweep / 360f * 100).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(segmentColors[i % segmentColors.size]),
                        )
                        Text(
                            text = artist.artist.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = makeTimeString(displayTotalTime) ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.stats_total_time_listened),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun createDistinctArtistColors(
    seedColor: Color,
    count: Int,
): List<Color> {
    if (count <= 0) return emptyList()

    val seedHsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColor.toArgb(), seedHsv)
    val saturation = seedHsv[1].coerceAtLeast(0.62f)
    val brightness = seedHsv[2].coerceIn(0.68f, 0.88f)
    val hueStep = 360f / count

    return List(count) { index ->
        Color.hsv(
            hue = (seedHsv[0] + hueStep * index) % 360f,
            saturation = saturation,
            value = brightness,
        )
    }
}

@Composable
private fun ListeningByDayChart(
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
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { slots.maxOfOrNull { it.timeListened } ?: 1L }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.secondaryContainer

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_listening_by_day),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                for (day in 0..6) {
                    val time = slotMap[day]?.timeListened ?: 0L
                    val fraction = time.toFloat() / maxTime
                    val barColor = if (day == currentDayOfWeek) primaryColor else containerColor
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(400),
                        label = "bar_$day",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(24.dp)
                                    .height(80.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(24.dp)
                                        .height((80 * animatedFraction).dp.coerceAtLeast(2.dp))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor),
                            )
                        }
                        Text(
                            text = stringResource(dayLabels[day]),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == currentDayOfWeek) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (day == currentDayOfWeek) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningByHourChart(
    slots: List<ListeningBySlot>,
    modifier: Modifier = Modifier,
) {
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { slots.maxOfOrNull { it.timeListened } ?: 1L }
    val peakSlot = remember(slots) { slots.maxByOrNull { it.timeListened }?.slot }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    val peakLabel =
        remember(peakSlot) {
            val formatter = DateTimeFormatter.ofPattern("ha")
            peakSlot?.let { LocalTime.of(it, 0).format(formatter) }
        }
    val timeLabels =
        remember {
            val formatter = DateTimeFormatter.ofPattern("ha")
            listOf(0, 6, 12, 18, 0).map { hour ->
                LocalTime.of(hour, 0).format(formatter)
            }
        }

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_listening_by_hour),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (peakLabel != null) {
                    Text(
                        text = stringResource(R.string.stats_peak_hour, peakLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (hour in 0..23) {
                    val time = slotMap[hour]?.timeListened ?: 0L
                    val fraction = time.toFloat() / maxTime
                    val isPeak = hour == peakSlot
                    val barColor = if (isPeak) primaryColor else containerColor.copy(alpha = 0.6f + fraction * 0.4f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(400),
                        label = "hour_$hour",
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height((48 * animatedFraction).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(barColor),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                timeLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
