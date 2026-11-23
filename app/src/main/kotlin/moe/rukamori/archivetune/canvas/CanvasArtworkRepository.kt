/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import moe.rukamori.archivetune.modules.ModuleCapabilities
import moe.rukamori.archivetune.modules.ModuleContentRepository
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanvasArtworkRepository @Inject constructor(
    private val spotifyCanvas: SpotifyCanvasRepository,
    private val moduleContent: ModuleContentRepository,
) {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    val spotifyConnected = spotifyCanvas.connected

    suspend fun resolve(
        request: CanvasPlaybackRequest,
        policy: CanvasPolicy,
        forceRefresh: Boolean = false,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (request.mediaId.isBlank()) return@withContext null
        val source = policy.configuration.source
        if (!forceRefresh) {
            val cached = CanvasArtworkPlaybackCache.get(request.mediaId, preferCachedOnly = true)
            if (cached != null && source.accepts(cached.source) && cached.matches(request)) {
                return@withContext cached.copy(static = if (policy.networkAllowed) cached.static else null)
            }
        }
        if (!policy.networkAllowed) return@withContext null
        CanvasNetworkAccess.check()
        val songTitle = normalizeCanvasSongTitle(request.title)
        val artistName = normalizeCanvasArtistName(request.artist)
        val candidates = linkedSetOf(
            songTitle to artistName,
            request.title to artistName,
            songTitle to request.artist,
            request.title to request.artist,
        ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
        val fetched = providers.firstNotNullOfOrNull { provider ->
            if (!source.accepts(provider) || (provider == CanvasSource.TIDAL && request.requireVertical)) {
                return@firstNotNullOfOrNull null
            }
            try {
                withTimeoutOrNull(if (provider == CanvasSource.SPOTIFY) 45_000L else 20_000L) {
                    if (provider == CanvasSource.MODULES) return@withTimeoutOrNull moduleCanvas(request)
                    candidates.firstNotNullOfOrNull { (song, artist) ->
                        CanvasNetworkAccess.check(provider)
                        when (provider) {
                            CanvasSource.BETTER_LYRICS, CanvasSource.APPLE_MUSIC -> ArchiveTuneCanvas.getBySongArtist(
                                song = song,
                                artist = artist,
                                storefront = request.storefront,
                                source = provider,
                                requireVertical = request.requireVertical,
                                forceRefresh = forceRefresh,
                            )
                            CanvasSource.TIDAL -> TidalCanvasProvider.getBySongArtist(song, artist, request.storefront)
                            CanvasSource.SPOTIFY -> spotifyCanvas.getBySongArtist(song, artist)
                            CanvasSource.MODULES, CanvasSource.ALL -> error("Canvas lookup requires one provider")
                        }?.takeIf { it.matches(request) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Canvas lookup failed: %s", provider)
                null
            }
        } ?: return@withContext null
        CanvasNetworkAccess.check(fetched.source)
        val artwork = if (forceRefresh) {
            CanvasArtworkPlaybackCache.replace(request.mediaId, fetched)
        } else {
            CanvasArtworkPlaybackCache.put(request.mediaId, fetched)
        }
        if (forceRefresh) mutableRevision.update { it + 1 }
        artwork
    }

    private suspend fun moduleCanvas(request: CanvasPlaybackRequest): CanvasArtwork? {
        val song = moduleContent.song(request.mediaId, request.title, emptyList(), album = null, durationSeconds = 0)
            ?: moduleContent.song(request.mediaId, request.title, listOf(request.artist), album = null, durationSeconds = 0)
            ?: return null
        for (module in moduleContent.providers(ModuleCapabilities.CANVAS)) {
            CanvasNetworkAccess.check(CanvasSource.MODULES)
            val canvas = try {
                moduleContent.canvas(module, song)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Module canvas lookup failed")
                null
            } ?: continue
            val artwork = CanvasArtwork(
                source = CanvasSource.MODULES,
                name = canvas.title ?: request.title,
                artist = canvas.artist ?: request.artist,
                static = canvas.staticImage,
                animated = canvas.animated,
                animatedVertical = canvas.animatedVertical,
                videoUrl = canvas.videoUrl,
                videoUrlVertical = canvas.videoUrlVertical,
            )
            if (artwork.matches(request)) return artwork
        }
        return null
    }

    private companion object {
        val providers = listOf(CanvasSource.MODULES, CanvasSource.BETTER_LYRICS, CanvasSource.APPLE_MUSIC, CanvasSource.TIDAL, CanvasSource.SPOTIFY)
    }
}

private fun CanvasArtwork.matches(request: CanvasPlaybackRequest): Boolean =
    matchesSongIdentity(request.title, request.artist) &&
        !(if (request.requireVertical) preferredVerticalAnimationUrl else preferredAnimationUrl).isNullOrBlank()

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
