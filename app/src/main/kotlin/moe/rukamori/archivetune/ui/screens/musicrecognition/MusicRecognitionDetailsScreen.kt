/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.decodeRecognizedTrack
import moe.rukamori.archivetune.musicrecognition.openMusicRecognition
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import androidx.compose.material3.LargeFlexibleTopAppBar
import moe.rukamori.archivetune.viewmodels.RecognizedTrackUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionDetailsScreen(
    navController: NavHostController,
    encodedTrack: String,
) {
    val context = LocalContext.current
    val track = remember(encodedTrack) {
        decodeRecognizedTrack(encodedTrack)
    }

    val uiModel = remember(track) {
        RecognizedTrackUiModel(
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.coverArtHqUrl ?: track.coverArtUrl,
            metadata = listOfNotNull(track.genre, track.releaseDate).filter(String::isNotBlank).joinToString(" • "),
            label = track.label?.takeIf(String::isNotBlank),
            lyricsPreview = track.lyrics.take(6).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            shazamUrl = track.shazamUrl?.takeIf(String::isNotBlank),
            isrc = track.isrc?.takeIf(String::isNotBlank),
            searchQuery = track.searchQuery,
        )
    }

    val onNavigateBack = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }

    val onListenAgain = remember(navController) {
        {
            navController.openMusicRecognition()
        }
    }

    val onSearch = remember(navController, uiModel.searchQuery) {
        {
            navController.navigate(onlineSearchResultRoute(uiModel.searchQuery))
        }
    }

    val onOpenUri = remember(context) {
        { uri: String ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }
    }

    val scrollBehavior = appBarScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideLayout = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    val maximumContentWidth = if (useWideLayout) 1_040.dp else 680.dp

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.music_recognition),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maximumContentWidth)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    RecognitionResultContent(
                        track = uiModel,
                        useWideLayout = useWideLayout,
                        onListenAgain = onListenAgain,
                        onSearch = onSearch,
                        onOpenUri = onOpenUri,
                    )
                }
            }
        }
    }
}

private fun WindowSizeClass.isWidthAtLeastBreakpoint(breakpoint: Int): Boolean {
    return windowWidthSizeClass != WindowWidthSizeClass.COMPACT
}
