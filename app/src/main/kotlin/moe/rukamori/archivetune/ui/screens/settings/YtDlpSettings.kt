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
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeStatus
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupScope
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.YtDlpSettingsUiData
import moe.rukamori.archivetune.viewmodels.YtDlpSettingsUiState
import moe.rukamori.archivetune.viewmodels.YtDlpSettingsViewModel
import java.text.DateFormat
import java.util.Date

private val YtDlpContentMaxWidth = 840.dp
private val YtDlpMessageMaxWidth = 480.dp

@Composable
fun YtDlpSettings(
    navController: NavController,
    viewModel: YtDlpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val onNavigateUp =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }
    val onNavigateToMain = remember(navController) { { navController.backToMain() } }
    val onCheckForUpdates = remember(viewModel) { { viewModel.checkForUpdates() } }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = navController.context.getString(event.messageRes),
            )
        }
    }

    YtDlpSettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onNavigateToMain = onNavigateToMain,
        onCheckForUpdates = onCheckForUpdates,
    )
}

@Composable
private fun YtDlpSettingsContent(
    state: YtDlpSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onNavigateToMain: () -> Unit,
    onCheckForUpdates: () -> Unit,
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
                        text = stringResource(R.string.ytdlp_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = { Text(stringResource(R.string.ytdlp_settings_description)) },
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
        val contentModifier =
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
        when (state) {
            YtDlpSettingsUiState.Loading -> {
                YtDlpLoadingState(modifier = contentModifier)
            }

            YtDlpSettingsUiState.Empty -> {
                YtDlpMessageState(
                    title = stringResource(R.string.ytdlp_empty_title),
                    description = stringResource(R.string.ytdlp_empty_description),
                    onCheckForUpdates = onCheckForUpdates,
                    modifier = contentModifier,
                )
            }

            is YtDlpSettingsUiState.Error -> {
                YtDlpMessageState(
                    title = stringResource(R.string.ytdlp_error_title),
                    description = stringResource(state.messageRes),
                    onCheckForUpdates = onCheckForUpdates,
                    modifier = contentModifier,
                )
            }

            is YtDlpSettingsUiState.Success -> {
                YtDlpSettingsSuccess(
                    data = state.data,
                    onCheckForUpdates = onCheckForUpdates,
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
private fun YtDlpSettingsSuccess(
    data: YtDlpSettingsUiData,
    onCheckForUpdates: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val neverCheckedText = stringResource(R.string.ytdlp_never_checked)
    val bundledRuntimeText = stringResource(R.string.ytdlp_bundled_runtime)
    val lastCheckedText = rememberDateTime(data.lastCheckedAtMillis, neverCheckedText)
    val lastUpdatedText = rememberDateTime(data.lastUpdatedAtMillis, bundledRuntimeText)
    val statusText =
        when (data.status) {
            YtDlpRuntimeStatus.READY -> stringResource(R.string.ytdlp_status_ready)
            YtDlpRuntimeStatus.CHECKING -> stringResource(R.string.ytdlp_status_checking)
            YtDlpRuntimeStatus.RESTART_REQUIRED -> stringResource(R.string.ytdlp_status_restart_required)
            YtDlpRuntimeStatus.FAILED -> stringResource(R.string.ytdlp_status_failed)
        }
    val manualUpdateDescription =
        data.rateLimitCountdown?.let {
            stringResource(R.string.ytdlp_rate_limit_countdown, it)
        } ?: stringResource(
            R.string.ytdlp_updates_remaining,
            data.remainingManualUpdates,
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
                YtDlpRuntimeOverview(
                    status = data.status,
                    statusText = statusText,
                    countdown = data.nextUpdateCountdown,
                    intervalProgress = intervalProgressProvider,
                    modifier =
                        Modifier
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .widthIn(max = YtDlpContentMaxWidth)
                            .fillMaxWidth(),
                )
            }
        }

        item(key = "runtime_details", contentType = "preference_group") {
            CenteredPreferenceGroup(title = stringResource(R.string.ytdlp_runtime_section)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_active_version)) },
                        description = data.activeVersion,
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_bundled_version)) },
                        description = data.bundledVersion,
                        icon = { Icon(painterResource(R.drawable.info), null) },
                    )
                }
                data.pendingVersion?.let { pendingVersion ->
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.ytdlp_pending_version)) },
                            description = pendingVersion,
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                        )
                    }
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_last_checked)) },
                        description = lastCheckedText,
                        icon = { Icon(painterResource(R.drawable.timer), null) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_last_updated)) },
                        description = lastUpdatedText,
                        icon = { Icon(painterResource(R.drawable.timer), null) },
                    )
                }
            }
        }

        item(key = "update_controls", contentType = "preference_group") {
            CenteredPreferenceGroup(title = stringResource(R.string.ytdlp_update_section)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_check_for_updates)) },
                        description = manualUpdateDescription,
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        trailingContent = {
                            if (data.isChecking) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                            }
                        },
                        isEnabled = !data.isChecking,
                        onClick = onCheckForUpdates,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ytdlp_automatic_updates)) },
                        description = stringResource(R.string.ytdlp_automatic_updates_description),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredPreferenceGroup(
    title: String,
    content: PreferenceGroupScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        PreferenceGroup(
            title = title,
            modifier =
                Modifier
                    .widthIn(max = YtDlpContentMaxWidth)
                    .fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun YtDlpRuntimeOverview(
    status: YtDlpRuntimeStatus,
    statusText: String,
    countdown: String?,
    intervalProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val statusShape = rememberYtDlpStatusShape()
    val statusContainerColor =
        when (status) {
            YtDlpRuntimeStatus.READY -> MaterialTheme.colorScheme.primaryContainer
            YtDlpRuntimeStatus.CHECKING -> MaterialTheme.colorScheme.tertiaryContainer
            YtDlpRuntimeStatus.RESTART_REQUIRED -> MaterialTheme.colorScheme.secondaryContainer
            YtDlpRuntimeStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        }
    val statusContentColor =
        when (status) {
            YtDlpRuntimeStatus.READY -> MaterialTheme.colorScheme.onPrimaryContainer
            YtDlpRuntimeStatus.CHECKING -> MaterialTheme.colorScheme.onTertiaryContainer
            YtDlpRuntimeStatus.RESTART_REQUIRED -> MaterialTheme.colorScheme.onSecondaryContainer
            YtDlpRuntimeStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            painter = painterResource(R.drawable.integration),
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
                        text = stringResource(R.string.ytdlp_status),
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
                    text = stringResource(R.string.ytdlp_next_update_check),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = countdown ?: stringResource(R.string.ytdlp_update_due),
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
private fun YtDlpLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(72.dp))
        Text(
            text = stringResource(R.string.ytdlp_loading_runtime),
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun YtDlpMessageState(
    title: String,
    description: String,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageShape = rememberYtDlpStatusShape()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = messageShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.integration),
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
            modifier =
                Modifier
                    .padding(top = 24.dp)
                    .widthIn(max = YtDlpMessageMaxWidth),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = 8.dp, bottom = 24.dp)
                    .widthIn(max = YtDlpMessageMaxWidth),
        )
        Button(onClick = onCheckForUpdates) {
            Text(stringResource(R.string.ytdlp_check_now))
        }
    }
}

@Composable
private fun rememberDateTime(
    timestampMillis: Long?,
    fallback: String,
): String =
    remember(timestampMillis, fallback) {
        timestampMillis?.let { timestamp ->
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(timestamp))
        } ?: fallback
    }

@Composable
private fun rememberYtDlpStatusShape(): Shape = MaterialShapes.Cookie9Sided.toShape()
