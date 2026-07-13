/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Deezer lossless source via a public downloader instance (e.g. the Monochrome community's
 * `dzr.*` host). The instance is ISRC-only — there is no text search — so callers must resolve an
 * ISRC first (see [IsrcResolver]). Instances gate on an allowed Referer and may be temporarily
 * down (dead ARLs); in both cases resolution fails gracefully and playback falls back to the next
 * configured source.
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioSourceType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object DeezerAudioProvider {
    const val DEFAULT_INSTANCE = "https://dzr.tabs-vs-spaces.wtf"

    /**
     * Built-in fallback instances tried (in order) when the configured/primary instance is down.
     * A single instance frequently returns 503 (dead ARL / overloaded), so we sequentially fall
     * back to the next candidate rather than giving up on the whole source. Users can add their
     * own working instances in Player & audio → Streaming sources.
     */
    val FALLBACK_INSTANCES =
        listOf(
            "https://dzr.tabs-vs-spaces.wtf",
        )

    // Deezer instances only accept requests carrying an allowed site Referer.
    private const val ALLOWED_REFERER = "https://monochrome.tf"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    /**
     * Resolves a direct FLAC stream for the given ISRC by trying the configured instance first and
     * then falling back through [FALLBACK_INSTANCES]. Returns null only if every instance is down,
     * has no live accounts, or does not carry the track.
     */
    suspend fun resolve(
        isrc: String,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            // Build an ordered, de-duplicated candidate list: configured instance(s) first, then
            // built-in fallbacks.
            val configured =
                instanceBaseUrl
                    .split('\n', ',')
                    .map { it.trim().trimEnd('/') }
                    .filter { it.isNotEmpty() }
            val candidates = (configured + FALLBACK_INSTANCES).distinct()
            Timber.tag("DeezerAudio").d("Deezer resolve isrc=%s across %d instance(s)", isrc, candidates.size)

            for ((index, base) in candidates.withIndex()) {
                Timber.tag("DeezerAudio").d("Deezer try %d/%d: %s", index + 1, candidates.size, base)
                val stream = resolveFromInstance(isrc, base)
                if (stream != null) {
                    Timber.tag("DeezerAudio").i("Deezer HIT on %s (%s)", base, stream.label)
                    return@withContext stream
                }
            }
            Timber.tag("DeezerAudio").w("Deezer failed on all %d instance(s) for isrc=%s", candidates.size, isrc)
            null
        }

    private fun resolveFromInstance(
        isrc: String,
        base: String,
    ): DirectStream? {
        val url = "$base/track/?isrc=${isrc.trim()}"
        val request =
            Request
                .Builder()
                .url(url)
                .header("Referer", ALLOWED_REFERER)
                .header("Origin", ALLOWED_REFERER)
                .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                .get()
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    Timber.tag("DeezerAudio").w("Deezer %s failed: HTTP %d", base, response.code)
                    return@use null
                }
                val json = JSONObject(body)
                if (json.has("error")) {
                    Timber.tag("DeezerAudio").w("Deezer %s error: %s", base, json.optString("error"))
                    return@use null
                }
                parseStream(json)
            }
        }.getOrElse {
            Timber.tag("DeezerAudio").w(it, "Deezer %s resolution error", base)
            null
        }
    }

    /**
     * Extracts a direct media URL from the instance response. The exact schema varies between
     * instance versions, so this searches the JSON tree for the first plausible stream URL and any
     * accompanying size/format metadata rather than assuming fixed key names.
     */
    private fun parseStream(json: JSONObject): DirectStream? {
        val url = findStreamUrl(json) ?: return null
        val size = findLong(json, SIZE_KEYS)
        val format = findString(json, FORMAT_KEYS)?.uppercase().orEmpty()
        val isFlac = format.contains("FLAC") || url.contains(".flac", ignoreCase = true)
        return DirectStream(
            uri = url,
            mimeType = if (isFlac) "audio/flac" else "audio/mpeg",
            codecs = if (isFlac) "flac" else "mp3",
            contentLength = size?.takeIf { it > 0 },
            label = "Deezer ${format.ifBlank { if (isFlac) "FLAC" else "MP3" }}",
            source = AudioSourceType.DEEZER,
        )
    }

    private val URL_KEYS = listOf("url", "link", "download", "stream", "streamUrl", "flac", "media")
    private val SIZE_KEYS = listOf("size", "filesize", "contentLength", "bytes", "fileSize")
    private val FORMAT_KEYS = listOf("format", "quality", "codec", "type")

    private fun findStreamUrl(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                for (key in URL_KEYS) {
                    val v = node.opt(key)
                    if (v is String && v.startsWith("http")) return v
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findStreamUrl(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) findStreamUrl(node.opt(i))?.let { return it }
            }
            is String -> if (node.startsWith("http") && looksLikeMedia(node)) return node
        }
        return null
    }

    private fun looksLikeMedia(url: String): Boolean =
        listOf(".flac", ".mp3", ".m4a", "/stream", "/media", "cdn").any { url.contains(it, ignoreCase = true) }

    private fun findLong(
        node: JSONObject,
        keys: List<String>,
    ): Long? {
        for (key in keys) {
            val v = node.opt(key)
            when (v) {
                is Number -> return v.toLong()
                is String -> v.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun findString(
        node: JSONObject,
        keys: List<String>,
    ): String? {
        for (key in keys) {
            (node.opt(key) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
