/**
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Tidal account login via the OAuth 2.0 Device Authorization Grant, plus subscription
 * (HiFi/Premium) detection. Signing in is optional: playback itself is served by the public
 * HiFi instances configured in [TidalAudioProvider]. Logging in only lets the app tell the user
 * whether their own Tidal account is a paid tier, and warn when it is not.
 */

package moe.rukamori.archivetune.tidal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.DirectStream
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TidalAccountManager {
    // Well-known public Tidal "TV/device" OAuth client used by open-source Tidal tooling for the
    // device authorization grant. These are not secret user credentials.
    private const val CLIENT_ID = "zU4XHVVkc2tDPo4t"
    private const val CLIENT_SECRET = "VJKhDFqJPqvsPVNBV6ukXTJmwlvbttP7wlMlrc72se4="

    private const val DEVICE_AUTH_ENDPOINT = "https://auth.tidal.com/v1/oauth2/device_authorization"
    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val API_BASE = "https://api.tidal.com/v1"
    private const val SCOPE = "r_usr+w_usr+w_sub"
    private const val COUNTRY_CODE = "US"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    private val client =
        OkHttpClient
            .Builder()
            .dns(TidalDns) // DoH fallback so login works on ISPs that DNS-block tidal.com
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    /** Details returned when a device login is initiated; shown to the user to authorize. */
    data class DeviceAuthorization(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
    )

    /** Result of a successful token exchange. [expiresAtMillis] is an absolute epoch time. */
    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val userId: Long?,
        val username: String?,
    )

    /** Subscription tier resolved from the Tidal API. */
    enum class Subscription {
        UNKNOWN,
        PREMIUM,
        FREE,
    }

    sealed interface PollResult {
        data class Success(val token: TokenResult) : PollResult

        /** Authorization still pending; keep polling. */
        data object Pending : PollResult

        /** Slow down polling (server returned slow_down). */
        data object SlowDown : PollResult

        /** Transient connectivity failure (e.g. offline / DNS); retryable with backoff. */
        data object NetworkError : PollResult

        /** Terminal failure (expired, denied, or network). */
        data class Failure(val reason: String) : PollResult
    }

    /**
     * Step 1: request a device + user code. Returns null on failure (e.g. no network).
     * Runs on IO.
     */
    suspend fun requestDeviceAuthorization(): DeviceAuthorization? =
        withContext(Dispatchers.IO) {
            val body =
                FormBody
                    .Builder()
                    .add("client_id", CLIENT_ID)
                    .add("scope", SCOPE)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(DEVICE_AUTH_ENDPOINT)
                    .post(body)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Timber.tag("TidalAccount").w("device_authorization failed: %d %s", response.code, payload)
                        return@use null
                    }
                    val json = JSONObject(payload)
                    DeviceAuthorization(
                        deviceCode = json.getString("deviceCode"),
                        userCode = json.getString("userCode"),
                        verificationUri = json.optString("verificationUri", "link.tidal.com"),
                        verificationUriComplete =
                            json.optString(
                                "verificationUriComplete",
                                "https://${json.optString("verificationUri", "link.tidal.com")}/${json.optString("userCode")}",
                            ),
                        expiresInSeconds = json.optInt("expiresIn", 300),
                        intervalSeconds = json.optInt("interval", 2),
                    )
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "device_authorization error")
                null
            }
        }

    /** Step 2 (single attempt): exchange the device code for tokens. Runs on IO. */
    suspend fun pollForToken(deviceCode: String): PollResult =
        withContext(Dispatchers.IO) {
            val body =
                FormBody
                    .Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("device_code", deviceCode)
                    .add("grant_type", DEVICE_GRANT)
                    .add("scope", SCOPE)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(payload) }.getOrNull()
                    if (response.isSuccessful && json != null) {
                        val user = json.optJSONObject("user")
                        return@use PollResult.Success(
                            TokenResult(
                                accessToken = json.getString("access_token"),
                                refreshToken = json.optString("refresh_token").ifBlank { null },
                                expiresAtMillis =
                                    System.currentTimeMillis() +
                                        (json.optLong("expires_in", 3600L) * 1000L),
                                userId = user?.optLong("userId")?.takeIf { it > 0 },
                                username = user?.optString("username")?.ifBlank { null },
                            ),
                        )
                    }
                    when (json?.optString("error")) {
                        "authorization_pending" -> PollResult.Pending
                        "slow_down" -> PollResult.SlowDown
                        "expired_token" -> PollResult.Failure("expired")
                        "access_denied" -> PollResult.Failure("denied")
                        else -> PollResult.Failure(json?.optString("error").orEmpty().ifBlank { "http_${response.code}" })
                    }
                }
            }.getOrElse {
                if (it is IOException) {
                    // Offline / DNS / socket errors are transient and expected while polling; log a
                    // concise one-liner (no stack trace) to avoid flooding logcat, then back off.
                    Timber.tag("TidalAccount").w("token poll network error: %s", it.message ?: it.javaClass.simpleName)
                    PollResult.NetworkError
                } else {
                    Timber.tag("TidalAccount").w(it, "token poll error")
                    PollResult.Pending
                }
            }
        }

    /**
     * Exchanges a stored refresh token for a fresh access token via the OAuth `refresh_token`
     * grant. Tidal typically does not return a new refresh_token here, so callers should keep the
     * existing one when [TokenResult.refreshToken] is null. Returns null on failure (expired /
     * revoked / offline), signalling the caller to fall back to the public instances.
     */
    suspend fun refreshAccessToken(refreshToken: String): TokenResult? =
        withContext(Dispatchers.IO) {
            val body =
                FormBody
                    .Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .add("scope", SCOPE)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        Timber.tag("TidalAccount").w("token refresh failed: %d", response.code)
                        return@use null
                    }
                    val json = JSONObject(payload)
                    val user = json.optJSONObject("user")
                    TokenResult(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token").ifBlank { null },
                        expiresAtMillis =
                            System.currentTimeMillis() + (json.optLong("expires_in", 3600L) * 1000L),
                        userId = user?.optLong("userId")?.takeIf { it > 0 },
                        username = user?.optString("username")?.ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "token refresh error")
                null
            }
        }

    /**
     * Convenience: run the full device flow, polling until success/failure or [onCode]-provided
     * authorization expires. [onCode] is invoked once with the authorization so the UI can show
     * the code and link. Returns the token or null on failure.
     */
    suspend fun login(onCode: suspend (DeviceAuthorization) -> Unit): TokenResult? {
        val auth = requestDeviceAuthorization() ?: return null
        onCode(auth)
        val baseIntervalMs = auth.intervalSeconds.coerceAtLeast(1) * 1000L
        var intervalMs = baseIntervalMs
        var networkBackoffMs = 0L
        val deadline = System.currentTimeMillis() + auth.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs + networkBackoffMs)
            when (val result = pollForToken(auth.deviceCode)) {
                is PollResult.Success -> return result.token
                is PollResult.Pending -> networkBackoffMs = 0L // reachable again, reset backoff
                is PollResult.SlowDown -> intervalMs += 2000L
                is PollResult.NetworkError ->
                    // Exponential backoff (capped at 30s) so we don't spam while offline.
                    networkBackoffMs = (if (networkBackoffMs == 0L) 2000L else networkBackoffMs * 2).coerceAtMost(30_000L)
                is PollResult.Failure -> {
                    Timber.tag("TidalAccount").w("login failed: %s", result.reason)
                    return null
                }
            }
        }
        return null
    }

    /**
     * Resolves the subscription tier for the signed-in account. Any paid tier (HIFI, PREMIUM,
     * PREMIUM_PLUS, HIFI_PLUS, etc.) maps to [Subscription.PREMIUM]; an explicit free tier maps to
     * [Subscription.FREE]; anything indeterminate is [Subscription.UNKNOWN]. Runs on IO.
     */
    suspend fun fetchSubscription(
        accessToken: String,
        userId: Long,
    ): Subscription =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("$API_BASE/users/$userId/subscription?countryCode=$COUNTRY_CODE")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Timber.tag("TidalAccount").w("subscription lookup failed: %d", response.code)
                        return@use Subscription.UNKNOWN
                    }
                    val json = JSONObject(payload)
                    val type =
                        json
                            .optJSONObject("subscription")
                            ?.optString("type")
                            ?.uppercase()
                            .orEmpty()
                    when {
                        type.isBlank() -> Subscription.UNKNOWN
                        type.contains("FREE") -> Subscription.FREE
                        // HIFI, HIFI_PLUS, PREMIUM, PREMIUM_PLUS, etc.
                        else -> Subscription.PREMIUM
                    }
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "subscription lookup error")
                Subscription.UNKNOWN
            }
        }

    /**
     * Resolves a directly-playable stream for the given metadata using the signed-in user's own
     * Tidal account (official API), rather than a public instance. Returns null if the account
     * cannot stream the track (not found, no entitlement, or an unexpected manifest type), so the
     * caller can fall back to the public instances and then YouTube.
     *
     * [audioQuality] is a Tidal API quality string: "LOW", "HIGH", "LOSSLESS" or "HI_RES_LOSSLESS".
     */
    suspend fun resolveDirectStream(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        audioQuality: String,
        cacheDir: File,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            val trackId = searchTrackId(accessToken, title, artists, durationMs) ?: return@withContext null
            resolvePlaybackInfo(accessToken, trackId, audioQuality, durationMs, cacheDir)
        }

    /** Searches the official API for the best-matching track id. */
    private fun searchTrackId(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? {
        val primaryArtist = artists.firstOrNull().orEmpty()
        val query = URLEncoder.encode("$title $primaryArtist".trim(), "UTF-8")
        val request =
            Request
                .Builder()
                .url("$API_BASE/search/tracks?query=$query&limit=15&countryCode=$COUNTRY_CODE")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) return@use null
                val items = JSONObject(payload).optJSONArray("items") ?: return@use null

                var bestId: String? = null
                var bestScore = Int.MIN_VALUE
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optLong("id").takeIf { it > 0 }?.toString() ?: continue
                    var score = 0
                    val candTitle = item.optString("title")
                    if (candTitle.equals(title, ignoreCase = true)) {
                        score += 50
                    } else if (candTitle.contains(title, ignoreCase = true) ||
                        title.contains(candTitle, ignoreCase = true)
                    ) {
                        score += 25
                    }
                    val candArtists =
                        item.optJSONArray("artists")?.let { arr ->
                            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                        }.orEmpty()
                    if (primaryArtist.isNotBlank() &&
                        candArtists.any { it.contains(primaryArtist, ignoreCase = true) }
                    ) {
                        score += 30
                    }
                    val candDurationMs = item.optLong("duration").takeIf { it > 0 }?.times(1000L)
                    if (durationMs != null && candDurationMs != null &&
                        abs(candDurationMs - durationMs) <= 5000L
                    ) {
                        score += 20
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
                // Require at least a title or artist hit to avoid false matches.
                if (bestScore >= 40) bestId else null
            }
        }.getOrElse {
            Timber.tag("TidalAccount").w(it, "account track search error")
            null
        }
    }

    /**
     * Fetches playback info for a track from the official API, then delegates manifest handling to
     * [TidalAudioProvider.resolveAccountManifest] so both BTS (direct URL) and DASH (segmented
     * lossless/HiRes) manifests produce a playable stream. Previously this only accepted the BTS
     * manifest, so lossless/HiRes — which Tidal returns as DASH — always fell through, which is why
     * the signed-in account path never actually played.
     */
    private fun resolvePlaybackInfo(
        accessToken: String,
        trackId: String,
        audioQuality: String,
        durationMs: Long?,
        cacheDir: File,
    ): DirectStream? {
        val url =
            "$API_BASE/tracks/$trackId/playbackinfopostpaywall" +
                "?audioquality=$audioQuality&playbackmode=STREAM&assetpresentation=FULL"
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Timber.tag("TidalAccount").w("playbackinfo failed: %d", response.code)
                    return@use null
                }
                val json = JSONObject(payload)
                // Never serve a preview clip from the account path (we requested FULL, but be safe).
                if (json.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    Timber.tag("TidalAccount").w("playbackinfo returned PREVIEW; skipping account stream")
                    return@use null
                }
                val manifestB64 = json.optString("manifest").takeIf { it.isNotBlank() } ?: return@use null
                val manifestMime = json.optString("manifestMimeType").ifBlank { null }
                TidalAudioProvider.resolveAccountManifest(
                    manifestB64 = manifestB64,
                    declaredMimeType = manifestMime,
                    trackId = trackId,
                    quality = audioQuality,
                    durationMs = durationMs,
                    cacheDir = cacheDir,
                )
            }
        }.getOrElse {
            Timber.tag("TidalAccount").w(it, "playbackinfo error")
            null
        }
    }
}
