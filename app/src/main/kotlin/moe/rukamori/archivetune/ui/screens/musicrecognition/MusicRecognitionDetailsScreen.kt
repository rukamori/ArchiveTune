/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionDetailsRoute
import moe.rukamori.archivetune.musicrecognition.openMusicRecognition
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.viewmodels.MusicRecognitionDetailsEvent
import moe.rukamori.archivetune.viewmodels.MusicRecognitionDetailsState
import moe.rukamori.archivetune.viewmodels.MusicRecognitionDetailsViewModel
import moe.rukamori.archivetune.viewmodels.RecognizedTrackUiModel

@Composable
fun MusicRecognitionDetailsScreen(
    navController: NavHostController,
    viewModel: MusicRecognitionDetailsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val onBack: () -> Unit = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }
    val onSearch = remember(viewModel) { viewModel::onSearchRequested }
    val onListenAgain = remember(viewModel) { viewModel::onListenAgainRequested }
    val onShazam = remember(viewModel) { viewModel::onShazamRequested }
    LaunchedEffect(viewModel, navController, context, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                if (navController.currentDestination?.route != MusicRecognitionDetailsRoute) return@collect
                when (event) {
                    MusicRecognitionDetailsEvent.ListenAgain -> navController.openMusicRecognition()
                    is MusicRecognitionDetailsEvent.Search -> {
                        navController.navigate(onlineSearchResultRoute(event.query)) { launchSingleTop = true }
                    }
                    is MusicRecognitionDetailsEvent.OpenUri -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri))
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
    MusicRecognitionDetailsContent(state, onBack, onSearch, onListenAgain, onShazam)
}

@Composable
private fun MusicRecognitionDetailsContent(
    state: MusicRecognitionDetailsState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onListenAgain: () -> Unit,
    onShazam: () -> Unit,
) {
    val scrollBehavior = appBarScrollBehavior()
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    )
    val animationsEnabled = recognitionAnimationsEnabled()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.music_recognition)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1_040.dp).fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = if (wide) 24.dp else 16.dp,
                    vertical = 24.dp,
                ),
            ) {
                item(key = "recognition_details", contentType = "recognition_details") {
                    when (state) {
                        MusicRecognitionDetailsState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 224.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            ) {
                                if (animationsEnabled) {
                                    LoadingIndicator(modifier = Modifier.size(64.dp))
                                } else {
                                    Icon(painterResource(R.drawable.cached), null, modifier = Modifier.size(48.dp))
                                }
                                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        is MusicRecognitionDetailsState.Success -> {
                            RecognitionResultContent(state.track, wide, onSearch, onListenAgain, onShazam)
                        }
                        MusicRecognitionDetailsState.Empty, MusicRecognitionDetailsState.Error -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.music_recognition_error),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )
                                    Text(
                                        stringResource(R.string.music_recognition_details_unavailable),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = onListenAgain,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(stringResource(R.string.music_recognition_listen_again))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionResultContent(
    track: RecognizedTrackUiModel,
    wide: Boolean,
    onSearch: () -> Unit,
    onListenAgain: () -> Unit,
    onShazam: () -> Unit,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.weight(0.9f), contentAlignment = Alignment.TopCenter) {
                CoverArt(track.artworkUrl, minOf(320.dp, maxWidth))
            }
            RecognitionSongInformation(
                track, onSearch, onListenAgain, onShazam,
                modifier = Modifier.weight(1.1f),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoverArt(track.artworkUrl, minOf(240.dp, maxWidth))
            }
            RecognitionSongInformation(track, onSearch, onListenAgain, onShazam)
        }
    }
}

@Composable
private fun RecognitionSongInformation(
    track: RecognizedTrackUiModel,
    onSearch: () -> Unit,
    onListenAgain: () -> Unit,
    onShazam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                track.title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSearch,
                modifier = Modifier.heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.search), null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.search))
            }
            FilledTonalButton(
                onClick = onListenAgain,
                modifier = Modifier.heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.replay), null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_listen_again))
            }
        }
        if (track.album != null || track.metadata.isNotBlank() || track.label != null || track.isrc != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                track.album?.let { album ->
                    Text(
                        stringResource(R.string.music_recognition_notification_album, album),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (track.metadata.isNotBlank()) {
                    Text(
                        track.metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                track.label?.let { label ->
                    Text(
                        stringResource(R.string.music_recognition_notification_label, label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                track.isrc?.let { isrc ->
                    Text(
                        stringResource(R.string.music_recognition_notification_isrc, isrc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        track.lyricsPreview?.let { lyrics ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.music_recognition_lyrics_preview),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(lyrics, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (track.shazamUrl != null) {
            OutlinedButton(
                onClick = onShazam,
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.link), null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_open_shazam))
            }
        }
    }
}
