/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.ReleaseInfo
import moe.rukamori.archivetune.utils.Updater
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    channel: UpdateChannel = UpdateChannel.STABLE,
) {
    val coroutineScope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadReleases(forceRefresh: Boolean) {
        val result =
            when (channel) {
                UpdateChannel.CANARY -> Updater.getAllCanaryReleases(forceRefresh = forceRefresh)
                else -> Updater.getAllReleases(forceRefresh = forceRefresh)
            }
        result
            .onSuccess { r ->
                releases = r
                error = null
            }.onFailure { e ->
                if (releases.isEmpty()) {
                    error = e.message
                }
            }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val cachedReleases =
            when (channel) {
                UpdateChannel.CANARY -> Updater.getCachedCanaryReleases()
                else -> Updater.getCachedReleases()
            }
        if (cachedReleases.isNotEmpty()) {
            releases = cachedReleases
            isLoading = false
        }
        loadReleases(forceRefresh = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog)) },
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
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            when {
                isLoading -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                error != null && releases.isEmpty() -> {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.error_loading_changelog),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            isLoading = releases.isEmpty()
                            error = null
                            coroutineScope.launch {
                                loadReleases(forceRefresh = true)
                            }
                        }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                releases.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_releases),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        items(releases) { release ->
                            ReleaseCard(release = release)
                        }

                        item { Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseInfo) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    val formattedDate =
        remember(release.publishedAt) {
            try {
                val date = dateFormat.parse(release.publishedAt.substring(0, 10))
                date?.let { displayDateFormat.format(it) } ?: release.publishedAt
            } catch (e: Exception) {
                release.publishedAt
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.name.ifBlank { release.tagName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!release.body.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                MarkdownText(
                    markdown = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
