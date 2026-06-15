/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.models.MediaMetadata

@Immutable
data class LibraryTopMix(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)

@Immutable
data class GeneratedLibraryTopMix(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)
