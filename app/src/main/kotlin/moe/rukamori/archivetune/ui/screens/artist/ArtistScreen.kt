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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
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
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
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
    val density = LocalDensity.current

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

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    // Gradient colors for mesh background
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Get thumbnail URL
    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

    // Extract gradient colors from artist image
    LaunchedEffect(thumbnail) {
        if (thumbnail != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnail)
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

    // Calculate gradient opacity based on scroll position
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 800f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    LaunchedEffect(libraryArtist) {
        showLocal = libraryArtist?.artist?.isLocal == true
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
                        .fillMaxSize(0.65f)
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
                                                    c0.copy(alpha = gradientAlpha * 0.72f),
                                                    c0.copy(alpha = gradientAlpha * 0.4f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.2f),
                                            radius = width * 0.7f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c1.copy(alpha = gradientAlpha * 0.56f),
                                                    c1.copy(alpha = gradientAlpha * 0.3f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.15f, height * 0.35f),
                                            radius = width * 0.6f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c2.copy(alpha = gradientAlpha * 0.52f),
                                                    c2.copy(alpha = gradientAlpha * 0.26f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.85f, height * 0.45f),
                                            radius = width * 0.65f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c3.copy(alpha = gradientAlpha * 0.34f),
                                                    c3.copy(alpha = gradientAlpha * 0.18f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.35f, height * 0.6f),
                                            radius = width * 0.8f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c4.copy(alpha = gradientAlpha * 0.28f),
                                                    c4.copy(alpha = gradientAlpha * 0.14f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.55f, height * 0.85f),
                                            radius = width * 0.95f,
                                        ),
                                )
                            } else if (gradientColors.isNotEmpty()) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.6f),
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.3f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.3f),
                                            radius = width * 0.8f,
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
            if (artistPage == null && !showLocal) {
                // Shimmer loading state
                item(key = "shimmer") {
                    ShimmerHost {
                        // Hero section placeholder
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight),
                        ) {
                            // Artist image placeholder - circular
                            Box(
                                modifier =
                                    Modifier
                                        .padding(top = 8.dp)
                                        .size(210.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .shimmer()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface),
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Artist name placeholder
                            TextPlaceholder(
                                height = 32.dp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.5f)
                                        .align(Alignment.CenterHorizontally),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stats placeholder
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                repeat(3) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        TextPlaceholder(
                                            height = 20.dp,
                                            modifier = Modifier.width(40.dp),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextPlaceholder(
                                            height = 14.dp,
                                            modifier = Modifier.width(50.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Buttons placeholder
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            ) {
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
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        // Songs list placeholder
                        repeat(5) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                // Hero Header
                item(key = "header") {
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = systemBarsTopPadding + AppBarHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Artist Image - Circular with shadow
                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 8.dp, bottom = 16.dp),
                        ) {
                            if (thumbnail != null) {
                                AsyncImage(
                                    model = thumbnail.resize(600, 600),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(210.dp)
                                            .clip(CircleShape),
                                )
                            } else {
                                // Placeholder when no image
                                Box(
                                    modifier =
                                        Modifier
                                            .size(200.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Artist Name
                        Text(
                            text = artistName ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )

                        // Artist Description (expandable)
                        val description = artistPage?.description
                        if (!description.isNullOrBlank()) {
                            var isExpanded by rememberSaveable { mutableStateOf(false) }
                            val maxLines = if (isExpanded) Int.MAX_VALUE else 4

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .combinedClickable(
                                            onClick = { isExpanded = !isExpanded },
                                            onLongClick = {},
                                        ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = maxLines,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (!isExpanded) {
                                    Text(
                                        text = stringResource(R.string.more),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

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

                        if (artistStats.isNotEmpty()) {
                            ArtistStatsButtonGroup(stats = artistStats)
                        }

                        // Action Buttons
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null

                            ToggleButton(
                                checked = isSubscribed,
                                onCheckedChange = {
                                    database.transaction {
                                        val artist = libraryArtist?.artist
                                        if (artist != null) {
                                            update(artist.toggleLike())
                                        } else {
                                            artistPage?.artist?.let {
                                                insert(
                                                    ArtistEntity(
                                                        id = it.id,
                                                        name = it.title,
                                                        channelId = it.channelId,
                                                        thumbnailUrl = it.thumbnail,
                                                    ).toggleLike(),
                                                )
                                            }
                                        }
                                    }
                                },
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shapes =
                                    if (!showLocal && artistPage?.artist?.radioEndpoint != null) {
                                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    } else {
                                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    },
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (isSubscribed) R.drawable.done else R.drawable.add,
                                        ),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text =
                                        stringResource(
                                            if (isSubscribed) R.string.subscribed else R.string.subscribe,
                                        ),
                                    maxLines = 1,
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = {
                                    if (!showLocal) {
                                        artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                            playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        }
                                    } else if (librarySongs.isNotEmpty()) {
                                        val shuffledSongs = librarySongs.shuffled()
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                items = shuffledSongs.map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                enabled = if (showLocal) librarySongs.isNotEmpty() else artistPage?.artist?.shuffleEndpoint != null,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shapes =
                                    if (!showLocal && artistPage?.artist?.radioEndpoint != null) {
                                        ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    } else {
                                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    },
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
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.shuffle),
                                    maxLines = 1,
                                )
                            }

                            if (!showLocal) {
                                artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                    ToggleButton(
                                        checked = false,
                                        onCheckedChange = {
                                            playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .height(48.dp),
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
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.radio))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
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
                    artistPage?.sections?.fastForEach { section ->
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
                onClick = {
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
                },
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        },
        colors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            } else {
                TopAppBarDefaults.topAppBarColors()
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

@Immutable
private data class ArtistStatItemUiModel(
    val icon: ImageVector,
    val value: String,
    val contentDescription: String,
)

@Composable
private fun ArtistStatsButtonGroup(
    stats: List<ArtistStatItemUiModel>,
    modifier: Modifier = Modifier,
) {
    val buttonShapes = ButtonDefaults.shapes()

    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.fastForEach { stat ->
            FilledTonalButton(
                onClick = NoOpStatButtonClick,
                shapes = buttonShapes,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 72.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = stat.contentDescription
                        },
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
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
): List<ArtistStatItemUiModel> {
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

    return buildList {
        if (songCount > 0) {
            val value = compactCountText(songCount, hasMoreSongs)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.MusicNote,
                    value = value,
                    contentDescription = "$songsLabel $value",
                ),
            )
        }

        if (albumCount > 0) {
            val value = compactCountText(albumCount, hasMoreAlbums)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Album,
                    value = value,
                    contentDescription = "$albumsLabel $value",
                ),
            )
        }

        artistPage?.artist?.monthlyListenerCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Hearing,
                    value = value,
                    contentDescription = "$monthlyListenersLabel $value",
                ),
            )
        }

        artistPage?.artist?.subscriberCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.PersonAdd,
                    value = value,
                    contentDescription = "$subscribersLabel $value",
                ),
            )
        }
    }
}

private fun compactCountText(
    count: Int,
    hasMore: Boolean,
): String {
    val value = formatCompactCount(count.toLong())
    return if (hasMore) "$value+" else value
}

private val CompactArtistCountPattern = Regex("""\d+(?:[.,]\d+)?\s*[KMB]""", RegexOption.IGNORE_CASE)
private val ArtistCountPattern = Regex("""\d+(?:[.,]\d+)*""")
private val NoOpStatButtonClick: () -> Unit = {}

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
