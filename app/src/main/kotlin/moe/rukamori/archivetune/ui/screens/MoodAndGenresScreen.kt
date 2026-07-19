/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Context
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.viewmodels.MoodAndGenresViewModel
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val moodAndGenres by viewModel.moodAndGenres.collectAsState()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val topPadding = with(density) { windowInsets.getTop(this).toDp() }
    val bottomPadding = with(density) { windowInsets.getBottom(this).toDp() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            gridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
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
                    onClick = {
                        navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
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
) {
    val colorScheme = MaterialTheme.colorScheme
    val base = remember(stripeColor) { Color(stripeColor) }
    val artworkUrl = rememberMoodAndGenresArtworkUrl(endpoint)
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
private fun rememberMoodAndGenresArtworkUrl(endpoint: BrowseEndpoint?): String? {
    endpoint ?: return null
    val context = LocalContext.current

    val cacheKey = buildMoodAndGenresArtworkCacheKey(endpoint)
    val cachedEntry = moodAndGenresArtworkCache[cacheKey]
    val cachedArtwork =
        cachedEntry
            ?.takeIf { isMoodAndGenresArtworkFresh(it.cachedAt) }
            ?.url

    if (cachedEntry != null && cachedArtwork == null) {
        moodAndGenresArtworkCache.remove(cacheKey, cachedEntry)
    }

    val artworkUrl by
        produceState(
            initialValue = cachedArtwork,
            key1 = cacheKey,
            key2 = cachedArtwork,
        ) {
            if (cachedArtwork != null) {
                value = cachedArtwork
                return@produceState
            }

            // Clear a previously remembered value before resolving an expired entry.
            value = null

            val persistedEntry =
                withContext(Dispatchers.IO) {
                    val sharedPrefs =
                        context.getSharedPreferences(
                            MoodAndGenresArtworkPreferencesName,
                            Context.MODE_PRIVATE,
                        )
                    val encodedEntry = sharedPrefs.getString(cacheKey, null)
                    val entry = decodeMoodAndGenresArtworkCacheEntry(encodedEntry)
                    if (entry != null && isMoodAndGenresArtworkFresh(entry.cachedAt)) {
                        entry
                    } else {
                        if (encodedEntry != null) {
                            sharedPrefs.edit().remove(cacheKey).apply()
                        }
                        null
                    }
                }

            if (persistedEntry != null) {
                moodAndGenresArtworkCache[cacheKey] = persistedEntry
                value = persistedEntry.url
                return@produceState
            }

            val resolvedArtwork =
                withContext(Dispatchers.IO) {
                    YouTube.browse(endpoint.browseId, endpoint.params).getOrNull()?.thumbnail
                }

            if (!resolvedArtwork.isNullOrBlank()) {
                val resolvedEntry =
                    MoodAndGenresArtworkCacheEntry(
                        url = resolvedArtwork,
                        cachedAt = System.currentTimeMillis(),
                    )
                withContext(Dispatchers.IO) {
                    context
                        .getSharedPreferences(
                            MoodAndGenresArtworkPreferencesName,
                            Context.MODE_PRIVATE,
                        ).edit()
                        .putString(cacheKey, resolvedEntry.encode())
                        .apply()
                }
                moodAndGenresArtworkCache[cacheKey] = resolvedEntry
                value = resolvedEntry.url
            }
        }

    return artworkUrl
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
            val endpointKey = endpoint?.let(::buildMoodAndGenresArtworkCacheKey).orEmpty()
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

private fun buildMoodAndGenresArtworkCacheKey(endpoint: BrowseEndpoint): String = "${endpoint.browseId}:${endpoint.params.orEmpty()}"

private data class MoodAndGenresArtworkCacheEntry(
    val url: String,
    val cachedAt: Long,
) {
    fun encode(): String = "$url|$cachedAt"
}

private fun decodeMoodAndGenresArtworkCacheEntry(encodedEntry: String?): MoodAndGenresArtworkCacheEntry? {
    if (encodedEntry == null) return null
    val separatorIndex = encodedEntry.lastIndexOf('|')
    if (separatorIndex <= 0 || separatorIndex == encodedEntry.lastIndex) return null

    val url = encodedEntry.substring(0, separatorIndex)
    val cachedAt = encodedEntry.substring(separatorIndex + 1).toLongOrNull() ?: return null
    return url
        .takeIf(String::isNotBlank)
        ?.let { MoodAndGenresArtworkCacheEntry(url = it, cachedAt = cachedAt) }
}

private fun isMoodAndGenresArtworkFresh(
    cachedAt: Long,
    now: Long = System.currentTimeMillis(),
): Boolean {
    val age = now - cachedAt
    return cachedAt > 0L && age in 0..MoodAndGenresArtworkTtlMs
}

private const val MoodAndGenresArtworkPreferencesName = "mood_genre_artwork_cache"
private const val MoodAndGenresArtworkTtlMs = 7L * 24 * 60 * 60 * 1000

private val moodAndGenresArtworkCache = ConcurrentHashMap<String, MoodAndGenresArtworkCacheEntry>()

private val MoodAndGenresButtonShape = RoundedCornerShape(24.dp)
private val MoodAndGenresCoverShape = RoundedCornerShape(16.dp)
private val MoodAndGenresCoverSize = 80.dp
private val MoodAndGenresArtworkRequestSize = 80.dp

val MoodAndGenresButtonHeight = 100.dp
