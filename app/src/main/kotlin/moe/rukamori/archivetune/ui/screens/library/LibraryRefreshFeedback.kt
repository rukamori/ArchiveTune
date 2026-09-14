/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.library.LibrarySyncFailure
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.viewmodels.LibraryRefreshState

@Composable
internal fun LibraryRefreshContainer(
    state: LibraryRefreshState,
    onRefresh: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorOffset: Dp = LibraryPullToRefreshIndicatorOffset,
    content: @Composable BoxScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    LibraryRefreshFeedbackEffect(
        state = state,
        snackbarHostState = snackbarHostState,
        onErrorShown = onErrorShown,
    )

    Box(modifier = modifier) {
        ExpressivePullToRefreshBox(
            isRefreshing = state is LibraryRefreshState.Loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorOffset = indicatorOffset,
            content = content,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding),
        )
    }
}

@Composable
internal fun LibraryRefreshFeedbackEffect(
    state: LibraryRefreshState,
    snackbarHostState: SnackbarHostState,
    onErrorShown: () -> Unit,
) {
    val errorMessage =
        when ((state as? LibraryRefreshState.Error)?.failure) {
            LibrarySyncFailure.LoginRequired -> stringResource(R.string.not_logged_in_youtube)
            LibrarySyncFailure.SyncDisabled -> stringResource(R.string.sync_disabled)
            LibrarySyncFailure.RequestFailed -> stringResource(R.string.library_sync_failed)
            null -> null
        }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }
}
