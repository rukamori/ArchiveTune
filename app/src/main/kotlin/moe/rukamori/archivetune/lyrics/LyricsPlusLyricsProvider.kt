/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.EnableLyricsPlusKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Community "LyricsPlus" provider (the source Metrolist migrated to). It returns richly-synced
 * lyrics — including word-by-word timing — from a pool of mirror servers. We query
 * `/v2/lyrics/get`, remember the last server that worked, and convert the JSON response into the
 * enhanced LRC dialect this fork's lyric parser understands (`[mm:ss.xx]` line timing with inline
 * `<mm:ss.xx>` word markers, plus same-timestamp translation lines).
 *
 * Implemented with OkHttp + org.json (the same stack used elsewhere in the app module) so it is
 * fully self-contained and does not depend on the read-only `:lyrics` submodule.
 */
object LyricsPlusLyricsProvider : LyricsProvider {
    override val name = "LyricsPlus"

    // Mirror pool (kept in sync with the community server list). Order is a sensible default; the
    // last server that produced lyrics is tried first on subsequent lookups.
    private val baseUrls =
        listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus-seven.vercel.app",
        )

    @Volatile
    private var lastWorkingServer: String? = null

    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLyricsPlusKey] ?: false

    private fun prioritizedServers(): List<String> {
        val last = lastWorkingServer
        return if (last != null && last in baseUrls) {
            listOf(last) + baseUrls.filter { it != last }
        } else {
            baseUrls
        }
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || artist.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing title/artist"))
            }
            for (base in prioritizedServers()) {
                val lrc =
                    runCatching { fetchAndConvert(base, title, artist, album, duration) }
                        .onFailure { Timber.tag("LyricsPlus").d(it, "fetch failed from %s", base) }
                        .getOrNull()
                if (!lrc.isNullOrBlank()) {
                    lastWorkingServer = base
                    return@withContext Result.success(lrc)
                }
            }
            Result.failure(IllegalStateException("Lyrics unavailable"))
        }

    private fun fetchAndConvert(
        base: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): String? {
        val url =
            StringBuilder("$base/v2/lyrics/get")
                .append("?title=").append(encode(title))
                .append("&artist=").append(encode(artist))
                .apply {
                    // API expects duration in seconds; MediaMetadata stores milliseconds.
                    if (duration > 0) append("&duration=").append(duration / 1000)
                    if (!album.isNullOrBlank()) append("&album=").append(encode(album))
                }.toString()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "ArchiveTune")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val payload = response.body?.string().orEmpty().ifBlank { return null }
            return convertToLrc(JSONObject(payload))
        }
    }

    /**
     * Converts a LyricsPlus JSON response into the enhanced LRC dialect ArchiveTune's
     * [LyricsUtils.parseLyrics] understands:
     *   [mm:ss.xx]<mm:ss.xx>word1 <mm:ss.xx>word2   ← line time + inline word timestamps
     *   [mm:ss.xx]translated text                    ← duplicate timestamp → provider translation
     *
     * Word timestamps use the `<mm:ss.xx>` form matched by `ENHANCED_LRC_WORD_TIME_REGEX`; the
     * renderer keeps line-level sync and gracefully strips the word markers for display. We do NOT
     * emit Metrolist's `{agent:v1}` / `<word:start:end|...>` tags because this fork's LRC parser
     * only supports word/voice karaoke through TTML — those tags would otherwise show as literal
     * text. Background-vocal syllables are folded into their line's own timestamped entry.
     */
    private fun convertToLrc(root: JSONObject): String? {
        val lines = root.optJSONArray("lyrics") ?: return null
        if (lines.length() == 0) return null
        val isWordSync = root.optString("type").equals("Word", ignoreCase = true)

        val sb = StringBuilder(lines.length() * 96)
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val lineTime = line.optLong("time", 0L)
            val syllabus = line.optJSONArray("syllabus")
            val words =
                buildList {
                    if (syllabus != null) {
                        for (w in 0 until syllabus.length()) {
                            syllabus.optJSONObject(w)?.let { add(it) }
                        }
                    }
                }.filter { it.optString("text").isNotBlank() }

            val body =
                if (isWordSync && words.isNotEmpty()) {
                    buildEnhancedLine(words)
                } else {
                    line.optString("text").trim()
                }
            if (body.isBlank()) continue

            sb.append(formatTime(lineTime, '[', ']')).append(body).append('\n')

            // A translation sharing the same timestamp is merged as the line's translation.
            val translation = line.optJSONObject("translation")?.optString("text")?.trim()
            if (!translation.isNullOrBlank()) {
                sb.append(formatTime(lineTime, '[', ']')).append(translation).append('\n')
            }
        }
        return sb.toString().trimEnd().ifBlank { null }
    }

    /** Builds a line with an inline `<mm:ss.xx>` timestamp before each word. */
    private fun buildEnhancedLine(words: List<JSONObject>): String =
        buildString {
            words.forEachIndexed { index, word ->
                val text = word.optString("text").trim()
                if (text.isEmpty()) return@forEachIndexed
                if (index > 0 && isNotEmpty()) append(' ')
                append(formatTime(word.optLong("time", 0L), '<', '>')).append(text)
            }
        }.trim()

    private fun formatTime(
        timeMs: Long,
        open: Char,
        close: Char,
    ): String {
        val safe = timeMs.coerceAtLeast(0L)
        val m = safe / 60000
        val s = (safe % 60000) / 1000
        val c = (safe % 1000) / 10
        return buildString {
            append(open)
            if (m < 10) append('0')
            append(m).append(':')
            if (s < 10) append('0')
            append(s).append('.')
            if (c < 10) append('0')
            append(c).append(close)
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
