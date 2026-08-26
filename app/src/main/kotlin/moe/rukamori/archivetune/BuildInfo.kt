/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import moe.rukamori.archivetune.constants.UpdateChannel

private val CanaryVersionRegex = Regex("""^N\d{8}$""")

internal val isCanaryBuild: Boolean
    get() = CanaryVersionRegex.matches(BuildConfig.VERSION_NAME)

internal val isNightlyBuild: Boolean
    get() = BuildConfig.IS_NIGHTLY_BUILD

internal val defaultUpdateChannel: UpdateChannel
    get() = if (isCanaryBuild) UpdateChannel.ARTIFACT else UpdateChannel.STABLE

internal val channelTitle: String
    get() = if (isNightlyBuild) "Nightly" else "Stable"

internal val currentBuildHash: String?
    get() = BuildConfig.NIGHTLY_BUILD_HASH.takeIf { it.isNotBlank() }

internal fun formatVersionName(
    versionName: String = BuildConfig.VERSION_NAME,
    buildHash: String? = currentBuildHash,
): String = listOfNotNull(versionName.takeIf { it.isNotBlank() }, buildHash).joinToString(" ")
