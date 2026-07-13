/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas.models

import java.text.Normalizer
import java.util.Locale

fun CanvasArtwork.matchesSongIdentity(
    song: String,
    artist: String,
): Boolean {
    val requestedSong = song.toCanvasSongIdentity()
    val requestedArtist = artist.toCanvasPrimaryArtistIdentity()
    val resolvedSong = name?.toCanvasSongIdentity().orEmpty()
    val resolvedArtist = this.artist?.toCanvasPrimaryArtistIdentity().orEmpty()

    return requestedSong.isNotEmpty() &&
        requestedArtist.isNotEmpty() &&
        resolvedSong == requestedSong &&
        resolvedArtist == requestedArtist
}

private fun String.toCanvasSongIdentity(): String =
    replace(CanvasFeaturingQualifierRegex, "")
        .replace(CanvasPresentationQualifierRegex, "")
        .toCanvasIdentity()

private fun String.toCanvasPrimaryArtistIdentity(): String =
    split(CanvasArtistSeparatorRegex, limit = 2)
        .firstOrNull()
        .orEmpty()
        .toCanvasIdentity()

private fun String.toCanvasIdentity(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .replace(CanvasCombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
        .replace(CanvasIdentitySeparatorRegex, " ")
        .trim()

private val CanvasFeaturingQualifierRegex =
    Regex(
        """\s*[\[(](?:feat\.?|ft\.?|featuring|with)\s+[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasPresentationQualifierRegex =
    Regex(
        """\s*(?:[-–—:]\s*)?[\[(]?(?:official\s+)?(?:music\s+)?(?:lyric\s+video|video|audio|lyrics?|visualizer|visualiser)[\])]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasArtistSeparatorRegex =
    Regex(
        """(?:\s*,\s*|\s*&\s*|\s+x\s+|\s+(?:feat\.?|ft\.?|featuring|with)\s+)""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasCombiningMarkRegex = Regex("\\p{M}+")
private val CanvasIdentitySeparatorRegex = Regex("[^\\p{L}\\p{N}]+")
