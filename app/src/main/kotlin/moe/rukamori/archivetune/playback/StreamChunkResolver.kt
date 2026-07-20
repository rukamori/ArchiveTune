/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

internal fun resolveStreamChunkLength(
    requestedLength: Long,
    position: Long,
    knownContentLength: Long?,
    chunkLength: Long,
    mimeType: String? = null,
): Long? {
    if (chunkLength <= 0L || position < 0L) return null
    if (knownContentLength != null && position >= knownContentLength) return null
    if (requestedLength <= 0L && mimeType.requiresOpenEndedRead()) return null

    val remainingLength = knownContentLength?.minus(position)?.takeIf { it > 0L }
    val resolvedLength =
        listOfNotNull(
            chunkLength,
            requestedLength.takeIf { it > 0L },
            remainingLength,
        ).minOrNull()

    return resolvedLength?.takeIf { it > 0L }
}

private fun String?.requiresOpenEndedRead(): Boolean {
    val normalizedMimeType =
        this
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return normalizedMimeType == "audio/mp4" ||
        normalizedMimeType == "video/mp4" ||
        normalizedMimeType == "application/mp4" ||
        normalizedMimeType == "audio/x-m4a" ||
        normalizedMimeType == "audio/webm" ||
        normalizedMimeType == "video/webm" ||
        normalizedMimeType == "application/webm" ||
        normalizedMimeType == "audio/ogg" ||
        normalizedMimeType == "application/ogg"
}
