/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import moe.rukamori.archivetune.models.MediaMetadata
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfficialAudioResolver
    @Inject
    constructor() {
        sealed interface Resolution {
            data class Available(
                val song: SongItem,
            ) : Resolution

            data object Unavailable : Resolution
        }

        private val resolutions = LruCache<String, Resolution>(MAX_CACHED_RESOLUTIONS)

        fun markUnavailable(videoId: String) {
            resolutions.put(videoId, Resolution.Unavailable)
        }

        suspend fun resolve(video: MediaMetadata): Resolution? {
            resolutions.get(video.id)?.let { return it }
            val resolution =
                withContext<Resolution?>(Dispatchers.IO) {
                    YouTube
                        .songCounterpart(video.id)
                        .rethrowCancellation()
                        .getOrElse { return@withContext null }
                        ?.takeIf { it.id != video.id }
                        ?.let { return@withContext Resolution.Available(it) }
                    YouTube
                        .search(video.searchQuery(), YouTube.SearchFilter.FILTER_SONG)
                        .rethrowCancellation()
                        .getOrElse { return@withContext null }
                        .items
                        .asSequence()
                        .filterIsInstance<SongItem>()
                        .firstOrNull { isOfficialAudioOf(video, it) }
                        ?.let { Resolution.Available(it) }
                        ?: Resolution.Unavailable
                } ?: return null
            resolutions.put(video.id, resolution)
            return resolution
        }

        private fun <T> Result<T>.rethrowCancellation(): Result<T> =
            onFailure { failure ->
                if (failure is CancellationException) throw failure
            }

        private fun MediaMetadata.searchQuery(): String =
            buildString {
                append(title.withoutVideoMarkers())
                artists.firstOrNull()?.name?.takeIf(String::isNotBlank)?.let { append(' ').append(it) }
            }

        private fun isOfficialAudioOf(
            video: MediaMetadata,
            candidate: SongItem,
        ): Boolean {
            if (candidate.id == video.id || candidate.isPodcast) return false
            val candidateType =
                candidate.endpoint
                    ?.watchEndpointMusicSupportedConfigs
                    ?.watchEndpointMusicConfig
                    ?.musicVideoType
            if (candidateType != null && candidateType != MUSIC_VIDEO_TYPE_ATV) return false
            val candidateTitle = candidate.title.comparableTitle()
            if (candidateTitle.isEmpty()) return false
            val candidateArtistIds = candidate.artists.mapNotNullTo(HashSet()) { it.id }
            val candidateArtistNames = candidate.artists.mapTo(HashSet()) { it.name.comparable() }
            val artistMatches =
                video.artists.any { artist ->
                    (artist.id != null && artist.id in candidateArtistIds) ||
                        artist.name.comparable() in candidateArtistNames
                }
            val videoTitle = video.title.comparableTitle()
            if (artistMatches && videoTitle == candidateTitle) return true
            val separator = TITLE_ARTIST_SEPARATOR.find(videoTitle) ?: return false
            val titledArtist = videoTitle.substring(0, separator.range.first).trim()
            val titledSong = videoTitle.substring(separator.range.last + 1).trim()
            val titledArtistMatches =
                candidateArtistNames.any { name ->
                    titledArtist == name || titledArtist.startsWith("$name ") || titledArtist.startsWith("$name,")
                }
            return (artistMatches || titledArtistMatches) && titledSong == candidateTitle
        }

        private fun String.withoutVideoMarkers(): String =
            replace(BRACKETED_SEGMENT) { match ->
                if (VIDEO_MARKER.containsMatchIn(match.groupValues[1])) " " else match.value
            }.replace(PIPED_SUFFIX) { match ->
                if (VIDEO_MARKER.containsMatchIn(match.groupValues[1])) "" else match.value
            }.replace(WHITESPACE, " ")
                .trim()

        private fun String.comparable(): String =
            Normalizer
                .normalize(this, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace(SINGLE_QUOTES, "'")
                .replace(DOUBLE_QUOTES, "\"")
                .replace(WHITESPACE, " ")
                .trim()

        private fun String.comparableTitle(): String = withoutVideoMarkers().comparable()

        private companion object {
            const val MAX_CACHED_RESOLUTIONS = 512
            val BRACKETED_SEGMENT = Regex("""[(\[【「]([^)\]】」]*)[)\]】」]""")
            val PIPED_SUFFIX = Regex("""\s+[|｜]\s+(.*)$""")
            val VIDEO_MARKER =
                Regex(
                    """\b(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|4k|hd|hq|remaster(ed)?)\b""",
                    RegexOption.IGNORE_CASE,
                )
            val TITLE_ARTIST_SEPARATOR = Regex("""\s[-–—]\s""")
            val SINGLE_QUOTES = Regex("""[‘’‛′]""")
            val DOUBLE_QUOTES = Regex("""[“”„″]""")
            val WHITESPACE = Regex("""\s+""")
        }
    }
