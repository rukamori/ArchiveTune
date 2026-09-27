/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasHealth
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupScope
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.CanvasCacheOption
import moe.rukamori.archivetune.viewmodels.CanvasSettingsAction
import moe.rukamori.archivetune.viewmodels.CanvasSettingsDialog
import moe.rukamori.archivetune.viewmodels.CanvasSettingsState
import moe.rukamori.archivetune.viewmodels.CanvasSettingsUiModel
import moe.rukamori.archivetune.viewmodels.CanvasSettingsViewModel

@Composable
fun CanvasSettings(
    navController: NavController,
    viewModel: CanvasSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack: () -> Unit = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }
    val onBackToMain: () -> Unit = remember(navController) { { navController.backToMain() } }
    CanvasSettingsContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        onBackToMain = onBackToMain,
    )
}

@Composable
private fun CanvasSettingsContent(
    state: CanvasSettingsState,
    onAction: (CanvasSettingsAction) -> Unit,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archivetune_canvas)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        onLongClick = onBackToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val insets = LocalPlayerAwareWindowInsets.current
        val contentModifier = remember(topPadding, insets) {
            Modifier.padding(top = topPadding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        }
        when (state) {
            CanvasSettingsState.Loading ->
                Box(
                    modifier = contentModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            is CanvasSettingsState.Success -> CanvasSettingsBody(state.model, onAction, contentModifier)
            CanvasSettingsState.Empty -> CanvasSettingsFailure(R.string.canvas_settings_load_failed, onAction, contentModifier)
            is CanvasSettingsState.Error -> CanvasSettingsFailure(state.messageRes, onAction, contentModifier)
        }
    }
}

@Composable
private fun CanvasSettingsBody(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onEnabled: (Boolean) -> Unit =
        remember(onAction) {
            { enabled -> onAction(CanvasSettingsAction.SetEnabled(enabled)) }
        }
    val onSourceSelected: (CanvasSource) -> Unit =
        remember(onAction) {
            { source -> onAction(CanvasSettingsAction.SelectSource(source)) }
        }
    val onWifiOnly: (Boolean) -> Unit =
        remember(onAction) {
            { wifiOnly -> onAction(CanvasSettingsAction.SetWifiOnly(wifiOnly)) }
        }
    val onCacheLimitSelected: (CanvasCacheOption) -> Unit =
        remember(onAction) {
            { option -> onAction(CanvasSettingsAction.SetCacheLimit(option.limitMb)) }
        }
    val onRefresh = remember(onAction) { { onAction(CanvasSettingsAction.RefreshHealth) } }
    val onClear = remember(onAction) { { onAction(CanvasSettingsAction.ShowClearCache) } }
    val sources = remember { CanvasSource.entries.toList() }
    val selectedCacheOption =
        remember(model.configuration.cacheLimitMb, model.cacheLimit, model.cacheOptions) {
            model.cacheOptions.values.firstOrNull {
                it.limitMb == model.configuration.cacheLimitMb
            } ?: CanvasCacheOption(
                limitMb = model.configuration.cacheLimitMb,
                formattedSize = model.cacheLimit,
            )
        }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        PreferenceGroup(title = stringResource(R.string.general)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.canvas_enable)) },
                    description = stringResource(R.string.archivetune_canvas_desc),
                    icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                    checked = model.configuration.enabled,
                    onCheckedChange = onEnabled,
                    isEnabled = !model.busy,
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.canvas_source)) },
                    description =
                        when (model.configuration.source) {
                            CanvasSource.ALL -> stringResource(R.string.canvas_source_all_desc)
                            CanvasSource.SPOTIFY -> stringResource(R.string.canvas_spotify_desc)
                            else -> null
                        },
                    icon = { Icon(painterResource(R.drawable.music_note), null) },
                    selectedValue = model.configuration.source,
                    values = sources,
                    valueText = { source -> stringResource(source.labelResource()) },
                    onValueSelected = onSourceSelected,
                    isEnabled = !model.busy,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.canvas_wifi_only)) },
                    description = stringResource(R.string.canvas_wifi_only_desc),
                    icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                    checked = model.configuration.wifiOnly,
                    onCheckedChange = onWifiOnly,
                    isEnabled = !model.busy,
                )
            }
        }

        CanvasHealthPreferences(
            model = model,
            onRefresh = onRefresh,
        )

        PreferenceGroup(title = stringResource(R.string.canvas_cache)) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.max_cache_size)) },
                    icon = { Icon(painterResource(R.drawable.storage), null) },
                    selectedValue = selectedCacheOption,
                    values = model.cacheOptions.values,
                    valueText = { option -> option.displayName() },
                    onValueSelected = onCacheLimitSelected,
                    isEnabled = !model.busy,
                )
            }

            item {
                CanvasCacheUsagePreference(model = model)
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.clear_canvas_cache)) },
                    icon = { Icon(painterResource(R.drawable.delete), null) },
                    onClick = onClear,
                    isEnabled = !model.busy,
                )
            }
        }
    }

    CanvasSettingsDialogs(
        model = model,
        onAction = onAction,
    )
}

