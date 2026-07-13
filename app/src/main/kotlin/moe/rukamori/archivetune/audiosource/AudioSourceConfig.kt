/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import moe.rukamori.archivetune.constants.AudioSourceType

/**
 * A resolved direct-playable stream from any audio source. All providers (Tidal, Deezer, Amazon)
 * normalize their result into this shape so the playback layer can treat them uniformly.
 */
data class DirectStream(
    val uri: String,
    val mimeType: String,
    val codecs: String,
    val contentLength: Long?,
    /** Human-readable label for logging/UI, e.g. "Deezer FLAC" or "Tidal (account) HI_RES". */
    val label: String,
    val source: AudioSourceType,
)

/**
 * Pure helpers for the multi-source framework. These operate on already-read preference values so
 * they stay free of any DataStore/Android dependencies and are trivially testable.
 */
object AudioSourceConfig {
    /** Sources that can actually stream lossless/hi-res, in their built-in default priority. */
    val DEFAULT_ORDER: List<AudioSourceType> =
        listOf(
            AudioSourceType.TIDAL,
            AudioSourceType.DEEZER,
            AudioSourceType.AMAZON,
            AudioSourceType.YOUTUBE,
        )

    /** YouTube is the guaranteed fallback and is always enabled + always last in the chain. */
    private val ALWAYS_ENABLED = setOf(AudioSourceType.YOUTUBE)

    private fun parseType(name: String): AudioSourceType? =
        runCatching { AudioSourceType.valueOf(name.trim().uppercase()) }.getOrNull()

    /**
     * Resolves the effective ordered list of ALL sources from the stored CSV, appending any missing
     * sources (e.g. after an app update introduces a new one) in default order, and guaranteeing
     * YouTube is present and last.
     */
    fun parseOrder(rawOrder: String?): List<AudioSourceType> {
        val parsed =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        val merged = LinkedHashSet(parsed)
        // Add any sources not present in the stored order (default-order tail).
        DEFAULT_ORDER.forEach { merged.add(it) }
        // Force YouTube to the very end as the ultimate fallback.
        merged.remove(AudioSourceType.YOUTUBE)
        return merged.toList() + AudioSourceType.YOUTUBE
    }

    /**
     * Whether a source is enabled. If the stored set is null (never configured), fall back to the
     * provided per-source defaults. YouTube is always enabled.
     */
    fun isEnabled(
        source: AudioSourceType,
        enabledSet: Set<String>?,
        default: Boolean,
    ): Boolean {
        if (source in ALWAYS_ENABLED) return true
        val set = enabledSet ?: return default
        return set.any { parseType(it) == source }
    }

    /**
     * The ordered list of sources to actually attempt for playback resolution: enabled sources in
     * priority order, with the user's primary/search source pinned to the front, and YouTube last.
     */
    fun resolutionChain(
        rawOrder: String?,
        enabledSet: Set<String>?,
        searchSource: AudioSourceType?,
        defaults: Map<AudioSourceType, Boolean>,
    ): List<AudioSourceType> {
        val ordered =
            parseOrder(rawOrder).filter { source ->
                isEnabled(source, enabledSet, defaults[source] ?: false)
            }
        if (searchSource == null || searchSource == AudioSourceType.YOUTUBE) return ordered
        if (searchSource !in ordered) return ordered
        // Pin the primary source to the front (but never move YouTube off the tail).
        return listOf(searchSource) + ordered.filter { it != searchSource }
    }
}
