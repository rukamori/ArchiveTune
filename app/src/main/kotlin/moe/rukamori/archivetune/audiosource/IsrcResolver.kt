/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Resolves an ISRC (International Standard Recording Code) from track metadata via the public
 * MusicBrainz API. Sources like Deezer are ISRC-only (no text search), so this bridges a
 * YouTube-sourced track (title/artist/duration, no ISRC) to an ISRC those sources can look up.
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object IsrcResolver {
    private const val API_BASE = "https://musicbrainz.org/ws/2"

    // MusicBrainz requires a descriptive User-Agent and rate limiting to ~1 request/second.
    private const val USER_AGENT = "ArchiveTune/1.0 (https://github.com/rukamori)"
    private const val MIN_REQUEST_INTERVAL_MS = 1100L
    private const val DURATION_TOLERANCE_MS = 7000L

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    // Cache by normalized query so repeat plays of the same track don't re-hit the API.
    // A null value is cached too (as an empty string) to avoid retrying known misses in a session.
    private val cache = ConcurrentHashMap<String, String>()

    private val throttleMutex = Mutex()

    @Volatile
    private var lastRequestAtMs = 0L

    /**
     * Resolves an ISRC for the given metadata, or null if none can be confidently found.
     * Results (including misses) are cached in-memory for the process lifetime.
     */
    suspend fun resolve(
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? =
        withContext(Dispatchers.IO) {
            val primaryArtist = artists.firstOrNull()?.trim().orEmpty()
            val cacheKey = "${title.trim().lowercase()}|${primaryArtist.lowercase()}"
            cache[cacheKey]?.let { 
                Timber.tag("IsrcResolver").d("ISRC cache hit for %s: %s", title, it.ifBlank { "<null>" })
                return@withContext it.ifBlank { null } 
            }

            Timber.tag("IsrcResolver").d("Resolving ISRC for \"%s\" by %s...", title, primaryArtist)
            val mbid = searchRecordingMbid(title, primaryArtist, durationMs)
            Timber.tag("IsrcResolver").d("MusicBrainz MBID: %s", mbid ?: "<not found>")
            val isrc = mbid?.let { lookupIsrc(it) }
            Timber.tag("IsrcResolver").d("ISRC resolved: %s", isrc ?: "<not found>")
            cache[cacheKey] = isrc.orEmpty()
            isrc
        }

    /** Step 1: find the best-matching recording MBID for the metadata. */
    private suspend fun searchRecordingMbid(
        title: String,
        artist: String,
        durationMs: Long?,
    ): String? {
        val queryParts =
            buildList {
                add("recording:\"${escapeLucene(title)}\"")
                if (artist.isNotBlank()) add("artist:\"${escapeLucene(artist)}\"")
            }
        val query = queryParts.joinToString(" AND ")
        val url = "$API_BASE/recording?query=${encode(query)}&fmt=json&limit=8"
        val json = getJson(url) ?: return null
        val recordings = json.optJSONArray("recordings") ?: return null
        if (recordings.length() == 0) return null

        var bestMbid: String? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until recordings.length()) {
            val rec = recordings.optJSONObject(i) ?: continue
            val id = rec.optString("id").takeIf { it.isNotBlank() } ?: continue
            // MusicBrainz relevance score (0-100).
            var score = rec.optInt("score", 0)
            // Prefer a close duration match when we have one.
            val recLength = rec.optLong("length", -1L)
            if (durationMs != null && recLength > 0) {
                if (abs(recLength - durationMs) <= DURATION_TOLERANCE_MS) score += 25 else score -= 20
            }
            if (score > bestScore) {
                bestScore = score
                bestMbid = id
            }
        }
        // Require a reasonable confidence to avoid false matches.
        return if (bestScore >= 80) bestMbid else null
    }

    /** Step 2: fetch the recording's ISRCs and return the first. */
    private suspend fun lookupIsrc(mbid: String): String? {
        val url = "$API_BASE/recording/$mbid?inc=isrcs&fmt=json"
        val json = getJson(url) ?: return null
        val isrcs = json.optJSONArray("isrcs") ?: return null
        for (i in 0 until isrcs.length()) {
            isrcs.optString(i).takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        }
        return null
    }

    private suspend fun getJson(url: String): JSONObject? {
        throttle()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
        return runCatching {
            Timber.tag("IsrcResolver").d("Querying MusicBrainz: %s", url)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Timber.tag("IsrcResolver").w("MusicBrainz request failed: HTTP %d", response.code)
                    return@use null
                }
                Timber.tag("IsrcResolver").d("MusicBrainz response OK (%d bytes)", body.length)
                JSONObject(body)
            }
        }.getOrElse {
            Timber.tag("IsrcResolver").w(it, "MusicBrainz network error")
            null
        }
    }

    /** Serializes requests and enforces the MusicBrainz ~1 req/sec rate limit. */
    private suspend fun throttle() {
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMs)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastRequestAtMs = System.currentTimeMillis()
        }
    }

    private fun escapeLucene(input: String): String =
        input.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
