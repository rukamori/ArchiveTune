/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.LocalMusicIdentity
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.LocalSongScanSummary
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class LocalSongsViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val scanLocalMusic: ScanLocalMusicUseCase,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {
    private val _scanState = MutableStateFlow<LocalSongsScanState>(LocalSongsScanState.Empty)
    val scanState = _scanState.asStateFlow()
    private var scanJob: Job? = null

    private val completedDownloadSongs =
        downloadUtil.downloads
            .map { downloads ->
                downloads.values
                    .filter { download ->
                        download.state == Download.STATE_COMPLETED ||
                            download.state == Download.STATE_DOWNLOADING ||
                            download.state == Download.STATE_QUEUED
                    }
                    .map { download -> download.request.id }
                    .distinct()
            }.distinctUntilChanged()
            .flatMapLatest { songIds ->
                val songFlows =
                    songIds
                        .chunked(MAX_SONG_IDS_PER_QUERY)
                        .map(database::songsByIds)
                if (songFlows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(songFlows) { songGroups -> songGroups.flatMap { it } }
                }
            }
            .map { songs -> songs.filterNot { song -> song.song.isLocal } }

    val songs =
        combine(database.localSongs(), completedDownloadSongs) { localSongs, completedDownloads ->
            val activeDownloadIds = completedDownloads.mapTo(mutableSetOf()) { it.id }
            (localSongs.filterNot { it.song.exportedDownloadId in activeDownloadIds } + completedDownloads).distinctBy { song ->
                LocalMusicIdentity.trackKey(
                    title = song.song.title,
                    artists = song.artists.map { it.name },
                    durationSeconds = song.song.duration,
                    fallbackId = song.id,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    //* adding this here so we can fetch each song's play count on loading it and allowing ordering
    @OptIn(ExperimentalCoroutinesApi::class)
    val playCounts = songs
        .flatMapLatest { localSongs ->
            val playCountFlows = localSongs.map { song -> database.getLifetimePlayCount(song.id) }
            if (playCountFlows.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(playCountFlows) { counts ->
                    localSongs.mapIndexed { index, song -> song.id to counts[index] }.toMap()
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap(),
        )

    fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()) {
        scanJob?.cancel()
        _scanState.value = LocalSongsScanState.Loading
        scanJob = viewModelScope.launch {
            try {
                _scanState.value = LocalSongsScanState.Success(scanLocalMusic(scanConfig))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportException(error)
                _scanState.value = LocalSongsScanState.Error(
                    if (error is SecurityException) R.string.local_songs_permission_body else R.string.local_songs_scan_failed,
                )
            }
        }
    }

    companion object {
        private const val MAX_SONG_IDS_PER_QUERY = 900
    }
}

sealed interface LocalSongsScanState {
    data object Loading : LocalSongsScanState

    @Immutable
    data class Success(val summary: LocalSongScanSummary) : LocalSongsScanState

    data object Empty : LocalSongsScanState

    @Immutable
    data class Error(@StringRes val messageRes: Int) : LocalSongsScanState

    val isScanning: Boolean get() = this is Loading
    val lastSummary: LocalSongScanSummary? get() = (this as? Success)?.summary
    val errorMessageRes: Int? get() = (this as? Error)?.messageRes
}
