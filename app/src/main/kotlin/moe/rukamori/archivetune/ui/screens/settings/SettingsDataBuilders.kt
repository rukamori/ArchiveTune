/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
): List<SettingsGroup> {
    val account =
        SettingsItem(
            key = "account",
            icon = painterResource(R.drawable.account),
            title = stringResource(R.string.account),
            subtitle = stringResource(R.string.settings_account_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("settings/account") },
        )
    val stats =
        SettingsItem(
            key = "stats",
            icon = painterResource(R.drawable.stats),
            title = stringResource(R.string.settings_stats_title),
            subtitle = stringResource(R.string.settings_stats_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("stats") },
        )
    val appearance =
        SettingsItem(
            key = "appearance",
            icon = painterResource(R.drawable.palette),
            title = stringResource(R.string.appearance),
            subtitle = stringResource(R.string.settings_appearance_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate("settings/appearance") },
        )
    val playback =
        SettingsItem(
            key = "playback",
            icon = painterResource(R.drawable.music_note),
            title = stringResource(R.string.settings_playback_title),
            subtitle = stringResource(R.string.settings_playback_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            onClick = { navController.navigate("settings/player") },
        )
    val lyrics =
        SettingsItem(
            key = "lyrics",
            icon = painterResource(R.drawable.lyrics),
            title = stringResource(R.string.lyrics),
            subtitle = stringResource(R.string.settings_lyrics_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate("settings/lyrics") },
        )
    val content =
        SettingsItem(
            key = "content",
            icon = painterResource(R.drawable.language),
            title = stringResource(R.string.content),
            subtitle = stringResource(R.string.settings_content_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("settings/content") },
        )
    val behavior =
        SettingsItem(
            key = "behavior",
            icon = painterResource(R.drawable.swipe),
            title = stringResource(R.string.settings_behavior_title),
            subtitle = stringResource(R.string.settings_behavior_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("settings/privacy") },
        )
    val integration =
        SettingsItem(
            key = "integration",
            icon = painterResource(R.drawable.auto_awesome),
            title = stringResource(R.string.integration),
            subtitle = stringResource(R.string.settings_integration_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate("settings/integration") },
        )
    val aiIntegration =
        SettingsItem(
            key = "ai_integration",
            icon = painterResource(R.drawable.ai),
            title = stringResource(R.string.ai_integration),
            subtitle = stringResource(R.string.ai_integration_desc),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate("settings/ai_integration") },
        )
    val internet =
        SettingsItem(
            key = "internet",
            icon = painterResource(R.drawable.wifi_proxy),
            title = stringResource(R.string.internet),
            subtitle = stringResource(R.string.settings_internet_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            onClick = { navController.navigate("settings/internet") },
        )
    val poToken =
        SettingsItem(
            key = "po_token",
            icon = painterResource(R.drawable.token),
            title = stringResource(R.string.po_token_generation),
            subtitle = stringResource(R.string.settings_po_token_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate(PO_TOKEN_ROUTE) },
        )
    val storage =
        SettingsItem(
            key = "storage",
            icon = painterResource(R.drawable.storage),
            title = stringResource(R.string.storage),
            subtitle = stringResource(R.string.settings_storage_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("settings/storage") },
        )
    val backupRestore =
        SettingsItem(
            key = "backup_restore",
            icon = painterResource(R.drawable.backup),
            title = stringResource(R.string.backup_restore),
            subtitle = stringResource(R.string.settings_backup_restore_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = { navController.navigate("settings/backup_restore") },
        )
    val developerOptions =
        SettingsItem(
            key = "developer_options",
            icon = painterResource(R.drawable.experiment),
            title = stringResource(R.string.settings_developer_options_title),
            subtitle = stringResource(R.string.settings_developer_options_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            onClick = { navController.navigate("settings/misc") },
        )
    val defaultLinks =
        if (isAndroid12OrLater) {
            SettingsItem(
                key = "default_links",
                icon = painterResource(R.drawable.link),
                title = stringResource(R.string.default_links),
                subtitle = stringResource(R.string.open_supported_links),
                accentColor = MaterialTheme.colorScheme.secondary,
                onClick = {
                    try {
                        val intent =
                            Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        when (e) {
                            is ActivityNotFoundException,
                            is SecurityException,
                            -> {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }

                            else -> {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }
                    }
                },
            )
        } else {
            null
        }
    val updates =
        if (BuildConfig.UPDATER_AVAILABLE) {
            SettingsItem(
                key = "updates",
                icon = painterResource(R.drawable.update),
                title = stringResource(R.string.updates),
                subtitle =
                    if (hasUpdate) {
                        stringResource(R.string.new_version_available)
                    } else {
                        stringResource(R.string.settings_updates_subtitle)
                    },
                showUpdateIndicator = hasUpdate,
                accentColor =
                    if (hasUpdate) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                badge = if (hasUpdate) "v${BuildConfig.VERSION_NAME}" else BuildConfig.VERSION_NAME,
                onClick = { navController.navigate("settings/update") },
            )
        } else {
            null
        }
    val about =
        SettingsItem(
            key = "about",
            icon = painterResource(R.drawable.info),
            title = stringResource(R.string.about),
            subtitle = stringResource(R.string.settings_about_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = { navController.navigate("settings/about") },
        )

    return listOf(
        SettingsGroup(
            title = stringResource(R.string.settings),
            items = listOf(account, stats),
        ),
        SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(appearance, playback, lyrics, content, behavior),
        ),
        SettingsGroup(
            title = stringResource(R.string.integration),
            items = listOf(integration, aiIntegration, internet, poToken),
        ),
        SettingsGroup(
            title = stringResource(R.string.storage),
            items = listOf(storage, backupRestore),
        ),
        SettingsGroup(
            title = stringResource(R.string.about),
            items =
                buildList {
                    add(developerOptions)
                    defaultLinks?.let(::add)
                    updates?.let(::add)
                    add(about)
                },
        ),
    )
}
