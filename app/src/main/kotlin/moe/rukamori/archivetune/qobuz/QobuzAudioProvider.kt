/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.qobuz

import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Streaming/playback provider for user-provided Qobuz-DL proxy instances (squid.wtf / kennyy /
 * arcod-style). ArchiveTune bundles NO endpoints — the user pastes their own instance base URLs in
 * settings; when the list is empty the provider resolves nothing and playback falls through to the
 * next audio source.
 *
 * Proxy API shape (common across the squid.wtf family):
 *  - GET {instance}/api/get-music?q={query}&offset=0
 *      -> { success, data: { tracks: { items: [ {id,title,performer:{name},album:{title},
 *           duration, maximum_bit_depth, maximum_sampling_rate, isrc}, ... ] } } }
 *  - GET {instance}/api/download-music?track_id={id}&quality={formatId}
 *      -> { success, data: { url: "https://.../file.flac?...&etsp=<expiry>" } }
 *
 * Parsing is intentionally defensive (multiple key shapes) because the proxies differ slightly.
 * This mirrors [TidalAudioProvider] but is leaner: it reuses [TidalAudioProvider.InstanceHealth]
 * so the settings status chips are shared, and there is no account/OAuth path (proxies are anon).
 */
object QobuzAudioProvider {
    private const val USER_AGENT = "ArchiveTune-Android"
    private const val SEARCH_LIMIT = 10
    private const val MIN_MATCH_SCORE = 60
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L
    private const val INSTANCE_SOFT_COOLDOWN_MS = 60_000L
    private const val INSTANCE_HARD_COOLDOWN_MS = 600_000L
    private const val AUDIO_FLAC_MIME_TYPE = "audio/flac"

    private val STOP_WORDS =
        setOf("the", "a", "an", "of", "and", "feat", "ft", "featuring", "with")
    private val VERSION_TOKENS =
        setOf("remix", "acoustic", "live", "instrumental", "demo", "edit", "mix", "version")

    private const val QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2"

    /** A single configured proxy instance. */
    private data class Instance(
        val label: String,
        val baseUrl: String,
    )

    @Volatile
    private var instances: List<Instance> = emptyList()

    @Volatile
    private var tokens: List<QobuzToken> = emptyList()

    val activeInstanceUrls: List<String>
        get() = instances.map { it.baseUrl }

    val activeTokenIds: List<String>
        get() = tokens.map { it.id }

    /** Replaces the active direct-API token list. Duplicates (by token string) are dropped. */
    fun setTokens(newTokens: List<QobuzToken>) {
        val seen = LinkedHashSet<String>()
        tokens = newTokens.filter { it.token.isNotBlank() && seen.add(it.token) }
    }

    /**
     * A resolution backend: either a direct Qobuz API token or a proxy instance. Both expose the same
     * search + download surface so the matching/scoring pipeline is shared.
     */
    private class Backend(
        val id: String,
        val label: String,
        val isToken: Boolean,
        val search: (String) -> JSONArray?,
        val download: (String, Int) -> DownloadResult?,
    )

    /** The last Qobuz track id that resolved successfully, usable as a health-probe track. */
    @Volatile
    var lastResolvedTrackId: String? = null
        private set

    fun seedProbeTrack(trackId: String) {
        if (lastResolvedTrackId.isNullOrBlank() && trackId.isNotBlank()) {
            lastResolvedTrackId = trackId
        }
    }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    private val healthClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    private data class CachedSearch(val trackId: String?, val expiresAt: Long)

    private data class CachedStream(val stream: DirectStream, val expiresAt: Long)

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val failureCache = ConcurrentHashMap<String, Long>()
    private val instanceCooldownUntilMs = ConcurrentHashMap<String, Long>()

    /** Replaces the active instance list. Invalid/duplicate URLs are dropped. Empty stays empty. */
    fun setInstances(baseUrls: List<String>) {
        val seen = LinkedHashSet<String>()
        instances =
            baseUrls.mapNotNull { raw ->
                val normalized = normalizeInstanceUrl(raw) ?: return@mapNotNull null
                if (!seen.add(normalized)) return@mapNotNull null
                Instance(instanceLabel(normalized), normalized)
            }
    }

