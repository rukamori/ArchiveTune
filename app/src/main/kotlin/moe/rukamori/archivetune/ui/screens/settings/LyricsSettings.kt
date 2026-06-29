/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixYouTubeLyricsKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PreloadQueueLyricsEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats
import moe.rukamori.archivetune.paxsenix.models.ProviderStats
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.NumberPickerPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ContentSettingsViewModel
import moe.rukamori.archivetune.viewmodels.PaxsenixStatsState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@Composable
fun LyricsSettings(
    navController: NavController,
    viewModel: ContentSettingsViewModel = hiltViewModel(),
) {
    var showClearLyricsDialog by remember { mutableStateOf(false) }
    var showPaxsenixStatsDialog by remember { mutableStateOf(false) }

    if (showClearLyricsDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_lyrics_cache),
            onDismiss = { showClearLyricsDialog = false },
            onConfirm = {
                viewModel.clearLyricsCache()
                showClearLyricsDialog = false
            },
            onCancel = { showClearLyricsDialog = false },
        ) {
            Text(stringResource(R.string.clear_lyrics_cache_confirm))
        }
    }

    if (showPaxsenixStatsDialog) {
        val statsState by viewModel.paxsenixStatsState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.fetchPaxsenixStats()
        }

        PaxsenixStatsDialog(
            state = statsState,
            onDismiss = { showPaxsenixStatsDialog = false },
            onRetry = { viewModel.fetchPaxsenixStats() },
        )
    }

    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsMode, onLyricsModeChange) = rememberEnumPreference(LyricsModeKey, defaultValue = LyricsMode.ENHANCED)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableYouLyPlusLyrics, onEnableYouLyPlusLyricsChange) =
        rememberPreference(key = EnableYouLyPlusLyricsKey, defaultValue = true)
    val (enableSimpMusicLyrics, onEnableSimpMusicLyricsChange) = rememberPreference(key = EnableSimpMusicLyricsKey, defaultValue = true)
    val (enablePaxsenixLyrics, onEnablePaxsenixLyricsChange) = rememberPreference(key = EnablePaxsenixLyricsKey, defaultValue = true)
    val (enablePaxsenixAppleMusicLyrics, onEnablePaxsenixAppleMusicLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixAppleMusicLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixNeteaseLyrics, onEnablePaxsenixNeteaseLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixNeteaseLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixSpotifyLyrics, onEnablePaxsenixSpotifyLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixSpotifyLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixMusixmatchLyrics, onEnablePaxsenixMusixmatchLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixMusixmatchLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixYouTubeLyrics, onEnablePaxsenixYouTubeLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixYouTubeLyricsKey,
            defaultValue = true,
        )
    val (enableUnisonLyrics, onEnableUnisonLyricsChange) = rememberPreference(key = EnableUnisonLyricsKey, defaultValue = true)
    val (providerOrderStr, onProviderOrderStrChange) =
        rememberPreference(
            key = LyricsProviderOrderKey,
            defaultValue = "",
        )
    val providerOrder =
        remember(providerOrderStr) {
            deserializeLyricsProviderOrder(providerOrderStr)
        }
    val (lyricsLineBlur, onLyricsLineBlurChange) = rememberPreference(LyricsLineBlurKey, defaultValue = true)
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizeOtherLanguages, onLyricsRomanizeOtherLanguagesChange) =
        rememberPreference(
            LyricsRomanizeOtherLanguagesKey,
            defaultValue = true,
        )
    val (preloadQueueLyricsEnabled, onPreloadQueueLyricsEnabledChange) =
        rememberPreference(
            PreloadQueueLyricsEnabledKey,
            defaultValue = true,
        )
    val (queueLyricsPreloadCount, onQueueLyricsPreloadCountChange) = rememberPreference(QueueLyricsPreloadCountKey, defaultValue = 1)

    var showProviderOrderDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderOrderDialog) {
        LyricsProviderOrderDialog(
            initialOrder = providerOrder,
            onDismiss = { showProviderOrderDialog = false },
            onConfirm = { newOrder ->
                onProviderOrderStrChange(newOrder.joinToString(",") { it.name })
                showProviderOrderDialog = false
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        var showLyricsTextSizeDialog by rememberSaveable { mutableStateOf(false) }

        if (showLyricsTextSizeDialog) {
            var tempTextSize by remember { mutableFloatStateOf(lyricsTextSize) }

            DefaultDialog(
                onDismiss = {
                    tempTextSize = lyricsTextSize
                    showLyricsTextSizeDialog = false
                },
                buttons = {
                    TextButton(
                        onClick = { tempTextSize = 24f },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.reset))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            tempTextSize = lyricsTextSize
                            showLyricsTextSizeDialog = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onLyricsTextSizeChange(tempTextSize)
                            showLyricsTextSizeDialog = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_text_size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    Text(
                        text = "${tempTextSize.roundToInt()} sp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    Slider(
                        value = tempTextSize,
                        onValueChange = { tempTextSize = it },
                        valueRange = 16f..36f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        var showLyricsLineSpacingDialog by rememberSaveable { mutableStateOf(false) }

        if (showLyricsLineSpacingDialog) {
            var tempLineSpacing by remember { mutableFloatStateOf(lyricsLineSpacing) }

            DefaultDialog(
                onDismiss = {
                    tempLineSpacing = lyricsLineSpacing
                    showLyricsLineSpacingDialog = false
                },
                buttons = {
                    TextButton(
                        onClick = { tempLineSpacing = 1.3f },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.reset))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            tempLineSpacing = lyricsLineSpacing
                            showLyricsLineSpacingDialog = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onLyricsLineSpacingChange(tempLineSpacing)
                            showLyricsLineSpacingDialog = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_line_spacing),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    Text(
                        text = "${String.format("%.1f", tempLineSpacing)}x",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    Slider(
                        value = tempLineSpacing,
                        onValueChange = { tempLineSpacing = it },
                        valueRange = 1.0f..2.0f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PreferenceGroup(title = stringResource(R.string.display)) {
            item {
                EnumListPreference(
                    title = { Text(stringResource(R.string.lyrics_mode)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    selectedValue = lyricsMode,
                    onValueSelected = onLyricsModeChange,
                    valueText = {
                        when (it) {
                            LyricsMode.V2 -> stringResource(R.string.lyrics_mode_v2)
                            LyricsMode.ENHANCED -> stringResource(R.string.lyrics_mode_enhanced)
                        }
                    },
                )
            }

            item {
                val animationSettingsEnabled = lyricsMode == LyricsMode.V2

                PreferenceEntry(
                    title = { Text(stringResource(R.string.lyrics_animation_style)) },
                    description = if (animationSettingsEnabled) null else stringResource(R.string.lyrics_animation_style_v2_only),
                    icon = { Icon(painterResource(R.drawable.animation), null) },
                    onClick = { navController.navigate("settings/appearance/lyrics_animations") },
                    isEnabled = animationSettingsEnabled,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_click_change)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsClick,
                    onCheckedChange = onLyricsClickChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsScroll,
                    onCheckedChange = onLyricsScrollChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_line_blur)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsLineBlur,
                    onCheckedChange = onLyricsLineBlurChange,
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.lyrics_text_size)) },
                    description = "${lyricsTextSize.roundToInt()} sp",
                    icon = { Icon(painterResource(R.drawable.text_fields), null) },
                    onClick = { showLyricsTextSizeDialog = true },
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.lyrics_line_spacing)) },
                    description = "${String.format("%.1f", lyricsLineSpacing)}x",
                    icon = { Icon(painterResource(R.drawable.text_fields), null) },
                    onClick = { showLyricsLineSpacingDialog = true },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.providers)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_betterlyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableBetterLyrics,
                    onCheckedChange = onEnableBetterLyricsChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_youlyplus_lyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableYouLyPlusLyrics,
                    onCheckedChange = onEnableYouLyPlusLyricsChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_lrclib)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableLrclib,
                    onCheckedChange = onEnableLrclibChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_kugou)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableKugou,
                    onCheckedChange = onEnableKugouChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_unison_lyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableUnisonLyrics,
                    onCheckedChange = onEnableUnisonLyricsChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_simpmusic_lyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableSimpMusicLyrics,
                    onCheckedChange = onEnableSimpMusicLyricsChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_paxsenix_lyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixLyrics,
                    onCheckedChange = onEnablePaxsenixLyricsChange,
                )
            }

            item(visible = enablePaxsenixLyrics) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.paxsenix_stats)) },
                    icon = { Icon(painterResource(R.drawable.stats), null) },
                    onClick = { showPaxsenixStatsDialog = true },
                )
            }

            item(visible = enablePaxsenixLyrics) {
                SwitchPreference(
                    title = { Text("Paxsenix: Apple Music") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixAppleMusicLyrics,
                    onCheckedChange = onEnablePaxsenixAppleMusicLyricsChange,
                )
            }

            item(visible = enablePaxsenixLyrics) {
                SwitchPreference(
                    title = { Text("Paxsenix: NetEase") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixNeteaseLyrics,
                    onCheckedChange = onEnablePaxsenixNeteaseLyricsChange,
                )
            }

            item(visible = enablePaxsenixLyrics) {
                SwitchPreference(
                    title = { Text("Paxsenix: Spotify") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixSpotifyLyrics,
                    onCheckedChange = onEnablePaxsenixSpotifyLyricsChange,
                )
            }

            item(visible = enablePaxsenixLyrics) {
                SwitchPreference(
                    title = { Text("Paxsenix: Musixmatch") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixMusixmatchLyrics,
                    onCheckedChange = onEnablePaxsenixMusixmatchLyricsChange,
                )
            }

            item(visible = enablePaxsenixLyrics) {
                SwitchPreference(
                    title = { Text("Paxsenix: YouTube") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enablePaxsenixYouTubeLyrics,
                    onCheckedChange = onEnablePaxsenixYouTubeLyricsChange,
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.set_first_lyrics_provider)) },
                    description = providerOrder.firstOrNull()?.displayName(),
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    onClick = { showProviderOrderDialog = true },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.romanization)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsRomanizeJapanese,
                    onCheckedChange = onLyricsRomanizeJapaneseChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsRomanizeKorean,
                    onCheckedChange = onLyricsRomanizeKoreanChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsRomanizeChinese,
                    onCheckedChange = onLyricsRomanizeChineseChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsRomanizeHindi,
                    onCheckedChange = onLyricsRomanizeHindiChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_romanize_other_languages)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = lyricsRomanizeOtherLanguages,
                    onCheckedChange = onLyricsRomanizeOtherLanguagesChange,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.queue)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.preload_queue_lyrics)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = preloadQueueLyricsEnabled,
                    onCheckedChange = onPreloadQueueLyricsEnabledChange,
                )
            }

            item(visible = preloadQueueLyricsEnabled) {
                NumberPickerPreference(
                    title = { Text(stringResource(R.string.queue_lyrics_preload_count)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    value = queueLyricsPreloadCount,
                    onValueChange = onQueueLyricsPreloadCountChange,
                    minValue = 0,
                    maxValue = 10,
                    valueText = { if (it == 0) "Off" else it.toString() },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.cache)) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.clear_lyrics_cache)) },
                    icon = { Icon(painterResource(R.drawable.delete), null) },
                    onClick = { showClearLyricsDialog = true },
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics)) },
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
}

