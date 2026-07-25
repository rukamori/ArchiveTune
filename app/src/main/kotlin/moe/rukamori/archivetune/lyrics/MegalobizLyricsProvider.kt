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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import moe.rukamori.archivetune.constants.EnableMegalobizLyricsKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object MegalobizLyricsProvider : LyricsProvider {
    override val name: String = "Megalobiz"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMegalobizLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = "$artist $title".trim()
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.megalobiz.com/searchall?qry=$encodedQuery"

                val request =
                    Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                val searchHtml =
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.body?.string()
                    } ?: return@runCatching null

                val lrcPathRegex = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""")
                val match = lrcPathRegex.find(searchHtml) ?: return@runCatching null
                val lrcUrl = "https://www.megalobiz.com" + match.groupValues[1]

                val lrcRequest =
                    Request.Builder()
                        .url(lrcUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                val detailHtml =
                    client.newCall(lrcRequest).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.body?.string()
                    } ?: return@runCatching null

                val lrcSpanRegex = Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                val rawLrcText = lrcSpanRegex.find(detailHtml)?.groupValues?.get(1) ?: detailHtml

                val cleanedText =
                    rawLrcText
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .replace("<br>", "\n")
                        .replace("<br/>", "\n")
                        .replace("<br />", "\n")
                        .replace(Regex("""<[^>]+>"""), "")
                        .trim()

                if (LyricsUtils.isLineSyncedLrc(cleanedText)) {
                    cleanedText
                } else {
                    null
                }
            }.mapCatching {
                it ?: throw Exception("Lyrics not found on Megalobiz")
            }
        }
}