    /** Normalizes an instance URL to `scheme://host[:port]` form, or null when invalid. */
    fun normalizeInstanceUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank() || !url.host.contains('.')) return null
        return withScheme
    }

    private fun instanceLabel(baseUrl: String): String =
        baseUrl.toHttpUrlOrNull()?.host ?: baseUrl

    private fun markInstanceHealthy(baseUrl: String) {
        instanceCooldownUntilMs.remove(baseUrl)
    }

    private fun markInstanceFailed(
        baseUrl: String,
        hardFailure: Boolean,
    ) {
        val cooldownMs = if (hardFailure) INSTANCE_HARD_COOLDOWN_MS else INSTANCE_SOFT_COOLDOWN_MS
        instanceCooldownUntilMs[baseUrl] = System.currentTimeMillis() + cooldownMs
    }

    private fun isInstanceCoolingDown(
        baseUrl: String,
        now: Long,
    ): Boolean = (instanceCooldownUntilMs[baseUrl] ?: 0L) > now

    /**
     * Lightweight reachability probe. Returns round-trip latency in ms, or null when unreachable.
     * Runs blocking network I/O — call off the main thread.
     */
    fun checkInstance(baseUrl: String): Long? {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return null
        val request =
            Request
                .Builder()
                .url("$normalized/")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        return runCatching {
            val start = System.currentTimeMillis()
            healthClient.newCall(request).execute().use { response ->
                if (response.code in 200..499) System.currentTimeMillis() - start else null
            }
        }.getOrNull()
    }

    /**
     * Deep health probe: runs an actual search + download for [probeTrackId] (or a canned query when
     * blank) and inspects whether the instance returns a real lossless URL or only a preview/sample.
     * Runs blocking network I/O — call off the main thread.
     */
    fun verifyInstance(
        baseUrl: String,
        probeTrackId: String?,
        formatId: Int,
    ): TidalAudioProvider.InstanceHealth {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return TidalAudioProvider.InstanceHealth.UNREACHABLE
        val trackId = probeTrackId?.trim().orEmpty()
        if (trackId.isEmpty()) {
            // No probe track yet: fall back to reachability + a search sanity check.
            if (checkInstance(normalized) == null) return TidalAudioProvider.InstanceHealth.UNREACHABLE
            val sample = runCatching { searchTrackId(normalized, "adele hello") }.getOrNull()
            return if (sample != null) {
                TidalAudioProvider.InstanceHealth.HEALTHY
            } else {
                TidalAudioProvider.InstanceHealth.UNREACHABLE
            }
        }
        return runCatching {
            when (val result = fetchDownload(normalized, trackId, formatId)) {
                null -> TidalAudioProvider.InstanceHealth.UNREACHABLE
                else -> if (result.isPreview) {
                    TidalAudioProvider.InstanceHealth.PREVIEW_ONLY
                } else {
                    TidalAudioProvider.InstanceHealth.HEALTHY
                }
            }
        }.getOrElse { TidalAudioProvider.InstanceHealth.UNREACHABLE }
    }

    /** Feeds an external health result into the runtime cooldown map used by the resolver. */
    fun applyHealthResult(
        baseUrl: String,
        healthy: Boolean,
    ) {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return
        if (healthy) markInstanceHealthy(normalized) else markInstanceFailed(normalized, hardFailure = true)
    }

    /** Lookup metadata for a track, matching Tidal's [TidalAudioProvider.Query] shape. */
    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    /**
     * Resolves a direct-playable lossless stream for [query] at the requested Qobuz [formatId]
     * (6=CD, 7=hi-res ≤96kHz, 27=max). Returns null when no instance can serve a full track. Runs
     * blocking network I/O — call off the main thread.
     */
    fun resolve(
        query: Query,
        formatId: Int,
    ): DirectStream? {
        val backends = orderedBackends()
        if (backends.isEmpty()) {
            Timber.tag("Qobuz").d("resolve skipped: no tokens or instances configured")
            return null
        }
        val now = System.currentTimeMillis()
        val cacheKey = query.cacheKey() + ":" + formatId
        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) {
                Timber.tag("Qobuz").d("stream cache hit for %s", query.title)
                return cached.stream
            }
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }

        val available = backends.filterNot { isInstanceCoolingDown(it.id, now) }.ifEmpty { backends }
        for (backend in available) {
            val trackId =
                runCatching { resolveTrackId(backend, query) }
                    .onFailure { markInstanceFailed(backend.id, hardFailure = it is java.io.IOException) }
                    .getOrNull() ?: continue
            val download =
                runCatching { backend.download(trackId, formatId) }
                    .onFailure { markInstanceFailed(backend.id, hardFailure = it is java.io.IOException) }
                    .getOrNull()
            if (download == null) {
                markInstanceFailed(backend.id, hardFailure = false)
                continue
            }
            if (download.isPreview) {
                // Unsubscribed/expired backing account: skip this backend for a while, try the next.
                Timber.tag("Qobuz").w("%s returned preview-only; skipping", backend.label)
                markInstanceFailed(backend.id, hardFailure = false)
                continue
            }
            markInstanceHealthy(backend.id)
            lastResolvedTrackId = trackId
            val stream =
                DirectStream(
                    uri = download.url,
                    mimeType = download.mimeType,
                    codecs = download.codecs,
                    contentLength = null,
                    label = "Qobuz ${qualityLabel(formatId)}",
                    source = AudioSourceType.QOBUZ,
                )
            streamCache[cacheKey] = CachedStream(stream, now + STREAM_CACHE_MS)
            Timber.tag("Qobuz").i("resolved \"%s\" via %s [%s]", query.title, backend.label, stream.label)
            return stream
        }
        failureCache[cacheKey] = now + FAILURE_CACHE_MS
        return null
    }

    /** Builds the ordered backend list: direct API tokens first (highest fidelity), then proxies. */
    private fun orderedBackends(): List<Backend> {
        val tokenBackends =
            tokens.map { token ->
                Backend(
                    id = "token:${token.id}",
                    label = "Qobuz token ${token.label.ifBlank { token.userId.ifBlank { "account" } }}",
                    isToken = true,
                    search = { q -> searchItemsDirect(token, q) },
                    download = { id, fmt -> fetchDownloadDirect(token, id, fmt) },
                )
            }
        val proxyBackends =
            instances.map { instance ->
                Backend(
                    id = instance.baseUrl,
                    label = instance.label,
                    isToken = false,
                    search = { q -> searchItems(instance.baseUrl, q) },
                    download = { id, fmt -> fetchDownload(instance.baseUrl, id, fmt) },
                )
            }
        return tokenBackends + proxyBackends
    }

    private fun qualityLabel(formatId: Int): String =
        when (formatId) {
            6 -> "FLAC"
            7 -> "HI_RES"
            27 -> "MAX"
            else -> "FLAC"
        }

    // -------------------------------------------------------------------------
    // Search + download
    // -------------------------------------------------------------------------

    /** Resolves the best-matching Qobuz track id for [query] against a single backend. */
    private fun resolveTrackId(
        backend: Backend,
        query: Query,
    ): String? {
        val now = System.currentTimeMillis()
        val key = backend.id + "|" + query.cacheKey()
        searchCache[key]?.let { cached ->
            if (cached.expiresAt > now) return cached.trackId
            searchCache.remove(key)
        }
        val searchQuery =
            listOf(query.artists.firstOrNull()?.searchArtist().orEmpty(), query.title.searchTitle())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { query.title }
        val trackId = bestMatch(backend, searchQuery, query)
        searchCache[key] = CachedSearch(trackId, now + SEARCH_CACHE_MS)
        return trackId
    }

    /** Runs search and scores results against [query], returning the best track id above threshold. */
    private fun bestMatch(
        backend: Backend,
        searchQuery: String,
        query: Query,
    ): String? {
        val items = backend.search(searchQuery) ?: return null
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        var bestId: String? = null
        var bestScore = Int.MIN_VALUE
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.trackId() ?: continue
            val candidateTitle = (item.stringOrNull("title") ?: continue).titleMatchNormalized()
            val candidateArtist =
                item.optJSONObject("performer")?.stringOrNull("name")
                    ?: item.stringOrNull("artist")
                    ?: item.optJSONObject("album")?.optJSONObject("artist")?.stringOrNull("name")
                    ?: ""
            val candidateDurationMs = item.longOrNull("duration")?.times(1000L)
            val score =
                scoreMatch(
                    wantedTitle = wantedTitle,
                    wantedArtists = wantedArtists,
                    candidateTitle = candidateTitle,
                    candidateArtist = candidateArtist.normalized(),
                    wantedDurationMs = query.durationMs,
                    candidateDurationMs = candidateDurationMs,
                )
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        return if (bestScore >= MIN_MATCH_SCORE) bestId else null
    }

    /** Health-only search helper: returns any track id for a canned query (or null). */
    private fun searchTrackId(
        baseUrl: String,
        searchQuery: String,
    ): String? {
        val items = searchItems(baseUrl, searchQuery) ?: return null
        for (index in 0 until items.length()) {
            items.optJSONObject(index)?.trackId()?.let { return it }
        }
        return null
    }

    private fun searchItems(
        baseUrl: String,
        searchQuery: String,
    ): JSONArray? {
        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("get-music")
                .addQueryParameter("q", searchQuery)
                .addQueryParameter("offset", "0")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return root.findTrackItems()
        }
    }

    private data class DownloadResult(
        val url: String,
        val mimeType: String,
        val codecs: String,
        val isPreview: Boolean,
    )

    /** Calls download-music and extracts the direct URL + a best-effort preview flag. */
    private fun fetchDownload(
        baseUrl: String,
        trackId: String,
        formatId: Int,
    ): DownloadResult? {
        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("download-music")
                .addQueryParameter("track_id", trackId)
                .addQueryParameter("quality", formatId.toString())
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val streamUrl = root.findStreamUrl() ?: return null
            val isPreview = root.looksLikePreview()
            val mime =
                when {
                    streamUrl.contains(".flac", ignoreCase = true) -> AUDIO_FLAC_MIME_TYPE
                    streamUrl.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
                    else -> AUDIO_FLAC_MIME_TYPE
                }
            val codecs = if (mime == AUDIO_FLAC_MIME_TYPE) "flac" else "mp4a.40.2"
            return DownloadResult(streamUrl, mime, codecs, isPreview)
        }
    }

    // -------------------------------------------------------------------------
    // Direct Qobuz API (token) — www.qobuz.com/api.json/0.2 with MD5 request signature
    // -------------------------------------------------------------------------

    private fun searchItemsDirect(
        token: QobuzToken,
        searchQuery: String,
    ): JSONArray? {
        val url =
            "$QOBUZ_API_BASE/track/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", searchQuery)
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("app_id", token.appId)
                .build()
        client.newCall(directRequest(url.toString(), token)).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return root.findTrackItems()
        }
    }

    /**
     * Calls track/getFileUrl with the Qobuz MD5 request signature. The signed payload is the
     * concatenation (no separators) of the sorted call params + a unix timestamp + the app secret:
     *   md5("trackgetFileUrl" + "format_id"+fmt + "intent"+"stream" + "track_id"+id + ts + secret)
     */
    private fun fetchDownloadDirect(
        token: QobuzToken,
        trackId: String,
        formatId: Int,
    ): DownloadResult? {
        val ts = System.currentTimeMillis() / 1000L
        val sig = md5("trackgetFileUrlformat_id${formatId}intentstreamtrack_id$trackId$ts${token.appSecret}")
        val url =
            "$QOBUZ_API_BASE/track/getFileUrl"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("request_ts", ts.toString())
                .addQueryParameter("request_sig", sig)
                .addQueryParameter("track_id", trackId)
                .addQueryParameter("format_id", formatId.toString())
                .addQueryParameter("intent", "stream")
                .addQueryParameter("app_id", token.appId)
                .build()
        client.newCall(directRequest(url.toString(), token)).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val streamUrl =
                root.stringOrNull("url")?.takeIf { it.startsWith("http") }
                    ?: root.findStreamUrl()
                    ?: return null
            // Qobuz sets "sample":true for 30s previews (unsubscribed, or track unavailable at quality).
            val isPreview = root.optBoolean("sample", false) || root.looksLikePreview()
            val apiMime = root.stringOrNull("mime_type")
            val mime =
                when {
                    apiMime?.contains("flac", true) == true -> AUDIO_FLAC_MIME_TYPE
                    apiMime?.contains("mpeg", true) == true || apiMime?.contains("mp3", true) == true -> "audio/mpeg"
                    streamUrl.contains(".flac", true) -> AUDIO_FLAC_MIME_TYPE
                    streamUrl.contains(".mp3", true) -> "audio/mpeg"
                    else -> AUDIO_FLAC_MIME_TYPE
                }
            val codecs = if (mime == AUDIO_FLAC_MIME_TYPE) "flac" else "mp4a.40.2"
            return DownloadResult(streamUrl, mime, codecs, isPreview)
        }
    }

    private fun directRequest(
        url: String,
        token: QobuzToken,
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("X-App-Id", token.appId)
            .header("X-User-Auth-Token", token.token)
            .get()
            .build()

    private fun md5(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Deep health probe for a direct-API token: searches + calls getFileUrl for [probeTrackId] and
     * classifies HEALTHY / PREVIEW_ONLY (auth OK but no lossless entitlement) / UNREACHABLE (expired,
     * invalid, or network error). Runs blocking network I/O — call off the main thread.
     */
    fun verifyToken(
        token: QobuzToken,
        probeTrackId: String?,
        formatId: Int,
    ): TidalAudioProvider.InstanceHealth {
        val trackId = probeTrackId?.trim().orEmpty()
        return runCatching {
            val resolvedId =
                trackId.ifBlank {
                    val items = searchItemsDirect(token, "adele hello") ?: return@runCatching TidalAudioProvider.InstanceHealth.UNREACHABLE
                    (0 until items.length()).firstNotNullOfOrNull { items.optJSONObject(it)?.trackId() }
                        ?: return@runCatching TidalAudioProvider.InstanceHealth.UNREACHABLE
                }
            when (val result = fetchDownloadDirect(token, resolvedId, formatId)) {
                null -> TidalAudioProvider.InstanceHealth.UNREACHABLE
                else -> if (result.isPreview) {
                    TidalAudioProvider.InstanceHealth.PREVIEW_ONLY
                } else {
                    TidalAudioProvider.InstanceHealth.HEALTHY
                }
            }
        }.getOrElse { TidalAudioProvider.InstanceHealth.UNREACHABLE }
    }

    // -------------------------------------------------------------------------
    // Defensive JSON extraction (proxy responses vary slightly)
    // -------------------------------------------------------------------------

    /** Finds the track item array across the common response shapes. */
    private fun JSONObject.findTrackItems(): JSONArray? {
        val data = optJSONObject("data") ?: this
        data.optJSONObject("tracks")?.optJSONArray("items")?.let { return it }
        data.optJSONArray("items")?.let { return it }
        data.optJSONArray("tracks")?.let { return it }
        optJSONArray("items")?.let { return it }
        optJSONArray("tracks")?.let { return it }
        return null
    }

    /** Recursively searches for the first plausible stream URL field. */
    private fun JSONObject.findStreamUrl(): String? {
        for (key in listOf("url", "downloadUrl", "download_url", "stream_url", "streamUrl", "link")) {
            stringOrNull(key)?.takeIf { it.startsWith("http") }?.let { return it }
        }
        optJSONObject("data")?.findStreamUrl()?.let { return it }
        val names = keys()
        while (names.hasNext()) {
            val value = opt(names.next())
            when (value) {
                is JSONObject -> value.findStreamUrl()?.let { return it }
                is String -> if (value.startsWith("http") && (value.contains(".flac", true) || value.contains(".mp3", true))) return value
            }
        }
        return null
    }

    /** Best-effort preview/sample detection from the download payload. */
    private fun JSONObject.looksLikePreview(): Boolean {
        val data = optJSONObject("data") ?: this
        if (data.optBoolean("sample", false)) return true
        if (data.optBoolean("preview", false)) return true
        // Some proxies signal an unsubscribed account via a short duration or an explicit flag.
        data.stringOrNull("type")?.let { if (it.equals("preview", true) || it.equals("sample", true)) return true }
        return false
    }

    private fun JSONObject.trackId(): String? =
        stringOrNull("id") ?: longOrNull("id")?.toString() ?: stringOrNull("track_id")

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key)) optLong(key).takeIf { it > 0L } else null

    // -------------------------------------------------------------------------
    // Matching (self-contained; mirrors Tidal's heuristics)
    // -------------------------------------------------------------------------

    private fun scoreMatch(
        wantedTitle: String,
        wantedArtists: List<String>,
        candidateTitle: String,
        candidateArtist: String,
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Int {
        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return Int.MIN_VALUE
        // Reject clear version mismatches (live vs studio, remix, instrumental, ...).
        if (hasVersionMismatch(" $wantedTitle ", " $candidateTitle ")) return Int.MIN_VALUE
        val titleOverlap = tokenOverlap(significantTokens(wantedTitle), significantTokens(candidateTitle))
        if (titleOverlap < 0.5) return Int.MIN_VALUE
        var score = (titleOverlap * 100).toInt()
        if (wantedArtists.isNotEmpty() && candidateArtist.isNotBlank()) {
            val artistOverlap =
                wantedArtists.maxOf { tokenOverlap(significantTokens(it), significantTokens(candidateArtist)) }
            score += (artistOverlap * 60).toInt()
        }
        if (wantedDurationMs != null && candidateDurationMs != null) {
            if (durationMatches(wantedDurationMs, candidateDurationMs)) score += 30 else score -= 40
        }
        return score
    }

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
        return VERSION_TOKENS.any { token ->
            wanted.contains(" $token ") != candidate.contains(" $token ")
        }
    }

    private fun Query.cacheKey(): String =
        listOf(
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

    private fun String.searchTitle(): String =
        trim()
            .replace(Regex("""\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.searchArtist(): String =
        trim()
            .substringBefore(',')
            .replace(Regex("\\s+"), " ")
            .trim()
}
