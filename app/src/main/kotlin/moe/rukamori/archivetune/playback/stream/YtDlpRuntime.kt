/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.content.Context
import android.net.Uri
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeStore
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpRuntime
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val resolutionPermits = Semaphore(2)
        private val pythonModuleLock = Any()

        @Volatile
        private var pythonModule: PyObject? = null

        private val cookieDirectory: File by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            File(context.cacheDir, "yt_dlp_cookies").apply {
                mkdirs()
                val staleBefore = System.currentTimeMillis() - STALE_COOKIE_FILE_MS
                listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < staleBefore) {
                        file.delete()
                    }
                }
            }
        }

        suspend fun preWarm() {
            resolutionPermits.withPermit {
                withContext(Dispatchers.IO) {
                    try {
                        val activeArchive = YtDlpRuntimeStore.activeArchive(context)
                        val module = getPythonModule()
                        module.callAttr(
                            "prewarm_runtime",
                            activeArchive?.absolutePath.orEmpty(),
                        )
                        if (
                            activeArchive != null &&
                            !module.callAttr("is_runtime_archive_loaded").toBoolean()
                        ) {
                            YtDlpRuntimeStore.rollback(context)
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        Timber.tag(TAG).w(exception, "yt-dlp runtime prewarm failed")
                    }
                }
            }
        }

        suspend fun resolve(
            request: AudioStreamRequest,
            authState: moe.rukamori.archivetune.innertube.PlaybackAuthState,
        ): ResolvedAudioStream {
            return resolutionPermits.withPermit {
                withContext(Dispatchers.IO) {
                    val module = getPythonModule()
                    val activeArchive = YtDlpRuntimeStore.activeArchive(context)
                    val requestJson =
                        JSONObject()
                            .put("media_id", request.mediaId)
                            .put("quality", request.quality.name)
                            .put("network_metered", request.networkMetered)
                            .put("pinned_format_id", request.pinnedFormatId)
                            .put("requires_song_metadata", request.requiresSongMetadata)
                            .put("cookie", authState.cookie)
                            .put("data_sync_id", authState.dataSyncId)
                            .put(
                                "po_token_web_creator_gvs_session",
                                authState.poTokenGvsSession,
                            )
                            .put(
                                "po_token_web_creator_gvs_video",
                                authState.poTokenGvs?.takeIf {
                                    authState.poTokenGvsVideoId == request.mediaId
                                },
                            )
                            .toString()
                    val response =
                        try {
                            module
                                .callAttr(
                                    "resolve_audio",
                                    requestJson,
                                    activeArchive?.absolutePath.orEmpty(),
                                    cookieDirectory.absolutePath,
                                ).toString()
                        } catch (throwable: Throwable) {
                            if (
                                activeArchive != null &&
                                runCatching {
                                    !module.callAttr("is_runtime_archive_loaded").toBoolean()
                                }.getOrDefault(false)
                            ) {
                                YtDlpRuntimeStore.rollback(context)
                            }
                            throw throwable.asYtDlpExtractionFailure()
                        }
                    parseResponse(
                        response = response,
                        request = request,
                        authFingerprint = authState.streamCacheFingerprint,
                        requestedArchive = activeArchive,
                    )
                }
            }
        }

        private fun getPythonModule(): PyObject {
            pythonModule?.let { return it }
            return synchronized(pythonModuleLock) {
                pythonModule
                    ?: run {
                        if (!Python.isStarted()) {
                            Python.start(AndroidPlatform(context))
                        }
                        Python
                            .getInstance()
                            .getModule("archivetune_ytdlp")
                            .also { pythonModule = it }
                    }
            }
        }

        private fun parseResponse(
            response: String,
            request: AudioStreamRequest,
            authFingerprint: String,
            requestedArchive: File?,
        ): ResolvedAudioStream {
            val json = JSONObject(response)
            if (requestedArchive != null && !json.optBoolean("archive_loaded", false)) {
                YtDlpRuntimeStore.rollback(context)
            }
            val headersJson = json.optJSONObject("headers")
            val headers =
                buildMap {
                    headersJson?.let { values ->
                        values.keys().forEach { name ->
                            if (values.isNull(name)) return@forEach
                            values.optString(name)
                                .takeIf { value ->
                                name.isNotBlank() &&
                                    name.none { it in CONTROL_CHARACTERS } &&
                                    value.isNotBlank() &&
                                        value.none { it in CONTROL_CHARACTERS }
                                }?.let { value -> put(name, value) }
                        }
                    }
                }
            val url = json.getString("url").trim()
            val scheme = Uri.parse(url).scheme?.lowercase()
            require(scheme != null && scheme in HTTP_SCHEMES)
            val expiresAtMs =
                json.optLong("expires_at_ms")
                    .takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + DEFAULT_STREAM_LIFETIME_MS)
            return ResolvedAudioStream(
                url = url,
                requestHeaders = headers,
                formatId = json.optString("format_id").toIntOrNull() ?: -1,
                mimeType = json.optString("mime_type", "audio/webm"),
                codecs = json.optString("codecs"),
                bitrate = json.optInt("bitrate"),
                sampleRate = json.optInt("sample_rate").takeIf { it > 0 },
                contentLength = json.optLong("content_length").coerceAtLeast(0L),
                expiresAtMs = expiresAtMs,
                authFingerprint = authFingerprint,
                source = StreamSource.YT_DLP,
                runtimeVersion = json.optString("runtime_version").takeIf(String::isNotBlank),
                title = json.optString("title").takeIf(String::isNotBlank),
                durationSeconds = json.optInt("duration_seconds").takeIf { it > 0 },
                thumbnailUrl = json.optString("thumbnail_url").takeIf(String::isNotBlank),
            )
        }

        private companion object {
            const val TAG = "YtDlpRuntime"
            const val STALE_COOKIE_FILE_MS = 60L * 60L * 1000L
            const val DEFAULT_STREAM_LIFETIME_MS = 5L * 60L * 1000L
            const val CONTROL_CHARACTERS = "\r\n"
            val HTTP_SCHEMES = setOf("http", "https")
        }
    }

internal class YtDlpExtractionException(
    cause: Throwable,
) : Exception(cause.message, cause)

private fun Throwable.asYtDlpExtractionFailure(): Throwable {
    val isYouTubeDownloadError =
        generateSequence(this) { it.cause }
            .mapNotNull(Throwable::message)
            .any { message ->
                message.contains("DownloadError: ERROR: [youtube]", ignoreCase = true)
            }
    return if (isYouTubeDownloadError) YtDlpExtractionException(this) else this
}
