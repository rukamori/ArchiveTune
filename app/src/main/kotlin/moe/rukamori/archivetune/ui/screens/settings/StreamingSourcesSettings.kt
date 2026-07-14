/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Streaming source settings (Tidal) surfaced under Player & Audio.
 * Account login/logout for Tidal lives separately in the Integration section.
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioSearchSourceKey
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.TidalAccountFirstKey
import moe.rukamori.archivetune.constants.TidalAnimatedCoversEnabledKey
import moe.rukamori.archivetune.constants.TidalArtworkFallbackEnabledKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingSourcesSettings(navController: NavController) {
    val context = LocalContext.current

    val (tidalEnabled, onTidalEnabledChange) = rememberPreference(TidalEnabledKey, true)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(TidalAudioQualityKey, TidalAudioQuality.FLAC)
    val (artworkFallback, onArtworkFallbackChange) =
        rememberPreference(TidalArtworkFallbackEnabledKey, true)
    val (animatedCovers, onAnimatedCoversChange) =
        rememberPreference(TidalAnimatedCoversEnabledKey, false)

    // ----- Multi-source framework state -----
    val (searchSource, onSearchSourceChange) =
        rememberEnumPreference(AudioSearchSourceKey, AudioSourceType.TIDAL)
    val (sourceOrderRaw, onSourceOrderChange) = rememberPreference(AudioSourceOrderKey, "")
    val (tidalAccountFirst, onTidalAccountFirstChange) = rememberPreference(TidalAccountFirstKey, true)

    // Reorderable, non-YouTube sources in priority order (YouTube is the fixed final fallback).
    val orderedSources =
        remember(sourceOrderRaw) {
            AudioSourceConfig.parseOrder(sourceOrderRaw.ifBlank { null })
                .filter { it != AudioSourceType.YOUTUBE }
        }

    fun moveSource(
        from: Int,
        to: Int,
    ) {
        if (to < 0 || to >= orderedSources.size) return
        val mutable = orderedSources.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        onSourceOrderChange((mutable + AudioSourceType.YOUTUBE).joinToString(",") { it.name })
    }

    fun sourceLabel(source: AudioSourceType): String =
        when (source) {
            AudioSourceType.TIDAL -> context.getString(R.string.source_tidal)
            AudioSourceType.YOUTUBE -> context.getString(R.string.source_youtube)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.streaming_sources)) },
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
            PreferenceGroup(title = stringResource(R.string.audio_sources)) {
                item {
                    InfoLabel(text = stringResource(R.string.audio_sources_description))
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.audio_search_source)) },
                        icon = { Icon(painterResource(R.drawable.search), null) },
                        selectedValue = searchSource,
                        onValueSelected = onSearchSourceChange,
                        valueText = { sourceLabel(it) },
                    )
                }

                // Priority list: each source shows enabled state + up/down reordering controls.
                orderedSources.forEachIndexed { index, source ->
                    item {
                        val enabled =
                            when (source) {
                                AudioSourceType.TIDAL -> tidalEnabled
                                AudioSourceType.YOUTUBE -> true
                            }
                        PreferenceEntry(
                            title = { Text("${index + 1}. ${sourceLabel(source)}") },
                            description =
                                if (enabled) {
                                    stringResource(R.string.audio_source_enabled)
                                } else {
                                    stringResource(R.string.audio_source_disabled)
                                },
                            icon = { Icon(painterResource(R.drawable.play), null) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { moveSource(index, index - 1) },
                                        onLongClick = {},
                                        enabled = index > 0,
                                    ) {
                                        Icon(painterResource(R.drawable.arrow_upward), null)
                                    }
                                    IconButton(
                                        onClick = { moveSource(index, index + 1) },
                                        onLongClick = {},
                                        enabled = index < orderedSources.lastIndex,
                                    ) {
                                        Icon(painterResource(R.drawable.arrow_downward), null)
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    InfoLabel(text = stringResource(R.string.audio_source_youtube_fallback))
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_integration)) {
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
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_account_first)) },
                        description = stringResource(R.string.tidal_account_first_description),
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        checked = tidalAccountFirst,
                        onCheckedChange = onTidalAccountFirstChange,
                        isEnabled = tidalEnabled,
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
        }
    }
}
