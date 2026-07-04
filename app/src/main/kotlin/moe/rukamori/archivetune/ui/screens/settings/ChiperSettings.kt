/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.morideobfuscator.CipherRuntimeStatus
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ChiperSettingsUiData
import moe.rukamori.archivetune.viewmodels.ChiperSettingsUiState
import moe.rukamori.archivetune.viewmodels.ChiperSettingsViewModel
import java.text.DateFormat
import java.util.Date

private val CipherContentMaxWidth = 840.dp
private val CipherMessageMaxWidth = 480.dp

@Composable
fun ChiperSettings(
    navController: NavController,
    viewModel: ChiperSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = navController.context.getString(event.messageRes),
            )
        }
    }

    ChiperSettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = navController::navigateUp,
        onNavigateToMain = navController::backToMain,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun ChiperSettingsContent(
    state: ChiperSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onNavigateToMain: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scrollBehavior = appBarScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.mori_cipher_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(stringResource(R.string.mori_cipher_settings_description))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (state) {
            is ChiperSettingsUiState.Loading -> {
                CipherLoadingState(
                    progressPercent = state.progressPercent,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            ChiperSettingsUiState.Empty -> {
                CipherMessageState(
                    title = stringResource(R.string.mori_cipher_empty_title),
                    description = stringResource(R.string.mori_cipher_empty_description),
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            is ChiperSettingsUiState.Error -> {
                CipherMessageState(
                    title = stringResource(R.string.mori_cipher_error_title),
                    description = stringResource(state.messageRes),
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            is ChiperSettingsUiState.Success -> {
                CipherSettingsSuccess(
                    data = state.data,
                    onRefresh = onRefresh,
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = SettingsDimensions.ScreenBottomPadding,
                        ),
                    modifier =
                        Modifier
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }
        }
    }
}

@Composable
private fun CipherSettingsSuccess(
    data: ChiperSettingsUiData,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lastUpdatedText =
        remember(data.lastUpdatedMillis) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(data.lastUpdatedMillis))
        }
    val statusText =
        when (data.status) {
            CipherRuntimeStatus.READY -> stringResource(R.string.mori_cipher_status_ready)
            CipherRuntimeStatus.REFRESHING -> stringResource(R.string.mori_cipher_status_refreshing)
            CipherRuntimeStatus.DEGRADED -> stringResource(R.string.mori_cipher_status_degraded)
            CipherRuntimeStatus.UNINITIALIZED -> stringResource(R.string.mori_cipher_status_uninitialized)
        }
    val manualRefreshDescription =
        data.rateLimitCountdown?.let {
            stringResource(R.string.mori_cipher_rate_limit_countdown, it)
        } ?: stringResource(
            R.string.mori_cipher_refresh_remaining,
            data.remainingManualRefreshes,
        )
    val intervalProgressProvider =
        remember(data.intervalProgress) {
            { data.intervalProgress }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
    ) {
        item(key = "runtime_overview", contentType = "runtime_overview") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CipherRuntimeOverview(
                    status = data.status,
                    statusText = statusText,
                    countdown = data.countdown,
                    intervalProgress = intervalProgressProvider,
                    modifier =
                        Modifier
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .widthIn(max = CipherContentMaxWidth)
                            .fillMaxWidth(),
                )
            }
        }

        item(key = "runtime_details", contentType = "preference_group") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PreferenceGroup(
                    title = stringResource(R.string.mori_cipher_runtime_section),
                    modifier =
                        Modifier
                            .widthIn(max = CipherContentMaxWidth)
                            .fillMaxWidth(),
                ) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mori_cipher_player_id)) },
                            description = data.playerId,
                            icon = { Icon(painterResource(R.drawable.integration), null) },
                        )
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mori_cipher_last_updated)) },
                            description = lastUpdatedText,
                            icon = { Icon(painterResource(R.drawable.timer), null) },
                        )
                    }
                }
            }
        }

        item(key = "refresh_controls", contentType = "preference_group") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PreferenceGroup(
                    title = stringResource(R.string.mori_cipher_update_section),
                    modifier =
                        Modifier
                            .widthIn(max = CipherContentMaxWidth)
                            .fillMaxWidth(),
                ) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mori_cipher_refresh)) },
                            description = manualRefreshDescription,
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                            trailingContent = {
                                if (data.isRefreshing) {
                                    LoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            },
                            isEnabled = !data.isRefreshing,
                            onClick = onRefresh,
                        )
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.mori_cipher_automatic_updates)) },
                            description = stringResource(R.string.mori_cipher_automatic_updates_description),
                            icon = { Icon(painterResource(R.drawable.info), null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CipherRuntimeOverview(
    status: CipherRuntimeStatus,
    statusText: String,
    countdown: String,
    intervalProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val statusShape = rememberCipherStatusShape()
    val statusContainerColor =
        when (status) {
            CipherRuntimeStatus.READY -> MaterialTheme.colorScheme.primaryContainer
            CipherRuntimeStatus.REFRESHING -> MaterialTheme.colorScheme.tertiaryContainer
            CipherRuntimeStatus.DEGRADED -> MaterialTheme.colorScheme.secondaryContainer
            CipherRuntimeStatus.UNINITIALIZED -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val statusContentColor =
        when (status) {
            CipherRuntimeStatus.READY -> MaterialTheme.colorScheme.onPrimaryContainer
            CipherRuntimeStatus.REFRESHING -> MaterialTheme.colorScheme.onTertiaryContainer
            CipherRuntimeStatus.DEGRADED -> MaterialTheme.colorScheme.onSecondaryContainer
            CipherRuntimeStatus.UNINITIALIZED -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = statusShape,
                    color = statusContainerColor,
                    contentColor = statusContentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.security),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mori_cipher_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.mori_cipher_next_refresh),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                LinearWavyProgressIndicator(
                    progress = intervalProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CipherLoadingState(
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    val progress = progressPercent.coerceIn(0, 100) / 100f
    val progressProvider =
        remember(progress) {
            { progress }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContainedLoadingIndicator(
            progress = progressProvider,
            modifier = Modifier.size(96.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text =
                stringResource(
                    R.string.mori_cipher_loading_configuration,
                    progressPercent,
                ),
            modifier =
                Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 320.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CipherMessageState(
    title: String,
    description: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageShape = rememberCipherStatusShape()

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item(key = "message", contentType = "message") {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = CipherMessageMaxWidth)
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = messageShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.security),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.refresh))
                }
            }
        }
    }
}

@Composable
private fun rememberCipherStatusShape(): Shape = MaterialShapes.Cookie9Sided.toShape()
