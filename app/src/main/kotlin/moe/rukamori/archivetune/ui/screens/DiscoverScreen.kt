/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.SwipeableSongCardStack
import moe.rukamori.archivetune.viewmodels.DiscoverUiState
import moe.rukamori.archivetune.viewmodels.DiscoverViewModel

@Composable
fun DiscoverScreen(
    navController: NavController,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = WindowInsets.systemBars.asPaddingValues()

    val layoutDirection = LocalLayoutDirection.current


    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview(playerConnection)
        }
    }


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is DiscoverUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparazione Discover Settimanale...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            is DiscoverUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::loadCards) {
                            Text(text = "Riprova")
                        }
                    }
                }
            }

            is DiscoverUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nessun brano raccomandato al momento",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = viewModel::loadCards) {
                            Text(text = "Ricarica Discover")
                        }
                    }
                }
            }

            is DiscoverUiState.Success -> {
                val currentSong = state.cardState.songs.getOrNull(state.cardState.currentIndex)

                // Auto-play preview and seek to chorus when new card appears
                LaunchedEffect(state.cardState.currentIndex) {
                    if (currentSong != null) {
                        viewModel.autoPlayCurrentCard(playerConnection)
                    }
                }

                // Smoothly crossfade blurred background artwork colors between cards over 600ms
                Crossfade(
                    targetState = currentSong?.thumbnail,
                    animationSpec = tween(600),
                    label = "background_artwork_crossfade",
                ) { url ->
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .blur(60.dp),
                        )
                    }
                }


                // Dark overlay over blurred background
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Black.copy(alpha = 0.88f),
                                        ),
                                ),
                            ),
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = windowInsets.calculateStartPadding(layoutDirection),
                                top = windowInsets.calculateTopPadding() + 56.dp,
                                end = windowInsets.calculateEndPadding(layoutDirection),
                                bottom = windowInsets.calculateBottomPadding() + 88.dp,
                            ),

                ) {

                    // Top Bar Header
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Discover Settimanale",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }


                        if (state.savedCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.favorite),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(
                                        text = "${state.savedCount} salvate",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    // Card Stack
                    SwipeableSongCardStack(
                        state = state.cardState,
                        onSwipeLeft = { _ -> viewModel.swipeLeft(playerConnection) },
                        onSwipeRight = { song -> viewModel.swipeRight(song, playerConnection) },
                        onTogglePreview = { viewModel.togglePreview(playerConnection) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
