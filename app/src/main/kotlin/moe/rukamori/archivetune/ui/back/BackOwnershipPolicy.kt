/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.back

internal object BackOwnershipPolicy {
    fun playerHandlesBack(
        aodModeEnabled: Boolean,
        queueVisible: Boolean,
        isExpandedOrExpanding: Boolean,
    ): Boolean = !aodModeEnabled && !queueVisible && isExpandedOrExpanding

    fun screensHandleBack(
        isAppUpdateScreen: Boolean,
        isExpandedOrExpanding: Boolean,
    ): Boolean = isAppUpdateScreen || !isExpandedOrExpanding

    fun <T : Any> pickSheetOwner(
        resolved: T?,
        inherited: T,
    ): T? = resolved?.takeUnless { it === inherited }
}
