/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.net.Uri

object ArchiveTuneShareLinks {
    private const val SCHEME = "https"
    private const val HOST = "archivetune.koiiverse.cloud"
    private const val PATH = "share.html"

    const val TYPE_WATCH = "watch"
    const val TYPE_PLAYLIST = "playlist"
    const val TYPE_CHANNEL = "channel"
    const val TYPE_ALBUM = "album"

    fun buildSongShareUrl(
        videoId: String,
        playlistId: String? = null,
    ): String =
        shareUriBuilder(TYPE_WATCH, videoId)
            .apply {
                playlistId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appendQueryParameter("list", it) }
            }.build()
            .toString()

    fun buildPlaylistShareUrl(playlistId: String): String = shareUriBuilder(TYPE_PLAYLIST, playlistId).build().toString()

    fun buildChannelShareUrl(channelId: String): String = shareUriBuilder(TYPE_CHANNEL, channelId).build().toString()

    fun buildAlbumShareUrl(browseId: String): String = shareUriBuilder(TYPE_ALBUM, browseId).build().toString()

    fun isArchiveTuneShareUri(uri: Uri): Boolean =
        uri.scheme.equals(SCHEME, ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.path == "/$PATH"

    fun toYouTubeMusicUri(uri: Uri): Uri? {
        if (!isArchiveTuneShareUri(uri)) return null

        val type = uri.getQueryParameter("type")?.takeIf { it in supportedTypes } ?: return null
        val id = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
        val list = uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }
        if (list != null && type != TYPE_WATCH) return null
        return when (type) {
            TYPE_WATCH ->
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority("music.youtube.com")
                    .path("watch")
                    .appendQueryParameter("v", id)
                    .apply { list?.let { appendQueryParameter("list", it) } }
                    .build()

            TYPE_PLAYLIST ->
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority("music.youtube.com")
                    .path("playlist")
                    .appendQueryParameter("list", id)
                    .build()

            TYPE_CHANNEL ->
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority("music.youtube.com")
                    .appendPath("channel")
                    .appendPath(id)
                    .build()

            TYPE_ALBUM ->
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority("music.youtube.com")
                    .appendPath("browse")
                    .appendPath(id)
                    .build()

            else -> null
        }
    }

    fun fromYouTubeMusicShareUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (!uri.host.equals("music.youtube.com", ignoreCase = true)) return url

        return when (uri.pathSegments.firstOrNull()) {
            "watch" ->
                uri
                    .getQueryParameter("v")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { buildSongShareUrl(it, uri.getQueryParameter("list")) }
                    ?: url

            "playlist" ->
                uri
                    .getQueryParameter("list")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::buildPlaylistShareUrl)
                    ?: url

            "channel" ->
                uri
                    .lastPathSegment
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::buildChannelShareUrl)
                    ?: url

            "browse" ->
                uri
                    .lastPathSegment
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::buildAlbumShareUrl)
                    ?: url

            else -> url
        }
    }

    private fun shareUriBuilder(
        type: String,
        id: String,
    ): Uri.Builder =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(PATH)
            .appendQueryParameter("type", type)
            .appendQueryParameter("id", id)

    private val supportedTypes = setOf(TYPE_WATCH, TYPE_PLAYLIST, TYPE_CHANNEL, TYPE_ALBUM)
}
