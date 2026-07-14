/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import moe.rukamori.archivetune.constants.AudioSourceType

/**
 * A resolved direct-playable stream from any audio source. All providers normalize their result
 * into this shape so the playback layer can treat them uniformly.
 */
data class DirectStream(
    val uri: String,
    val mimeType: String,
    val codecs: String,
    val contentLength: Long?,
    /** Human-readable label for logging/UI, e.g. "Tidal (account) HI_RES". */
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
            AudioSourceType.QOBUZ,
            AudioSourceType.YOUTUBE,
        )

    /** YouTube is the guaranteed fallback and is always enabled, but its position is user-controlled. */
    private val ALWAYS_ENABLED = setOf(AudioSourceType.YOUTUBE)

    private fun parseType(name: String): AudioSourceType? =
        runCatching { AudioSourceType.valueOf(name.trim().uppercase()) }.getOrNull()

    /**
     * Resolves the effective ordered list of ALL sources from the stored CSV, preserving the user's
     * chosen order (including where they placed YouTube) and appending any sources missing from the
     * stored order (e.g. after an app update introduces a new one) in default order. YouTube is
     * guaranteed to be present, but its position is user-controlled: placing it earlier means the
     * app prefers YouTube's own stream over the lossless override sources listed after it.
     */
    fun parseOrder(rawOrder: String?): List<AudioSourceType> {
        val parsed =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        val merged = LinkedHashSet(parsed)
        // Append any sources not present in the stored order in their default position. When nothing
        // is stored this yields DEFAULT_ORDER (Tidal, Qobuz, YouTube), i.e. YouTube stays last.
        DEFAULT_ORDER.forEach { merged.add(it) }
        return merged.toList()
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
     * the user's chosen priority order. The single stored order is authoritative — the source at
     * the top of the list is the preferred source — so there is no separate "primary" control to
     * reconcile.
     */
    fun resolutionChain(
        rawOrder: String?,
        enabledSet: Set<String>?,
        defaults: Map<AudioSourceType, Boolean>,
    ): List<AudioSourceType> =
        parseOrder(rawOrder).filter { source ->
            isEnabled(source, enabledSet, defaults[source] ?: false)
        }
}
