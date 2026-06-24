/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import timber.log.Timber

internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    albumId: String? = null,
    albumTitleRaw: String? = null,
    storefront: String,
    requireVertical: Boolean,
    allowNetwork: Boolean,
): CanvasArtwork? {
    CanvasArtworkPlaybackCache
        .get(mediaId)
        ?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
        ?.let { return it }

    if (!allowNetwork || mediaId.isBlank()) {
        Timber.tag(CanvasArtworkLogTag).d("Skipping canvas network lookup for %s", mediaId)
        return null
    }

    return withContext(Dispatchers.IO) {
        val fetched =
            fetchCanvasArtworkForPlayback(
                songTitleRaw = songTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
            ) ?: fetchCanvasArtworkByAlbumFallback(
                albumId = albumId,
                albumTitleRaw = albumTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
            )

        if (fetched == null) {
            Timber.tag(CanvasArtworkLogTag).d("No playable canvas resolved for %s", mediaId)
            return@withContext null
        }

        CanvasArtworkPlaybackCache
            .put(mediaId, fetched)
            .takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)
    val candidates =
        linkedSetOf(
            songTitle to artistName,
            songTitleRaw to artistName,
            songTitle to artistNameRaw,
            songTitleRaw to artistNameRaw,
        ).filter { (song, artist) ->
            song.isNotBlank() && artist.isNotBlank()
        }

    return candidates.firstNotNullOfOrNull { (song, artist) ->
        ArchiveTuneCanvas
            .getBySongArtist(
                song = song,
                artist = artist,
                storefront = storefront,
            )?.takeIf { artwork ->
                if (requireVertical) {
                    !artwork.preferredVerticalAnimationUrl.isNullOrBlank()
                } else {
                    !artwork.preferredAnimationUrl.isNullOrBlank()
                }
            }
    }
}

private suspend fun fetchCanvasArtworkByAlbumFallback(
    albumId: String?,
    albumTitleRaw: String?,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    albumId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { nonBlankAlbumId ->
            ArchiveTuneCanvas
                .getByAlbumId(nonBlankAlbumId)
                ?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
                ?.let { return it }
        }

    val albumTitle = albumTitleRaw?.trim().orEmpty()
    val artistName = artistNameRaw.trim()
    if (albumTitle.isBlank() || artistName.isBlank()) return null

    return ArchiveTuneCanvas
        .getBySongArtist(
            song = albumTitle,
            artist = artistName,
            storefront = storefront,
        )?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
}

private fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) {
        !preferredVerticalAnimationUrl.isNullOrBlank()
    } else {
        !preferredAnimationUrl.isNullOrBlank()
    }

private const val CanvasArtworkLogTag = "CanvasArtwork"

private fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull()
            .orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
