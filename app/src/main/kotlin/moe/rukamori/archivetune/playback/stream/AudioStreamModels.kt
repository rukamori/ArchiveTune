/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState

enum class StreamPurpose {
    PLAYBACK,
    DOWNLOAD,
}

enum class StreamSource {
    YOUTUBEI,
}

data class AudioStreamRequest(
    val mediaId: String,
    val playlistId: String? = null,
    val quality: AudioQuality,
    val networkMetered: Boolean,
    val purpose: StreamPurpose,
    val authState: PlaybackAuthState,
    val pinnedFormatId: Int? = null,
    val requiresSongMetadata: Boolean = false,
)

data class ResolvedAudioStream(
    val url: String,
    val requestHeaders: Map<String, String>,
    val formatId: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val expiresAtMs: Long,
    val authFingerprint: String,
    val source: StreamSource,
    val runtimeVersion: String? = null,
    val title: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    val playbackTrackingUrl: String? = null,
)
