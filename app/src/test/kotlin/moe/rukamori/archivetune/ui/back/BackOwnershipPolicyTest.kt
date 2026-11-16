/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.back

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackOwnershipPolicyTest {
    @Test
    fun player_ownsBack_whileOpenOrOpening() {
        assertTrue(
            BackOwnershipPolicy.playerHandlesBack(
                aodModeEnabled = false,
                queueVisible = false,
                isExpandedOrExpanding = true,
            ),
        )
    }

    @Test
    fun player_releasesBack_theMomentCollapseStarts() {
        assertFalse(
            BackOwnershipPolicy.playerHandlesBack(
                aodModeEnabled = false,
                queueVisible = false,
                isExpandedOrExpanding = false,
            ),
        )
    }

    @Test
    fun player_doesNotOwnBack_whileQueueOrAodIsUp() {
        assertFalse(
            BackOwnershipPolicy.playerHandlesBack(
                aodModeEnabled = false,
                queueVisible = true,
                isExpandedOrExpanding = true,
            ),
        )
        assertFalse(
            BackOwnershipPolicy.playerHandlesBack(
                aodModeEnabled = true,
                queueVisible = false,
                isExpandedOrExpanding = true,
            ),
        )
    }

    @Test
    fun screens_takeOver_asSoonAsTheCollapseStarts() {
        assertTrue(
            BackOwnershipPolicy.screensHandleBack(
                isAppUpdateScreen = false,
                isExpandedOrExpanding = false,
            ),
        )
    }

    @Test
    fun screens_staySwitchedOff_whileThePlayerIsOpen() {
        assertFalse(
            BackOwnershipPolicy.screensHandleBack(
                isAppUpdateScreen = false,
                isExpandedOrExpanding = true,
            ),
        )
    }

    @Test
    fun screens_ownBack_onTheAppUpdateScreen_evenWhileThePlayerIsOpen() {
        assertTrue(
            BackOwnershipPolicy.screensHandleBack(
                isAppUpdateScreen = true,
                isExpandedOrExpanding = true,
            ),
        )
    }

    @Test
    fun neitherSideOwnsBack_whileTheQueueSheetIsUp() {
        assertFalse(
            BackOwnershipPolicy.playerHandlesBack(
                aodModeEnabled = false,
                queueVisible = true,
                isExpandedOrExpanding = true,
            ),
        )
        assertFalse(
            BackOwnershipPolicy.screensHandleBack(
                isAppUpdateScreen = false,
                isExpandedOrExpanding = true,
            ),
        )
    }

    @Test
    fun handOver_leavesNoGap_betweenPlayerAndScreens() {
        for (aodModeEnabled in booleanArrayOf(true, false)) {
            for (queueVisible in booleanArrayOf(true, false)) {
                val case = "aod=$aodModeEnabled queue=$queueVisible"
                assertTrue(
                    case,
                    BackOwnershipPolicy.screensHandleBack(
                        isAppUpdateScreen = false,
                        isExpandedOrExpanding = false,
                    ),
                )
                assertFalse(
                    case,
                    BackOwnershipPolicy.playerHandlesBack(
                        aodModeEnabled = aodModeEnabled,
                        queueVisible = queueVisible,
                        isExpandedOrExpanding = false,
                    ),
                )
            }
        }
    }
}
