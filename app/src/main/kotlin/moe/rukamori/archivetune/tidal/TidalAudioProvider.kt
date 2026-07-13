/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package moe.rukamori.archivetune.tidal

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.constants.AudioSourceType
import android.util.Base64
import moe.rukamori.archivetune.constants.TidalAudioQuality
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object TidalAudioProvider {
    private const val API_BASE_URL = "https://tidal.com/v1"
    private const val SONG_LINK_API_URL = "https://api.song.link/v1-alpha.1/links"
    private const val PUBLIC_TOKEN = "49YxDN9a2aFV6RTG"
    private const val COUNTRY_CODE = "US"
    private const val LOCALE = "en_US"
    private const val DEVICE_TYPE = "BROWSER"
    private const val DOWNLOAD_USER_AGENT = "ArchiveTune-Android"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private const val DASH_MIME_TYPE = "application/dash+xml"
    private const val AUDIO_MP4_MIME_TYPE = "audio/mp4"
    private const val AUDIO_FLAC_MIME_TYPE = "audio/flac"
    private const val MP4_BOX_HEADER_SIZE = 8
    private const val MP4_EXTENDED_BOX_HEADER_SIZE = 16
    private const val STREAM_CACHE_MS = 45 * 60 * 1000L
    private const val TRACK_CACHE_MS = 60 * 60 * 1000L
    private const val STREAM_FAILURE_CACHE_MS = 15 * 60 * 1000L
    private const val TRACK_STREAM_FAILURE_CACHE_MS = 60 * 60 * 1000L
    private const val TEMP_FILE_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val RATE_LIMIT_COOLDOWN_MS = 8 * 60 * 1000L
    private const val SEARCH_LIMIT = 8
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val MAX_STREAM_CANDIDATES = 2
    private const val MAX_DIRECT_STREAM_CANDIDATES = 3
    private const val MIN_MATCH_SCORE = 90

    // How long a failing instance is skipped before it is retried again.
    private const val INSTANCE_SOFT_COOLDOWN_MS = 60_000L // transient errors (HTTP 5xx, timeouts)
    private const val INSTANCE_HARD_COOLDOWN_MS = 600_000L // unreachable host / DNS failure
    private const val STRONG_MATCH_SCORE = 150
    private const val REJECT_SCORE = -1_000_000
    private val AMAZON_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
    // Current public streaming instances (from the Monochrome community list). These rotate over
    // time, so auto-discovery and the user's manual list can extend/override them at runtime.
    private val DEFAULT_DOWNLOAD_API_ENDPOINTS =
        listOf(
            TidalDownloadEndpoint("Monochrome EU", "https://eu-central.monochrome.tf"),
            TidalDownloadEndpoint("Monochrome US", "https://us-west.monochrome.tf"),
            TidalDownloadEndpoint("Monochrome API", "https://api.monochrome.tf"),
            TidalDownloadEndpoint("Arran (Monochrome)", "https://arran.monochrome.tf"),
            TidalDownloadEndpoint("Triton (squid.wtf)", "https://triton.squid.wtf"),
            TidalDownloadEndpoint("Wolf QQDL", "https://wolf.qqdl.site"),
            TidalDownloadEndpoint("Hund QQDL", "https://hund.qqdl.site"),
        )

    // Community source that publishes the public HiFi/QQDL instance list. Best-effort only: it
    // rotates and may disappear, so discovery is allowed to fail and callers keep the manual list.
    // The payload is a JSON object of the form { "api": [...], "streaming": [...] }.
    private val INSTANCE_DISCOVERY_SOURCES =
        listOf(
            "https://monochrome.tf/instances.json",
        )

    /**
     * User-configured instance list (base URLs), applied via [setInstances]. When null or empty
     * the built-in [DEFAULT_DOWNLOAD_API_ENDPOINTS] are used so the source keeps working out of
     * the box.
     */
    @Volatile
    private var customEndpoints: List<TidalDownloadEndpoint>? = null

    private val activeEndpoints: List<TidalDownloadEndpoint>
        get() = customEndpoints?.takeIf { it.isNotEmpty() } ?: DEFAULT_DOWNLOAD_API_ENDPOINTS

    /** Base URLs of the built-in default instances, for the settings UI "reset" action. */
    val defaultInstanceUrls: List<String>
        get() = DEFAULT_DOWNLOAD_API_ENDPOINTS.map { it.baseUrl }

    /** Base URLs currently in effect (custom list if set, otherwise defaults). */
    val activeInstanceUrls: List<String>
        get() = activeEndpoints.map { it.baseUrl }

    /**
     * Replaces the active instance list. Invalid or duplicate URLs are dropped; if nothing valid
     * remains the provider reverts to the built-in defaults.
     */
    fun setInstances(baseUrls: List<String>) {
        val seen = LinkedHashSet<String>()
        val parsed =
            baseUrls.mapNotNull { raw ->
                val normalized = normalizeInstanceUrl(raw) ?: return@mapNotNull null
                if (!seen.add(normalized)) return@mapNotNull null
                TidalDownloadEndpoint(instanceLabel(normalized), normalized)
            }
        customEndpoints = parsed.ifEmpty { null }
    }

    /** Normalizes an instance URL to `scheme://host[:port]` form, or null if it is not valid. */
    fun normalizeInstanceUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank() || !url.host.contains('.')) return null
        return withScheme
    }

    private fun instanceLabel(baseUrl: String): String =
        baseUrl.toHttpUrlOrNull()?.host ?: baseUrl

    /**
     * Lightweight reachability probe for a single instance. Returns the round-trip latency in
     * milliseconds when the instance responds, or null when it is unreachable. Runs a blocking
     * network call, so callers must invoke it off the main thread.
     */
    fun checkInstance(baseUrl: String): Long? {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return null
        val request =
            Request
                .Builder()
                .url("$normalized/")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .get()
                .build()
        return runCatching {
            val start = System.currentTimeMillis()
            healthClient.newCall(request).execute().use { response ->
                if (response.code in 200..499) System.currentTimeMillis() - start else null
            }
        }.getOrNull()
    }

    /** Result of a deep instance health probe (see [verifyInstance]). */
    enum class InstanceHealth {
        /** Reachable and served a FULL (non-preview) lossless manifest for the probe track. */
        HEALTHY,

        /** Reachable, but its backing account is unsubscribed so it only serves 30s previews. */
        PREVIEW_ONLY,

        /** Unreachable (DNS/timeout/5xx) or did not return a usable response. */
        UNREACHABLE,
    }

    /** The last Tidal track id that resolved successfully, usable as a health-probe track. */
    @Volatile
    var lastResolvedTrackId: String? = null
        private set

    /**
     * Seeds the health-probe track from persisted storage on startup, without overwriting a track
     * that has already resolved this session.
     */
    fun seedProbeTrack(trackId: String) {
        if (lastResolvedTrackId.isNullOrBlank() && trackId.isNotBlank()) {
            lastResolvedTrackId = trackId
        }
    }

    /**
     * Deep health probe for a single instance. Unlike [checkInstance] (reachability only), this
     * resolves an actual [probeTrackId] manifest and inspects whether the instance serves a FULL
     * track or only a PREVIEW (unsubscribed backing account). When [probeTrackId] is blank it
     * degrades to a reachability check (HEALTHY/UNREACHABLE). Runs blocking network I/O, so callers
     * must invoke it off the main thread.
     */
    fun verifyInstance(
        baseUrl: String,
        probeTrackId: String?,
        quality: TidalAudioQuality = TidalAudioQuality.FLAC,
    ): InstanceHealth {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return InstanceHealth.UNREACHABLE
        val trackId = probeTrackId?.trim().orEmpty()
        if (trackId.isEmpty()) {
            return if (checkInstance(normalized) != null) InstanceHealth.HEALTHY else InstanceHealth.UNREACHABLE
        }
        val qualityString =
            when (quality) {
                TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
                TidalAudioQuality.FLAC -> "LOSSLESS"
                TidalAudioQuality.AAC_320 -> "HIGH"
            }
        val manifestFormats = qualityString.tidalManifestFormats()
            ?: return if (checkInstance(normalized) != null) InstanceHealth.HEALTHY else InstanceHealth.UNREACHABLE
        val url =
            normalized
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("trackManifests")
                .addQueryParameter("id", trackId)
                .apply { manifestFormats.forEach { addQueryParameter("formats", it) } }
                .addQueryParameter("adaptive", "false")
                .addQueryParameter("manifestType", "MPEG_DASH")
                .addQueryParameter("uriScheme", "HTTPS")
                .addQueryParameter("usage", "PLAYBACK")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()
        return runCatching {
            healthClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@use InstanceHealth.UNREACHABLE
                val attributes =
                    JSONObject(body)
                        .optJSONObject("data")
                        ?.optJSONObject("data")
                        ?.optJSONObject("attributes")
                        ?: return@use InstanceHealth.UNREACHABLE
                val presentation =
                    attributes.stringOrNull("trackPresentation")
                        ?: attributes.stringOrNull("assetPresentation")
                if (presentation.equals("PREVIEW", ignoreCase = true)) {
                    InstanceHealth.PREVIEW_ONLY
                } else {
                    InstanceHealth.HEALTHY
                }
            }
        }.getOrElse { InstanceHealth.UNREACHABLE }
    }

    /**
     * Feeds an externally-obtained health result (e.g. from the startup scan) into the same runtime
     * cooldown map the resolver uses, so verified-healthy instances are tried first and
     * dead/preview-only ones are skipped without re-probing them on the next play.
     */
    fun applyHealthResult(
        baseUrl: String,
        healthy: Boolean,
    ) {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return
        if (healthy) markInstanceHealthy(normalized) else markInstanceFailed(normalized, hardFailure = true)
    }

    /**
     * Best-effort auto-discovery of additional public instances from community sources. Returns
     * the list of newly discovered, valid base URLs (may be empty). Never throws; on failure it
     * simply returns an empty list so the caller can keep the manual list. Runs blocking network
     * calls, so invoke it off the main thread.
     */
    fun discoverInstances(): List<String> {
        val discovered = LinkedHashSet<String>()
        for (source in INSTANCE_DISCOVERY_SOURCES) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(source)
                        .header("User-Agent", DOWNLOAD_USER_AGENT)
                        .get()
                        .build()
                healthClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()
                    parseDiscoveredInstances(body).forEach { url ->
                        normalizeInstanceUrl(url)?.let(discovered::add)
                    }
                }
            }
        }
        return discovered.toList()
    }

    /**
     * Parses a discovery payload. Supports:
     *  - a JSON object whose values are arrays of URLs, e.g. Monochrome's
     *    `{ "api": [...], "streaming": [...] }` (streaming instances are preferred);
     *  - a JSON array of URL strings or objects with a "url"/"host" field;
     *  - plain newline / whitespace separated URLs.
     */
    private fun parseDiscoveredInstances(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        // JSON object with array-valued fields (Monochrome format). "streaming" instances serve
        // audio and are listed first; other array fields (e.g. "api") are appended as fallbacks.
        runCatching {
            val obj = JSONObject(trimmed)
            val result = LinkedHashSet<String>()
            val preferredKeys = listOf("streaming", "instances", "api")
            val keys = preferredKeys + obj.keys().asSequence().filterNot { it in preferredKeys }
            for (key in keys) {
                val arr = obj.optJSONArray(key) ?: continue
                collectStringsFromArray(arr, result)
            }
            if (result.isNotEmpty()) return result.toList()
        }

        // JSON array of strings or objects with a "url"/"host" field.
        runCatching {
            val result = LinkedHashSet<String>()
            collectStringsFromArray(JSONArray(trimmed), result)
            if (result.isNotEmpty()) return result.toList()
        }

        // Plain newline / whitespace separated URLs.
        return trimmed
            .split('\n', '\r', ' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun collectStringsFromArray(
        arr: JSONArray,
        into: MutableCollection<String>,
    ) {
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> item.takeIf { it.isNotBlank() }?.let(into::add)
                is JSONObject -> {
                    (item.optString("url").takeIf { it.isNotBlank() }
                        ?: item.optString("host").takeIf { it.isNotBlank() })
                        ?.let(into::add)
                }
            }
        }
    }

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val contentLength: Long?,
        val expiresAtMs: Long,
        val losslessDowngradedBitrateKbps: Int? = null,
        val isLiveManifest: Boolean = false,
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    open class TidalAudioResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class TidalRateLimitedException(
        val retryAfterMs: Long,
    ) : TidalAudioResolutionException("TIDAL FLAC resolver is rate limited; cooling down for ${retryAfterMs / 1000L}s")

    private data class CachedTrack(
        val track: MatchedTrack,
        val expiresAtMs: Long,
    )

    private data class CachedFailure(
        val message: String,
        val expiresAtMs: Long,
    )

    private data class CachedSearch(
        val results: JSONArray,
        val expiresAtMs: Long,
    )

    private data class MatchedTrack(
        val trackId: String,
        val title: String,
        val artistNames: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val audioQuality: String?,
        val mediaTags: Set<String>,
        val audioModes: Set<String>,
        val sampleRate: Int?,
    ) {
        fun supportsLossless(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("LOSSLESS", ignoreCase = true) ||
                    signal.contains("FLAC", ignoreCase = true)
            }

        fun supportsHiResLossless(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("HI_RES", ignoreCase = true) ||
                    signal.contains("HIRES", ignoreCase = true) ||
                    signal.contains("MAX", ignoreCase = true)
            }

        fun supportsAtmos(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("DOLBY_ATMOS", ignoreCase = true) ||
                    signal.contains("DOLBY ATMOS", ignoreCase = true) ||
                    signal.contains("EAC3_JOC", ignoreCase = true) ||
                    signal.contains("ATMOS", ignoreCase = true)
            }

        fun losslessRank(): Int =
            when {
                supportsHiResLossless() -> 3
                supportsLossless() -> 2
                supportsAtmos() -> 1
                else -> 0
            }

        fun qualityScoreBoost(): Int =
            when {
                supportsHiResLossless() -> 70
                supportsLossless() -> 55
                supportsAtmos() -> 20
                else -> 0
            }

        private fun qualitySignals(): List<String> =
            buildList {
                audioQuality?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(mediaTags)
                addAll(audioModes)
            }
    }

    private data class ParsedManifest(
        val url: String,
        val mimeType: String,
        val codecs: String,
        val sampleRate: Int?,
        val bitrate: Int?,
        val expiryUrl: String?,
        val isDash: Boolean,
        val segmentUrls: List<String> = emptyList(),
        val outputExtension: String = ".flac",
        val manifestText: String? = null,
    )

    private data class StreamMetadata(
        val mimeType: String?,
        val contentLength: Long?,
    )

    private data class PlaybackStreamInfo(
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
    )

    private class TidalDownloadEndpoint(
        val name: String,
        baseUrl: String,
    ) {
        val baseUrl: String = baseUrl.trimEnd('/')
    }

    private data class ScoredTrack(
        val track: MatchedTrack,
        val score: Int,
    )

    private val client =
        OkHttpClient
            .Builder()
            .dns(TidalDns) // DoH fallback so streaming works on ISPs that DNS-block tidal.com
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    // Short-timeout client used for instance health checks and discovery so a dead instance
    // fails fast instead of blocking on the long streaming timeouts above.
    private val healthClient =
        OkHttpClient
            .Builder()
            .dns(TidalDns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

    private val trackCache = ConcurrentHashMap<String, CachedTrack>()
    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val streamFailureCache = ConcurrentHashMap<String, CachedFailure>()

    @Volatile
    private var resolverRateLimitedUntilMs = 0L

    // Runtime health of each instance (keyed by base URL). When an instance fails during
    // resolution it is put on a cooldown and skipped while healthy instances remain, so dead
    // mirrors like a vanished domain do not slow every playback attempt.
    private val instanceCooldownUntilMs = ConcurrentHashMap<String, Long>()

    private fun markInstanceHealthy(baseUrl: String) {
        instanceCooldownUntilMs.remove(baseUrl)
    }

    private fun markInstanceFailed(
        baseUrl: String,
        hardFailure: Boolean,
    ) {
        // Unreachable hosts get a longer cooldown than transient 5xx errors.
        val cooldownMs = if (hardFailure) INSTANCE_HARD_COOLDOWN_MS else INSTANCE_SOFT_COOLDOWN_MS
        instanceCooldownUntilMs[baseUrl] = System.currentTimeMillis() + cooldownMs
    }

    private fun isInstanceCoolingDown(
        baseUrl: String,
        now: Long,
    ): Boolean = (instanceCooldownUntilMs[baseUrl] ?: 0L) > now

    /**
     * Orders endpoints so healthy ones are tried first and instances on cooldown are tried last
     * (only reached if every instance is currently cooling down).
     */
    private fun orderedEndpoints(): List<TidalDownloadEndpoint> {
        val now = System.currentTimeMillis()
        return activeEndpoints.sortedBy { if (isInstanceCoolingDown(it.baseUrl, now)) 1 else 0 }
    }

    fun resolve(
        query: Query,
        cacheDir: File? = null,
        preferAtmos: Boolean = false,
        preferLiveDash: Boolean = true,
        audioQuality: TidalAudioQuality = TidalAudioQuality.AAC_320,
    ): Resolved {
        val now = System.currentTimeMillis()
        val cooldownRemainingMs = resolverRateLimitedUntilMs - now
        if (cooldownRemainingMs > 0L) {
            throw TidalRateLimitedException(cooldownRemainingMs)
        }
        cacheDir?.let(::cleanupTempFiles)
        val directTrackId = query.mediaId.toTidalTrackIdOrNull()
        val trackCacheKey = query.trackCacheKey()
        val tracks = if (directTrackId != null) {
            val directTrack = resolveTrackById(directTrackId) ?: query.toDirectMatchedTrack(directTrackId)
            buildList {
                add(directTrack)
                findCandidateTracks(query)
                    .asSequence()
                    .filterNot { it.trackId == directTrack.trackId }
                    .take(MAX_DIRECT_STREAM_CANDIDATES - 1)
                    .forEach { add(it) }
            }.distinctBy { it.trackId }
        } else {
            val cached =
                trackCache[trackCacheKey]
                ?.takeIf { it.expiresAtMs > now }
                ?.track
            val candidates = findCandidateTracks(query)
            buildList {
                addAll(candidates)
                cached?.let(::add)
            }.distinctBy { it.trackId }
        }

        if (tracks.isEmpty()) {
            throw TidalAudioResolutionException("TIDAL match not found for ${query.title}")
        }

        val errors = mutableListOf<String>()
        var deferredAacFallback: Resolved? = null
        val streamCandidateLimit =
            if (directTrackId != null) MAX_DIRECT_STREAM_CANDIDATES else MAX_STREAM_CANDIDATES
        for (track in tracks.take(streamCandidateLimit)) {
            for (quality in streamQualityCandidates(track, preferAtmos, audioQuality)) {
                val streamCacheKey = "${query.mediaId}::${track.trackId}::$quality::${if (preferLiveDash) "live" else "temp"}"
                val trackFailureCacheKey = "track:${track.trackId}::$quality::${if (preferLiveDash) "live" else "temp"}"
                val cachedStream = streamCache[streamCacheKey]
                    ?.takeIf { it.expiresAtMs > now + 20_000L }
                if (cachedStream != null) {
                    if (cachedStream.losslessDowngradedBitrateKbps == null) {
                        return cachedStream
                    }
                    deferredAacFallback = deferredAacFallback ?: cachedStream
                    errors += "${track.trackId}/$quality: cached AAC fallback"
                    continue
                }

                val cachedFailure = cachedFailureFor(now, streamCacheKey, trackFailureCacheKey)
                if (cachedFailure != null) {
                    errors += "${track.trackId}/$quality: cached resolver failure: ${cachedFailure.message}"
                    continue
                }

                val streamAttempt = runCatching {
                    runBlocking(Dispatchers.IO) {
                        requestDirectFlac(
                            track = track,
                            quality = quality,
                            durationMs = query.durationMs ?: track.durationMs,
                            now = now,
                            cacheDir = cacheDir,
                            preferLiveDash = preferLiveDash,
                            audioQuality = audioQuality,
                        )
                    }
                }.onFailure { error ->
                    Timber.tag("TidalAudio").w(error, "TIDAL $quality stream failed for ${track.trackId}")
                    errors += "${track.trackId}/$quality: ${error.message ?: error.javaClass.simpleName}"
                    cacheStreamFailure(now, error, streamCacheKey, trackFailureCacheKey)
                }

                streamAttempt.getOrNull()?.let { resolved ->
                    streamCache[streamCacheKey] = resolved
                    if (directTrackId == null) {
                        trackCache[trackCacheKey] = CachedTrack(track, now + TRACK_CACHE_MS)
                    }
                    if (resolved.losslessDowngradedBitrateKbps == null) {
                        return resolved
                    }
                    deferredAacFallback = deferredAacFallback ?: resolved
                    errors += "${track.trackId}/$quality: returned AAC fallback"
                }
            }
        }

        deferredAacFallback?.let { return it }

        throw TidalAudioResolutionException(
            "TIDAL FLAC stream not found for ${query.title}: ${errors.joinToString("; ").take(720)}",
        )
    }

    fun invalidate(mediaId: String) {
        val prefix = "$mediaId::"
        for (key in streamCache.keys) {
            if (key.startsWith(prefix)) {
                streamCache.remove(key)?.mediaUri?.deleteIfLocalFileUri()
            }
        }
        for (key in streamFailureCache.keys) {
            if (key.startsWith(prefix)) {
                streamFailureCache.remove(key)
            }
        }
        trackCache.remove(mediaId.trackCacheKeyFallback())
    }

    private fun cachedFailureFor(
        now: Long,
        vararg keys: String,
    ): CachedFailure? {
        for (key in keys.distinct()) {
            val failure = streamFailureCache[key] ?: continue
            if (failure.expiresAtMs > now) return failure
            streamFailureCache.remove(key)
        }
        return null
    }

    private fun cacheStreamFailure(
        now: Long,
        error: Throwable,
        vararg keys: String,
    ) {
        val message = error.message ?: error.javaClass.simpleName
        val expiresAtMs = now + error.streamFailureCacheDurationMs()
        keys
            .distinct()
            .forEach { key ->
                streamFailureCache[key] = CachedFailure(
                    message = message,
                    expiresAtMs = expiresAtMs,
                )
            }
    }

    private fun Throwable.streamFailureCacheDurationMs(): Long =
        when {
            this is TidalRateLimitedException || causeChainContains("rate limited") -> RATE_LIMIT_COOLDOWN_MS
            causeChainContains("did not return FLAC") || causeChainContains("downgraded") -> TRACK_STREAM_FAILURE_CACHE_MS
            else -> STREAM_FAILURE_CACHE_MS
        }

    private fun Throwable.causeChainContains(text: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current.message?.contains(text, ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    fun isTidalTrackId(value: String): Boolean = value.toTidalTrackIdOrNull() != null

    fun searchCandidates(
        query: Query,
        limit: Int = 8,
    ): List<CandidateMetadata> =
        findCandidateTracks(query)
            .take(limit.coerceAtLeast(1))
            .map { track ->
                CandidateMetadata(
                    trackId = track.trackId,
                    title = track.title,
                    artist = track.artistNames.joinToString(", "),
                    album = track.album,
                    isrc = track.isrc,
                    durationMs = track.durationMs,
                )
            }

    fun isLiveManifestUri(value: String?): Boolean =
        value
            ?.let(Uri::parse)
            ?.path
            ?.replace('\\', '/')
            ?.let { path ->
                path.contains("/tidal-temp/") && path.endsWith(".mpd", ignoreCase = true)
            } == true

    fun normalizeIsrc(value: String?): String? {
        val compact = value
            ?.uppercase(Locale.US)
            ?.replace(Regex("[^A-Z0-9]"), "")
            ?: return null
        return Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
            .find(compact)
            ?.value
    }

    private fun findCandidateTracks(query: Query): List<MatchedTrack> {
        val candidates = mutableListOf<ScoredTrack>()
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        normalizeIsrc(query.isrc)?.let { isrc ->
            searchTracks(isrc, exactIsrc = true)
                ?.let { selectCandidateTracks(it, query, exactIsrcOnly = true) }
                ?.losslessFirst()
                ?.firstOrNull()
                ?.takeIf { it.score >= MIN_MATCH_SCORE }
                ?.let { return listOf(it.track) }
        }

        val terms = searchTerms(query)
        for (term in terms) {
            val results = searchTracks(term) ?: continue
            candidates += selectCandidateTracks(results, query)
            candidates.losslessFirst()
                .firstOrNull()
                ?.takeIf { it.score >= STRONG_MATCH_SCORE && it.track.losslessRank() > 0 }
                ?.let { return listOf(it.track) }
        }
        resolveSongLinkTidalTrackId(query)?.let { tidalId ->
            val track = resolveTrackById(tidalId) ?: query.toDirectMatchedTrack(tidalId)
            val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score >= MIN_MATCH_SCORE) {
                if (track.losslessRank() > 0) {
                    return listOf(track)
                }
                candidates += ScoredTrack(track, score)
                Timber.tag("TidalAudio").w("Deferred lossy song.link TIDAL match $tidalId for ${query.title}: score=$score")
            } else {
                Timber.tag("TidalAudio").w("Ignored weak song.link TIDAL match $tidalId for ${query.title}: score=$score")
            }
        }
        return candidates
            .groupBy { it.track.trackId }
            .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
            .losslessFirst()
            .map { it.track }
    }

    private fun searchTracks(
        term: String,
        exactIsrc: Boolean = false,
    ): JSONArray? {
        if (term.isBlank()) return null
        searchTracksFromDirectApi(term, exactIsrc)?.takeIf { it.length() > 0 }?.let { return it }
        return searchTracksFromTidalApi(term)
    }

    private fun searchTracksFromDirectApi(
        term: String,
        exactIsrc: Boolean,
    ): JSONArray? {
        val cacheKey = "direct:${if (exactIsrc) "isrc" else "query"}:${term.lowercase(Locale.US)}"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return it.results }
        val parameter = if (exactIsrc) "i" else "s"
        for (endpoint in orderedEndpoints()) {
            val url =
                endpoint.baseUrl
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("search")
                    .addQueryParameter(parameter, term)
                    .addQueryParameter("limit", SEARCH_LIMIT.toString())
                    .addQueryParameter("offset", "0")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", DOWNLOAD_USER_AGENT)
                    .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("data")?.optJSONArray("items")
                        ?: root.optJSONArray("items")
                }
            }.getOrNull()?.let { results ->
                searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                return results
            }
        }
        return null
    }

    private fun searchTracksFromTidalApi(term: String): JSONArray? {
        val url =
            "$API_BASE_URL/search/tracks"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("countryCode", COUNTRY_CODE)
                .addQueryParameter("locale", LOCALE)
                .addQueryParameter("deviceType", DEVICE_TYPE)
                .addQueryParameter("query", term)
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("offset", "0")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("x-tidal-token", PUBLIC_TOKEN)
                .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).optJSONArray("items")
            }
        }.getOrNull()
    }

    private fun resolveTrackById(trackId: String): MatchedTrack? {
        val url =
            "$API_BASE_URL/tracks/$trackId"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("countryCode", COUNTRY_CODE)
                .addQueryParameter("locale", LOCALE)
                .addQueryParameter("deviceType", DEVICE_TYPE)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("x-tidal-token", PUBLIC_TOKEN)
                .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).toMatchedTrack()
            }
        }.getOrNull()
    }

    private fun selectCandidateTracks(
        results: JSONArray,
        query: Query,
        exactIsrcOnly: Boolean = false,
    ): List<ScoredTrack> {
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val candidates = mutableListOf<ScoredTrack>()
        for (index in 0 until results.length()) {
            val obj = results.optJSONObject(index) ?: continue
            val track = obj.toMatchedTrack() ?: continue
            if (exactIsrcOnly && normalizeIsrc(track.isrc) != wantedIsrc) continue
            val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score >= MIN_MATCH_SCORE) {
                candidates += ScoredTrack(track, score)
            }
        }
        return candidates.losslessFirst()
    }

    private fun List<ScoredTrack>.losslessFirst(): List<ScoredTrack> =
        sortedWith(
            compareByDescending<ScoredTrack> { it.track.losslessRank() }
                .thenByDescending { it.score },
        )

    private fun scoreTrack(
        track: MatchedTrack,
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String,
        wantedIsrc: String?,
        wantedDurationMs: Long?,
    ): Int {
        val candidateTitle = track.title.titleMatchNormalized()
        val candidateArtists = track.artistNames.map { it.normalized() }.filter { it.isNotBlank() }
        val candidateAlbum = track.album.normalized()
        val candidateIsrc = normalizeIsrc(track.isrc)

        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return REJECT_SCORE
        if (wantedIsrc != null && candidateIsrc == wantedIsrc) {
            return if (durationMatches(wantedDurationMs, track.durationMs)) {
                220 + track.qualityScoreBoost()
            } else {
                REJECT_SCORE
            }
        }
        if (hasVersionMismatch(wantedTitle, candidateTitle)) return REJECT_SCORE

        val wantedTitleTokens = significantTokens(wantedTitle)
        val candidateTitleTokens = significantTokens(candidateTitle)
        var score = 0
        when {
            candidateTitle == wantedTitle -> score += 110
            candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> score += 62
            else -> {
                val titleOverlap = tokenOverlap(wantedTitleTokens, candidateTitleTokens)
                if (titleOverlap < 0.50) return REJECT_SCORE
                score += (titleOverlap * 48).roundToInt()
            }
        }
        val extraCandidateTitleTokens = candidateTitleTokens - wantedTitleTokens
        score -= (extraCandidateTitleTokens.size * 6).coerceAtMost(24)

        if (wantedArtists.isNotEmpty() && candidateArtists.isNotEmpty()) {
            val artistHit = wantedArtists.any { wanted ->
                candidateArtists.any { candidate ->
                    candidate == wanted || candidate.contains(wanted) || wanted.contains(candidate)
                }
            }
            if (artistHit) {
                score += 38
            } else {
                return REJECT_SCORE
            }
        }

        if (wantedAlbum.isNotBlank() && candidateAlbum.isNotBlank()) {
            score += when {
                candidateAlbum == wantedAlbum -> 18
                candidateAlbum.contains(wantedAlbum) || wantedAlbum.contains(candidateAlbum) -> 10
                else -> 0
            }
        }

        if (wantedIsrc != null) {
            score += when {
                candidateIsrc == wantedIsrc -> 160
                candidateIsrc != null -> -30
                else -> 0
            }
        }

        val candidateDurationMs = track.durationMs
        if (wantedDurationMs != null && candidateDurationMs != null) {
            val diffSeconds = abs(wantedDurationMs - candidateDurationMs) / 1000L
            if (diffSeconds > 45) return REJECT_SCORE
            score += when {
                diffSeconds <= 3 -> 36
                diffSeconds <= 8 -> 18
                diffSeconds <= 20 -> 4
                else -> -50
            }
        }

        score += track.qualityScoreBoost()

        return score
    }

    private suspend fun requestDirectFlac(
        track: MatchedTrack,
        quality: String,
        durationMs: Long?,
        now: Long,
        cacheDir: File?,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved {
        val isAtmosRequest = quality.equals("DOLBY_ATMOS", ignoreCase = true)
        val manifestFormats = quality.tidalManifestFormats()
        val errors = mutableListOf<String>()
        var rateLimitCount = 0
        var longestRetryAfterMs = 0L
        // Healthy instances first; instances on cooldown are only reached if all are down.
        val endpoints = orderedEndpoints()
        
        // Race all instances concurrently: first to return successfully wins.
        return runBlocking(Dispatchers.IO) {
            val tasks = endpoints.map { endpoint ->
                async {
                    runCatching {
                        requestDirectFlacFromEndpoint(
                            endpoint = endpoint,
                            isAtmosRequest = isAtmosRequest,
                            manifestFormats = manifestFormats,
                            track = track,
                            quality = quality,
                            durationMs = durationMs,
                            now = now,
                            cacheDir = cacheDir,
                            preferLiveDash = preferLiveDash,
                            audioQuality = audioQuality,
                        )
                    }.also { result ->
                        result.getOrNull()?.let {
                            markInstanceHealthy(endpoint.baseUrl)
                            // Remember this track as a health-probe for future instance scans.
                            lastResolvedTrackId = track.trackId
                        }
                        result.exceptionOrNull()?.let { error ->
                            if (error is TidalRateLimitedException) {
                                rateLimitCount += 1
                                longestRetryAfterMs = maxOf(longestRetryAfterMs, error.retryAfterMs)
                            } else {
                                val hardFailure =
                                    error is java.net.UnknownHostException || error is java.net.ConnectException
                                markInstanceFailed(endpoint.baseUrl, hardFailure = hardFailure)
                            }
                            errors += "${endpoint.name}: ${error.message ?: error.javaClass.simpleName}"
                            Timber.tag("TidalAudio").w(error, "TIDAL resolver ${endpoint.name} failed for ${track.trackId}")
                        }
                    }
                }
            }
            
            // Await all tasks and find the first success.
            val results = tasks.awaitAll()
            results.forEach { result ->
                result.getOrNull()?.let { return@runBlocking it }
            }
            
            // All failed; check if rate-limited.
            if (rateLimitCount == endpoints.size && longestRetryAfterMs > 0L) {
                resolverRateLimitedUntilMs = System.currentTimeMillis() + longestRetryAfterMs
                throw TidalRateLimitedException(longestRetryAfterMs)
            }
            throw TidalAudioResolutionException(
                "TIDAL resolver failed on all mirrors for ${track.title}: ${errors.joinToString(" | ").take(720)}",
            )
        }
    }

    private fun requestDirectFlacFromEndpoint(
        endpoint: TidalDownloadEndpoint,
        isAtmosRequest: Boolean,
        manifestFormats: List<String>?,
        track: MatchedTrack,
        quality: String,
        durationMs: Long?,
        now: Long,
        cacheDir: File?,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved {
        val url = if (manifestFormats != null) {
            endpoint.baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("trackManifests")
                .addQueryParameter("id", track.trackId)
                .apply {
                    manifestFormats.forEach { format ->
                        addQueryParameter("formats", format)
                    }
                }
                .addQueryParameter("adaptive", "false")
                .addQueryParameter("manifestType", "MPEG_DASH")
                .addQueryParameter("uriScheme", "HTTPS")
                .addQueryParameter("usage", "PLAYBACK")
                .build()
        } else {
            endpoint.baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("track")
                .addQueryParameter("id", track.trackId)
                .addQueryParameter("quality", quality)
                .build()
        }
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 429) {
                val headerRetryAfterMs = response.header("Retry-After")
                    ?.toLongOrNull()
                    ?.times(1000L)
                    ?.coerceAtLeast(60_000L)
                val bodyRetryAfterMs = runCatching {
                    JSONObject(responseBody)
                        .optLong("retry_after")
                        .takeIf { it > 0L }
                        ?.times(1000L)
                }.getOrNull()
                val retryAfterMs = headerRetryAfterMs
                    ?: bodyRetryAfterMs
                    ?: RATE_LIMIT_COOLDOWN_MS
                throw TidalRateLimitedException(retryAfterMs)
            }
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException(
                    "TIDAL ${endpoint.name} HTTP ${response.code}: ${responseBody.take(180)}",
                )
            }
            val root = JSONObject(responseBody)
            val data = root.optJSONObject("data")
                ?: throw TidalAudioResolutionException("TIDAL FLAC resolver returned no data")
            val effectiveDurationMs = durationMs ?: track.durationMs
            val resolvedQuality: String
            val manifest: ParsedManifest
            val dataSampleRate: Int?
            val dataBitDepth: Int?
            if (manifestFormats != null) {
                val attributes = data
                    .optJSONObject("data")
                    ?.optJSONObject("attributes")
                    ?: throw TidalAudioResolutionException("TIDAL manifest payload missing attributes")
                // Unsubscribed/expired instance accounts return a 30s PREVIEW instead of the full
                // track (trackPresentation=PREVIEW, previewReason=FULL_REQUIRES_SUBSCRIPTION). Reject
                // it so playback falls through to the next instance/source instead of serving a clip.
                val presentation = attributes.stringOrNull("trackPresentation")
                    ?: attributes.stringOrNull("assetPresentation")
                if (presentation.equals("PREVIEW", ignoreCase = true)) {
                    val reason = attributes.stringOrNull("previewReason").orEmpty()
                    throw TidalAudioResolutionException(
                        "TIDAL instance returned a PREVIEW (unsubscribed account)${if (reason.isNotBlank()) ": $reason" else ""}",
                    )
                }
                val formats = attributes.optJSONArray("formats")
                val returnedFormats =
                    formats
                        ?.let { array ->
                            (0 until array.length())
                                .mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
                        }
                        .orEmpty()
                if (returnedFormats.none { returned -> manifestFormats.any { it.equals(returned, ignoreCase = true) } }) {
                    throw TidalAudioResolutionException("TIDAL API did not report ${manifestFormats.joinToString()} for this track")
                }
                val manifestUrl = attributes.stringOrNull("uri")
                    ?: throw TidalAudioResolutionException("TIDAL manifest URI was empty")
                val manifestText = fetchText(manifestUrl)
                manifest = parseManifestText(manifestText, "audio/mp4")
                resolvedQuality = returnedFormats.toResolvedTidalQuality(quality)
                dataSampleRate = manifest.sampleRate ?: 48_000
                dataBitDepth = null
            } else {
                if (data.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    throw TidalAudioResolutionException("TIDAL FLAC resolver returned a preview asset")
                }
                val manifestPayload = data.stringOrNull("manifest")
                    ?: throw TidalAudioResolutionException("TIDAL FLAC resolver returned no manifest")
                resolvedQuality = data.stringOrNull("audioQuality") ?: quality
                manifest = parseManifest(manifestPayload, data.stringOrNull("manifestMimeType"), effectiveDurationMs)
                dataSampleRate = normalizeSampleRate(data.doubleOrNull("sampleRate"))
                dataBitDepth = data.longOrNull("bitDepth")?.toInt()
            }
            val manifestDeclaresFlac =
                manifest.mimeType.contains("flac", ignoreCase = true) ||
                    manifest.codecs.contains("flac", ignoreCase = true)
            val manifestLooksFlac =
                manifestDeclaresFlac
            val isAtmosManifest =
                isAtmosRequest ||
                    resolvedQuality.contains("ATMOS", ignoreCase = true) ||
                    manifest.codecs.contains("eac3", ignoreCase = true) ||
                    manifest.codecs.contains("ec-3", ignoreCase = true)
            val returnedHiRes =
                resolvedQuality.contains("HI_RES", ignoreCase = true) ||
                    resolvedQuality.contains("HIRES", ignoreCase = true) ||
                    resolvedQuality.contains("MAX", ignoreCase = true) ||
                    (dataBitDepth ?: 0) >= 24 ||
                    (dataSampleRate ?: manifest.sampleRate ?: 0) > 48_000
            if (
                audioQuality == TidalAudioQuality.HI_RES_LOSSLESS &&
                quality.isLosslessQuality() &&
                !returnedHiRes
            ) {
                throw TidalAudioResolutionException(
                    "TIDAL HiRes was unavailable for ${track.title}; resolver returned ${resolvedQuality.ifBlank { quality }}",
                )
            }

            val localFile = if (manifest.isDash && manifestLooksFlac && cacheDir != null) {
                downloadDashFlacToTempFile(track.trackId, quality, manifest, cacheDir)
            } else if (!preferLiveDash && manifest.isDash && cacheDir != null) {
                downloadManifestToTempFile(track.trackId, quality, manifest, cacheDir)
            } else {
                null
            }
            val liveManifestFile = if (localFile == null && preferLiveDash && manifest.isDash && cacheDir != null) {
                writeDashManifestToTempFile(track, quality, manifest, cacheDir)
            } else {
                null
            }
            val streamUri = liveManifestFile
                ?.let { Uri.fromFile(it).toString() }
                ?: localFile?.let { Uri.fromFile(it).toString() }
                ?: manifest.url
            val playbackStreamInfo = localFile
                ?.let { inspectLocalPlaybackFile(it, manifest) }
                ?: if (liveManifestFile == null && !manifest.isDash) {
                    inspectRemotePlaybackUrl(manifest.url, manifest)
                } else {
                    null
                }
            val resolvedCodecs = playbackStreamInfo?.codecs
                ?: manifest.codecs.takeIf { it.isNotBlank() }
                ?: when {
                    manifestLooksFlac -> "flac"
                    isAtmosManifest -> "ec-3"
                    else -> "mp4a.40.2"
                }
            val playbackMimeType = if (liveManifestFile != null) {
                DASH_MIME_TYPE
            } else {
                playbackStreamInfo?.mimeType ?: manifest.mimeType.coerceDirectPlaybackMimeType(manifest.url)
            }
            val isResolvedFlacLike =
                playbackMimeType.contains("flac", ignoreCase = true) ||
                    resolvedCodecs.contains("flac", ignoreCase = true)
            val isLosslessDowngrade = quality.isLosslessQuality() && !isResolvedFlacLike && !isAtmosManifest
            if (quality.equals("HI_RES_LOSSLESS", ignoreCase = true) && isLosslessDowngrade) {
                throw TidalAudioResolutionException(
                    "TIDAL $quality did not return FLAC (${playbackMimeType}, ${resolvedCodecs})",
                )
            }
            val streamMetadata = when {
                liveManifestFile != null -> StreamMetadata(playbackMimeType, null)
                playbackStreamInfo != null -> StreamMetadata(
                    playbackStreamInfo.mimeType,
                    playbackStreamInfo.contentLength,
                )
                manifest.isDash -> throw TidalAudioResolutionException("TIDAL DASH manifest needs live DASH playback")
                else -> fetchStreamMetadata(manifest.url)
            }
            val sampleRate = dataSampleRate
                ?: manifest.sampleRate
                ?: normalizeSampleRate(track.sampleRate?.toDouble())
            val bitrate = manifest.bitrate ?: estimateBitrate(streamMetadata.contentLength, effectiveDurationMs)
            validateLikelyFullTrack(track, effectiveDurationMs, streamMetadata.contentLength)
            val resolvedLabel = when {
                isAtmosManifest -> "TIDAL Atmos"
                isLosslessDowngrade -> "TIDAL AAC"
                resolvedQuality.contains("HI_RES", ignoreCase = true) -> "TIDAL HiRes FLAC"
                quality.isLossyQuality() || resolvedQuality.equals("HIGH", ignoreCase = true) -> "TIDAL AAC"
                else -> "TIDAL FLAC"
            }
            val losslessDowngradedBitrateKbps =
                if (isLosslessDowngrade) ((bitrate ?: 320_000) / 1000).coerceAtLeast(1) else null
            Timber.tag("TidalAudio").i(
                "Resolved TIDAL ${resolvedQuality.ifBlank { quality }} stream for ${track.trackId}: label=$resolvedLabel, local=${localFile != null}, dash=${manifest.isDash}, bitrate=$bitrate, sampleRate=$sampleRate",
            )

            return Resolved(
                mediaUri = streamUri,
                trackId = track.trackId,
                label = resolvedLabel,
                mimeType = playbackMimeType,
                codecs = resolvedCodecs,
                bitrate = bitrate ?: if (isLosslessDowngrade) 320_000 else 0,
                sampleRate = sampleRate,
                contentLength = streamMetadata.contentLength,
                expiresAtMs = (localFile ?: liveManifestFile)?.let { now + STREAM_CACHE_MS }
                    ?: extractExpiryMs(manifest.expiryUrl ?: manifest.url, now),
                losslessDowngradedBitrateKbps = losslessDowngradedBitrateKbps,
                isLiveManifest = liveManifestFile != null,
            )
        }
    }

    private fun streamQualityCandidates(
        track: MatchedTrack,
        preferAtmos: Boolean,
        audioQuality: TidalAudioQuality,
    ): List<String> =
        buildList {
            if (preferAtmos && audioQuality != TidalAudioQuality.AAC_320) {
                add("DOLBY_ATMOS")
            }
            when (audioQuality) {
                TidalAudioQuality.AAC_320 -> add("HIGH")
                TidalAudioQuality.FLAC -> {
                    if (track.supportsHiResLossless() || track.supportsLossless()) {
                        add("LOSSLESS")
                    }
                    add("HIGH")
                }
                TidalAudioQuality.HI_RES_LOSSLESS -> {
                    if (track.supportsHiResLossless()) {
                        add("HI_RES_LOSSLESS")
                    }
                    if (track.supportsHiResLossless() || track.supportsLossless()) {
                        add("LOSSLESS")
                    }
                    add("HIGH")
                }
            }
        }.distinct()

    private fun String.tidalManifestFormats(): List<String>? =
        when {
            equals("DOLBY_ATMOS", ignoreCase = true) -> listOf("EAC3_JOC")
            equals("HI_RES_LOSSLESS", ignoreCase = true) ||
                equals("HI_RES", ignoreCase = true) -> listOf("FLAC_HIRES")
            equals("LOSSLESS", ignoreCase = true) -> listOf("FLAC")
            else -> null
        }

    private fun List<String>.toResolvedTidalQuality(requestedQuality: String): String =
        when {
            any { it.equals("EAC3_JOC", ignoreCase = true) } -> "DOLBY_ATMOS"
            any { it.equals("FLAC_HIRES", ignoreCase = true) } -> "HI_RES_LOSSLESS"
            any { it.equals("FLAC", ignoreCase = true) } -> "LOSSLESS"
            else -> requestedQuality
        }

    private fun String.isLosslessQuality(): Boolean =
        equals("LOSSLESS", ignoreCase = true) ||
            equals("HI_RES_LOSSLESS", ignoreCase = true) ||
            equals("HI_RES", ignoreCase = true)

    private fun String.isLossyQuality(): Boolean =
        equals("HIGH", ignoreCase = true) ||
            equals("LOW", ignoreCase = true)

    /**
     * Resolves a directly-playable [DirectStream] from an official-API `playbackinfopostpaywall`
     * manifest, reusing the same BTS/DASH handling as the public-instance path. This is what makes
     * the signed-in account path work for lossless/HiRes: Tidal returns a segmented DASH manifest
     * there (not a BTS direct URL), so the segments are stitched into a temp FLAC in [cacheDir].
     *
     * Returns null if the manifest can't be turned into a playable stream, so the caller can fall
     * back to the public instances and then YouTube.
     */
    fun resolveAccountManifest(
        manifestB64: String,
        declaredMimeType: String?,
        trackId: String,
        quality: String,
        durationMs: Long?,
        cacheDir: File,
    ): DirectStream? =
        runCatching {
            val manifest = parseManifest(manifestB64, declaredMimeType, durationMs)
            if (!manifest.isDash) {
                // BTS manifest: a single directly-playable URL (AAC, or a single-file FLAC).
                return@runCatching DirectStream(
                    uri = manifest.url,
                    mimeType = manifest.mimeType,
                    codecs = manifest.codecs,
                    contentLength = null,
                    label = "account $quality",
                    source = AudioSourceType.TIDAL,
                )
            }
            // Segmented DASH (typical for lossless/HiRes): stitch to a local temp file so ExoPlayer
            // can play it as a single progressive stream (the DataSpec path can't do multi-segment).
            val looksFlac =
                manifest.mimeType.contains("flac", ignoreCase = true) ||
                    manifest.codecs.contains("flac", ignoreCase = true)
            val localFile =
                if (looksFlac && manifest.segmentUrls.size >= 2) {
                    downloadDashFlacToTempFile(trackId, quality, manifest, cacheDir)
                } else {
                    downloadManifestToTempFile(trackId, quality, manifest, cacheDir)
                }
            val isFlacOut = localFile.extension.equals("flac", ignoreCase = true)
            DirectStream(
                uri = Uri.fromFile(localFile).toString(),
                mimeType = if (isFlacOut) AUDIO_FLAC_MIME_TYPE else manifest.mimeType,
                codecs = if (isFlacOut) "flac" else manifest.codecs,
                contentLength = localFile.length().takeIf { it > 0L },
                label = "account $quality",
                source = AudioSourceType.TIDAL,
            )
        }.onFailure {
            Timber.tag("TidalAudioProvider").w(it, "account manifest resolve failed")
        }.getOrNull()

    private fun parseManifest(
        manifestB64: String,
        declaredMimeType: String?,
        durationMs: Long?,
    ): ParsedManifest {
        val text =
            String(
                Base64.decode(manifestB64.trim(), Base64.DEFAULT),
                Charsets.UTF_8,
            ).trim()
        return parseManifestText(text, declaredMimeType, durationMs)
    }

    private fun parseManifestText(
        text: String,
        declaredMimeType: String?,
        durationMs: Long? = null,
    ): ParsedManifest {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            throw TidalAudioResolutionException("TIDAL FLAC manifest was empty")
        }
        if (cleanText.startsWith("{")) {
            val root = JSONObject(cleanText)
            val urls = root.optJSONArray("urls")
                ?: throw TidalAudioResolutionException("TIDAL FLAC manifest had no URLs")
            val directUrl = urls.firstStringOrNull()
                ?: throw TidalAudioResolutionException("TIDAL FLAC manifest URL was empty")
            val rawMimeType = root.stringOrNull("mimeType") ?: AUDIO_FLAC_MIME_TYPE
            val codecs = root.stringOrNull("codecs") ?: if (rawMimeType.contains("flac", ignoreCase = true)) "flac" else "mp4a.40.2"
            val mimeType = rawMimeType.coerceDirectPlaybackMimeType(directUrl)
            val sampleRate = normalizeSampleRate(root.doubleOrNull("sampleRate"))
            val bitrate = root.longOrNull("bitrate")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
                ?: root.longOrNull("bandwidth")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
            return ParsedManifest(
                url = directUrl,
                mimeType = mimeType,
                codecs = codecs,
                sampleRate = sampleRate,
                bitrate = bitrate,
                expiryUrl = directUrl,
                isDash = false,
                outputExtension = if (mimeType.contains("flac", ignoreCase = true) || codecs.contains("flac", ignoreCase = true)) {
                    ".flac"
                } else {
                    ".m4a"
                },
            )
        }
        if (cleanText.contains("<MPD", ignoreCase = true)) {
            return parseDashManifest(cleanText.sanitizeXmlEntities(), declaredMimeType)
        }
        throw TidalAudioResolutionException("TIDAL FLAC manifest format was not recognized")
    }

    private fun parseDashManifest(
        text: String,
        declaredMimeType: String?,
    ): ParsedManifest {
        if (
            !text.contains("<SegmentTemplate", ignoreCase = true) &&
            !text.contains("<SegmentList", ignoreCase = true) &&
            !text.contains("<SegmentBase", ignoreCase = true) &&
            !text.contains("<BaseURL", ignoreCase = true)
        ) {
            throw TidalAudioResolutionException("TIDAL DASH manifest had no playable segments")
        }
        val codecs = firstXmlAttr(text, "codecs").ifBlank { "flac" }
        val representationMime = firstXmlAttr(text, "mimeType")
        val sampleRate = firstXmlAttr(text, "audioSamplingRate").toIntOrNull()
        val bandwidth = xmlAttrValues(text, "bandwidth").maxOrNull()
        val segmentUrls = extractDashSegmentUrls(text)
        val firstSegmentUrl = Regex("""https?://[^"'<>\s]+""")
            .find(text)
            ?.value
            ?.xmlUnescape()
        return ParsedManifest(
            url = "",
            mimeType = representationMime.ifBlank { declaredMimeType ?: "audio/mp4" },
            codecs = codecs,
            sampleRate = sampleRate,
            bitrate = bandwidth,
            expiryUrl = firstSegmentUrl,
            isDash = true,
            segmentUrls = segmentUrls,
            outputExtension = ".m4a",
            manifestText = text,
        )
    }

    private fun String.coerceDirectPlaybackMimeType(url: String): String {
        if (!contains("dash", ignoreCase = true)) return this
        return when {
            url.endsWith(".flac", ignoreCase = true) -> AUDIO_FLAC_MIME_TYPE
            else -> AUDIO_MP4_MIME_TYPE
        }
    }

    private fun extractDashSegmentUrls(text: String): List<String> {
        extractSegmentListUrls(text).takeIf { it.isNotEmpty() }?.let { return it }

        val baseUrl = firstXmlTagValue(text, "BaseURL").xmlUnescape()
        val init = firstXmlAttr(text, "initialization").xmlUnescape()
        val mediaTemplate = firstXmlAttr(text, "media").xmlUnescape()
        if (init.isBlank() || mediaTemplate.isBlank()) {
            val directBase = baseUrl.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            if (directBase != null) return listOf(directBase)
            throw TidalAudioResolutionException("TIDAL DASH manifest had no initialization/media templates")
        }

        var segmentCount = 0
        Regex("""<S\s+[^>]*d="(\d+)"(?:\s+r="(-?\d+)")?[^>]*/?>""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { match ->
                val repeat = match.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
                segmentCount += repeat + 1
            }
        if (segmentCount <= 0) {
            throw TidalAudioResolutionException("TIDAL DASH manifest listed no media segments")
        }

        return buildList {
            add(resolveDashUrl(baseUrl, init))
            for (index in 1..segmentCount) {
                add(resolveDashUrl(baseUrl, mediaTemplate.replace("\$Number\$", index.toString())))
            }
        }
    }

    private fun extractSegmentListUrls(text: String): List<String> {
        val baseUrl = firstXmlTagValue(text, "BaseURL").xmlUnescape()
        val initialization =
            Regex("<Initialization\\b[^>]*sourceURL=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.xmlUnescape()
        val mediaSegments =
            Regex("<SegmentURL\\b[^>]*media=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .findAll(text)
                .mapNotNull { it.groupValues.getOrNull(1)?.xmlUnescape() }
                .toList()
        return buildList {
            initialization?.let { add(resolveDashUrl(baseUrl, it)) }
            mediaSegments.forEach { add(resolveDashUrl(baseUrl, it)) }
        }
    }

    private fun resolveDashUrl(
        baseUrl: String,
        path: String,
    ): String {
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return path
        }
        if (baseUrl.isBlank()) return path
        val absoluteBase = baseUrl.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return path
        return absoluteBase.toHttpUrlOrNull()
            ?.resolve(path)
            ?.toString()
            ?: absoluteBase.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun downloadDashFlacToTempFile(
        trackId: String,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val urls = manifest.segmentUrls
        if (urls.size < 2 || urls.any { it.isBlank() }) {
            throw TidalAudioResolutionException("TIDAL HiRes DASH manifest did not list FLAC segments")
        }

        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "$trackId-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}.flac",
        )

        try {
            val initBytes = downloadSegmentBytes(urls.first(), 1, urls.size)
            val flacMetadataBlocks = extractFlacMetadataBlocks(initBytes)
            outputFile.outputStream().buffered().use { out ->
                out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                out.write(flacMetadataBlocks)
                urls.drop(1).forEachIndexed { index, url ->
                    val segmentBytes = downloadSegmentBytes(url, index + 2, urls.size)
                    writeMdatPayloads(segmentBytes, out)
                }
            }
        } catch (error: Throwable) {
            outputFile.delete()
            if (error is TidalAudioResolutionException) throw error
            throw TidalAudioResolutionException("TIDAL HiRes FLAC remux failed: ${error.message}", error)
        }

        if (outputFile.length() <= 42L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL HiRes FLAC remux produced an empty file")
        }
        return outputFile
    }

    private fun downloadSegmentBytes(
        url: String,
        index: Int,
        total: Int,
    ): ByteArray {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL segment $index/$total HTTP ${response.code}")
            }
            return response.body.bytes()
        }
    }

    private fun extractFlacMetadataBlocks(initBytes: ByteArray): ByteArray {
        val typeOffset = initBytes.indexOfAscii("dfLa")
        if (typeOffset < 4) {
            throw TidalAudioResolutionException("TIDAL HiRes init segment had no FLAC metadata")
        }
        val boxStart = typeOffset - 4
        val boxSize = initBytes.readMp4BoxSize(boxStart)
        val boxEnd = boxStart + boxSize
        val payloadStart = typeOffset + 8
        if (boxSize < 16 || payloadStart + 4 > boxEnd || boxEnd > initBytes.size) {
            throw TidalAudioResolutionException("TIDAL HiRes FLAC metadata box was invalid")
        }
        val metadataBlocks = initBytes.copyOfRange(payloadStart, boxEnd)
        val firstBlockType = metadataBlocks[0].toInt() and 0x7f
        val firstBlockLength =
            ((metadataBlocks[1].toInt() and 0xff) shl 16) or
                ((metadataBlocks[2].toInt() and 0xff) shl 8) or
                (metadataBlocks[3].toInt() and 0xff)
        if (firstBlockType != 0 || firstBlockLength != 34 || metadataBlocks.size < 4 + firstBlockLength) {
            throw TidalAudioResolutionException("TIDAL HiRes FLAC STREAMINFO was invalid")
        }
        return metadataBlocks
    }

    private fun writeMdatPayloads(
        bytes: ByteArray,
        out: OutputStream,
    ) {
        var foundMdat = false
        var offset = 0
        while (offset + MP4_BOX_HEADER_SIZE <= bytes.size) {
            val size = bytes.readMp4BoxSize(offset)
            val type = bytes.readMp4BoxType(offset + 4)
            val headerSize = if (bytes.readUInt32(offset) == 1L) MP4_EXTENDED_BOX_HEADER_SIZE else MP4_BOX_HEADER_SIZE
            val boxEnd = offset + size
            if (size < headerSize || boxEnd > bytes.size) {
                throw TidalAudioResolutionException("TIDAL HiRes segment had an invalid MP4 box")
            }
            if (type == "mdat") {
                out.write(bytes, offset + headerSize, size - headerSize)
                foundMdat = true
            }
            offset = boxEnd
        }
        if (!foundMdat) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had no audio payload")
        }
    }

    private fun ByteArray.indexOfAscii(value: String): Int {
        val needle = value.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return -1
        for (index in 0..size - needle.size) {
            var matches = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }

    private fun ByteArray.readMp4BoxSize(offset: Int): Int {
        val size32 = readUInt32(offset)
        val size = when (size32) {
            0L -> this.size.toLong() - offset.toLong()
            1L -> readUInt64(offset + 8)
            else -> size32
        }
        if (size <= 0L || size > Int.MAX_VALUE) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had an invalid MP4 box size")
        }
        return size.toInt()
    }

    private fun ByteArray.readUInt32(offset: Int): Long {
        if (offset < 0 || offset + 4 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated MP4 box")
        }
        return ((this[offset].toLong() and 0xffL) shl 24) or
            ((this[offset + 1].toLong() and 0xffL) shl 16) or
            ((this[offset + 2].toLong() and 0xffL) shl 8) or
            (this[offset + 3].toLong() and 0xffL)
    }

    private fun ByteArray.readUInt64(offset: Int): Long {
        if (offset < 0 || offset + 8 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated large MP4 box")
        }
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (this[offset + index].toLong() and 0xffL)
        }
        return value
    }

    private fun ByteArray.readMp4BoxType(offset: Int): String {
        if (offset < 0 || offset + 4 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated MP4 type")
        }
        return String(this, offset, 4, Charsets.US_ASCII)
    }

    private fun downloadManifestToTempFile(
        trackId: String,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "$trackId-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}${manifest.outputExtension}",
        )
        val urls = if (manifest.isDash) manifest.segmentUrls else listOf(manifest.url)
        if (urls.any { it.isBlank() }) {
            throw TidalAudioResolutionException("TIDAL manifest contained an empty URL")
        }

        try {
            outputFile.outputStream().buffered().use { out ->
                urls.forEachIndexed { index, url ->
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                            .header("Accept-Encoding", "identity")
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TidalAudioResolutionException(
                                "TIDAL segment ${index + 1}/${urls.size} HTTP ${response.code}",
                            )
                        }
                        response.body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            outputFile.delete()
            if (error is TidalAudioResolutionException) throw error
            throw TidalAudioResolutionException("TIDAL temporary download failed: ${error.message}", error)
        }

        if (outputFile.length() <= 0L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL temporary download produced an empty file")
        }
        return outputFile
    }

    private fun inspectLocalPlaybackFile(
        file: File,
        manifest: ParsedManifest,
    ): PlaybackStreamInfo {
        val length = file.length()
        if (length <= 0L) {
            file.delete()
            throw TidalAudioResolutionException("TIDAL temporary file was empty")
        }

        val header = ByteArray(16)
        val bytesRead = file.inputStream().use { input -> input.read(header) }
        val isFlac =
            bytesRead >= 4 &&
                header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte()
        val isMp4 =
            bytesRead >= 12 &&
                header[4] == 'f'.code.toByte() &&
                header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() &&
                header[7] == 'p'.code.toByte()

        if (isFlac) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_FLAC_MIME_TYPE,
                codecs = "flac",
                contentLength = length,
            )
        }
        if (isMp4) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_MP4_MIME_TYPE,
                codecs = manifest.codecs.takeIf { it.isNotBlank() } ?: "mp4a.40.2",
                contentLength = length,
            )
        }

        val headerText = header
            .take(bytesRead.coerceAtLeast(0))
            .map { byte ->
                val value = byte.toInt() and 0xff
                if (value in 32..126) value.toChar() else '.'
            }
            .joinToString("")
            .trim()
        file.delete()
        throw TidalAudioResolutionException(
            "TIDAL temporary file was not a playable FLAC/MP4 container (${headerText.ifBlank { "binary header" }})",
        )
    }

    private fun inspectRemotePlaybackUrl(
        url: String,
        manifest: ParsedManifest,
    ): PlaybackStreamInfo {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-15")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL stream probe HTTP ${response.code}")
            }
            val header = ByteArray(16)
            val bytesRead = response.body.byteStream().use { input -> input.read(header) }
            val contentLength =
                response.header("Content-Range")
                    ?.substringAfterLast('/', missingDelimiterValue = "")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L && response.code != 206 }

            return inspectPlaybackHeader(
                bytes = header,
                bytesRead = bytesRead,
                manifest = manifest,
                contentLength = contentLength,
                label = "TIDAL stream",
            )
        }
    }

    private fun inspectPlaybackHeader(
        bytes: ByteArray,
        bytesRead: Int,
        manifest: ParsedManifest,
        contentLength: Long?,
        label: String,
    ): PlaybackStreamInfo {
        val isFlac =
            bytesRead >= 4 &&
                bytes[0] == 'f'.code.toByte() &&
                bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() &&
                bytes[3] == 'C'.code.toByte()
        val isMp4 =
            bytesRead >= 12 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()

        if (isFlac) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_FLAC_MIME_TYPE,
                codecs = "flac",
                contentLength = contentLength,
            )
        }
        if (isMp4) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_MP4_MIME_TYPE,
                codecs = manifest.codecs.takeIf { it.isNotBlank() } ?: "mp4a.40.2",
                contentLength = contentLength,
            )
        }

        val headerText = bytes
            .take(bytesRead.coerceAtLeast(0))
            .map { byte ->
                val value = byte.toInt() and 0xff
                if (value in 32..126) value.toChar() else '.'
            }
            .joinToString("")
            .trim()
        throw TidalAudioResolutionException(
            "$label was not a playable FLAC/MP4 container (${headerText.ifBlank { "binary header" }})",
        )
    }

    private fun writeDashManifestToTempFile(
        track: MatchedTrack,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val manifestText = manifest.manifestText
            ?: throw TidalAudioResolutionException("TIDAL live DASH manifest text was missing")
        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "${track.trackId}-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}.mpd",
        )
        runCatching {
            outputFile.writeText(manifestText, Charsets.UTF_8)
        }.onFailure { error ->
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL live manifest write failed: ${error.message}", error)
        }
        if (outputFile.length() <= 0L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL live manifest file was empty")
        }
        return outputFile
    }

    private fun cleanupTempFiles(cacheDir: File) {
        val tidalDir = File(cacheDir, "tidal-temp")
        if (!tidalDir.exists()) return
        val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        tidalDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun fetchStreamMetadata(url: String): StreamMetadata {
        val httpUrl = url.toHttpUrlOrNull() ?: return StreamMetadata("audio/flac", null)
        val builder =
            Request
                .Builder()
                .url(httpUrl)
                .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", BROWSER_USER_AGENT)

        runCatching {
            client.newCall(builder.head().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                StreamMetadata(
                    mimeType = response.header("Content-Type")?.substringBefore(';'),
                    contentLength = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L },
                )
            }
        }.getOrNull()?.let { return it }

        return runCatching {
            client.newCall(
                builder
                    .get()
                    .header("Range", "bytes=0-0")
                    .build(),
            ).execute().use { response ->
                StreamMetadata(
                    mimeType = response.header("Content-Type")?.substringBefore(';'),
                    contentLength =
                        response.header("Content-Range")
                            ?.substringAfterLast('/', missingDelimiterValue = "")
                            ?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L && response.code == 206 },
                )
            }
        }.getOrElse {
            StreamMetadata("audio/flac", null)
        }
    }

    private fun fetchText(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/dash+xml,application/xml,text/xml,*/*;q=0.8")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL manifest fetch HTTP ${response.code}: ${body.take(180)}")
            }
            return body.takeIf { it.isNotBlank() }
                ?: throw TidalAudioResolutionException("TIDAL manifest fetch returned an empty body")
        }
    }

    private fun validateLikelyFullTrack(
        track: MatchedTrack,
        durationMs: Long?,
        contentLength: Long?,
    ) {
        val duration = durationMs?.takeIf { it >= 60_000L } ?: return
        val length = contentLength?.takeIf { it > 0L } ?: return
        val minimumBytesForFullTrack = (duration / 1000.0 * 96_000.0 / 8.0 * 0.70).toLong()
        if (length < minimumBytesForFullTrack) {
            throw TidalAudioResolutionException(
                "TIDAL returned a likely preview for ${track.title}: $length bytes for ${duration / 1000L}s",
            )
        }
    }

    private fun searchTerms(query: Query): List<String> =
        buildList {
            val title = query.title.searchQueryTitle()
            val primaryArtist = query.artists
                .firstOrNull { it.isNotBlank() }
                ?.searchQueryArtist()
                .orEmpty()
            val titleAndArtist = listOf(title, primaryArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            add(titleAndArtist)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Query.toDirectMatchedTrack(trackId: String): MatchedTrack =
        MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = isrc,
            durationMs = durationMs,
            audioQuality = null,
            mediaTags = emptySet(),
            audioModes = emptySet(),
            sampleRate = null,
        )

    private fun resolveSongLinkTidalTrackId(query: Query): String? {
        for (sourceUrl in songLinkSourceUrls(query.mediaId)) {
            val url =
                SONG_LINK_API_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("url", sourceUrl)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("linksByPlatform")
                        ?.optJSONObject("tidal")
                        ?.stringOrNull("url")
                        ?.toTidalTrackIdOrNull()
                        ?: root.optJSONObject("songUrls")
                            ?.stringOrNull("Tidal")
                            ?.toTidalTrackIdOrNull()
                }
            }.getOrNull()?.let { trackId ->
                Timber.tag("TidalAudio").i("Resolved TIDAL track $trackId through song.link from $sourceUrl")
                return trackId
            }
        }
        return null
    }

    private fun songLinkSourceUrls(mediaId: String): List<String> {
        val trimmed = mediaId.trim()
        if (trimmed.isBlank()) return emptyList()
        return buildList {
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                add(trimmed)
            }
            Regex("""spotify[:/](?:track[:/])?([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { add("https://open.spotify.com/track/$it") }
            if (trimmed.matches(Regex("[A-Za-z0-9]{22}"))) {
                add("https://open.spotify.com/track/$trimmed")
            }
            if (trimmed.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                add("https://music.youtube.com/watch?v=$trimmed")
            }
        }.distinct()
    }

    private fun estimateBitrate(
        contentLength: Long?,
        durationMs: Long?,
    ): Int? {
        val length = contentLength?.takeIf { it > 0L } ?: return null
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        val bitrate = (length * 8L * 1000L) / duration
        return bitrate
            .takeIf { it in 32_000L..20_000_000L }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    }

    private fun normalizeSampleRate(value: Double?): Int? {
        val sampleRate = value?.takeIf { it > 0.0 } ?: return null
        return when {
            sampleRate < 1000.0 -> (sampleRate * 1000.0).roundToInt()
            else -> sampleRate.roundToInt()
        }.takeIf { it > 0 }
    }

    private fun extractExpiryMs(
        url: String,
        now: Long,
    ): Long {
        val httpUrl = url.toHttpUrlOrNull() ?: return now + STREAM_CACHE_MS
        val expiresSeconds = httpUrl.queryParameterIgnoreCase("X-Amz-Expires")?.toLongOrNull()
        val date = httpUrl.queryParameterIgnoreCase("X-Amz-Date")
        if (expiresSeconds != null && date != null) {
            runCatching {
                val issuedAt = LocalDateTime.parse(date, AMAZON_DATE).toInstant(ZoneOffset.UTC).toEpochMilli()
                return (issuedAt + expiresSeconds * 1000L - 30_000L).coerceAtLeast(now + 60_000L)
            }
        }
        return now + STREAM_CACHE_MS
    }

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        val trackId = stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: stringOrNull("name") ?: return null
        val artists = collectArtistNames()
        val album = optJSONObject("album")?.stringOrNull("title") ?: optJSONObject("album")?.stringOrNull("name")
        return MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = stringOrNull("isrc"),
            durationMs = longOrNull("duration")?.takeIf { it > 0L }?.times(1000L),
            audioQuality = stringOrNull("audioQuality"),
            mediaTags = collectMediaTags(),
            audioModes = collectAudioModes(),
            sampleRate = normalizeSampleRate(doubleOrNull("sampleRate")),
        )
    }

    private fun JSONObject.collectArtistNames(): List<String> {
        val names = mutableListOf<String>()
        optJSONArray("artists")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
        }
        optJSONObject("artist")?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        return names.distinct()
    }

    private fun JSONObject.collectMediaTags(): Set<String> {
        val tags = mutableSetOf<String>()
        optJSONObject("mediaMetadata")
            ?.optJSONArray("tags")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(tags::add)
                }
            }
        optJSONArray("mediaMetadataTags")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(tags::add)
                }
            }
        return tags
    }

    private fun JSONObject.collectAudioModes(): Set<String> {
        val modes = mutableSetOf<String>()
        stringOrNull("audioMode")?.let(modes::add)
        optJSONArray("audioModes")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(modes::add)
                }
            }
        optJSONObject("mediaMetadata")
            ?.optJSONArray("audioModes")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(modes::add)
                }
            }
        return modes
    }

    private fun String.toTidalTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        Regex("""^tidal:track:(\d+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        Regex("""tidal\.com/(?:browse/)?track/(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return null
    }

    private fun String.trackCacheKeyFallback(): String = "direct:${trim().lowercase(Locale.US)}"

    private fun Query.trackCacheKey(): String =
        mediaId.toTidalTrackIdOrNull()
            ?.let { "direct:$it" }
            ?: listOf(
                title.normalized(),
                artists.joinToString(",") { it.normalized() },
                album.normalized(),
                durationMs?.div(1000L)?.toString().orEmpty(),
            ).joinToString("::")

    private fun String?.normalized(): String =
        this
            ?.lowercase(Locale.US)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{Mn}+"), "")
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            .orEmpty()

    private fun String.titleMatchNormalized(): String =
        normalized()
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), "")
            .replace(Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.searchQueryTitle(): String =
        trim()
            .replace(Regex("""\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.searchQueryArtist(): String =
        trim()
            .substringBefore(',')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun significantTokens(value: String): Set<String> =
        value
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    private fun tokenOverlap(
        wanted: Set<String>,
        candidate: Set<String>,
    ): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.0
        val shared = wanted.intersect(candidate).size
        return shared.toDouble() / wanted.size.coerceAtLeast(candidate.size).toDouble()
    }

    private fun durationMatches(
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Boolean {
        if (wantedDurationMs == null || candidateDurationMs == null) return true
        return abs(wantedDurationMs - candidateDurationMs) <= 45_000L
    }

    private fun hasVersionMismatch(
        wanted: String,
        candidate: String,
    ): Boolean {
        val wantedLive = wanted.contains(" live ")
        val candidateLive = candidate.contains(" live ")
        if (wantedLive != candidateLive) return true
        val wantedInstrumental = wanted.contains(" instrumental ")
        val candidateInstrumental = candidate.contains(" instrumental ")
        if (wantedInstrumental != candidateInstrumental) return true
        return VERSION_TOKENS.any { token ->
            wanted.hasToken(token) != candidate.hasToken(token)
        }
    }

    private fun String.hasToken(token: String): Boolean =
        split(' ').any { it == token }

    private fun JSONArray.firstStringOrNull(): String? {
        for (index in 0 until length()) {
            val value = optString(index).takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key)) optLong(key).takeIf { it > 0L } else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeIf { it > 0.0 && !it.isNaN() } else null

    private fun HttpUrl.queryParameterIgnoreCase(name: String): String? {
        val key = queryParameterNames.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return queryParameter(key)
    }

    private fun firstXmlAttr(
        text: String,
        attr: String,
    ): String =
        Regex("""\b${Regex.escape(attr)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private fun xmlAttrValues(
        text: String,
        attr: String,
    ): List<Int> =
        Regex("""\b${Regex.escape(attr)}\s*=\s*"(\d+)"""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()

    private fun firstXmlTagValue(
        text: String,
        tag: String,
    ): String =
        Regex(
            """<${Regex.escape(tag)}\b[^>]*>(.*?)</${Regex.escape(tag)}>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .trim()

    private fun String.xmlUnescape(): String =
        replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun String.sanitizeXmlEntities(): String =
        replace(Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-fA-F]+;)"""), "&amp;")

    private fun String.deleteIfLocalFileUri() {
        runCatching {
            val uri = Uri.parse(this)
            if (uri.scheme == "file") {
                uri.path?.let(::File)?.delete()
            }
        }
    }

    private val STOP_WORDS =
        setOf(
            "the",
            "and",
            "feat",
            "ft",
            "with",
            "remaster",
            "remastered",
            "version",
            "explicit",
            "clean",
            "audio",
            "official",
        )

    private val VERSION_TOKENS =
        setOf(
            "remix",
            "mix",
            "edit",
            "acoustic",
            "sped",
            "slowed",
            "nightcore",
            "karaoke",
        )
}
