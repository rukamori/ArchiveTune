/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import java.util.Locale

object SupportedLocalAudio {
    private val supportedExtensions =
        setOf(
            "aac",
            "amr",
            "flac",
            "m4a",
            "m4b",
            "m4p",
            "mid",
            "mka",
            "mp3",
            "mp4",
            "oga",
            "ogg",
            "opus",
            "wav",
            "weba",
            "webm",
            "3ga",
            "3gp",
        )

    private val blockedExtensions =
        setOf(
            "ape",
            "cue",
            "dsd",
            "dsf",
            "dff",
            "midi",
            "iso",
            "tta",
            "wv",
            "wvc",
        )

    private val blockedMimeTypes =
        setOf(
            "audio/x-ape",
            "audio/x-dsd",
            "audio/x-dsf",
            "audio/x-ms-wma",
            "audio/x-tta",
            "audio/x-wavpack",
            "audio/x-mpegurl",
            "audio/mpegurl",
            "audio/x-scpls",
            "audio/x-flac+cue",
        )

    private val supportedMimeTypes =
        setOf(
            "audio/aac",
            "audio/aac-adts",
            "audio/amr",
            "audio/amr-wb",
            "audio/flac",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "audio/opus",
            "audio/wav",
            "audio/wave",
            "audio/webm",
            "audio/x-aac",
            "audio/x-flac",
            "audio/x-m4a",
            "audio/x-mka",
            "audio/x-matroska",
            "audio/x-mpeg",
            "audio/x-wav",
            "application/ogg",
            "video/mp4",
            "video/webm",
            "video/3gpp",
        )

    fun isSupported(
        displayName: String?,
        mimeType: String?,
    ): Boolean {
        val extension =
            displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()

        if (extension in blockedExtensions) return false
        if (extension in supportedExtensions) return true

        val normalizedMime =
            mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()

        if (normalizedMime in blockedMimeTypes) return false
        return normalizedMime in supportedMimeTypes || normalizedMime.startsWith("audio/")
    }
}
