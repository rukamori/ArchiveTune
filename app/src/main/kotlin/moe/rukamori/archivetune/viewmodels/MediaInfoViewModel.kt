/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.mediainfo.MediaInfoData
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoMetric
import moe.rukamori.archivetune.mediainfo.MediaInfoQuickFact
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoTab
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.mediainfo.ObserveMediaInfoUseCase
import moe.rukamori.archivetune.mediainfo.asMediaInfoError
import moe.rukamori.archivetune.ui.utils.numberFormatter

@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    private val observeMediaInfo: ObserveMediaInfoUseCase,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MediaInfoState<MediaInfoUiModel>>(MediaInfoState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableTab = MutableStateFlow(
        MediaInfoTab.entries.firstOrNull { it.name == savedStateHandle.get<String>(TabKey) }
            ?: MediaInfoTab.Information,
    )
    val selectedTab = mutableTab.asStateFlow()
    private val eventChannel = Channel<MediaInfoEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var currentVideoId: String? = null

    fun open(videoId: String, volume: Float?) {
        if (currentVideoId == videoId && loadJob?.isActive == true) return
        loadJob?.cancel()
        currentVideoId = videoId
        if (savedStateHandle.get<String>(VideoIdKey) != videoId) {
            savedStateHandle[VideoIdKey] = videoId
            selectTab(MediaInfoTab.Information)
        }
        mutableState.value = MediaInfoState.Loading
        if (videoId.isBlank()) {
            mutableState.value = MediaInfoState.Empty
            return
        }
        loadJob = viewModelScope.launch {
            observeMediaInfo(videoId)
                .map<MediaInfoData, MediaInfoState<MediaInfoUiModel>> { data ->
                    MediaInfoState.Success(mapData(videoId, volume, data))
                }
                .catch { failure -> emit(failure.asMediaInfoError()) }
                .collect { mutableState.value = it }
        }
    }

    fun release(videoId: String) {
        if (currentVideoId != videoId) return
        loadJob?.cancel()
        loadJob = null
        currentVideoId = null
        mutableState.value = MediaInfoState.Loading
    }

    fun selectTab(tab: MediaInfoTab) {
        mutableTab.value = tab
        savedStateHandle[TabKey] = tab.name
    }

    fun copy(value: String) {
        eventChannel.trySend(MediaInfoEvent.Copy(value))
    }

    fun copyId() {
        currentVideoId?.let(::copy)
    }

    fun share() {
        currentVideoId?.let { eventChannel.trySend(MediaInfoEvent.Share("https://music.youtube.com/watch?v=$it")) }
    }

    fun close() {
        eventChannel.trySend(MediaInfoEvent.Close)
    }

    private fun mapData(videoId: String, volume: Float?, data: MediaInfoData): MediaInfoUiModel {
        val local = (data.local as? MediaInfoState.Success)?.data
        val metadata = (data.metadata as? MediaInfoState.Success)?.data
        val statistics = (data.statistics as? MediaInfoState.Success)?.data
        val format = local?.format
        val title = local?.title ?: metadata?.title ?: videoId
        val artists = local?.artists ?: metadata?.author
        val fileSize = format?.contentLength?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
        val bitrate = format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} Kbps" }
        val technical = buildList {
            format?.let {
                add(MediaInfoDetail(R.string.media_info_itag, it.itag.toString()))
                it.mimeType.takeIf(String::isNotBlank)?.let { value -> add(MediaInfoDetail(R.string.mime_type, value)) }
                it.codecs.takeIf(String::isNotBlank)?.let { value -> add(MediaInfoDetail(R.string.codecs, value)) }
                bitrate?.let { value -> add(MediaInfoDetail(R.string.bitrate, value)) }
                it.sampleRate?.takeIf { value -> value > 0 }?.let { value -> add(MediaInfoDetail(R.string.sample_rate, "$value Hz")) }
                it.loudnessDb?.let { value -> add(MediaInfoDetail(R.string.loudness, "$value dB")) }
                fileSize?.let { value -> add(MediaInfoDetail(R.string.file_size, value)) }
            }
            volume?.let { add(MediaInfoDetail(R.string.volume, "${(it * 100).toInt()}%")) }
        }
        val technicalState: MediaInfoState<ImmutableList<MediaInfoDetail>> = when {
            data.local is MediaInfoState.Error -> data.local
            technical.isEmpty() -> MediaInfoState.Empty
            else -> MediaInfoState.Success(ImmutableList.copyOf(technical))
        }
        val facts = buildList {
            format?.mimeType?.substringBefore(';')?.takeIf(String::isNotBlank)?.let {
                add(MediaInfoQuickFact(R.drawable.graphic_eq, it))
            }
            bitrate?.let { add(MediaInfoQuickFact(R.drawable.waves, it)) }
            fileSize?.let { add(MediaInfoQuickFact(R.drawable.storage, it)) }
            metadata?.subscribers?.takeIf(String::isNotBlank)?.let { add(MediaInfoQuickFact(R.drawable.person, it)) }
        }
        return MediaInfoUiModel(
            videoId = videoId,
            title = title,
            subtitle = artists,
            artwork = local?.artwork ?: metadata?.artwork,
            metadata = data.metadata,
            descriptionState = when (data.metadata) {
                is MediaInfoState.Success -> metadata?.description?.let { MediaInfoState.Success(it) } ?: MediaInfoState.Empty
                MediaInfoState.Loading -> MediaInfoState.Loading
                MediaInfoState.Empty -> MediaInfoState.Empty
                is MediaInfoState.Error -> data.metadata
            },
            technicalDetails = technicalState,
            statistics = data.statistics,
            overview = ImmutableList.of(
                MediaInfoDetail(R.string.song_title, local?.title ?: metadata?.title),
                MediaInfoDetail(R.string.song_artists, artists),
                MediaInfoDetail(R.string.media_id, videoId),
            ),
            quickFacts = ImmutableList.copyOf(facts),
            metrics = ImmutableList.of(
                MediaInfoMetric(R.string.subscribers, metadata?.subscribers),
                MediaInfoMetric(R.string.views, statistics?.views?.let(::numberFormatter)),
                MediaInfoMetric(R.string.likes, statistics?.likes?.let(::numberFormatter)),
                MediaInfoMetric(R.string.dislikes, statistics?.dislikes?.let(::numberFormatter)),
            ),
        )
    }

    private companion object {
        const val TabKey = "mediaInfoTab"
        const val VideoIdKey = "mediaInfoVideoId"
    }
}
