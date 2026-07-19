/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

private const val PlayerArtworkHighResPx = 1080
private const val YtimgFourThreeAspectRatio = 4f / 3f

enum class YTThumbQuality(
    val value: String,
) {
    MAXRES("maxresdefault"),
    HQ720("hq720"),
    HQ("hqdefault"),
    MQ("mqdefault"),
    DEFAULT("default"),
}

enum class YtimgResizePolicy {
    PreserveOriginal,
    MatchSourceAspect,
    AllowAnyAspect,
}

fun buildYTThumbnailUrl(
    videoId: String,
    quality: YTThumbQuality = YTThumbQuality.MAXRES,
): String = "https://i.ytimg.com/vi/$videoId/${quality.value}.jpg"

private val wHPathRegex = Regex("w\\d+-h\\d+")
private val wHParamRegex = Regex("=w(\\d+)-h(\\d+)")
private val sParamRegex = Regex("=s(\\d+)")
private val brokenSAppendRegex = Regex("-s\\d+")
private val videoIdRegex = Regex("/vi/([a-zA-Z0-9_-]{11})")

private val DEFAULT_SIZE_BUCKETS = listOf(48, 72, 96, 120, 200, 320, 480, 544, 720, 1080)

private fun getBucketSize(
    size: Int,
    buckets: List<Int>,
): Int {
    val sortedBuckets = buckets.sorted()
    for (bucket in sortedBuckets) {
        if (size <= bucket) return bucket
    }
    return sortedBuckets.lastOrNull() ?: 1080
}

private fun chooseFourThreeQuality(size: Int): YTThumbQuality = if (size <= 120) YTThumbQuality.DEFAULT else YTThumbQuality.HQ

private fun chooseLandscapeQuality(
    size: Int,
    maxresAllowed: Boolean,
): YTThumbQuality =
    when {
        size <= 320 -> YTThumbQuality.MQ
        size <= 720 || !maxresAllowed -> YTThumbQuality.HQ720
        else -> YTThumbQuality.MAXRES
    }

private fun chooseAnyAspectQuality(
    size: Int,
    maxresAllowed: Boolean,
): YTThumbQuality =
    when {
        size <= 120 -> YTThumbQuality.DEFAULT
        size <= 320 -> YTThumbQuality.MQ
        size <= 480 -> YTThumbQuality.HQ
        size <= 720 || !maxresAllowed -> YTThumbQuality.HQ720
        else -> YTThumbQuality.MAXRES
    }

/**
 * Returns an image URL sized for the requested display bounds.
 *
 * Google CDN URLs keep their existing source/crop family and only have their size parameters
 * updated. YouTube's fixed `i.ytimg.com` variants can use different aspect families, so callers
 * must choose an explicit [YtimgResizePolicy] whenever changing that family would be unsafe.
 * [YtimgResizePolicy.MatchSourceAspect] preserves the original URL when [sourceAspectRatio] is
 * missing or invalid.
 */
fun String.resize(
    width: Int? = null,
    height: Int? = null,
    maxresAllowed: Boolean = false,
    sizeBuckets: List<Int>? = null,
    // Legacy-compatible default; aspect-sensitive callers must opt in to a safer policy.
    ytimgResizePolicy: YtimgResizePolicy = YtimgResizePolicy.AllowAnyAspect,
    sourceAspectRatio: Float? = null,
): String {
    if (width == null && height == null) return this

    val isGoogleCdn = contains("googleusercontent.com") || contains("ggpht.com")
    val isYtimg = contains("i.ytimg.com")

    if (isGoogleCdn) {
        val rawW = width ?: height!!
        val rawH = height ?: width!!
        val buckets = sizeBuckets ?: DEFAULT_SIZE_BUCKETS
        val w = getBucketSize(rawW, buckets)
        val h = getBucketSize(rawH, buckets)

        if (wHPathRegex.containsMatchIn(this)) {
            return replace(wHPathRegex, "w$w-h$h")
        }

        wHParamRegex.find(this)?.let {
            return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
        }

        sParamRegex.find(this)?.let { match ->
            val before = substring(0, match.range.first)
            val after = substring(match.range.last + 1)
            return "$before=s${maxOf(w, h)}${after.replace(brokenSAppendRegex, "")}"
        }

        return this
    }

    if (isYtimg) {
        val videoId = videoIdRegex.find(this)?.groupValues?.get(1) ?: return this
        val size = maxOf(width ?: 0, height ?: 0)
        val quality =
            when (ytimgResizePolicy) {
                YtimgResizePolicy.PreserveOriginal -> {
                    return this
                }

                YtimgResizePolicy.MatchSourceAspect -> {
                    val sourceRatio =
                        sourceAspectRatio
                            ?.takeIf { it.isFinite() && it > 0f }
                            ?: return this
                    if (sourceRatio <= YtimgFourThreeAspectRatio) {
                        chooseFourThreeQuality(size)
                    } else {
                        chooseLandscapeQuality(size, maxresAllowed)
                    }
                }

                YtimgResizePolicy.AllowAnyAspect -> {
                    chooseAnyAspectQuality(size, maxresAllowed)
                }
            }
        return buildYTThumbnailUrl(videoId, quality)
    }

    return this
}

fun String.highRes(): String =
    resize(
        width = PlayerArtworkHighResPx,
        height = PlayerArtworkHighResPx,
        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
    )

fun getMusicVideoYTThumbnail(
    videoId: String?,
    ytmUrl: String?,
    isMusicVideo: Boolean,
    quality: YTThumbQuality = YTThumbQuality.HQ,
): String? = if (videoId != null && isMusicVideo) buildYTThumbnailUrl(videoId, quality) else ytmUrl

fun resolveMaxresFallback(
    url: String?,
    targetQuality: YTThumbQuality = YTThumbQuality.HQ720,
): String? {
    if (url == null) return null
    val videoId = videoIdRegex.find(url)?.groupValues?.get(1) ?: return null
    return buildYTThumbnailUrl(videoId, targetQuality)
}

private fun String.ytThumbQuality(): YTThumbQuality? {
    val variant = substringBefore('?').substringAfterLast('/').substringBefore('.')
    return YTThumbQuality.entries.firstOrNull { it.value.equals(variant, ignoreCase = true) }
}

/** Returns the next lower-quality YouTube thumbnail without crossing aspect families. */
fun getNextFallbackUrl(url: String?): String? {
    if (url == null) return null
    val targetQuality =
        when (url.ytThumbQuality()) {
            YTThumbQuality.MAXRES -> YTThumbQuality.HQ720

            YTThumbQuality.HQ720 -> YTThumbQuality.MQ

            YTThumbQuality.HQ -> YTThumbQuality.DEFAULT

            YTThumbQuality.MQ,
            YTThumbQuality.DEFAULT,
            null,
            -> return null
        }
    return resolveMaxresFallback(url, targetQuality)
}
