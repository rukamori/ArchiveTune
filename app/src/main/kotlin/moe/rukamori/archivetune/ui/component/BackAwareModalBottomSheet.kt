/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackAwareModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        // Back is answered by the handler below, not by material3: `BottomSheet`'s own
        // `PredictiveBackHandler` sits outside the provider below and would resolve the screen
        // dispatcher these sheets are trying to escape. The cost is that a press is binary - with
        // the native path the sheet would follow the finger through a predictive back gesture.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        val scope = rememberCoroutineScope()

        // The screen tree above publishes its own dispatcher through the composition local, and
        // that dispatcher is switched off while the player is expanded. Inheriting it here wires
        // this sheet's handlers to a dispatcher that never sees presses from the sheet's own window.
        // Resolve the owner of that window instead: ComponentDialog tags its decor view from
        // `setContentView()`, which `ModalBottomSheet` calls in the dialog wrapper's `init` before it
        // composes this content, so the view tree already carries a valid owner right here - no
        // attach listener, and no frame where the sheet has no handler at all.
        val hostOwner = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
            "No NavigationEventDispatcherOwner in the composition hosting this modal bottom sheet"
        }
        val view = LocalView.current
        val windowOwner =
            remember(view, hostOwner) {
                val resolved = view.findViewTreeNavigationEventDispatcherOwner()
                if (resolved == null || resolved === hostOwner) {
                    // Same shape as the bug this wrapper exists for: the sheet answers on a
                    // dispatcher whose window never receives the press. Loud instead of silent.
                    Log.w(
                        SheetBackTag,
                        "No window-scoped NavigationEventDispatcherOwner for this sheet: back " +
                            "will land on the host dispatcher and never reach the sheet.",
                    )
                    hostOwner
                } else {
                    resolved
                }
            }

        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides windowOwner) {
            BackHandler(enabled = sheetState.isVisible) {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) currentOnDismissRequest()
                }
            }
            content()
        }
    }
}

private const val SheetBackTag = "BackAwareModalBottomSheet"
