/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.preload

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.ytdlp.YtDlpSettingsRepository
import javax.inject.Inject

class ObservePlaybackPerformanceSettingsUseCase
    @Inject
    constructor(
        private val repository: PlaybackPerformanceSettingsRepository,
    ) {
        operator fun invoke(): Flow<PlaybackPerformanceSettings> = repository.settings
    }

class SetLowDataModeUseCase
    @Inject
    constructor(
        private val repository: PlaybackPerformanceSettingsRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            repository.updateSettings { settings ->
                settings.copy(
                    lowDataModeEnabled = enabled,
                    preloadNextSongEnabled = if (enabled) false else settings.preloadNextSongEnabled,
                )
            }
        }
    }

class SetPreloadNextSongUseCase
    @Inject
    constructor(
        private val repository: PlaybackPerformanceSettingsRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            repository.updateSettings { settings ->
                settings.copy(
                    preloadNextSongEnabled = enabled && !settings.lowDataModeEnabled,
                )
            }
        }
    }

class ObservePlaybackPreloadConfigurationUseCase
    @Inject
    constructor(
        private val repository: PlaybackPerformanceSettingsRepository,
        private val ytDlpSettingsRepository: YtDlpSettingsRepository,
    ) {
        operator fun invoke(): Flow<PlaybackPreloadConfiguration> =
            combine(
                repository.settings,
                repository.audioQuality,
                repository.playbackAuthState,
                ytDlpSettingsRepository.runtimeSnapshot,
            ) { settings, quality, authState, runtime ->
                PlaybackPreloadConfiguration(
                    enabled = settings.preloadNextSongEnabled && !settings.lowDataModeEnabled,
                    quality = quality,
                    authState = authState,
                    runtimeRevision = runtime.activeVersion,
                )
            }.distinctUntilChanged()
    }
