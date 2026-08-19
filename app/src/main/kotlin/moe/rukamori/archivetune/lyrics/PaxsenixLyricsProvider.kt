/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Auto)"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        return PaxsenixLyrics.getLyrics(
            title = title,
            artist = artist,
            duration = duration,
            apiKey = "Bearer sk-paxsenix-8nsAYxHsUXHZnzZK2Ez_tVmgeGd7OWBs6DjYjFY7ghy2_gS-"
        )
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        PaxsenixLyrics.getAllLyrics(
            title = title,
            artist = artist,
            duration = duration,
            apiKey = "Bearer sk-paxsenix-8nsAYxHsUXHZnzZK2Ez_tVmgeGd7OWBs6DjYjFY7ghy2_gS-",
            callback = callback
        )
    }
}
