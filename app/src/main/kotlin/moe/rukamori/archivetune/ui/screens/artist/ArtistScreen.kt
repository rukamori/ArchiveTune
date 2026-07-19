/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.flow.collect
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ARTIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_PLAYLIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AlbumGridItem
import moe.rukamori.archivetune.ui.component.HideOnScrollFAB
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailIconAction
import moe.rukamori.archivetune.ui.component.MediaDetailPrimaryActions
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ArtistAction
import moe.rukamori.archivetune.viewmodels.ArtistBlockState
import moe.rukamori.archivetune.viewmodels.ArtistEvent
import moe.rukamori.archivetune.viewmodels.ArtistViewModel
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val loadedArtistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val loadedLibrarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val loadedLibraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val blockState by viewModel.blockState.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val isArtistBlocked = (blockState as? ArtistBlockState.Success)?.isBlocked == true
    val artistPage =
        remember(loadedArtistPage, isArtistBlocked) {
            if (isArtistBlocked) {
                loadedArtistPage?.copy(
                    artist =
                        loadedArtistPage.artist.copy(
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        ),
                    sections = emptyList(),
                )
            } else {
                loadedArtistPage
            }
        }
    val librarySongs = remember(loadedLibrarySongs, isArtistBlocked) { loadedLibrarySongs.takeUnless { isArtistBlocked }.orEmpty() }
    val libraryAlbums = remember(loadedLibraryAlbums, isArtistBlocked) { loadedLibraryAlbums.takeUnless { isArtistBlocked }.orEmpty() }

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArtistEvent.Share -> {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.link)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }

                is ArtistEvent.CopyLink -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_link), event.link))
                    Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                }

                is ArtistEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val heroContentColor =
        if (surfaceColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    LaunchedEffect(libraryArtist) {
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    val latestRelease =
        remember(showLocal, artistPage, libraryAlbums) {
            if (showLocal) {
                libraryAlbums
                    .maxByOrNull { it.album.year ?: Int.MIN_VALUE }
                    ?.let { album ->
                        ArtistReleaseUiModel(
                            id = album.id,
                            title = album.album.title,
                            thumbnailUrl = album.album.thumbnailUrl,
                            year = album.album.year,
                            releaseType = AlbumReleaseType.ALBUM,
                        )
                    }
            } else {
                artistPage
                    ?.sections
                    .orEmpty()
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .filterIsInstance<AlbumItem>()
                    .maxByOrNull { it.year ?: Int.MIN_VALUE }
                    ?.toArtistReleaseUiModel()
            }
        }
    val orderedRemoteSections =
        remember(artistPage?.sections) {
            val sections = artistPage?.sections.orEmpty()
            val topSongsSection =
                sections.firstOrNull { section ->
                    section.layout == ArtistSectionLayout.LIST && section.items.all { it is SongItem }
                }
            if (topSongsSection == null) {
                sections
            } else {
                listOf(topSongsSection) + sections.filterNot { it === topSongsSection }
            }
        }
    val showArtistOverflowMenu: () -> Unit = {
        menuState.show {
            ArtistOverflowMenu(
                isBlocked = isArtistBlocked,
                blockActionEnabled =
                    blockState !is ArtistBlockState.Loading &&
                        (
                            artistPage
                                ?.artist
                                ?.title
                                .orEmpty()
                                .isNotBlank() ||
                                libraryArtist
                                    ?.artist
                                    ?.name
                                    .orEmpty()
                                    .isNotBlank()
                        ),
                onAction = { action ->
                    viewModel.onAction(action)
                    menuState.dismiss()
                },
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ArtistHeroMinHeight)
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = ArtistHorizontalPadding, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TextPlaceholder(height = 36.dp, modifier = Modifier.fillMaxWidth(0.55f))
                                Spacer(modifier = Modifier.height(12.dp))
                                TextPlaceholder(height = 16.dp, modifier = Modifier.fillMaxWidth(0.72f))
                                Spacer(modifier = Modifier.height(20.dp))
                                TextPlaceholder(height = 14.dp, modifier = Modifier.fillMaxWidth(0.82f))
                                Spacer(modifier = Modifier.height(12.dp))
                                ButtonPlaceholder(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(ButtonDefaults.MediumContainerHeight),
                                )
                            }
                        }

                        repeat(5) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name
                    val unknownArtist = stringResource(R.string.unknown_artist)
                    val songsLabel = stringResource(R.string.songs)
                    val albumsLabel = stringResource(R.string.albums)
                    val monthlyListenersLabel = stringResource(R.string.monthly_listeners)
                    val subscribersLabel = stringResource(R.string.subscribers)
                    val artistStats =
                        remember(
                            showLocal,
                            artistPage,
                            librarySongs.size,
                            libraryAlbums.size,
                            songsLabel,
                            albumsLabel,
                            monthlyListenersLabel,
                            subscribersLabel,
                        ) {
                            buildArtistStats(
                                showLocal = showLocal,
                                artistPage = artistPage,
                                librarySongCount = librarySongs.size,
                                libraryAlbumCount = libraryAlbums.size,
                                songsLabel = songsLabel,
                                albumsLabel = albumsLabel,
                                monthlyListenersLabel = monthlyListenersLabel,
                                subscribersLabel = subscribersLabel,
                            )
                        }
                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ArtistHeroMinHeight)
                                .background(surfaceColor),
                    ) {
                        if (thumbnail != null) {
                            AsyncImage(
                                model =
                                    thumbnail.resize(
                                        width = ArtistHeroArtworkSizePx,
                                        height = ArtistHeroArtworkSizePx,
                                        sizeBuckets = ArtistHeroArtworkSizeBuckets,
                                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                    ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.person),
                                    contentDescription = null,
                                    modifier = Modifier.size(96.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = 0.42f),
                                            0.18f to Color.Transparent,
                                            0.42f to Color.Transparent,
                                            0.72f to surfaceColor.copy(alpha = 0.78f),
                                            1f to surfaceColor,
                                        ),
                                    ),
                        )

                        Column(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(
                                        start = ArtistHorizontalPadding,
                                        top = systemBarsTopPadding + AppBarHeight + 96.dp,
                                        end = ArtistHorizontalPadding,
                                        bottom = 24.dp,
                                    ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = artistName ?: unknownArtist,
                                style = MaterialTheme.typography.headlineLarge,
                                color = heroContentColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (artistStats.audience.isNotEmpty()) {
                                Text(
                                    text = artistStats.audience,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = heroContentColor.copy(alpha = 0.62f),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                )
                            }

                            if (artistStats.catalog.isNotEmpty()) {
                                Text(
                                    text = artistStats.catalog,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = heroContentColor.copy(alpha = 0.62f),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top =
                                                    if (artistStats.audience.isEmpty()) {
                                                        16.dp
                                                    } else {
                                                        4.dp
                                                    },
                                            ),
                                )
                            }

                            ArtistPrimaryActions(
                                isSubscribed = isSubscribed,
                                contentColor = heroContentColor,
                                contrastingColor = surfaceColor,
                                canShuffle =
                                    if (showLocal) {
                                        librarySongs.isNotEmpty()
                                    } else {
                                        artistPage?.artist?.shuffleEndpoint != null
                                    },
                                canPlay =
                                    if (showLocal) {
                                        librarySongs.isNotEmpty()
                                    } else {
                                        artistPage?.artist?.playEndpoint != null
                                    },
                                onShuffle = {
                                    if (showLocal) {
                                        if (librarySongs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artistName ?: unknownArtist,
                                                    items = librarySongs.shuffled().map { it.toMediaItem() },
                                                ),
                                            )
                                        }
                                    } else {
                                        artistPage?.artist?.shuffleEndpoint?.let { endpoint ->
                                            playerConnection.playQueue(YouTubeQueue(endpoint))
                                        }
                                    }
                                },
                                onPlay = {
                                    if (showLocal) {
                                        if (librarySongs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artistName ?: unknownArtist,
                                                    items = librarySongs.map { it.toMediaItem() },
                                                ),
                                            )
                                        }
                                    } else {
                                        artistPage?.artist?.playEndpoint?.let { endpoint ->
                                            playerConnection.playQueue(YouTubeQueue(endpoint))
                                        }
                                    }
                                },
                                onToggleSubscription = {
                                    database.transaction {
                                        val artist = libraryArtist?.artist
                                        if (artist != null) {
                                            update(artist.toggleLike())
                                        } else {
                                            artistPage?.artist?.let { remoteArtist ->
                                                insert(
                                                    ArtistEntity(
                                                        id = remoteArtist.id,
                                                        name = remoteArtist.title,
                                                        channelId = remoteArtist.channelId,
                                                        thumbnailUrl = remoteArtist.thumbnail,
                                                    ).toggleLike(),
                                                )
                                            }
                                        }
                                    }
                                },
                                onRadio =
                                    if (showLocal) {
                                        null
                                    } else {
                                        artistPage?.artist?.radioEndpoint?.let { endpoint ->
                                            {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(endpoint),
                                                )
                                            }
                                        }
                                    },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }

                artistPage
                    ?.description
                    ?.takeIf(String::isNotBlank)
                    ?.let { description ->
                        item(
                            key = "artist_description",
                            contentType = CONTENT_TYPE_HEADER,
                        ) {
                            var isExpanded by rememberSaveable(description) { mutableStateOf(false) }
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = ArtistContentMaxWidth)
                                        .padding(horizontal = ArtistHorizontalPadding, vertical = 12.dp)
                                        .combinedClickable(
                                            onClick = { isExpanded = !isExpanded },
                                            onLongClick = {},
                                        ),
                            )
                        }
                    }

                latestRelease?.let { release ->
                    item(
                        key = "new_release_${release.id}",
                        contentType = CONTENT_TYPE_ALBUM,
                    ) {
                        ArtistNewReleaseSection(
                            release = release,
                            onClick = { navController.navigate("album/${release.id}") },
                        )
                    }
                }

                // Content sections
                if (showLocal) {
                    // Local Songs Section
                    if (librarySongs.isNotEmpty()) {
                        item {
                            NavigationTitle(
                                title = stringResource(R.string.songs),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                            )
                        }

                        val filteredLibrarySongs =
                            if (hideExplicit) {
                                librarySongs.filter { !it.song.explicit }
                            } else {
                                librarySongs
                            }

                        itemsIndexed(
                            items = filteredLibrarySongs.take(5),
                            key = { _, item -> "local_song_${item.id}" },
                            contentType = { _, _ -> CONTENT_TYPE_SONG },
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
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
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                            items = librarySongs.map { it.toMediaItem() },
                                                            startIndex = index,
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }

                        // Show "View All" if more songs available
                        if (filteredLibrarySongs.size > 5) {
                            item {
                                Surface(
                                    onClick = {
                                        navController.navigate("artist/${viewModel.artistId}/songs")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.view_all),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    // Local Albums Section
                    if (libraryAlbums.isNotEmpty()) {
                        item {
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                },
                            )
                        }

                        item {
                            val filteredLibraryAlbums =
                                if (hideExplicit) {
                                    libraryAlbums.filter { !it.album.explicit }
                                } else {
                                    libraryAlbums
                                }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { album -> "local_album_${album.id}" },
                                    contentType = { CONTENT_TYPE_ALBUM },
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        navController.navigate("album/${album.id}")
                                                    },
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
                } else {
                    // YouTube/Remote content sections
                    orderedRemoteSections.fastForEach { section ->
                        if (section.items.isNotEmpty()) {
                            item(
                                key = "youtube_section_header_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                                contentType = CONTENT_TYPE_HEADER,
                            ) {
                                NavigationTitle(
                                    title = section.title,
                                    onClick =
                                        section.moreEndpoint?.let {
                                            {
                                                navController.navigate(buildArtistItemsRoute(viewModel.artistId, it))
                                            }
                                        },
                                )
                            }
                        }

                        if (section.layout == ArtistSectionLayout.LIST && section.items.all { it is SongItem }) {
                            items(
                                items = section.items.distinctBy { it.id },
                                key = { "youtube_song_${it.id}" },
                                contentType = { CONTENT_TYPE_SONG },
                            ) { song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
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
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        } else {
                            item(
                                key = "youtube_section_grid_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                                contentType = CONTENT_TYPE_LIST,
                            ) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(
                                        items = section.items.distinctBy { it.id },
                                        key = {
                                            val type =
                                                when (it) {
                                                    is SongItem -> "song"
                                                    is AlbumItem -> "album"
                                                    is ArtistItem -> "artist"
                                                    is PlaylistItem -> "playlist"
                                                    else -> "item"
                                                }
                                            "youtube_${type}_${it.id}"
                                        },
                                        contentType = {
                                            when (it) {
                                                is SongItem -> CONTENT_TYPE_SONG
                                                is AlbumItem -> CONTENT_TYPE_ALBUM
                                                is ArtistItem -> CONTENT_TYPE_ARTIST
                                                is PlaylistItem -> CONTENT_TYPE_PLAYLIST
                                                else -> CONTENT_TYPE_LIST
                                            }
                                        },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive =
                                                when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            modifier =
                                                Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is SongItem -> {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue(
                                                                            WatchEndpoint(videoId = item.id),
                                                                            item.toMediaMetadata(),
                                                                        ),
                                                                    )
                                                                }

                                                                is AlbumItem -> {
                                                                    navController.navigate("album/${item.id}")
                                                                }

                                                                is ArtistItem -> {
                                                                    navController.navigate("artist/${item.id}")
                                                                }

                                                                is PlaylistItem -> {
                                                                    navController.navigate("online_playlist/${item.id}")
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is AlbumItem -> {
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is ArtistItem -> {
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PlaylistItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    ).animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // FAB for switching between local/remote view
        HideOnScrollFAB(
            visible = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            label = if (showLocal) stringResource(R.string.together_online) else stringResource(R.string.filter_library),
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            },
        )

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }

    // Top App Bar
    TopAppBar(
        title = {
            val animatedAlpha by animateFloatAsState(
                targetValue = if (!transparentAppBar) 1f else 0f,
                animationSpec = tween(200),
                label = "titleAlpha",
            )
            Text(
                text = artistPage?.artist?.title ?: libraryArtist?.artist?.name ?: "",
                modifier = Modifier.alpha(animatedAlpha),
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
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(
                onClick = showArtistOverflowMenu,
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        },
        colors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
    )
}

@Composable
private fun ArtistOverflowMenu(
    isBlocked: Boolean,
    blockActionEnabled: Boolean,
    onAction: (ArtistAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ArtistOverflowMenuItem(
            text = stringResource(R.string.share),
            iconRes = R.drawable.share,
            index = 0,
            count = ArtistOverflowMenuItemCount,
            onClick = { onAction(ArtistAction.Share) },
        )
        ArtistOverflowMenuItem(
            text = stringResource(R.string.copy_link),
            iconRes = R.drawable.copy,
            index = 1,
            count = ArtistOverflowMenuItemCount,
            onClick = { onAction(ArtistAction.CopyLink) },
        )
        ArtistOverflowMenuItem(
            text = stringResource(if (isBlocked) R.string.unblock_artist else R.string.block_artist),
            iconRes = R.drawable.block,
            index = 2,
            count = ArtistOverflowMenuItemCount,
            enabled = blockActionEnabled,
            onClick = { onAction(ArtistAction.ToggleBlock) },
        )
    }
}

@Composable
private fun ArtistOverflowMenuItem(
    text: String,
    iconRes: Int,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
    ) {
        Text(text = text)
    }
}

private const val ArtistOverflowMenuItemCount = 3
private const val ArtistHeroArtworkSizePx = 1200
private const val ArtistReleaseArtworkSizePx = 320
private val ArtistHeroArtworkSizeBuckets = listOf(ArtistHeroArtworkSizePx)
private val ArtistHeroMinHeight = 560.dp
private val ArtistHorizontalPadding = 24.dp
private val ArtistContentMaxWidth = 720.dp
private val ArtistReleaseArtworkSize = 112.dp
private const val ArtistStatSeparator = "  •  "

@Immutable
private data class ArtistStatsUi(
    val audience: String,
    val catalog: String,
)

@Immutable
private data class ArtistReleaseUiModel(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val year: Int?,
    val releaseType: AlbumReleaseType,
)

@Composable
private fun ArtistPrimaryActions(
    isSubscribed: Boolean,
    contentColor: Color,
    contrastingColor: Color,
    canShuffle: Boolean,
    canPlay: Boolean,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onToggleSubscription: () -> Unit,
    onRadio: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MediaDetailPrimaryActions(
        isAdded = isSubscribed,
        contentColor = contentColor,
        contrastingColor = contrastingColor,
        addContentDescription = R.string.subscribe,
        removeContentDescription = R.string.subscribed,
        onShuffle = if (canShuffle) onShuffle else null,
        onPlay = if (canPlay) onPlay else null,
        onToggleAdd = onToggleSubscription,
        additionalActions = { actionColor ->
            onRadio?.let { radio ->
                MediaDetailIconAction(
                    icon = R.drawable.radio,
                    contentDescription = R.string.start_radio,
                    contentColor = actionColor,
                    onClick = radio,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ArtistNewReleaseSection(
    release: ArtistReleaseUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val releaseTypeLabel =
        stringResource(
            when (release.releaseType) {
                AlbumReleaseType.ALBUM -> R.string.release_type_album
                AlbumReleaseType.SINGLE -> R.string.release_type_single
                AlbumReleaseType.EP -> R.string.ep
            },
        )
    val metadata =
        release.year?.let { year ->
            stringResource(R.string.release_metadata, releaseTypeLabel, year)
        } ?: releaseTypeLabel

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = ArtistContentMaxWidth),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (release.thumbnailUrl != null) {
                    AsyncImage(
                        model =
                            release.thumbnailUrl.resize(
                                width = ArtistReleaseArtworkSizePx,
                                height = ArtistReleaseArtworkSizePx,
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(ArtistReleaseArtworkSize)
                                .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(ArtistReleaseArtworkSize)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.latest_release).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun buildArtistStats(
    showLocal: Boolean,
    artistPage: ArtistPage?,
    librarySongCount: Int,
    libraryAlbumCount: Int,
    songsLabel: String,
    albumsLabel: String,
    monthlyListenersLabel: String,
    subscribersLabel: String,
): ArtistStatsUi {
    val songSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is SongItem }
        }
    val songCount =
        if (showLocal) {
            librarySongCount
        } else {
            songSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<SongItem>()
                ?.distinctBy { it.id }
                ?.count() ?: librarySongCount
        }
    val hasMoreSongs = !showLocal && songSections?.any { it.moreEndpoint != null } == true

    val albumSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is AlbumItem }
        }
    val albumCount =
        if (showLocal) {
            libraryAlbumCount
        } else {
            albumSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<AlbumItem>()
                ?.distinctBy { it.id }
                ?.count() ?: libraryAlbumCount
        }
    val hasMoreAlbums = !showLocal && albumSections?.any { it.moreEndpoint != null } == true

    val audience =
        buildList {
            artistPage?.artist?.monthlyListenerCountText?.toArtistCompactCountText()?.let { value ->
                add("$value $monthlyListenersLabel")
            }

            artistPage?.artist?.subscriberCountText?.toArtistCompactCountText()?.let { value ->
                add("$value $subscribersLabel")
            }
        }.joinToString(ArtistStatSeparator)
    val catalog =
        buildList {
            if (songCount > 0) {
                val value = compactCountText(songCount, hasMoreSongs)
                add("$value $songsLabel")
            }

            if (albumCount > 0) {
                val value = compactCountText(albumCount, hasMoreAlbums)
                add("$value $albumsLabel")
            }
        }.joinToString(ArtistStatSeparator)

    return ArtistStatsUi(
        audience = audience,
        catalog = catalog,
    )
}

private fun AlbumItem.toArtistReleaseUiModel() =
    ArtistReleaseUiModel(
        id = id,
        title = title,
        thumbnailUrl = thumbnail,
        year = year,
        releaseType = releaseType,
    )

private fun compactCountText(
    count: Int,
    hasMore: Boolean,
): String {
    val value = formatCompactCount(count.toLong())
    return if (hasMore) "$value+" else value
}

private val CompactArtistCountPattern = Regex("""\d+(?:[.,]\d+)?\s*[KMB]""", RegexOption.IGNORE_CASE)
private val ArtistCountPattern = Regex("""\d+(?:[.,]\d+)*""")

private fun String.toArtistCompactCountText(): String? {
    val compactText = CompactArtistCountPattern.find(this)?.value
    if (compactText != null) {
        return compactText
            .filterNot { it.isWhitespace() }
            .replace(',', '.')
            .uppercase(Locale.US)
    }

    val count =
        ArtistCountPattern
            .find(this)
            ?.value
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
            ?: return null

    return formatCompactCount(count)
}

private fun buildArtistItemsRoute(
    artistId: String,
    endpoint: BrowseEndpoint,
): String {
    val encodedArtistId = Uri.encode(artistId)
    val encodedBrowseId = Uri.encode(endpoint.browseId)
    val encodedParams =
        endpoint.params
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.encode(it) }

    return buildString {
        append("artist/")
        append(encodedArtistId)
        append("/items?browseId=")
        append(encodedBrowseId)
        if (encodedParams != null) {
            append("&params=")
            append(encodedParams)
        }
    }
}
