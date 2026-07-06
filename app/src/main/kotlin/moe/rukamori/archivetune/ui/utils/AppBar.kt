/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarScrollBehavior(
    state: TopAppBarState = remember { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) },
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
): TopAppBarScrollBehavior =
    AppBarScrollBehavior(
        state = state,
        snapAnimationSpec = snapAnimationSpec,
        flingAnimationSpec = flingAnimationSpec,
        canScroll = canScroll,
    )

@ExperimentalMaterial3Api
class AppBarScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
) : TopAppBarScrollBehavior {
    // The bar physically translates (rigid quick-return slide), so it is not pinned.
    override val isPinned: Boolean = false
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero

                // The limit is a negative value set from the rendered header height (via
                // the shell). Until it's measured, do nothing so the bar can't slide
                // off-screen unbounded (rememberTopAppBarState defaults the limit to
                // -Float.MAX_VALUE).
                val limit = state.heightOffsetLimit
                if (limit >= 0f || limit == -Float.MAX_VALUE) return Offset.Zero

                // Couple the bar to real content movement (consumed.y), and add only
                // POSITIVE leftover (available.y) so the header still reveals immediately
                // at the top edge (child consumed nothing). Negative leftover is discarded:
                // at the bottom edge the list is frozen (consumed.y == 0) yet emits a
                // negative available.y, which would otherwise hide the header while the
                // content sits still and break the sticky illusion.
                val delta = consumed.y + available.y.coerceAtLeast(0f)
                if (delta != 0f) {
                    state.contentOffset += consumed.y
                    // Explicit clamp: prevents overshoot beyond [limit, 0].
                    state.heightOffset = (state.heightOffset + delta).coerceIn(limit, 0f)
                    if (state.heightOffset == 0f) {
                        // Eliminate float precision drift when fully revealed.
                        state.contentOffset = 0f
                    }
                }

                // Never consume: content scrolls normally underneath the floating bar.
                return Offset.Zero
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.resetHeightOffset() {
    if (heightOffset != 0f) {
        animate(
            initialValue = heightOffset,
            targetValue = 0f,
        ) { value, _ ->
            heightOffset = value
        }
    }
    contentOffset = 0f
}
