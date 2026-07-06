/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.getCurrentQueueIndex
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player
    val localPlayer = service.localPlayer

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val playbackParameters = MutableStateFlow(player.playbackParameters)
    val isPlaying =
        combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
            playWhenReady && playbackState != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            player.playWhenReady && player.playbackState != STATE_ENDED,
        )
    val mediaMetadata = service.currentMediaMetadata
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.lyrics(mediaMetadata?.id)
        }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val aodModeEnabled = MutableStateFlow(false)

    val error = MutableStateFlow<PlaybackException?>(null)
    val waitingForNetworkConnection = service.waitingForNetworkConnection
    val queueRestoreCompleted = service.queueRestoreCompleted

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        playWhenReady.value = player.playWhenReady
        playbackParameters.value = player.playbackParameters
        queueTitle.value = service.queueTitle
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        shuffleModeEnabled.value = player.shuffleModeEnabled
        repeatMode.value = player.repeatMode
        if (player.mediaItemCount > 0 && service.currentMediaMetadata.value == null) {
            service.currentMediaMetadata.value = player.currentMetadata
        }

        // Lazy load bitrate and sample rate for local audio files dynamically
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mediaMetadata
                .distinctUntilChangedBy { it?.id }
                .collectLatest { metadata ->
                    val mediaId = metadata?.id ?: return@collectLatest
                    if (mediaId.isLocalMediaId()) {
                        val storedFormat = database.format(mediaId).first()
                        // Extract only if the format exists in DB, and both bitrate and sampleRate are unevaluated (bitrate == 0 and sampleRate == null)
                        if (storedFormat != null && storedFormat.bitrate == 0 && storedFormat.sampleRate == null) {
                            val (extractedBitrate, extractedSampleRate) = extractLocalAudioProperties(context, mediaId)
                            if (isActive) {
                                val finalBitrate = if (extractedBitrate <= 0 && extractedSampleRate == null) {
                                    -1 // Sentinel -1 to mark failed files so we don't loop I/O repeatedly
                                } else {
                                    extractedBitrate
                                }
                                database.updateLocalAudioMetadata(mediaId, finalBitrate, extractedSampleRate)
                            }
                        }
                    }
                }
        }
    }

    private fun extractLocalAudioProperties(context: Context, uriString: String): Pair<Int, Int?> {
        val extractor = android.media.MediaExtractor()
        var bitrate = 0
        var sampleRate: Int? = null
        try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                            bitrate = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                        }
                        if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.tag("LocalMetadataExtractor").w(e, "Failed to extract local metadata for %s", uriString)
        } finally {
            runCatching { extractor.release() }
        }
        return Pair(bitrate, sampleRate)
    }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
    }

    fun startRadioSeamlessly() {
        service.startRadioSeamlessly()
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        service.addToQueue(items)
    }

    fun playFromVoiceSearch(query: String) {
        service.playFromVoiceSearch(query)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun seekToNext() {
        val state = service.togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (state?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            service.requestTogetherControl(moe.rukamori.archivetune.together.ControlAction.SkipNext)
            return
        }
        player.seekToNext()
        player.prepare()
        player.playWhenReady = true
    }

    fun seekToPrevious() {
        val state = service.togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (state?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            service.requestTogetherControl(moe.rukamori.archivetune.together.ControlAction.SkipPrevious)
            return
        }
        player.seekToPrevious()
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        this.playbackParameters.value = playbackParameters
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                !window.isLive ||
                player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                window.isDynamic ||
                player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
    }
}
