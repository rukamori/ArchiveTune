/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Amazon Music source via a public instance (e.g. the Monochrome community's `amz.*` host).
 *
 * This mirrors the monochrome.tf web player exactly: a metadata lookup against the instance's
 * `/api/track/` endpoint returns a `stream_url` (a CENC-encrypted fragmented MP4) plus a raw
 * `decryption_key`. The stream is then downloaded and decrypted on-device by [AmazonCencDecryptor]
 * (the web player offloads decryption to the browser's ClearKey/EME stack, which this app lacks).
 *
 * IMPORTANT — the instance is gated behind Cloudflare Turnstile. Requests require either a
 * configured `bypass_token` (set by the instance operator) or a Turnstile JWT obtained interactively
 * via [moe.rukamori.archivetune.ui.screens.settings.AmazonTurnstileActivity]. Without one, the
 * request is rejected (HTTP 401/428) and the caller falls through to the next configured source.
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioSourceType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AmazonAudioProvider {
    const val DEFAULT_INSTANCE = "https://amz.geeked.wtf"

    // Cloudflare Turnstile config used by the official monochrome.tf web player. The sitekey is
    // domain-locked to monochrome.tf, so the challenge must be solved from that origin (see
    // AmazonTurnstileActivity, which renders the widget via loadDataWithBaseURL on that host).
    const val TURNSTILE_SITEKEY = "0x4AAAAAADgxqF6QVMm0GLHH"
    const val TURNSTILE_ORIGIN = "https://monochrome.tf/"

    // Turnstile JWTs mint with a ~1h lifetime; we treat them as valid for a little less to leave a
    // safety margin for clock skew and in-flight requests.
    const val TURNSTILE_JWT_TTL_MS = 60L * 60L * 1000L
    const val TURNSTILE_JWT_SAFETY_MS = 5L * 60L * 1000L

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    /**
     * Exchanges a solved Cloudflare Turnstile response token for the instance's access JWT, exactly
     * as the web player does: `POST {instance}/api/auth/turnstile` with `{cf_turnstile_response}`,
     * returning the `access_token` field. The returned JWT is sent as the `X-Turnstile-JWT` header
     * on subsequent track/stream requests. Returns null on failure.
     */
    suspend fun exchangeTurnstileToken(
        cfTurnstileResponse: String,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    instanceBaseUrl.trimEnd('/').toHttpUrl()
                        .newBuilder()
                        .addPathSegment("api")
                        .addPathSegment("auth")
                        .addPathSegment("turnstile")
                        .build()
                val bodyJson = JSONObject().put("cf_turnstile_response", cfTurnstileResponse).toString()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                        .post(bodyJson.toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful || body.isBlank()) {
                        Timber.tag("AmazonAudio").w("Turnstile exchange failed: HTTP %d", response.code)
                        return@use null
                    }
                    JSONObject(body).optString("access_token").ifBlank { null }
                }
            }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Turnstile exchange error")
                null
            }
        }

    /**
     * Resolves a fully-playable local stream from track metadata, matching exactly what the
     * monochrome.tf web player does for Amazon Music:
     *   1. GET `{instance}/api/track/?track=&artist=&album=&duration=&quality=` (auth via a Turnstile
     *      JWT header or an operator `bypass_token`), which returns a `stream_url` (a CENC-encrypted
     *      fragmented MP4) plus a raw `decryption_key`.
     *   2. Download the encrypted stream and decrypt it on-device via [AmazonCencDecryptor] (the web
     *      player offloads this to the browser's ClearKey/EME stack; we have no EME so we do it
     *      ourselves), writing a plain FLAC/Opus-in-MP4 to [cacheDir].
     *
     * Returns a [DirectStream] pointing at the decrypted local file, or null if no match is found or
     * the instance rejects the request (e.g. Turnstile 401/428) so the caller can fall through.
     */
    suspend fun resolveByMetadata(
        title: String,
        artists: List<String>,
        durationMs: Long?,
        cacheDir: java.io.File,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
        bypassToken: String? = null,
        quality: String = "LOSSLESS",
        turnstileJwt: String? = null,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            val artist = artists.firstOrNull().orEmpty()
            if (title.isBlank() || artist.isBlank()) {
                Timber.tag("AmazonAudio").d("Missing title/artist; skipping Amazon.")
                return@withContext null
            }
            if (bypassToken.isNullOrBlank() && turnstileJwt.isNullOrBlank()) {
                Timber.tag("AmazonAudio").d("No Turnstile JWT / bypass token; skipping Amazon (authorize in settings).")
                return@withContext null
            }
            val base = instanceBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_INSTANCE }
            val mappedQuality = mapAmazonQuality(quality)

            val payload =
                runCatching { fetchTrackPayload(base, title, artist, durationMs, mappedQuality, bypassToken, turnstileJwt) }
                    .getOrElse {
                        Timber.tag("AmazonAudio").w(it, "Amazon track lookup error for \"%s\"", title)
                        null
                    } ?: return@withContext null

            val streamUrl = payload.optString("stream_url").ifBlank { null }
            if (streamUrl == null) {
                Timber.tag("AmazonAudio").w("Amazon API returned no stream_url for \"%s\"", title)
                return@withContext null
            }
            val keyHex = extractDecryptionKey(payload)
            val codecHint = extractCodec(payload)
            val qualityLabel = payload.optString("quality_selected").ifBlank { mappedQuality }

            downloadAndPrepare(streamUrl, keyHex, codecHint, qualityLabel, cacheDir)
        }

    /** Fetches and unwraps the `/api/track/` payload; returns the object containing `stream_url`. */
    private fun fetchTrackPayload(
        base: String,
        title: String,
        artist: String,
        durationMs: Long?,
        mappedQuality: String,
        bypassToken: String?,
        turnstileJwt: String?,
    ): JSONObject? {
        val urlBuilder =
            "$base/api/track/".toHttpUrl()
                .newBuilder()
                .addQueryParameter("track", title)
                .addQueryParameter("artist", artist)
                .addQueryParameter("album", "")
                .addQueryParameter("duration", durationMs?.let { (it / 1000).toString() } ?: "")
                .addQueryParameter("quality", mappedQuality)
        if (!bypassToken.isNullOrBlank()) urlBuilder.addQueryParameter("bypass_token", bypassToken)
        val request =
            Request
                .Builder()
                .url(urlBuilder.build())
                .apply { if (!turnstileJwt.isNullOrBlank()) header("X-Turnstile-JWT", turnstileJwt) }
                .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 428) {
                Timber.tag("AmazonAudio").w("Amazon requires (re)authorization: HTTP %d", response.code)
                return null
            }
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                Timber.tag("AmazonAudio").w("Amazon track lookup failed: HTTP %d", response.code)
                return null
            }
            val root = JSONObject(body)
            // The payload may be wrapped under data/track/result (see monochrome getAmazonTrackApiPayload).
            return when {
                root.has("stream_url") -> root
                root.optJSONObject("data")?.has("stream_url") == true -> root.getJSONObject("data")
                root.optJSONObject("track")?.has("stream_url") == true -> root.getJSONObject("track")
                root.optJSONObject("result")?.has("stream_url") == true -> root.getJSONObject("result")
                else -> root
            }
        }
    }

    /** Downloads the (possibly encrypted) stream and, if a key is present, decrypts it to a local file. */
    private fun downloadAndPrepare(
        streamUrl: String,
        keyHex: String?,
        codecHint: String?,
        qualityLabel: String,
        cacheDir: java.io.File,
    ): DirectStream? {
        val bytes =
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(streamUrl)
                        .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("AmazonAudio").w("Amazon stream download failed: HTTP %d", response.code)
                        return null
                    }
                    response.body?.bytes()
                }
            }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Amazon stream download error")
                null
            } ?: return null

            val outCodec: String
            if (keyHex.isNullOrBlank()) {
                // Unencrypted (rare): play as-is.
                outCodec = codecHint ?: "flac"
            } else {
                val decoded = AmazonCencDecryptor.decryptInPlace(bytes, keyHex)
                if (decoded == null) {
                    Timber.tag("AmazonAudio").w("Amazon CENC decryption failed; skipping.")
                    return null
                }
                outCodec = decoded
            }

            val cacheRoot = java.io.File(cacheDir, "amazon").apply { mkdirs() }
            val file = java.io.File(cacheRoot, "amz_${abs(streamUrl.hashCode())}_$qualityLabel.mp4")
            runCatching { file.writeBytes(bytes) }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Failed writing decrypted Amazon file")
                return null
            }
            Timber.tag("AmazonAudio").d("Amazon stream ready (%s, %d bytes) -> %s", outCodec, file.length(), file.name)
            return DirectStream(
                uri = android.net.Uri.fromFile(file).toString(),
                mimeType = "audio/mp4",
                codecs = outCodec,
                contentLength = file.length(),
                label = "Amazon Music $qualityLabel",
                source = AudioSourceType.AMAZON,
            )
    }

    /** Maps a generic quality tier to Amazon's instance quality codes (see monochrome getAmazonMusicQuality). */
    private fun mapAmazonQuality(quality: String): String =
        when (quality.trim().uppercase()) {
            "HI_RES_LOSSLESS", "AUTO", "ADAPTIVE", "DOLBY_ATMOS" -> "UHD"
            "LOSSLESS" -> "HD"
            "HIGH" -> "SD_HIGH"
            "LOW" -> "SD_LOW"
            "NORMAL" -> "SD_MEDIUM"
            else -> "HD"
        }

    /** Extracts the raw CENC content key from the payload, checking all known field names. */
    private fun extractDecryptionKey(payload: JSONObject): String? =
        payload.optString("decryption_key").ifBlank { null }
            ?: payload.optString("decryptionKey").ifBlank { null }
            ?: payload.optJSONObject("decryption")?.optString("key")?.ifBlank { null }
            ?: payload.optJSONObject("drm")?.optString("decryption_key")?.ifBlank { null }
            ?: payload.optJSONObject("drm")?.optString("decryptionKey")?.ifBlank { null }

    /** Reads the codec (flac/opus) for the selected quality, falling back to the top-level codec. */
    private fun extractCodec(payload: JSONObject): String? {
        val selected = payload.optString("quality_selected")
        val available = payload.optJSONArray("available_qualities")
        if (available != null && selected.isNotBlank()) {
            for (i in 0 until available.length()) {
                val q = available.optJSONObject(i) ?: continue
                if (q.optString("quality") == selected) {
                    q.optString("codec").ifBlank { null }?.let { return it.lowercase() }
                }
            }
        }
        return payload.optString("codec").ifBlank { null }?.lowercase()
    }

}