@Composable
private fun CanvasHealthPreferences(
    model: CanvasSettingsUiModel,
    onRefresh: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.canvas_provider_health)) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.refresh)) },
                icon = { Icon(painterResource(R.drawable.sync), null) },
                trailingContent = {
                    if (model.health.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                onClick = onRefresh,
                isEnabled = model.canRefreshHealth && !model.busy,
            )
        }

        canvasHealthPreference(
            titleRes = R.string.canvas_better_lyrics,
            health = model.health.betterLyrics,
            iconRes = R.drawable.motion_photos_on,
        )
        canvasHealthPreference(
            titleRes = R.string.canvas_apple_music,
            health = model.health.appleMusic,
            iconRes = R.drawable.music_note,
        )
        canvasHealthPreference(
            titleRes = R.string.canvas_tidal,
            health = model.health.tidal,
            iconRes = R.drawable.music_note,
        )
        canvasHealthPreference(
            titleRes = R.string.canvas_spotify,
            health = model.health.spotify,
            iconRes = R.drawable.spotify_icon,
        )
    }
}

private fun PreferenceGroupScope.canvasHealthPreference(
    @StringRes titleRes: Int,
    health: CanvasHealth,
    iconRes: Int,
) {
    item {
        PreferenceEntry(
            title = { Text(stringResource(titleRes)) },
            description = stringResource(health.labelResource()),
            icon = { Icon(painterResource(iconRes), null) },
            trailingContent = {
                if (health == CanvasHealth.CHECKING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            },
        )
    }
}

@Composable
private fun CanvasCacheUsagePreference(model: CanvasSettingsUiModel) {
    val progress = remember(model.cacheProgress) { { model.cacheProgress } }
    PreferenceEntry(
        title = { Text(stringResource(R.string.size_used, model.cacheSize)) },
        description = stringResource(R.string.canvas_cache_desc),
        icon = { Icon(painterResource(R.drawable.storage), null) },
        content = {
            if (model.busy || model.configuration.cacheLimitMb > 0) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                if (model.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun CanvasCacheOption.displayName(): String =
    when (limitMb) {
        0 -> stringResource(R.string.disable)
        -1 -> stringResource(R.string.unlimited)
        else -> formattedSize
    }

@Composable
private fun CanvasSettingsDialogs(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
) {
    val onDismiss = remember(onAction) { { onAction(CanvasSettingsAction.DismissDialog) } }
    val onConfirmClear = remember(onAction) { { onAction(CanvasSettingsAction.ClearCache) } }
    when (model.dialog) {
        CanvasSettingsDialog.CLEAR_CACHE ->
            DefaultDialog(
                onDismiss = onDismiss,
                title = { Text(stringResource(R.string.clear_canvas_cache)) },
                buttons = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = onConfirmClear,
                        enabled = !model.busy,
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Text(stringResource(R.string.clear_canvas_cache_dialog))
            }

        null -> Unit
    }
}

@Composable
private fun CanvasSettingsFailure(
    @StringRes messageRes: Int,
    onAction: (CanvasSettingsAction) -> Unit,
    modifier: Modifier,
) {
    val onRetry = remember(onAction) { { onAction(CanvasSettingsAction.Retry) } }
    Column(
        modifier = modifier.fillMaxSize().padding(SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@StringRes
private fun CanvasSource.labelResource(): Int = when (this) {
    CanvasSource.BETTER_LYRICS -> R.string.canvas_better_lyrics
    CanvasSource.APPLE_MUSIC -> R.string.canvas_apple_music
    CanvasSource.TIDAL -> R.string.canvas_tidal
    CanvasSource.SPOTIFY -> R.string.canvas_spotify
    CanvasSource.ALL -> R.string.canvas_source_all
}

@StringRes
private fun CanvasHealth.labelResource(): Int = when (this) {
    CanvasHealth.NOT_CHECKED -> R.string.canvas_health_not_checked
    CanvasHealth.CHECKING -> R.string.canvas_health_checking
    CanvasHealth.AVAILABLE -> R.string.canvas_health_available
    CanvasHealth.UNAVAILABLE -> R.string.canvas_health_unavailable
    CanvasHealth.NOT_CONNECTED -> R.string.spotify_not_connected
    CanvasHealth.NOT_SELECTED -> R.string.canvas_health_not_selected
    CanvasHealth.DISABLED -> R.string.canvas_health_disabled
    CanvasHealth.OFFLINE -> R.string.canvas_health_offline
    CanvasHealth.WIFI_REQUIRED -> R.string.canvas_health_wifi_required
    CanvasHealth.LOW_DATA_MODE -> R.string.canvas_health_low_data
}
