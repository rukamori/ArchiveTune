/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.DarkMode
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.LyricsAnimationStyle
import moe.rukamori.archivetune.constants.LyricsAnimationStyleKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.AppleSwitch
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.SettingsGroup
import moe.rukamori.archivetune.design.components.SettingsRow
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * 設定（主要な系統だけ）。
 *
 * 旧UIの35個のサブ画面は流用しない。ここでは DataStore に直接乗る項目と、
 * 取り敢えず安全に切替できるものだけを、外観・歌詞・再生・ストレージ・
 * ダウンロード・アカウントの6系統に並べた。
 *
 * 各行は `rememberPreference` / `rememberEnumPreference` で読む
 * （この2つは `utils/DataStore.kt` にあり、旧UIに依存しない）。
 * 書き込みはその場で DataStore へ入る。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val metrics = appleMetrics()
    val windowInsets = LocalPlayerAwareWindowInsets.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = metrics.gutter.dp - metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                LargeTitleHeader(title = stringResource(R.string.settings))
            }

            item(key = "appearance") { AppearanceGroup() }
            item(key = "lyrics") { LyricsGroup() }
            item(key = "playback") { PlaybackGroup() }
            item(key = "storage") { StorageGroup() }
            item(key = "download") { DownloadGroup() }
            item(key = "account") { AccountGroup() }

            // 最後のグループの下に、ミニプレイヤー等と重ならない余白を取る。
            item(key = "tail") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((metrics.block * 2).dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────── 外観

@Composable
private fun AppearanceGroup() {
    var darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    var pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    val darkModeLabel =
        when (darkMode) {
            DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
            DarkMode.ON -> stringResource(R.string.dark_theme_on)
            DarkMode.OFF -> stringResource(R.string.dark_theme_off)
        }

    SettingsGroup(title = stringResource(R.string.appearance)) {
        SettingsRow(
            title = stringResource(R.string.dark_theme),
            value = darkModeLabel,
            showDivider = false,
            onClick = { darkMode = nextOption(DarkMode.entries, darkMode) },
        )
        SettingsRow(
            title = stringResource(R.string.pure_black),
            showDivider = true,
            onClick = { pureBlack = !pureBlack },
            trailing = {
                AppleSwitch(checked = pureBlack, onCheckedChange = { pureBlack = it })
            },
        )
    }
}

// ─────────────────────────────────────────────────────────── 歌詞

@Composable
private fun LyricsGroup() {
    var animationStyle by rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.FADE)
    var mode by rememberEnumPreference(LyricsModeKey, defaultValue = LyricsMode.V2)

    SettingsGroup(title = stringResource(R.string.lyrics)) {
        SettingsRow(
            title = stringResource(R.string.lyrics_animation_style),
            value = animationStyle.name,
            showDivider = false,
            onClick = { animationStyle = nextOption(LyricsAnimationStyle.entries, animationStyle) },
        )
        SettingsRow(
            title = stringResource(R.string.lyrics_mode),
            value = mode.name,
            showDivider = true,
            onClick = { mode = nextOption(LyricsMode.entries, mode) },
        )
    }
}

// ─────────────────────────────────────────────────────────── 再生

@Composable
private fun PlaybackGroup() {
    var persistentQueue by rememberPreference(PersistentQueueKey, defaultValue = true)
    var pauseOnMute by rememberPreference(PauseOnDeviceMuteKey, defaultValue = false)
    var skipSilence by rememberPreference(SkipSilenceKey, defaultValue = false)
    var normalization by rememberPreference(AudioNormalizationKey, defaultValue = false)

    SettingsGroup(title = stringResource(R.string.settings_playback_title)) {
        SettingsRow(
            title = stringResource(R.string.persistent_queue),
            showDivider = false,
            onClick = { persistentQueue = !persistentQueue },
            trailing = {
                AppleSwitch(checked = persistentQueue, onCheckedChange = { persistentQueue = it })
            },
        )
        SettingsRow(
            title = stringResource(R.string.pause_on_device_mute),
            showDivider = true,
            onClick = { pauseOnMute = !pauseOnMute },
            trailing = {
                AppleSwitch(checked = pauseOnMute, onCheckedChange = { pauseOnMute = it })
            },
        )
        SettingsRow(
            title = stringResource(R.string.skip_silence),
            showDivider = true,
            onClick = { skipSilence = !skipSilence },
            trailing = {
                AppleSwitch(checked = skipSilence, onCheckedChange = { skipSilence = it })
            },
        )
        SettingsRow(
            title = stringResource(R.string.audio_normalization),
            showDivider = true,
            onClick = { normalization = !normalization },
            trailing = {
                AppleSwitch(checked = normalization, onCheckedChange = { normalization = it })
            },
        )
    }
}

// ─────────────────────────────────────────────────────────── ストレージ

@Composable
private fun StorageGroup() {
    var smartTrimmer by rememberPreference(SmartTrimmerKey, defaultValue = true)

    SettingsGroup(title = stringResource(R.string.storage)) {
        SettingsRow(
            title = stringResource(R.string.smart_trimmer),
            showDivider = false,
            onClick = { smartTrimmer = !smartTrimmer },
            trailing = {
                AppleSwitch(checked = smartTrimmer, onCheckedChange = { smartTrimmer = it })
            },
        )
    }
}

// ─────────────────────────────────────────────────────────── ダウンロード

@Composable
private fun DownloadGroup() {
    var autoDownloadOnLike by rememberPreference(AutoDownloadOnLikeKey, defaultValue = false)
    var externalDownloader by rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)

    SettingsGroup(title = stringResource(R.string.download)) {
        SettingsRow(
            title = stringResource(R.string.auto_download_on_like),
            showDivider = false,
            onClick = { autoDownloadOnLike = !autoDownloadOnLike },
            trailing = {
                AppleSwitch(checked = autoDownloadOnLike, onCheckedChange = { autoDownloadOnLike = it })
            },
        )
        SettingsRow(
            title = stringResource(R.string.external_downloader),
            showDivider = true,
            onClick = { externalDownloader = !externalDownloader },
            trailing = {
                AppleSwitch(checked = externalDownloader, onCheckedChange = { externalDownloader = it })
            },
        )
    }
}

// ─────────────────────────────────────────────────────────── アカウント

@Composable
private fun AccountGroup() {
    var ytmSync by rememberPreference(YtmSyncKey, defaultValue = false)

    SettingsGroup(title = stringResource(R.string.account)) {
        SettingsRow(
            title = stringResource(R.string.yt_sync),
            showDivider = false,
            onClick = { ytmSync = !ytmSync },
            trailing = {
                AppleSwitch(checked = ytmSync, onCheckedChange = { ytmSync = it })
            },
        )
    }
}

/**
 * 選択肢を順に送る（末尾から先頭へ回る）。
 *
 * 行を「押すたびに変わる」操作にしているのは、選択肢が3〜6個で
 * かつ全件を1つの行に並べられないため。値は [SettingsRow] の右端に必ず出す。
 */
private fun <T> nextOption(
    options: List<T>,
    current: T,
): T {
    val index = options.indexOf(current)
    return if (index < 0) options.first() else options[(index + 1) % options.size]
}
