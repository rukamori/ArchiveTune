/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

private const val SQUARE_RATIO = 1f
private const val LANDSCAPE_RATIO = 16f / 9f

/**
 * Geometric midpoint between the square 1:1 and landscape 16:9 display anchors.
 * Ratios below 4:3 snap to square; ratios at or above 4:3 snap to landscape.
 */
private const val SNAP_THRESHOLD = 4f / 3f

val YTItem.thumbnailSourceRatio: Float?
    get() {
        val width = thumbnailWidth
        val height = thumbnailHeight
        return if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }
    }

val YTItem.preferredThumbnailRatio: Float
    get() = preferredThumbnailRatio(cropThumbnailToSquare = false)

fun YTItem.preferredThumbnailRatio(cropThumbnailToSquare: Boolean): Float {
    if (this is ArtistItem) return SQUARE_RATIO

    if (cropThumbnailToSquare && thumbnail?.contains("ytimg.com", ignoreCase = true) == true) {
        return SQUARE_RATIO
    }

    thumbnailSourceRatio?.let { sourceRatio ->
        return if (sourceRatio >= SNAP_THRESHOLD) LANDSCAPE_RATIO else SQUARE_RATIO
    }

    return fallbackThumbnailRatio
}

private val YTItem.fallbackThumbnailRatio: Float
    get() = if (this is SongItem) LANDSCAPE_RATIO else SQUARE_RATIO
