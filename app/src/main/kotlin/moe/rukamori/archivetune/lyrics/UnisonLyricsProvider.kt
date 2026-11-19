/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.util.Log
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.unison.Unison
import moe.rukamori.archivetune.unison.models.UnisonLyrics
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object UnisonLyricsProvider : LyricsProvider {
    init {
        Unison.logger = { message ->
            GlobalLog.append(Log.INFO, "Unison", message)
        }
    }

    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = getAttributedLyrics(id, title, artist, album, duration).map { it.lyrics }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getAllAttributedLyrics(id, title, artist, album, duration) { callback(it.lyrics) }
    }

    override suspend fun getAttributedLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<ProviderLyrics> =
        Unison
            .getLyrics(
                videoId = id,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = duration,
            ).map { it.toProviderLyrics() }

    override suspend fun getAllAttributedLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (ProviderLyrics) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
        ) { callback(it.toProviderLyrics()) }
    }

    private fun UnisonLyrics.toProviderLyrics() =
        ProviderLyrics(
            lyrics = lyrics,
            author = submitterName,
            sourceUrl = Unison.lyricsPageUrl(this),
            authorUrl = Unison.submitterPageUrl(this),
        )
}
