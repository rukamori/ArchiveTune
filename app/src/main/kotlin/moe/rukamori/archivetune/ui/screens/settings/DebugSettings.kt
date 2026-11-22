/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DevShowDiscordDebugKey
import moe.rukamori.archivetune.constants.DevShowNerdStatsKey
import moe.rukamori.archivetune.constants.ShowCodecOnPlayerKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupTitle
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettings(navController: NavController) {
    val (showDevDebug, onShowDevDebugChange) =
        rememberPreference(
            key = DevShowDiscordDebugKey,
            defaultValue = false,
        )

    val (showNerdStats, onShowNerdStatsChange) =
        rememberPreference(
            key = DevShowNerdStatsKey,
            defaultValue = false,
        )

    val (showCodecOnPlayer, onShowCodecOnPlayerChange) =
        rememberPreference(
            key = ShowCodecOnPlayerKey,
            defaultValue = false,
        )

    val playerConnection = LocalPlayerConnection.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val bottomInset = playerInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val beacons =
        listOf(
            ExperimentBeacon(R.drawable.discord, R.string.show_discord_debug_ui, showDevDebug),
            ExperimentBeacon(R.drawable.stats, R.string.show_nerd_stats, showNerdStats),
            ExperimentBeacon(R.drawable.graphic_eq, R.string.display_codec_on_player, showCodecOnPlayer),
        )

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.experiment_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom = bottomInset + SettingsDimensions.ScreenBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "lab_stage", contentType = "lab_stage") {
                ExperimentalLabStage(
                    beacons = beacons,
                    modifier = Modifier.padding(horizontal = ExperimentalScreenGutter),
                )
            }

            item(key = "experimental_features", contentType = "preferences") {
                PreferenceGroup(title = stringResource(R.string.experimental_features)) {
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.show_discord_debug_ui)) },
                            description = stringResource(R.string.enable_discord_debug_lines),
                            icon = { Icon(painterResource(R.drawable.discord), null) },
                            checked = showDevDebug,
                            onCheckedChange = onShowDevDebugChange,
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.show_nerd_stats)) },
                            description = stringResource(R.string.description_show_nerd_stats),
                            icon = { Icon(painterResource(R.drawable.stats), null) },
                            checked = showNerdStats,
                            onCheckedChange = onShowNerdStatsChange,
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.display_codec_on_player)) },
                            description = stringResource(R.string.description_display_codec_on_player),
                            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                            checked = showCodecOnPlayer,
                            onCheckedChange = onShowCodecOnPlayerChange,
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.debug_logs)) },
                            description = stringResource(R.string.view_debug_logs_description),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.manage_search),
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.navigate_next),
                                    contentDescription = null,
                                )
                            },
                            onClick = { navController.navigate("settings/logcat") },
                        )
                    }
                }
            }

            if (showDevDebug || showNerdStats) {
                item(key = "live_diagnostics_title", contentType = "section_title") {
                    PreferenceGroupTitle(
                        title = stringResource(R.string.live_diagnostics),
                        modifier =
                            Modifier
                                .experimentalItemMotion(this)
                                .padding(horizontal = ExperimentalScreenGutter),
                    )
                }
            }

            if (showDevDebug) {
                item(key = "discord_panel", contentType = "diagnostics_panel") {
                    ExperimentalDiscordPanel(
                        modifier =
                            Modifier
                                .experimentalItemMotion(this)
                                .padding(horizontal = ExperimentalScreenGutter),
                    )
                }
            }

            if (showNerdStats) {
                item(key = "nerd_stats_panel", contentType = "diagnostics_panel") {
                    ExperimentalNerdStatsPanel(
                        playerConnection = playerConnection,
                        modifier =
                            Modifier
                                .experimentalItemMotion(this)
                                .padding(horizontal = ExperimentalScreenGutter),
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.experimentalItemMotion(scope: LazyItemScope): Modifier {
    if (LocalAnimationsDisabled.current) return this
    val motion = MaterialTheme.motionScheme
    return with(scope) {
        this@experimentalItemMotion.animateItem(
            fadeInSpec = motion.defaultEffectsSpec(),
            placementSpec = motion.defaultSpatialSpec(),
            fadeOutSpec = motion.fastEffectsSpec(),
        )
    }
}
