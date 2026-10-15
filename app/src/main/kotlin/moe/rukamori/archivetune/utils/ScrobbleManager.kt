/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.models.MediaMetadata
import timber.log.Timber
import kotlin.math.min

class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 180,
) {
    private var scrobbleJob: Job? = null
    private var scrobbleRemainingMillis: Long = 0L
    private var scrobbleTimerStartedAt: Long = 0L
    private var songStartedAt: Long = 0L
    private var songStarted = false
    var useNowPlaying = true

    fun destroy() {
        scrobbleJob?.cancel()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
    }

    fun onSongStart(
        metadata: MediaMetadata?,
        duration: Long? = null,
    ) {
        if (metadata == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        songStarted = true
        startScrobbleTimer(metadata, duration)
        if (useNowPlaying) {
            updateNowPlaying(metadata)
        }
    }

    fun onSongResume(metadata: MediaMetadata) {
        resumeScrobbleTimer(metadata)
    }

    fun onSongPause() {
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        stopScrobbleTimer()
        songStarted = false
    }

    private fun startScrobbleTimer(
        metadata: MediaMetadata,
        duration: Long? = null,
    ) {
        scrobbleJob?.cancel()
        val resolvedDuration = duration?.toInt()?.div(1000) ?: metadata.duration

        if (resolvedDuration <= minSongDuration) return

        val threshold = resolvedDuration * 1000L * scrobbleDelayPercent
        scrobbleRemainingMillis = min(threshold.toLong(), scrobbleDelaySeconds * 1000L)

        if (scrobbleRemainingMillis <= 0) {
            scrobbleSong(metadata)
            return
        }
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(metadata)
                scrobbleJob = null
            }
    }

    private fun pauseScrobbleTimer() {
        scrobbleJob?.cancel()
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        }
    }

    private fun resumeScrobbleTimer(metadata: MediaMetadata) {
        if (scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(metadata)
                scrobbleJob = null
            }
    }

    private fun stopScrobbleTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0
    }

    private fun scrobbleSong(metadata: MediaMetadata) {
        scope.launch {
            val (track, artist) =
                normalizeScrobbleMetadata(
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                )
            LastFM
                .scrobble(
                    artist = artist,
                    track = track,
                    duration = metadata.duration,
                    timestamp = songStartedAt,
                    album = metadata.album?.title,
                ).onSuccess {
                    Timber
                        .tag(
                            "ScrobbleManager",
                        ).d("Scrobbled: $track by $artist")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag("ScrobbleManager").e(throwable, "Failed to scrobble: $track")
                }
        }
    }

    private fun updateNowPlaying(metadata: MediaMetadata) {
        scope.launch {
            val (track, artist) =
                normalizeScrobbleMetadata(
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                )
            LastFM
                .updateNowPlaying(
                    artist = artist,
                    track = track,
                    album = metadata.album?.title,
                    duration = metadata.duration,
                ).onSuccess {
                    Timber.tag("ScrobbleManager").d("Updated now playing: $track")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag("ScrobbleManager").e(throwable, "Failed to update now playing: $track")
                }
        }
    }

    fun onPlayerStateChanged(
        isPlaying: Boolean,
        metadata: MediaMetadata?,
        duration: Long? = null,
    ) {
        if (metadata == null) return
        if (isPlaying) {
            if (!songStarted) {
                onSongStart(metadata, duration)
            } else {
                onSongResume(metadata)
            }
        } else {
            onSongPause()
        }
    }
}

private val FEAT_BRACKETS_REGEX =
    Regex(
        """\s*[\(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s+([^\)\]]+)[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

private val FEAT_SUFFIX_REGEX =
    Regex(
        """\s*(?:-\s*)?(?:feat\.?|ft\.?|featuring)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

private val ARTIST_SPLIT_REGEX =
    Regex(
        """\s*(?:,|&|/|\band\b)\s*""",
        RegexOption.IGNORE_CASE,
    )

internal fun normalizeScrobbleMetadata(
    title: String,
    artists: List<String>,
): Pair<String, String> {
    var cleanTitle = title
    val featuredArtists = mutableListOf<String>()

    val bracketMatch = FEAT_BRACKETS_REGEX.find(cleanTitle)
    if (bracketMatch != null) {
        val rawArtists = bracketMatch.groupValues[1]
        featuredArtists.addAll(
            rawArtists
                .split(ARTIST_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() },
        )
        cleanTitle = cleanTitle.removeRange(bracketMatch.range).trim()
    } else {
        val suffixMatch = FEAT_SUFFIX_REGEX.find(cleanTitle)
        if (suffixMatch != null) {
            val rawArtists = suffixMatch.groupValues[1]
            featuredArtists.addAll(
                rawArtists
                    .split(ARTIST_SPLIT_REGEX)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
            cleanTitle = cleanTitle.removeRange(suffixMatch.range).trim()
        }
    }

    if (cleanTitle.isBlank()) {
        cleanTitle = title
    }

    val mergedArtists = artists.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    for (featured in featuredArtists) {
        if (mergedArtists.none { it.equals(featured, ignoreCase = true) }) {
            mergedArtists.add(featured)
        }
    }

    val finalArtist =
        if (mergedArtists.isNotEmpty()) {
            mergedArtists.joinToString(", ")
        } else {
            artists.joinToString(", ")
        }

    return cleanTitle to finalArtist
}
