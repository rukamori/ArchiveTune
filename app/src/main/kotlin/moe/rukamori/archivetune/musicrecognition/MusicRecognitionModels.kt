/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

data class RecognizedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtUrl: String?,
    val coverArtHqUrl: String?,
    val genre: String?,
    val releaseDate: String?,
    val label: String?,
    val lyrics: List<String>,
    val shazamUrl: String?,
    val isrc: String?,
) {
    val searchQuery: String
        get() = "$title $artist".trim()
}

data class RecognitionHistoryEntry(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtUrl: String?,
    val coverArtHqUrl: String?,
    val genre: String?,
    val releaseDate: String?,
    val shazamUrl: String?,
    val isrc: String?,
    val recognizedAtEpochMillis: Long,
) {
    val stableKey: String
        get() =
            trackId.takeIf { it.isNotBlank() }
                ?: listOf(title, artist, isrc.orEmpty())
                    .joinToString("|") { it.trim().lowercase() }

    val searchQuery: String
        get() = "$title $artist".trim()
}

enum class RecognitionPhase {
    Listening,
    Processing,
}

sealed interface MusicRecognitionFailure {
    data object NoMatch : MusicRecognitionFailure

    data object RecordingFailed : MusicRecognitionFailure

    data object SignatureFailed : MusicRecognitionFailure

    data object RecognitionFailed : MusicRecognitionFailure
}

class MusicRecognitionException(
    val failure: MusicRecognitionFailure,
    cause: Throwable? = null,
) : Exception(cause)
