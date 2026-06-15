/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import moe.rukamori.archivetune.ui.theme.LocalLiquidGlassEnabled
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.StatPeriod
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningSummary
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ChoiceChipsRow
import moe.rukamori.archivetune.ui.component.HideOnScrollFAB
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.LocalAlbumsGrid
import moe.rukamori.archivetune.ui.component.LocalArtistsGrid
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val listeningByHour by viewModel.listeningByHour.collectAsStateWithLifecycle()
    val listeningByDayOfWeek by viewModel.listeningByDayOfWeek.collectAsStateWithLifecycle()
    val listeningSummary by viewModel.listeningSummary.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val currentDate = remember { LocalDateTime.now() }
    var isYearPickerOpen by remember { mutableStateOf(false) }

    val availableYears = remember(currentDate, firstEvent) {
        val startYear = firstEvent?.event?.timestamp?.year ?: currentDate.year
        (currentDate.year downTo startYear).toList()
    }

    val weeklyDates = remember(currentDate, firstEvent) {
        val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
        generateSequence(currentDate) { it.minusWeeks(1) }
            .takeWhile { it.isAfter(first.event.timestamp.minusWeeks(1)) }
            .mapIndexed { index, date ->
                val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                val formatter = DateTimeFormatter.ofPattern("dd MMM")
                val startDateFormatted = formatter.format(date)
                val endDateFormatted = formatter.format(endDate)
                val text = when {
                    date.year != currentDate.year -> "$startDateFormatted, ${date.year} - $endDateFormatted, ${endDate.year}"
                    date.month != endDate.month -> "$startDateFormatted - $endDateFormatted"
                    else -> "${date.dayOfMonth} - $endDateFormatted"
                }
                Pair(index, text)
            }.toList()
    }

    val monthlyDates = remember(currentDate, firstEvent) {
        val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
        generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) { it.minusMonths(1) }
            .takeWhile { it.isAfter(first.event.timestamp.withDayOfMonth(1)) }
            .mapIndexed { index, date ->
                val formatter = DateTimeFormatter.ofPattern("MMM")
                val text = if (date.year != currentDate.year) "${formatter.format(date)} ${date.year}" else formatter.format(date)
                Pair(index, text)
            }.toList()
    }

    val yearlyDates = remember(currentDate, firstEvent) {
        val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
        generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) { it.minusYears(1) }
            .takeWhile { it.isAfter(first.event.timestamp) }
            .mapIndexed { index, date -> Pair(index, "${date.year}") }
            .toList()
    }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current

    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {
        if (!disableBlur) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)
                    .drawWithCache {
                        val width = this.size.width
                        val height = this.size.height
                        val brush1 = Brush.radialGradient(
                            colors = listOf(color1.copy(alpha = 0.38f), color1.copy(alpha = 0.24f), color1.copy(alpha = 0.14f), color1.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(width * 0.15f, height * 0.1f), radius = width * 0.55f,
                        )
                        val brush2 = Brush.radialGradient(
                            colors = listOf(color2.copy(alpha = 0.34f), color2.copy(alpha = 0.2f), color2.copy(alpha = 0.11f), color2.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.2f), radius = width * 0.65f,
                        )
                        val brush3 = Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.3f), color3.copy(alpha = 0.17f), color3.copy(alpha = 0.09f), color3.copy(alpha = 0.04f), Color.Transparent),
                            center = Offset(width * 0.3f, height * 0.45f), radius = width * 0.6f,
                        )
                        val brush4 = Brush.radialGradient(
                            colors = listOf(color4.copy(alpha = 0.26f), color4.copy(alpha = 0.14f), color4.copy(alpha = 0.08f), color4.copy(alpha = 0.03f), Color.Transparent),
                            center = Offset(width * 0.7f, height * 0.5f), radius = width * 0.7f,
                        )
                        val brush5 = Brush.radialGradient(
                            colors = listOf(color5.copy(alpha = 0.22f), color5.copy(alpha = 0.12f), color5.copy(alpha = 0.06f), color5.copy(alpha = 0.02f), Color.Transparent),
                            center = Offset(width * 0.5f, height * 0.75f), radius = width * 0.8f,
                        )
                        val overlayBrush = if (isLiquidGlassEnabled) null else Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, surfaceColor.copy(alpha = 0.22f), surfaceColor.copy(alpha = 0.55f), surfaceColor),
                            startY = height * 0.4f, endY = height,
                        )
                        onDrawBehind {
                            drawRect(brush = brush1); drawRect(brush = brush2); drawRect(brush = brush3)
                            drawRect(brush = brush4); drawRect(brush = brush5)
                            overlayBrush?.let { drawRect(brush = it) }
                        }
                    }
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            ),
        ) {
            item(contentType = "chips") {
                ChoiceChipsRow(
                    chips = when (selectedOption) {
                        OptionStats.WEEKS -> weeklyDates
                        OptionStats.MONTHS -> monthlyDates
                        OptionStats.YEARS -> yearlyDates
                        OptionStats.CONTINUOUS -> listOf(
                            StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
                            StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
                            StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
                            StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
                            StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
                            StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                        )
                    },
                    options = listOf(
                        OptionStats.CONTINUOUS to stringResource(id = R.string.continuous),
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

            item(contentType = "summary") {
                AnimatedContent(
                    targetState = listeningSummary,
                    transitionSpec = {
                        slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) togetherWith
                                slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
                    },
                    modifier = Modifier.animateItem(),
                    label = "summary",
                ) { summary ->
                    StatsSummarySection(summary = summary)
                }
            }

            item(contentType = "highlights") {
                AnimatedContent(
                    targetState = mostPlayedArtists.firstOrNull() to mostPlayedSongsStats.firstOrNull(),
                    transitionSpec = {
                        slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) togetherWith
                                slideOutVertically(tween(200)) { -it / 4 } + fadeOut(tween(200))
                    },
                    modifier = Modifier.animateItem(),
                    label = "highlights",
                ) { (topArtist, topSong) ->
                    StatsHighlightsSection(
                        topArtist = topArtist,
                        topSong = topSong,
                        topSongEntity = mostPlayedSongs.firstOrNull(),
                        navController = navController,
                    )
                }
            }

            item(key = "artistDonutChart", contentType = "chart") {
                if (mostPlayedArtists.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(8.dp))
                    SegmentedArtistChart(
                        artists = mostPlayedArtists.take(5),
                        totalTimeListened = listeningSummary.totalTimeListened,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }

            item(key = "listeningByDayChart", contentType = "chart") {
                if (listeningByDayOfWeek.isNotEmpty()) {
                    ListeningByDayChart(
                        slots = listeningByDayOfWeek,
                        currentDayOfWeek = remember { LocalDateTime.now().dayOfWeek.value % 7 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }

            item(key = "listeningByHourChart", contentType = "chart") {
                if (listeningByHour.isNotEmpty()) {
                    ListeningByHourChart(
                        slots = listeningByHour,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }

            item(key = "mostPlayedSongsHeader", contentType = "sectionHeader") {
                NavigationTitle(
                    title = "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}",
                    modifier = Modifier.animateItem(),
                )
            }

            val maxPlayCount = mostPlayedSongsStats.maxOfOrNull { it.songCountListened } ?: 1

            itemsIndexed(
                items = mostPlayedSongsStats,
                key = { _, song -> song.id },
                contentType = { _, _ -> "ranked_song" },
            ) { index, song ->
                val playFraction = song.songCountListened.toFloat() / maxPlayCount
                val medalColor = when (index) {
                    0 -> Color(0xFFFFD700)
                    1 -> Color(0xFFC0C0C0)
                    2 -> Color(0xFFCD7F32)
                    else -> null
                }
                val progressBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

                ListItem(
                    title = "${index + 1}. ${song.title}",
                    subtitle = joinByBullet(
                        pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                        makeTimeString(song.timeListened),
                    ),
                    thumbnailContent = {
                        Box {
                            ItemThumbnail(
                                thumbnailUrl = song.thumbnailUrl,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(56.dp),
                            )
                            if (medalColor != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(medalColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                color = progressBarColor,
                                size = Size(size.width * playFraction, size.height),
                            )
                        }
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(song.id),
                                            preloadItem = mostPlayedSongs[index].toMediaMetadata(),
                                        ),
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = mostPlayedSongs[index],
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                        .animateItem(),
                )
            }

            item(key = "mostPlayedArtists", contentType = "sectionHeader") {
                NavigationTitle(
                    title = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(
                items = mostPlayedArtists.chunked(2),
                key = { _, rowArtists -> rowArtists.first().id },
                contentType = { _, _ -> "artist_row" },
            ) { _, rowArtists ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowArtists.forEach { artist ->
                        LocalArtistsGrid(
                            title = artist.artist.name,
                            subtitle = joinByBullet(
                                pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                                makeTimeString(artist.timeListened?.toLong()),
                            ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier = Modifier
                                .weight(1f)
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
                                )
                                .animateItem(),
                        )
                    }
                    repeat(2 - rowArtists.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }

            item(key = "mostPlayedAlbumsHeader", contentType = "sectionHeader") {
                NavigationTitle(
                    title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
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
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle = joinByBullet(
                                    pluralStringResource(R.plurals.n_time, album.songCountListened!!, album.songCountListened),
                                    makeTimeString(album.timeListened?.toLong()),
                                ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }

        if (mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                label = stringResource(R.string.shuffle),
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.most_played_songs),
                            items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                        )
                    )
                },
            )
        }

        TopAppBar(
            title = { Text(stringResource(R.string.stats)) },
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
                    onClick = { isYearPickerOpen = true },
                    onLongClick = {},
                ) {
                    Icon(
                        painterResource(R.drawable.auto_awesome),
                        contentDescription = stringResource(R.string.year_in_music),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        if (isYearPickerOpen) {
            StatsYearPickerDialog(
                availableYears = availableYears,
                selectedYear = currentDate.year,
                onSelectYear = { year ->
                    isYearPickerOpen = false
                    navController.navigate("year_in_music?year=$year")
                },
                onDismiss = { isYearPickerOpen = false },
            )
        }
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
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                }
                            )
                            .clickable { onSelectYear(year) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_total_time_listened),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = makeTimeString(summary.totalTimeListened) ?: "-",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
}

@Composable
private fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: Song?,
    navController: NavController,
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_artist),
                mainText = topArtist.artist.name,
                subText = "${topArtist.songCount} ${stringResource(R.string.songs).lowercase()} • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                useCircleShape = true,
                onClick = { navController.navigate("artist/${topArtist.id}") },
            )
        }
        if (topSong != null && topSongEntity != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_song),
                mainText = topSong.title,
                subText = "${pluralStringResource(R.plurals.n_time, topSong.songCountListened, topSong.songCountListened)} • ${makeTimeString(topSong.timeListened)}",
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
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
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
    val displayTotalTime = remember(totalTimeListened, visibleArtistTime) {
        totalTimeListened.takeIf { it > 0L } ?: visibleArtistTime
    }
    val chartTotalTime = remember(displayTotalTime, visibleArtistTime) {
        maxOf(displayTotalTime, visibleArtistTime)
    }
    if (chartTotalTime == 0L) return

    val segmentData = remember(artists, chartTotalTime) {
        var startAngle = -90f
        artists.mapNotNull { artist ->
            val time = artist.timeListened?.toLong() ?: 0L
            val sweep = (time.toFloat() / chartTotalTime) * 360f
            if (sweep < 1f) return@mapNotNull null
            val entry = Triple(artist, startAngle, sweep)
            startAngle += sweep
            entry
        }
    }
    val visibleSegmentSweep = remember(segmentData) {
        segmentData.sumOf { it.third.toDouble() }.toFloat()
    }
    val remainingSweep = remember(visibleSegmentSweep) {
        (360f - visibleSegmentSweep)
            .takeIf { it >= 1f }
    }

    val segmentColors = remember {
        listOf(
            Color(0xFF6750A4), Color(0xFF4A8FA8), Color(0xFF4CAF50),
            Color(0xFFFF9800), Color(0xFFE91E63),
        )
    }
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .drawWithCache {
                        val strokeWidth = size.width * 0.18f
                        val inset = strokeWidth / 2f
                        val arcRect = Rect(
                            left = inset, top = inset,
                            right = size.width - inset, bottom = size.height - inset,
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
                            remainingSweep?.let { sweep ->
                                drawArc(
                                    color = remainingColor,
                                    startAngle = -90f + visibleSegmentSweep,
                                    sweepAngle = sweep,
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
                            modifier = Modifier
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

@Composable
private fun ListeningByDayChart(
    slots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    val dayLabels = listOf(
        R.string.day_sun, R.string.day_mon, R.string.day_tue, R.string.day_wed,
        R.string.day_thu, R.string.day_fri, R.string.day_sat,
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
                            modifier = Modifier
                                .width(24.dp)
                                .height(80.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
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

    val peakLabel = remember(peakSlot) {
        peakSlot?.let {
            val hour = it % 12
            val adjusted = if (hour == 0) 12 else hour
            val amPm = if (it < 12) "AM" else "PM"
            "$adjusted$amPm"
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
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
                Text(text = "12AM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "6AM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "12PM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "6PM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "12AM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