private enum class PaxsenixServerStatus { Operational, Degraded, Down }

private fun PreferredLyricsProvider.displayName(): String =
    when (this) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "KuGou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.YOULY_PLUS -> "YouLyPlus"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC -> "Paxsenix: Apple Music"
        PreferredLyricsProvider.PAXSENIX_NETEASE -> "Paxsenix: NetEase"
        PreferredLyricsProvider.PAXSENIX_SPOTIFY -> "Paxsenix: Spotify"
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH -> "Paxsenix: Musixmatch"
        PreferredLyricsProvider.PAXSENIX_YOUTUBE -> "Paxsenix: YouTube"
        PreferredLyricsProvider.UNISON -> "Unison"
    }

@Composable
private fun LyricsProviderOrderDialog(
    initialOrder: List<PreferredLyricsProvider>,
    onDismiss: () -> Unit,
    onConfirm: (List<PreferredLyricsProvider>) -> Unit,
) {
    val providers = remember { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val item = providers.removeAt(from.index)
            providers.add(to.index, item)
        }

    DefaultDialog(
        onDismiss = onDismiss,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onConfirm(providers.toList()) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.set_first_lyrics_provider),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                itemsIndexed(providers, key = { _, item -> item.name }) { index, provider ->
                    ReorderableItem(reorderableState, key = provider.name) {
                        val isFirst = index == 0
                        val containerColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        val contentColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index < providers.size - 1) 4.dp else 0.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(containerColor)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = provider.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun successRateToStatus(rate: Float): PaxsenixServerStatus =
    when {
        rate >= 90f -> PaxsenixServerStatus.Operational
        rate >= 70f -> PaxsenixServerStatus.Degraded
        else -> PaxsenixServerStatus.Down
    }

private fun formatUptimeSeconds(seconds: Double): String {
    val total = seconds.toLong()
    val days = total / 86400L
    val hours = (total % 86400L) / 3600L
    val minutes = (total % 3600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h ${minutes}m"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

@Composable
private fun PaxsenixStatsDialog(
    state: PaxsenixStatsState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.paxsenix_stats)) },
        icon = { Icon(painterResource(R.drawable.stats), contentDescription = null) },
        buttons = {
            if (state is PaxsenixStatsState.Error) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            } else {
                TextButton(onClick = { uriHandler.openUri("https://lyrics.paxsenix.org/") }) {
                    Text(stringResource(R.string.visit_website))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        when (state) {
            PaxsenixStatsState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            PaxsenixStatsState.Error -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = stringResource(R.string.paxsenix_stats_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is PaxsenixStatsState.Success -> {
                PaxsenixStatsContent(stats = state.stats)
            }
        }
    }
}

@Composable
private fun PaxsenixStatsContent(stats: PaxsenixStats) {
    val overallRate =
        remember(stats.overallSuccessRate) {
            stats.overallSuccessRate.trimEnd('%').toFloatOrNull() ?: 0f
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaxsenixStatusBar(successRate = overallRate)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.uptime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatUptimeSeconds(stats.uptimeSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.total_requests),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stats.totalRequests.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.success_rate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stats.overallSuccessRate,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (stats.providers.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.providers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.providers.forEach { (name, providerStats) ->
                    key(name) {
                        PaxsenixProviderRow(name = name, providerStats = providerStats)
                    }
                }
            }
        }

        if (stats.requestLog.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.recent_requests),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stats.requestLog.take(5).forEach { entry ->
                    key(entry.timestamp + entry.endpoint) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (entry.success) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.endpoint,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = entry.provider,
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (entry.success) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            },
                                    )
                                }
                                Text(
                                    text = "${entry.responseTimeMs.toInt()}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (entry.success) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaxsenixStatusBar(successRate: Float) {
    val status = remember(successRate) { successRateToStatus(successRate) }
    val statusColor =
        when (status) {
            PaxsenixServerStatus.Operational -> Color(0xFF4CAF50)
            PaxsenixServerStatus.Degraded -> Color(0xFFFF9800)
            PaxsenixServerStatus.Down -> MaterialTheme.colorScheme.error
        }
    val statusLabel =
        when (status) {
            PaxsenixServerStatus.Operational -> stringResource(R.string.paxsenix_status_operational)
            PaxsenixServerStatus.Degraded -> stringResource(R.string.paxsenix_status_degraded)
            PaxsenixServerStatus.Down -> stringResource(R.string.paxsenix_status_down)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = "${successRate.toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PaxsenixProviderRow(
    name: String,
    providerStats: ProviderStats,
) {
    val rate =
        remember(providerStats.successRate) {
            providerStats.successRate.trimEnd('%').toFloatOrNull() ?: 0f
        }
    val status = remember(rate) { successRateToStatus(rate) }
    val dotColor =
        when (status) {
            PaxsenixServerStatus.Operational -> Color(0xFF4CAF50)
            PaxsenixServerStatus.Degraded -> Color(0xFFFF9800)
            PaxsenixServerStatus.Down -> MaterialTheme.colorScheme.error
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${providerStats.hits} hits",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = providerStats.successRate,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
