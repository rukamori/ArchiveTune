/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
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
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    var downloadProgressToolbarDismissed by remember { mutableStateOf(true) }

    var selection by remember { mutableStateOf(false) }
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { index, song -> index to song }
            } else {
                songs
                    .mapIndexed { index, song -> index to song }
                    .filter { (_, song) ->
                        song.title.contains(query.text, ignoreCase = true) ||
                            song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                    }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler { selection = false }
    }

    val wrappedSongs =
        remember(filteredSongs) { filteredSongs.map { item -> ItemWrapper(item) } }
            .toMutableStateList()

    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
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

    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Extract gradient colors from playlist cover
    LaunchedEffect(playlist?.thumbnail) {
        val thumbnailUrl = playlist?.thumbnail
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                    ).allowHardware(false)
                    .build()

            val result = runCatching { context.imageLoader.execute(request) }.getOrNull()

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
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf { !disableBlur && !selection && !showTopBarTitle }
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            if (!isLoading && current != null && !isSearching) 1 else 0
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songs.size >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songs.size - 5
            ) {
                viewModel.loadMoreSongs()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        // Mesh gradient background layer
        if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
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
                                // Primary color blob - top center
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c0.copy(
                                                        alpha = gradientAlpha * 0.75f,
                                                    ),
                                                    c0.copy(
                                                        alpha = gradientAlpha * 0.4f,
                                                    ),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.15f),
                                            radius = width * 0.8f,
                                        ),
                                )

                                // Secondary color blob - left side
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c1.copy(
                                                        alpha = gradientAlpha * 0.55f,
                                                    ),
                                                    c1.copy(
                                                        alpha = gradientAlpha * 0.3f,
                                                    ),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.1f, height * 0.4f),
                                            radius = width * 0.6f,
                                        ),
                                )

                                // Third color blob - right side
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c2.copy(
                                                        alpha = gradientAlpha * 0.5f,
                                                    ),
                                                    c2.copy(
                                                        alpha = gradientAlpha * 0.25f,
                                                    ),
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
                                                    c3.copy(
                                                        alpha = gradientAlpha * 0.35f,
                                                    ),
                                                    c3.copy(
                                                        alpha = gradientAlpha * 0.18f,
                                                    ),
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
                                                    c4.copy(
                                                        alpha = gradientAlpha * 0.3f,
                                                    ),
                                                    c4.copy(
                                                        alpha = gradientAlpha * 0.15f,
                                                    ),
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
                                                    gradientColors[0].copy(
                                                        alpha = gradientAlpha * 0.7f,
                                                    ),
                                                    gradientColors[0].copy(
                                                        alpha = gradientAlpha * 0.35f,
                                                    ),
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
            contentPadding =
                LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist.let { playlist ->
                if (isLoading) {
                    // Shimmer Loading State
                    item(key = "shimmer") {
                        ShimmerHost {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Playlist art placeholder
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(top = 8.dp, bottom = 20.dp)
                                            .size(240.dp)
                                            .shimmer()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.onSurface),
                                )

                                // Title placeholder
                                TextPlaceholder(
                                    height = 28.dp,
                                    modifier =
                                        Modifier.fillMaxWidth(0.6f).padding(horizontal = 32.dp),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Author placeholder
                                TextPlaceholder(
                                    height = 20.dp,
                                    modifier = Modifier.fillMaxWidth(0.4f),
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Metadata placeholder
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    repeat(2) {
                                        TextPlaceholder(
                                            height = 32.dp,
                                            modifier = Modifier.width(80.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Buttons placeholder
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface),
                                    )
                                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
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

                            repeat(6) { ListItemPlaceHolder() }
                        }
                    }
                } else if (playlist != null) {
                    if (!isSearching) {
                        // Hero Header
                        item(key = "header") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight)
                                        .animateItem(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Playlist Thumbnail - Large centered with shadow
                                Box(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
                                    Surface(
                                        modifier =
                                            Modifier
                                                .size(240.dp)
                                                .shadow(
                                                    elevation = 24.dp,
                                                    shape = RoundedCornerShape(16.dp),
                                                    spotColor =
                                                        gradientColors
                                                            .getOrNull(0)
                                                            ?.copy(alpha = 0.5f)
                                                            ?: MaterialTheme.colorScheme.primary
                                                                .copy(alpha = 0.3f),
                                                ),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        AsyncImage(
                                            model = playlist.thumbnail,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                // Playlist Title
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )

                                // Author (Clickable)
                                playlist.author?.let { artist ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text =
                                            buildAnnotatedString {
                                                withStyle(
                                                    style =
                                                        MaterialTheme.typography.titleMedium
                                                            .copy(
                                                                fontWeight = FontWeight.Normal,
                                                                color =
                                                                    MaterialTheme.colorScheme
                                                                        .primary,
                                                            ).toSpanStyle(),
                                                ) {
                                                    if (artist.id != null) {
                                                        val link =
                                                            LinkAnnotation.Clickable(artist.id!!) {
                                                                navController.navigate(
                                                                    "artist/${artist.id}",
                                                                )
                                                            }
                                                        withLink(link) { append(artist.name) }
                                                    } else {
                                                        append(artist.name)
                                                    }
                                                }
                                            },
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Metadata Row - Song Count
                                playlist.songCountText?.let { songCountText ->
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        MetadataChip(
                                            icon = R.drawable.music_note,
                                            text = songCountText,
                                        )
                                    }
                                }

                                // Description (when available)
                                playlist.description?.let { desc ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val hasLike = playlist.id != "LM"
                                    val hasPlay = playlist.playEndpoint != null
                                    val hasShuffle = playlist.shuffleEndpoint != null
                                    val hasRadio = playlist.radioEndpoint != null
                                    val activeIndices =
                                        remember(hasLike, hasPlay, hasShuffle, hasRadio) {
                                            listOf(hasLike, hasPlay, hasShuffle, hasRadio, true, true)
                                                .withIndex()
                                                .filter { it.value }
                                                .map { it.index }
                                        }

                                    @Composable
                                    fun shapeFor(slotIndex: Int) =
                                        when {
                                            activeIndices.first() == slotIndex && activeIndices.last() == slotIndex -> {
                                                ButtonGroupDefaults
                                                    .connectedLeadingButtonShapes()
                                            }

                                            activeIndices.first() == slotIndex -> {
                                                ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            }

                                            activeIndices.last() == slotIndex -> {
                                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            }

                                            else -> {
                                                ButtonGroupDefaults.connectedMiddleButtonShapes()
                                            }
                                        }

                                    if (hasLike) {
                                        val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
                                        ToggleButton(
                                            checked = isBookmarked,
                                            onCheckedChange = {
                                                if (dbPlaylist?.playlist == null) {
                                                    database.transaction {
                                                        val existingPlaylist = playlistEntityByBrowseId(playlist.id)
                                                        val targetPlaylistId =
                                                            if (existingPlaylist == null) {
                                                                val playlistEntity =
                                                                    PlaylistEntity(
                                                                        name = playlist.title,
                                                                        browseId = playlist.id,
                                                                        thumbnailUrl =
                                                                            playlist.thumbnail,
                                                                        isEditable =
                                                                            playlist.isEditable,
                                                                        playEndpointParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                        shuffleEndpointParams =
                                                                            playlist.shuffleEndpoint
                                                                                ?.params,
                                                                        radioEndpointParams =
                                                                            playlist.radioEndpoint
                                                                                ?.params,
                                                                    ).toggleLike()
                                                                insert(playlistEntity)
                                                                playlistEntityByBrowseId(playlist.id)?.id ?: playlistEntity.id
                                                            } else {
                                                                val refreshedPlaylist =
                                                                    existingPlaylist.copy(
                                                                        name = playlist.title,
                                                                        browseId = playlist.id,
                                                                        thumbnailUrl = playlist.thumbnail,
                                                                        isEditable = playlist.isEditable,
                                                                        playEndpointParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                        shuffleEndpointParams =
                                                                            playlist.shuffleEndpoint
                                                                                ?.params,
                                                                        radioEndpointParams =
                                                                            playlist.radioEndpoint
                                                                                ?.params,
                                                                    )
                                                                update(
                                                                    if (existingPlaylist.bookmarkedAt == null) {
                                                                        refreshedPlaylist.toggleLike()
                                                                    } else {
                                                                        refreshedPlaylist
                                                                    },
                                                                )
                                                                existingPlaylist.id
                                                            }
                                                        if (songs.isNotEmpty()) {
                                                            clearPlaylist(targetPlaylistId)
                                                            songs
                                                                .onEach { song -> insert(song.toMediaMetadata()) }
                                                                .mapIndexed { index, song ->
                                                                    PlaylistSongMap(
                                                                        songId = song.id,
                                                                        playlistId = targetPlaylistId,
                                                                        position = index,
                                                                        setVideoId = song.setVideoId,
                                                                    )
                                                                }.forEach(::insert)
                                                        }
                                                    }
                                                } else {
                                                    database.transaction {
                                                        val currentPlaylist = dbPlaylist!!.playlist
                                                        update(currentPlaylist, playlist)
                                                        update(currentPlaylist.toggleLike())
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            shapes = shapeFor(0),
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
                                                        if (isBookmarked) {
                                                            R.drawable.favorite
                                                        } else {
                                                            R.drawable.favorite_border
                                                        },
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.playEndpoint?.let { playEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.playlist(playEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(1),
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
                                                contentDescription =
                                                    stringResource(R.string.play),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.playlist(shuffleEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(2),
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
                                                contentDescription =
                                                    stringResource(R.string.shuffle),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.radioEndpoint?.let { radioEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(radioEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(3),
                                            colors =
                                                ToggleButtonDefaults.toggleButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = stringResource(R.string.radio),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    ToggleButton(
                                        checked = downloadState == HeaderDownloadState.Completed,
                                        onCheckedChange = {
                                            val currentDownloadState = downloadState
                                            when (currentDownloadState) {
                                                HeaderDownloadState.Completed -> {
                                                    sendRemoveDownloads(
                                                        context = context,
                                                        songIds = songs.map { it.id },
                                                    )
                                                }

                                                is HeaderDownloadState.Partial -> {
                                                    val songIds = songs.map { it.id }
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
                                                            songs.map { song ->
                                                                HeaderDownloadItem(
                                                                    id = song.id,
                                                                    title = song.title,
                                                                )
                                                            },
                                                        downloads = downloads,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(56.dp),
                                        shapes = shapeFor(4),
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

                                            HeaderDownloadState.None -> {
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
                                                YouTubePlaylistMenu(
                                                    playlist = playlist,
                                                    songs = songs,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                    selectAction = { selection = true },
                                                    canSelect = true,
                                                    snackbarHostState = snackbarHostState,
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(56.dp),
                                        shapes = shapeFor(5),
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

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val mixEndpoint = playlist.shuffleEndpoint ?: playlist.radioEndpoint
                                    if (mixEndpoint != null) {
                                        Button(
                                            onClick = {
                                                playerConnection.playQueue(
                                                    if (mixEndpoint == playlist.shuffleEndpoint) {
                                                        YouTubeQueue.playlist(mixEndpoint)
                                                    } else {
                                                        YouTubeQueue(mixEndpoint)
                                                    },
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.mix),
                                                contentDescription = stringResource(R.string.start_radio),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    if (songs.isEmpty() && !isLoading && error == null) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_playlist),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_playlist_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Songs List
                    items(
                        items = wrappedSongs,
                        key = { it.item.second.setVideoId ?: "${it.item.second.id}-${it.item.first}" },
                        contentType = { "online_playlist_song" },
                    ) { song ->
                        YouTubeListItem(
                            item = song.item.second,
                            viewCountText =
                                viewCounts[song.item.second.id]?.let { count ->
                                    formatCompactCount(count.toLong())
                                },
                            isActive = mediaMetadata?.id == song.item.second.id,
                            isPlaying = isPlaying,
                            isSelected = song.isSelected && selection,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song.item.second,
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
                                        enabled = !hideExplicit || !song.item.second.explicit,
                                        onClick = {
                                            if (!selection) {
                                                if (song.item.second.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.playlist(
                                                            endpoint =
                                                                song.item.second
                                                                    .toPlaylistPlaybackEndpoint(
                                                                        playlistId = playlist.id,
                                                                        playlistPlayParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                    ),
                                                            preloadItem = song.item.second.toMediaMetadata(),
                                                        ),
                                                    )
                                                }
                                            } else {
                                                song.isSelected = !song.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            song.isSelected = true
                                        },
                                    ).animateItem(),
                        )
                    }

                    if (viewModel.continuation != null && songs.isNotEmpty() && isLoadingMore) {
                        item(key = "loading_more") {
                            ShimmerHost { repeat(2) { ListItemPlaceHolder() } }
                        }
                    }
                } else {
                    val isPrivatePlaylist = error?.contains("PLAYLIST_PRIVATE") == true
                    item(key = "error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isPrivatePlaylist) {
                                Image(
                                    painter = painterResource(R.drawable.anime_blank),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    text =
                                        if (error != null) {
                                            stringResource(R.string.error_unknown)
                                        } else {
                                            stringResource(R.string.playlist_not_found)
                                        },
                                    style = MaterialTheme.typography.titleLarge,
                                    color =
                                        if (error != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (error != null) {
                                            error!!
                                        } else {
                                            stringResource(R.string.playlist_not_found_desc)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (error != null) {
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
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

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
            colors = topAppBarColors,
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.title.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) {
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
                                    if (count == wrappedSongs.size) {
                                        R.drawable.deselect
                                    } else {
                                        R.drawable.select_all
                                    },
                                ),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map {
                                                it.item.second
                                                    .toMediaItem()
                                                    .metadata!!
                                            },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    currentItems = emptyList(),
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
                } else if (!isSearching) {
                    IconButton(onClick = { isSearching = true }, onLongClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        val currentDownloadState = downloadState
        val showDownloadProgressToolbar =
            currentDownloadState is HeaderDownloadState.Partial &&
                songs.isNotEmpty() &&
                !downloadProgressToolbarDismissed
        AnimatedVisibility(
            visible = showDownloadProgressToolbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 16.dp),
        ) {
            if (currentDownloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
                val songIds = remember(songs) { songs.map { it.id } }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime),
                    ).align(Alignment.BottomCenter),
        )
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

private fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): WatchEndpoint {
    val baseEndpoint = endpoint ?: WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}
