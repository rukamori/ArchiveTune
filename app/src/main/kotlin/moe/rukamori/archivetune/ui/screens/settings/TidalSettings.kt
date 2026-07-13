/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Tidal music source integration settings.
 * Ported from MetroFuse (github.com/956tris/MetroFuse) under GPL-3.0.
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TidalAccountNameKey
import moe.rukamori.archivetune.constants.TidalAnimatedCoversEnabledKey
import moe.rukamori.archivetune.constants.TidalArtworkFallbackEnabledKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.TidalCookieKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.tidal.isTidalCookieConfigured
import moe.rukamori.archivetune.utils.tidal.normalizeTidalCookieInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidalSettings(navController: NavController) {
    val (tidalEnabled, onTidalEnabledChange) = rememberPreference(TidalEnabledKey, false)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(TidalAudioQualityKey, TidalAudioQuality.FLAC)
    val (artworkFallback, onArtworkFallbackChange) =
        rememberPreference(TidalArtworkFallbackEnabledKey, true)
    val (animatedCovers, onAnimatedCoversChange) =
        rememberPreference(TidalAnimatedCoversEnabledKey, false)
    val (tidalCookie, onTidalCookieChange) = rememberPreference(TidalCookieKey, "")
    val accountName by rememberPreference(TidalAccountNameKey, "")

    val accountConfigured = tidalCookie.isNotBlank() && isTidalCookieConfigured(tidalCookie)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tidal_integration)) },
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
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.general)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_enable)) },
                        description = stringResource(R.string.tidal_enable_description),
                        icon = { Icon(painterResource(R.drawable.provider_tidal), null) },
                        checked = tidalEnabled,
                        onCheckedChange = onTidalEnabledChange,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.tidal_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = tidalEnabled,
                        valueText = { quality ->
                            when (quality) {
                                TidalAudioQuality.AAC_320 -> stringResource(R.string.tidal_quality_aac_320)
                                TidalAudioQuality.FLAC -> stringResource(R.string.tidal_quality_flac)
                                TidalAudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.tidal_quality_hires)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_artwork)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_artwork_fallback)) },
                        description = stringResource(R.string.tidal_artwork_fallback_description),
                        checked = artworkFallback,
                        onCheckedChange = onArtworkFallbackChange,
                        isEnabled = tidalEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_animated_covers)) },
                        description = stringResource(R.string.tidal_animated_covers_description),
                        checked = animatedCovers,
                        onCheckedChange = onAnimatedCoversChange,
                        isEnabled = tidalEnabled,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_account)) {
                item {
                    InfoLabel(text = stringResource(R.string.tidal_account_description))
                }

                item {
                    EditTextPreference(
                        title = {
                            Text(
                                if (accountConfigured) {
                                    stringResource(R.string.tidal_account_connected, accountName.ifBlank { "Tidal" })
                                } else {
                                    stringResource(R.string.tidal_set_cookie)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        value = tidalCookie,
                        isInputValid = { normalizeTidalCookieInput(it) != null },
                        onValueChange = { raw ->
                            onTidalCookieChange(normalizeTidalCookieInput(raw) ?: "")
                        },
                    )
                }

                if (accountConfigured) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tidal_disconnect)) },
                            icon = { Icon(painterResource(R.drawable.logout), null) },
                            onClick = { onTidalCookieChange("") },
                        )
                    }
                }
            }
        }
    }
}
