/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.repository.moodAndGenresArtworkKey
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.viewmodels.MoodAndGenresArtworkState
import moe.rukamori.archivetune.viewmodels.MoodAndGenresViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val moodAndGenres by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val topPadding = with(density) { windowInsets.getTop(this).toDp() }
    val bottomPadding = with(density) { windowInsets.getBottom(this).toDp() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            gridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(gridState, moodAndGenres, lifecycleOwner, viewModel) {
        val itemsByKey = moodAndGenres.orEmpty().associateBy {
            "${it.title}:${it.endpoint.browseId}:${it.endpoint.params}"
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.key } }
                    .distinctUntilChanged()
                    .collect { keys ->
                        viewModel.setVisibleArtwork(keys.mapNotNull { itemsByKey[it]?.endpoint })
                    }
            } finally {
                viewModel.setVisibleArtwork(emptyList())
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        state = gridState,
        contentPadding =
            PaddingValues(
                start = 6.dp,
                top = topPadding,
                end = 6.dp,
                bottom = bottomPadding,
            ),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            NavigationTitle(
                title = stringResource(R.string.mood_and_genres),
                modifier = Modifier.animateItem(),
            )
        }

        if (moodAndGenres == null) {
            items(
                count = 12,
                key = { index -> "mood_genres_shimmer_$index" },
                contentType = { "mood_genres_shimmer" },
            ) {
                ShimmerHost {
                    TextPlaceholder(
                        height = MoodAndGenresButtonHeight,
                        shape = MoodAndGenresButtonShape,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        } else {
            items(
                items = moodAndGenres.orEmpty(),
                key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                contentType = { "mood_genres_item" },
            ) { item ->
                MoodAndGenresButton(
                    title = item.title,
                    stripeColor = item.stripeColor,
                    endpoint = item.endpoint,
                    artwork = artwork[item.endpoint.moodAndGenresArtworkKey()],
                    onClick = {
                        navController.navigate(
                            buildString {
                                append("youtube_browse/${android.net.Uri.encode(item.endpoint.browseId)}")
                                append("?genreTitle=${android.net.Uri.encode(item.title)}")
                                item.endpoint.params?.let { append("&params=${android.net.Uri.encode(it)}") }
                            },
                        ) {
                            launchSingleTop = true
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                            .animateItem(),
                )
            }
        }
    }
}

@Composable
fun MoodAndGenresButton(
    title: String,
    stripeColor: Long,
    endpoint: BrowseEndpoint? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: MoodAndGenresArtworkState? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val base = remember(stripeColor) { Color(stripeColor) }
    val artworkUrl = (artwork as? MoodAndGenresArtworkState.Available)?.url
    val artworkModel = rememberMoodAndGenresArtworkModel(endpoint = endpoint, artworkUrl = artworkUrl)
    val cardStart =
        remember(base, colorScheme.primaryContainer) {
            lerp(base, colorScheme.primaryContainer, 0.18f)
        }
    val cardEnd =
        remember(base, colorScheme.surfaceContainerHighest) {
            lerp(base, colorScheme.surfaceContainerHighest, 0.34f)
        }
    val coverStart =
        remember(base, colorScheme.surface) {
            lerp(base, colorScheme.surface, 0.28f)
        }
    val coverEnd =
        remember(base, colorScheme.scrim) {
            lerp(base, colorScheme.scrim, 0.2f)
        }
    val cardBrush =
        remember(cardStart, cardEnd) {
            Brush.linearGradient(
                colors = listOf(cardStart, cardEnd),
                start = Offset.Zero,
                end = Offset(900f, 650f),
            )
        }
    val coverBrush =
        remember(coverStart, coverEnd) {
            Brush.linearGradient(
                colors = listOf(coverStart, coverEnd),
                start = Offset.Zero,
                end = Offset(360f, 360f),
            )
        }
    val textScrimBrush =
        remember(colorScheme.scrim) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        colorScheme.scrim.copy(alpha = 0.38f),
                        colorScheme.scrim.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
            )
        }

    Card(
        onClick = onClick,
        shape = MoodAndGenresButtonShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier =
            modifier
                .height(MoodAndGenresButtonHeight),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(cardBrush),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 12.dp)
                        .size(MoodAndGenresCoverSize)
                        .clip(MoodAndGenresCoverShape)
                        .background(coverBrush),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(textScrimBrush),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 92.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun rememberMoodAndGenresArtworkModel(
    endpoint: BrowseEndpoint?,
    artworkUrl: String?,
): ImageRequest? {
    if (artworkUrl.isNullOrBlank()) return null

    val context = LocalContext.current
    val requestSizePx = with(LocalDensity.current) { MoodAndGenresArtworkRequestSize.roundToPx() }
    val imageCacheKey =
        remember(endpoint, artworkUrl) {
            val endpointKey = endpoint?.moodAndGenresArtworkKey().orEmpty()
            "mood_and_genres:$endpointKey:$artworkUrl"
        }

    return remember(context, artworkUrl, imageCacheKey, requestSizePx) {
        ImageRequest
            .Builder(context)
            .data(artworkUrl)
            .memoryCacheKey(imageCacheKey)
            .diskCacheKey(imageCacheKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(requestSizePx)
            .build()
    }
}

private val MoodAndGenresButtonShape = RoundedCornerShape(24.dp)
private val MoodAndGenresCoverShape = RoundedCornerShape(16.dp)
private val MoodAndGenresCoverSize = 80.dp
private val MoodAndGenresArtworkRequestSize = 80.dp

val MoodAndGenresButtonHeight = 100.dp
