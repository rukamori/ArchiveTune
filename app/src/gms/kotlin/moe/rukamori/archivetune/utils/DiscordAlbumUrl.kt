/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.Song

internal fun Song.discordAlbumMusicUrl(): String? =
    album
        ?.takeUnless { it.isLocal }
        ?.let { album ->
            album.playlistId
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://music.youtube.com/playlist?list=$it" }
                ?: album.id
                    .takeIf { it.isNotBlank() }
                    ?.let { "https://music.youtube.com/browse/$it" }
        }
