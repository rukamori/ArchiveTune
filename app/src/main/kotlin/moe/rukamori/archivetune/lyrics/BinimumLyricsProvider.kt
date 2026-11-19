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
import moe.rukamori.archivetune.binimum.BinimumLyrics
import moe.rukamori.archivetune.constants.EnableBinimumLyricsKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.io.IOException

object BinimumLyricsProvider : LyricsProvider {
    override val name = "Binimum Lyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBinimumLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            BinimumLyrics.getLyrics(title = title, artist = artist, album = album, durationSeconds = duration).mapCatching { lyrics ->
                if (!LyricsUtils.isTtml(lyrics)) throw IOException("Invalid TTML response from Binimum Lyrics")
                lyrics
            }
        }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            BinimumLyrics.getAllLyrics(title = title, artist = artist, album = album, durationSeconds = duration) { lyrics ->
                if (LyricsUtils.isTtml(lyrics)) callback(lyrics)
            }
        }
}
