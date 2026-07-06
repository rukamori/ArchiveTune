/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AlbumUiState
import moe.rukamori.archivetune.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    // Gradient colors state for album cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Extract gradient colors from album cover
    LaunchedEffect(albumWithSongs?.album?.thumbnailUrl) {
        val thumbnailUrl = albumWithSongs?.album?.thumbnailUrl
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                    .allowHardware(false)
                    .build()

            val result =
                runCatching {
                    context.imageLoader.execute(request)
                }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                    val extractedColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val wrappedSongs =
        remember(albumWithSongs, hideExplicit) {
            val filteredSongs =
                if (hideExplicit) {
                    albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
                } else {
                    albumWithSongs?.songs ?: emptyList()
                }
            filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    var selection by remember { mutableStateOf(false) }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    var downloadProgressToolbarDismissed by remember { mutableStateOf(true) }

    LaunchedEffect(albumWithSongs) {
        val songIds = albumWithSongs?.songs?.map { it.id }.orEmpty()
        if (songIds.isEmpty()) {
            downloads = emptyMap()
            downloadState = HeaderDownloadState.None
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            downloads = currentDownloads
            downloadState = headerDownloadState(songIds, currentDownloads)
        }
    }

    // State for LazyColumn to track scroll
    val lazyListState = rememberLazyListState()

    // Calculate gradient opacity based on scroll position
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !selection && !showTopBarTitle
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        // Gradient background layer
        if (gradientColors.isNotEmpty() && gradientAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.55f)
                        .align(Alignment.TopCenter)
                        .zIndex(-1f)
                        .drawBehind {
                            val width = size.width
                            val height = size.height

                            if (gradientColors.size >= 3) {
                                val c0 = gradientColors[0]
                                val c1 = gradientColors[1]
                                val c2 = gradientColors[2]
                                val c3 = gradientColors.getOrElse(3) { c0 }
                                val c4 = gradientColors.getOrElse(4) { c1 }
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c0.copy(alpha = gradientAlpha * 0.75f),
                                                    c0.copy(alpha = gradientAlpha * 0.4f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.15f),
                                            radius = width * 0.8f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c1.copy(alpha = gradientAlpha * 0.55f),
                                                    c1.copy(alpha = gradientAlpha * 0.3f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.1f, height * 0.4f),
                                            radius = width * 0.6f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c2.copy(alpha = gradientAlpha * 0.5f),
                                                    c2.copy(alpha = gradientAlpha * 0.25f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.9f, height * 0.35f),
                                            radius = width * 0.55f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c3.copy(alpha = gradientAlpha * 0.35f),
                                                    c3.copy(alpha = gradientAlpha * 0.18f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.25f, height * 0.65f),
                                            radius = width * 0.75f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c4.copy(alpha = gradientAlpha * 0.3f),
                                                    c4.copy(alpha = gradientAlpha * 0.15f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.55f, height * 0.85f),
                                            radius = width * 0.9f,
                                        ),
                                )
                            } else if (gradientColors.isNotEmpty()) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.25f),
                                            radius = width * 0.85f,
                                        ),
                                )
                            }

                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                                surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                                surfaceColor,
                                            ),
                                        startY = height * 0.4f,
                                        endY = height,
                                    ),
                            )
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            val albumWithSongs = albumWithSongs
            val hasSongs = albumWithSongs?.songs?.isNotEmpty() == true
            if (hasSongs) {
                // Hero Header
                item(key = "header") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = systemBarsTopPadding + AppBarHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Album Art - Large centered with shadow and rounded corners
                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 8.dp, bottom = 20.dp),
                        ) {
                            Surface(
                                modifier =
                                    Modifier
                                        .size(240.dp)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            spotColor =
                                                gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                                    ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                AsyncImage(
                                    model = albumWithSongs.album.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        // Album Title
                        Text(
                            text = albumWithSongs.album.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Artist Names (Clickable)
                        Text(
                            text =
                                buildAnnotatedString {
                                    withStyle(
                                        style =
                                            MaterialTheme.typography.titleMedium
                                                .copy(
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ).toSpanStyle(),
                                    ) {
                                        albumWithSongs.artists.fastForEachIndexed { index, artist ->
                                            val link =
                                                LinkAnnotation.Clickable(artist.id) {
                                                    navController.navigate("artist/${artist.id}")
                                                }
                                            withLink(link) {
                                                append(artist.name)
                                            }
                                            if (index != albumWithSongs.artists.lastIndex) {
                                                append(", ")
                                            }
                                        }
                                    }
                                },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata Row - Year, Song Count, Duration
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Year
                            albumWithSongs.album.year?.let { year ->
                                MetadataChip(
                                    icon = R.drawable.calendar_today,
                                    text = year.toString(),
                                )
                            }

                            // Song Count
                            MetadataChip(
                                icon = R.drawable.music_note,
                                text =
                                    pluralStringResource(
                                        R.plurals.n_song,
                                        wrappedSongs.size,
                                        wrappedSongs.size,
                                    ),
                            )

                            // Duration
                            val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                            if (totalDuration > 0) {
                                MetadataChip(
                                    icon = R.drawable.timer,
                                    text = makeTimeString(totalDuration * 1000L),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons Row
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isBookmarked = albumWithSongs.album.bookmarkedAt != null

                            ToggleButton(
                                checked = isBookmarked,
                                onCheckedChange = {
                                    database.query {
                                        update(albumWithSongs.album.toggleLike())
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        checkedContentColor = MaterialTheme.colorScheme.error,
                                    ),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (isBookmarked) R.drawable.favorite else R.drawable.favorite_border,
                                        ),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = {
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs),
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = stringResource(R.string.play),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = {
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled())),
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = stringResource(R.string.shuffle),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            ToggleButton(
                                checked = downloadState == HeaderDownloadState.Completed,
                                onCheckedChange = {
                                    val currentDownloadState = downloadState
                                    when (currentDownloadState) {
                                        HeaderDownloadState.Completed -> {
                                            sendRemoveDownloads(
                                                context = context,
                                                songIds = albumWithSongs.songs.map { it.id },
                                            )
                                        }

                                        is HeaderDownloadState.Partial -> {
                                            val songIds = albumWithSongs.songs.map { it.id }
                                            if (currentDownloadState.paused) {
                                                sendResumeDownloads(context, songIds)
                                            } else {
                                                sendPauseDownloads(context, songIds)
                                            }
                                        }

                                        HeaderDownloadState.None -> {
                                            downloadProgressToolbarDismissed = false
                                            sendAddMissingDownloads(
                                                context = context,
                                                songs =
                                                    albumWithSongs.songs.map {
                                                        HeaderDownloadItem(
                                                            id = it.id,
                                                            title = it.song.title,
                                                        )
                                                    },
                                                downloads = downloads,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                val state = downloadState
                                when (state) {
                                    HeaderDownloadState.Completed -> {
                                        Icon(
                                            painter = painterResource(R.drawable.offline),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }

                                    is HeaderDownloadState.Partial -> {
                                        HeaderDownloadProgressIndicator(
                                            progress = state.progress,
                                            paused = state.paused,
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = {
                                    menuState.show {
                                        AlbumMenu(
                                            originalAlbum =
                                                Album(
                                                    albumWithSongs.album,
                                                    albumWithSongs.artists,
                                                ),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Songs Section Header
                item(key = "songs_header") {
                    NavigationTitle(
                        title = stringResource(R.string.songs),
                    )
                }

                // Songs List
                itemsIndexed(
                    items = wrappedSongs,
                    key = { _, song -> song.item.id },
                ) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        albumIndex = index + 1,
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = songWrapper.item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        isSelected = songWrapper.isSelected && selection,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (songWrapper.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    LocalAlbumRadio(albumWithSongs, startIndex = index),
                                                )
                                            }
                                        } else {
                                            songWrapper.isSelected = !songWrapper.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                        }
                                        wrappedSongs.forEach { it.isSelected = false }
                                        songWrapper.isSelected = true
                                    },
                                ),
                    )
                }

                // Other Versions Section
                if (otherVersions.isNotEmpty()) {
                    item(key = "other_versions_header") {
                        NavigationTitle(
                            title = stringResource(R.string.other_versions),
                        )
                    }
                    item(key = "other_versions_list") {
                        LazyRow {
                            items(
                                items = otherVersions.distinctBy { it.id },
                                key = { it.id },
                            ) { item ->
                                YouTubeGridItem(
                                    item = item,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = scope,
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = { navController.navigate("album/${item.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
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
            } else {
                when (val state = uiState) {
                    AlbumUiState.Loading,
                    AlbumUiState.Content,
                    -> {
                        item(key = "shimmer") {
                            ShimmerHost {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = systemBarsTopPadding + AppBarHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(top = 8.dp, bottom = 20.dp)
                                                .size(240.dp)
                                                .shimmer()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.onSurface),
                                    )

                                    TextPlaceholder(
                                        height = 28.dp,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(0.6f)
                                                .padding(horizontal = 32.dp),
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextPlaceholder(
                                        height = 20.dp,
                                        modifier = Modifier.fillMaxWidth(0.4f),
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                    ) {
                                        repeat(3) {
                                            TextPlaceholder(
                                                height = 32.dp,
                                                modifier = Modifier.width(70.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(48.dp)
                                                    .shimmer()
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        ButtonPlaceholder(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                        )
                                        ButtonPlaceholder(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                        )
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(48.dp)
                                                    .shimmer()
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                repeat(6) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    AlbumUiState.Empty -> {
                        item(key = "empty") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight)
                                        .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_album),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_album_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    is AlbumUiState.Error -> {
                        item(key = "error") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight)
                                        .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text =
                                        if (state.isNotFound) {
                                            stringResource(
                                                R.string.album_not_found,
                                            )
                                        } else {
                                            stringResource(R.string.error_unknown)
                                        },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (state.isNotFound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (state.isNotFound) {
                                            stringResource(
                                                R.string.album_not_found_desc,
                                            )
                                        } else {
                                            stringResource(R.string.error_unknown)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top App Bar
        val topAppBarColors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                )
            }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            colors = topAppBarColors,
            scrollBehavior = scrollBehavior,
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = albumWithSongs?.album?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!selection) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selection) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        val currentAlbumWithSongs = albumWithSongs
        val currentDownloadState = downloadState
        val showDownloadProgressToolbar =
            currentAlbumWithSongs != null &&
                currentDownloadState is HeaderDownloadState.Partial &&
                !downloadProgressToolbarDismissed
        AnimatedVisibility(
            visible = showDownloadProgressToolbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 16.dp),
        ) {
            if (currentAlbumWithSongs != null && currentDownloadState is HeaderDownloadState.Partial) {
                val songIds =
                    remember(currentAlbumWithSongs) {
                        currentAlbumWithSongs.songs.map { it.id }
                    }
                DownloadProgressFloatingToolbar(
                    state =
                        DownloadProgressToolbarState(
                            progress = currentDownloadState.progress,
                            paused = currentDownloadState.paused,
                            canPause = hasActiveDownloads(songIds, downloads),
                        ),
                    onPauseResume = {
                        if (currentDownloadState.paused) {
                            sendResumeDownloads(context, songIds)
                        } else {
                            sendPauseDownloads(context, songIds)
                        }
                    },
                    onDismiss = {
                        downloadProgressToolbarDismissed = true
                    },
                )
            }
        }
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
