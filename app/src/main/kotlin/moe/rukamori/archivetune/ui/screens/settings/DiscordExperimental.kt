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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.utils.TranslatorLanguages
import moe.rukamori.archivetune.utils.rememberPreference

private val DiscordExperimentalButtonUrlOptions =
    listOf("songurl", "artisturl", "albumurl", "custom")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordExperimental(navController: NavController) {
    val context = LocalContext.current
    val languages = remember(context) { TranslatorLanguages.load(context) }
    val languageCodes = remember(languages) { languages.map { it.code } }
    val languageNamesByCode = remember(languages) { languages.associate { it.code to it.name } }

    val (translatorEnabled, onTranslatorEnabledChange) =
        rememberPreference(key = EnableTranslatorKey, defaultValue = false)
    val (translatorContexts, onTranslatorContextsChange) =
        rememberPreference(
            key = TranslatorContextsKey,
            defaultValue = "{song}, {artist}, {album}",
        )
    val (translatorTargetLang, onTranslatorTargetLangChange) =
        rememberPreference(key = TranslatorTargetLangKey, defaultValue = "ENGLISH")

    val (button1Label, onButton1LabelChange) =
        rememberPreference(
            key = DiscordActivityButton1LabelKey,
            defaultValue = "Listen on YouTube Music",
        )
    val (button1Enabled, onButton1EnabledChange) =
        rememberPreference(
            key = DiscordActivityButton1EnabledKey,
            defaultValue = true,
        )
    val (button2Label, onButton2LabelChange) =
        rememberPreference(
            key = DiscordActivityButton2LabelKey,
            defaultValue = "Go to ArchiveTune",
        )
    val (button2Enabled, onButton2EnabledChange) =
        rememberPreference(
            key = DiscordActivityButton2EnabledKey,
            defaultValue = true,
        )

    val (button1UrlSource, onButton1UrlSourceChange) =
        rememberPreference(
            key = DiscordActivityButton1UrlSourceKey,
            defaultValue = "songurl",
        )
    val (button1CustomUrl, onButton1CustomUrlChange) =
        rememberPreference(
            key = DiscordActivityButton1CustomUrlKey,
            defaultValue = "",
        )
    val (button2UrlSource, onButton2UrlSourceChange) =
        rememberPreference(
            key = DiscordActivityButton2UrlSourceKey,
            defaultValue = "custom",
        )
    val (button2CustomUrl, onButton2CustomUrlChange) =
        rememberPreference(
            key = DiscordActivityButton2CustomUrlKey,
            defaultValue = "https://github.com/rukamori/ArchiveTune",
        )

    Scaffold { inner ->
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.experiment_settings)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        bottom = inner.calculateBottomPadding() + 80.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PreferenceGroup(title = stringResource(R.string.translator_options)) {
                        item {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.enable_translator)) },
                                description = stringResource(R.string.enable_translator_desc),
                                icon = { Icon(painterResource(R.drawable.translate), null) },
                                checked = translatorEnabled,
                                onCheckedChange = onTranslatorEnabledChange,
                            )
                        }

                        item(visible = translatorEnabled) {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.context_info)) },
                                icon = { Icon(painterResource(R.drawable.translate), null) },
                                value = translatorContexts,
                                onValueChange = onTranslatorContextsChange,
                                isInputValid = { true },
                            )
                        }

                        item(visible = translatorEnabled) {
                            ListPreference(
                                title = { Text(stringResource(R.string.target_language)) },
                                icon = { Icon(painterResource(R.drawable.translate), null) },
                                selectedValue = translatorTargetLang,
                                values = languageCodes,
                                valueText = { code -> languageNamesByCode[code] ?: code },
                                onValueSelected = onTranslatorTargetLangChange,
                            )
                        }
                    }
                }

                item {
                    PreferenceGroup(title = stringResource(R.string.discord_button_options)) {
                        item {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.show_button)) },
                                description = stringResource(R.string.show_button1_description),
                                icon = { Icon(painterResource(R.drawable.buttons), null) },
                                checked = button1Enabled,
                                onCheckedChange = onButton1EnabledChange,
                            )
                        }

                        item(visible = button1Enabled) {
                            ListPreference(
                                title = { Text(stringResource(R.string.discord_activity_button_1_url)) },
                                icon = { Icon(painterResource(R.drawable.link), null) },
                                selectedValue = button1UrlSource,
                                values = DiscordExperimentalButtonUrlOptions,
                                valueText = { discordExperimentalButtonUrlSourceLabel(it) },
                                onValueSelected = onButton1UrlSourceChange,
                            )
                        }

                        item(visible = button1Enabled) {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.discord_activity_button1_label)) },
                                icon = { Icon(painterResource(R.drawable.buttons), null) },
                                value = button1Label,
                                onValueChange = onButton1LabelChange,
                                isInputValid = { true },
                            )
                        }

                        item(visible = button1Enabled && button1UrlSource == "custom") {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.discord_activity_button1_url)) },
                                icon = { Icon(painterResource(R.drawable.link), null) },
                                value = button1CustomUrl,
                                onValueChange = onButton1CustomUrlChange,
                                isInputValid = { true },
                            )
                        }

                        item {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.show_button)) },
                                description = stringResource(R.string.show_button2_description),
                                icon = { Icon(painterResource(R.drawable.buttons), null) },
                                checked = button2Enabled,
                                onCheckedChange = onButton2EnabledChange,
                            )
                        }

                        item(visible = button2Enabled) {
                            ListPreference(
                                title = { Text(stringResource(R.string.discord_activity_button_2_url)) },
                                icon = { Icon(painterResource(R.drawable.link), null) },
                                selectedValue = button2UrlSource,
                                values = DiscordExperimentalButtonUrlOptions,
                                valueText = { discordExperimentalButtonUrlSourceLabel(it) },
                                onValueSelected = onButton2UrlSourceChange,
                            )
                        }

                        item(visible = button2Enabled) {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.discord_activity_button2_label)) },
                                icon = { Icon(painterResource(R.drawable.buttons), null) },
                                value = button2Label,
                                onValueChange = onButton2LabelChange,
                                isInputValid = { true },
                            )
                        }

                        item(visible = button2Enabled && button2UrlSource == "custom") {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.discord_activity_button2_url)) },
                                icon = { Icon(painterResource(R.drawable.link), null) },
                                value = button2CustomUrl,
                                onValueChange = onButton2CustomUrlChange,
                                isInputValid = { true },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun discordExperimentalButtonUrlSourceLabel(value: String): String =
    when (value) {
        "songurl" -> stringResource(R.string.discord_url_source_song)
        "artisturl" -> stringResource(R.string.discord_url_source_artist)
        "albumurl" -> stringResource(R.string.discord_url_source_album)
        "custom" -> stringResource(R.string.discord_url_source_custom)
        else -> value
    }
