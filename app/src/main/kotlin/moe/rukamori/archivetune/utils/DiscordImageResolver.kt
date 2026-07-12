/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.utils.getMusicVideoYTThumbnail
import timber.log.Timber

data class ResolvedDiscordImages(
    val thumbnailOriginalUrl: String?,
    val thumbnailResolvedId: String?,
    val artistOriginalUrl: String?,
    val artistResolvedId: String?,
)

object DiscordImageResolver {
    private const val TAG = "DiscordImageResolver"

    private var cachedSongId: String? = null
    private var cachedImages: ResolvedDiscordImages? = null

    @Synchronized
    fun getCachedImages(songId: String): ResolvedDiscordImages? = if (cachedSongId == songId) cachedImages else null

    @Synchronized
    private fun setCachedImages(
        songId: String,
        images: ResolvedDiscordImages,
    ) {
        cachedSongId = songId
        cachedImages = images
    }

    @Synchronized
    fun clearCache() {
        cachedSongId = null
        cachedImages = null
    }

    suspend fun resolveImagesForSong(
        context: Context,
        song: Song,
        isMusicVideo: Boolean = false,
    ): ResolvedDiscordImages {
        val songId = song.song.id
        val thumbnailUrl = song.song.thumbnailUrl?.asHttpUrl()
        val artistUrl =
            song.artists
                .firstOrNull()
                ?.thumbnailUrl
                ?.asHttpUrl()

        getCachedImages(songId)
            ?.takeIf { cached ->
                cached.thumbnailOriginalUrl == thumbnailUrl &&
                    cached.artistOriginalUrl == artistUrl
            }?.let { cached ->
                Timber.tag(TAG).d("Using cached images for song: %s", songId)
                return cached
            }

        val ytThumbnailUrl = getMusicVideoYTThumbnail(songId, thumbnailUrl, isMusicVideo)
        if (isMusicVideo && ytThumbnailUrl != thumbnailUrl) {
            Timber.tag(TAG).d("Using YT thumbnail for music video songId=%s ytUrl=%s", songId, ytThumbnailUrl)
        }
        val savedArtwork = ArtworkStorage.findBySongId(context, songId)

        val thumbnail = ytThumbnailUrl ?: savedArtwork?.thumbnail?.asHttpUrl() ?: thumbnailUrl
        val savedArtistUrl =
            savedArtwork
                ?.artist
                ?.asHttpUrl()
                ?.takeUnless { it == savedArtwork?.thumbnail?.asHttpUrl() }
        val persistedArtist = artistUrl ?: savedArtistUrl
        val artist = persistedArtist ?: thumbnail

        val images =
            ResolvedDiscordImages(
                thumbnailOriginalUrl = thumbnailUrl,
                thumbnailResolvedId = thumbnail,
                artistOriginalUrl = artistUrl,
                artistResolvedId = artist,
            )

        if (thumbnail != savedArtwork?.thumbnail || persistedArtist != savedArtwork?.artist) {
            runCatching {
                ArtworkStorage.saveOrUpdate(
                    context = context,
                    artwork =
                        SavedArtwork(
                            songId = songId,
                            thumbnail = thumbnail,
                            artist = persistedArtist,
                        ),
                )
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to persist Discord artwork URLs")
            }
        }

        setCachedImages(songId, images)
        return images
    }

    fun buildImageUrl(
        imageType: String,
        customUrl: String?,
        resolvedImages: ResolvedDiscordImages,
        song: Song,
    ): String? =
        when (imageType.lowercase()) {
            "thumbnail", "song", "album" -> {
                resolvedImages.thumbnailResolvedId
                    ?: resolvedImages.thumbnailOriginalUrl
                    ?: song.song.thumbnailUrl?.asHttpUrl()
            }

            "artist" -> {
                resolvedImages.artistResolvedId
                    ?: resolvedImages.artistOriginalUrl
                    ?: song.artists
                        .firstOrNull()
                        ?.thumbnailUrl
                        ?.asHttpUrl()
                    ?: resolvedImages.thumbnailResolvedId
                    ?: resolvedImages.thumbnailOriginalUrl
                    ?: song.song.thumbnailUrl?.asHttpUrl()
            }

            "appicon" -> {
                "https://raw.githubusercontent.com/rukamori/ArchiveTune/main/fastlane/metadata/android/en-US/images/icon.png"
            }

            "custom" -> {
                customUrl?.asHttpUrl()
                    ?: resolvedImages.thumbnailResolvedId
                    ?: resolvedImages.thumbnailOriginalUrl
            }

            "dontshow", "none" -> {
                null
            }

            else -> {
                resolvedImages.thumbnailResolvedId ?: resolvedImages.thumbnailOriginalUrl
            }
        }

    private fun String.asHttpUrl(): String? {
        val trimmed = trim()
        return trimmed.takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }
    }
}
