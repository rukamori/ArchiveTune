/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.db.entities.Song
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ListenBrainzManager {
    private val logTag = "ListenBrainzManager"
    private val started = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var lifecycleObserver: Any? = null
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    private val _lastSubmitTime = MutableStateFlow<Long?>(null)
    val lastSubmitTimeFlow = _lastSubmitTime.asStateFlow()

    private fun extractArtistName(artist: Any): String {
        try {
            val getterNames = listOf("getName", "getArtistName", "name")
            for (methodName in getterNames) {
                try {
                    val method = artist.javaClass.getMethod(methodName)
                    val result = method.invoke(artist)
                    if (result is String && result.isNotBlank()) {
                        return result
                    }
                } catch (e: Exception) {
                }
            }

            val fieldNames = listOf("name", "artistName")
            for (fieldName in fieldNames) {
                try {
                    val field = artist.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val result = field.get(artist)
                    if (result is String && result.isNotBlank()) {
                        return result
                    }
                } catch (e: Exception) {
                }
            }

            val str = artist.toString()
            val namePattern = Regex("""name\s*=\s*([^,)\]]+)""")
            val match = namePattern.find(str)
            if (match != null) {
                val extractedName = match.groupValues[1].trim()
                if (extractedName.isNotBlank()) {
                    return extractedName
                }
            }

            return str
        } catch (e: Exception) {
            Timber.tag(logTag).w(e, "extractArtistName failed, using toString()")
            return artist.toString()
        }
    }

    suspend fun submitPlayingNow(
        context: Context,
        token: String,
        song: Song?,
        positionMs: Long,
    ): Boolean {
        if (token.isBlank()) return false
        if (song == null) return false
        return withContext(Dispatchers.IO) {
            try {
                val listenedAt = System.currentTimeMillis() / 1000L
                val duration = song.song.duration
                val durationMs = (duration * 1000).toLong()
                val artistNames =
                    song.artists
                        .map { artist -> extractArtistName(artist) }
                        .joinToString(" & ")
                val releaseName = song.album?.title ?: ""
                val releasePart = if (releaseName.isBlank()) "" else "\"release_name\":\"${escapeJson(releaseName)}\","
                val trackMetadata = "{\"track_metadata\":{\"artist_name\":\"${escapeJson(
                    artistNames,
                )}\",\"track_name\":\"${escapeJson(
                    song.title,
                )}\",${releasePart}\"additional_info\":{\"duration_ms\":$durationMs,\"position_ms\":$positionMs,\"submission_client\":\"ArchiveTune\"}}}"
                val listensJson = "[$trackMetadata]"
                val bodyJson = "{\"listen_type\":\"playing_now\",\"payload\":$listensJson}"
                Timber.tag(logTag).d("submitPlayingNow JSON: %s", bodyJson)
                val mediaType = "application/json".toMediaType()
                val body = bodyJson.toRequestBody(mediaType)
                val request =
                    Request
                        .Builder()
                        .url("https://api.listenbrainz.org/1/submit-listens")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Token $token")
                        .build()

                httpClient.newCall(request).execute().use { resp ->
                    val success = resp.isSuccessful
                    if (success) {
                        _lastSubmitTime.value = System.currentTimeMillis()
                        Timber.tag(logTag).d("playing_now submitted for %s", song.title)
                    } else {
                        val respBody =
                            try {
                                resp.body?.string() ?: ""
                            } catch (e: Exception) {
                                "<unable to read>"
                            }
                        Timber.tag(logTag).w("playing_now submit failed: %s - %s", resp.code, respBody)
                    }
                    success
                }
            } catch (ex: Exception) {
                Timber.tag(logTag).e(ex, "submitPlayingNow failed")
                false
            }
        }
    }

    suspend fun submitFinished(
        context: Context,
        token: String,
        song: Song?,
        startMs: Long,
        endMs: Long,
    ): Boolean {
        if (token.isBlank()) return false
        if (song == null) return false
        return withContext(Dispatchers.IO) {
            try {
                val listenedAt = endMs / 1000L
                val duration = song.song.duration
                val durationMs = (duration * 1000).toLong()
                val artistNames =
                    song.artists
                        .map { artist -> extractArtistName(artist) }
                        .joinToString(" & ")
                val releaseName = song.album?.title ?: ""
                val releasePart = if (releaseName.isBlank()) "" else "\"release_name\":\"${escapeJson(releaseName)}\","
                var listenedAtStart = (startMs / 1000L)
                val MIN_LISTEN_TS = 1033430400L
                if (listenedAtStart < MIN_LISTEN_TS) {
                    Timber.tag(logTag).w("listened_at %s looks too small, replacing with current epoch seconds", listenedAtStart)
                    listenedAtStart = System.currentTimeMillis() / 1000L
                }
                val trackMetadataSingle = "{\"listened_at\":$listenedAtStart,\"track_metadata\":{\"artist_name\":\"${escapeJson(
                    artistNames,
                )}\",\"track_name\":\"${escapeJson(
                    song.title,
                )}\",${releasePart}\"additional_info\":{\"duration_ms\":$durationMs,\"start_ms\":$startMs,\"end_ms\":$endMs,\"submission_client\":\"ArchiveTune\"}}}"
                val listensJson = "[$trackMetadataSingle]"
                val bodyJson = "{\"listen_type\":\"single\",\"payload\":$listensJson}"
                Timber.tag(logTag).d("submitFinished JSON: %s", bodyJson)
                val mediaType = "application/json".toMediaType()
                val body = bodyJson.toRequestBody(mediaType)
                val request =
                    Request
                        .Builder()
                        .url("https://api.listenbrainz.org/1/submit-listens")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Token $token")
                        .build()

                httpClient.newCall(request).execute().use { resp ->
                    val success = resp.isSuccessful
                    if (success) {
                        _lastSubmitTime.value = System.currentTimeMillis()
                        Timber.tag(logTag).d("finished listen submitted for %s", song.title)
                    } else {
                        val respBody =
                            try {
                                resp.body?.string() ?: ""
                            } catch (e: Exception) {
                                "<unable to read>"
                            }
                        Timber.tag(logTag).w("finished listen submit failed: %s - %s", resp.code, respBody)
                    }
                    success
                }
            } catch (ex: Exception) {
                Timber.tag(logTag).e(ex, "submitFinished failed")
                false
            }
        }
    }

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun isRunning(): Boolean = started.get()
}
