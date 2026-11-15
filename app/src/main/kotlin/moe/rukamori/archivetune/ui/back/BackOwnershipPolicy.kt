/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.back

/**
 * Single source of truth for who answers a back press while the player sheet is in flight.
 *
 * Both sides of the hand-over used to be written by hand, in two different files, against two
 * different signals: the player's handler read the live animated value (`progress > 0f`) while the
 * screen dispatcher read it too, complementarily (`progress <= 0f`). Complementary predicates are
 * only safe as long as both sides agree, and they did not: the animated value keeps a collapsing
 * sheet "visible" long after `BottomSheetState.targetAnchor` has already accepted the collapse, so
 * the player owned the press with nothing to collapse and the whole screen tree was switched off.
 *
 * Both predicates are keyed on intent now (`BottomSheetState.isExpandedOrExpanding`), and live
 * here so the truth table can be unit tested without an Android runtime:
 *
 * | player expanded | aod | queue | player handles | screens handle |
 * | --- | --- | --- | --- | --- |
 * | yes | no  | no   | yes            | no             |
 * | yes | no  | yes  | no             | no  (queue sheet owns it) |
 * | yes | yes | no   | no             | no             |
 * | no  | any | any  | no             | yes            |
 *
 * "Neither" is a legal state: the foreground modal sheet, or the activity-level last-resort
 * callback, answers the press in those rows.
 */
internal object BackOwnershipPolicy {
    /** True while the player still has something of its own to close (lyrics page, or the sheet). */
    fun playerHandlesBack(
        aodModeEnabled: Boolean,
        queueVisible: Boolean,
        isExpandedOrExpanding: Boolean,
    ): Boolean = !aodModeEnabled && !queueVisible && isExpandedOrExpanding

    /**
     * True while the screen tree underneath the player may answer. Hand-over happens on intent, not
     * on the animation, so a press landing mid-collapse pops a screen instead of being swallowed.
     */
    fun screensHandleBack(
        isAppUpdateScreen: Boolean,
        isExpandedOrExpanding: Boolean,
    ): Boolean = isAppUpdateScreen || !isExpandedOrExpanding
}
