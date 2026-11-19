/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context

data class ProviderLyrics(
    val lyrics: String,
    val author: String? = null,
    val sourceUrl: String? = null,
    val authorUrl: String? = null,
)

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }

    suspend fun getAttributedLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<ProviderLyrics> = getLyrics(id, title, artist, album, duration).map { ProviderLyrics(it) }

    suspend fun getAllAttributedLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (ProviderLyrics) -> Unit,
    ) {
        getAllLyrics(id, title, artist, album, duration) { callback(ProviderLyrics(it)) }
    }
}

fun LyricsProvider.toLyricsResult(result: ProviderLyrics): LyricsResult {
    val author = result.author?.trim()?.takeIf { it.isNotEmpty() }
    return LyricsResult(
        providerName = author?.let { "$name - $it" } ?: name,
        lyrics = result.lyrics,
        providerUrl = result.sourceUrl,
        author = author,
        authorUrl = result.authorUrl?.takeIf { author != null },
    )
}
