/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.artwork

/**
 * Authoritative source of a piece of artwork. Ordering in this enum is NOT the priority;
 * priority is defined by [ArtworkResolver]'s resolution policy.
 */
enum class ArtworkProvider {
    /** Artwork extracted from (or associated with) a local file. Always wins for local media. */
    LOCAL_EMBEDDED,

    /** Artwork URL that arrived with the original media metadata (YouTube/innertube/DB). */
    ORIGINAL_METADATA,

    /** Artwork fetched from Tidal as a fallback when no original artwork exists. */
    TIDAL,

    /** ArchiveTune canvas/artwork service layer (identity-validated player artwork). */
    ARCHIVETUNE_CANVAS,
}

/**
 * The single source of truth for "which artwork is shown for this media item".
 * Every consumer (player, mini player, notification, blurred background, palette extraction)
 * must read the same [ResolvedArtwork] so the displayed image and the extracted palette
 * can never diverge.
 *
 * @param artworkIdentity stable identity of the actual image: a normalized URL, a provider
 * artwork id, or a content revision. Cache keys must be built from this, never from mediaId
 * alone, so that a changed image under the same mediaId cannot reuse stale derived state.
 */
data class ResolvedArtwork(
    val mediaId: String,
    val url: String?,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val matchConfidence: Float? = null,
)

/** Key for artwork fetch dedup/caching. Includes the actual artwork identity and size. */
data class ArtworkCacheKey(
    val mediaId: String,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val requestedSize: Int,
)

/** Key for player palette caching. A changed artwork URL/provider yields a fresh entry. */
data class PlayerPaletteCacheKey(
    val mediaId: String,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val backgroundMode: String,
    val darkTheme: Boolean,
)

/** Everything the resolver needs to know about a track to pick its artwork. */
data class ArtworkRequest(
    val mediaId: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val isrc: String?,
    val durationMs: Long?,
    /** Artwork that came with the metadata (YouTube/DB). Blank/null = no original artwork. */
    val originalArtworkUrl: String?,
    /** True when the media is a local file whose artwork is embedded/MediaStore backed. */
    val isLocal: Boolean = false,
)

/** User/provider configuration snapshot the resolver policy reads. */
data class ArtworkSettings(
    /** User preference: allow Tidal artwork fallback fetches. */
    val tidalArtworkEnabled: Boolean,
    /** Tidal source is enabled and minimally configured (instances or account). */
    val tidalAvailable: Boolean,
)

/** Result of a Tidal artwork lookup. */
data class TidalArtworkMatch(
    val trackId: String,
    val releaseId: String?,
    val artworkUrl: String,
    /** One of: ISRC, TRACK_ID, METADATA, SEARCH. */
    val matchMethod: String,
    /** 0f..1f; below [ArtworkResolver.MIN_TIDAL_CONFIDENCE] the match must be rejected. */
    val confidence: Float,
)

/** Abstraction over the Tidal backend so the resolver stays pure/testable. */
fun interface TidalArtworkFetcher {
    /**
     * Blocking lookup of Tidal cover art for [request]. Implementations must only be
     * called from a background dispatcher. Returns null when nothing acceptable matched.
     * Sensitive auth data must never appear in logs or the returned values.
     */
    fun fetchArtwork(request: ArtworkRequest): TidalArtworkMatch?
}

/** True for URIs pointing at on-device artwork (embedded/MediaStore/FileProvider). */
fun String?.isLocalArtworkUri(): Boolean {
    val scheme = this?.substringBefore(':')?.lowercase() ?: return false
    return scheme == "content" || scheme == "file" || scheme == "android.resource"
}

/**
 * Best-effort provider attribution for a URL when only the URL is available (e.g. the
 * metadata flow). Used for cache keying; the resolver remains the authoritative decider.
 */
fun guessArtworkProvider(url: String?): ArtworkProvider =
    when {
        url.isNullOrBlank() -> ArtworkProvider.ORIGINAL_METADATA
        url.isLocalArtworkUri() -> ArtworkProvider.LOCAL_EMBEDDED
        url.contains("resources.tidal.com") -> ArtworkProvider.TIDAL
        url.contains("koiiverse.cloud") || url.contains("boidu.dev") -> ArtworkProvider.ARCHIVETUNE_CANVAS
        else -> ArtworkProvider.ORIGINAL_METADATA
    }
