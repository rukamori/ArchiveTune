/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import timber.log.Timber

internal sealed interface CastRoutePickerScreenState {
    data object Loading : CastRoutePickerScreenState

    data class Success(
        val routes: List<CastRouteUiModel>,
    ) : CastRoutePickerScreenState

    data object Empty : CastRoutePickerScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : CastRoutePickerScreenState
}

@Immutable
internal data class CastRouteUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val selected: Boolean,
    val enabled: Boolean,
    val connecting: Boolean,
)

internal class CastRoutePickerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val router = MediaRouter.getInstance(application)
    private val _screenState = MutableStateFlow<CastRoutePickerScreenState>(CastRoutePickerScreenState.Loading)
    private var selector: MediaRouteSelector? = null
    private var callback: MediaRouter.Callback? = null
    private var emptyStateJob: Job? = null

    val screenState: StateFlow<CastRoutePickerScreenState> = _screenState.asStateFlow()

    fun startDiscovery() {
        if (callback != null) {
            refreshRoutes()
            return
        }

        val castSelector =
            runCatching { CastContext.getSharedInstance(getApplication<Application>()).mergedSelector }
                .onFailure { Timber.tag("Cast").w(it, "Unable to start Cast route discovery") }
                .getOrNull()

        if (castSelector == null) {
            _screenState.value = CastRoutePickerScreenState.Error(R.string.cast_route_picker_unavailable)
            return
        }

        selector = castSelector
        val routeCallback =
            object : MediaRouter.Callback() {
                override fun onRouteAdded(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteRemoved(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteChanged(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteSelected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()

                override fun onRouteUnselected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()
            }

        callback = routeCallback
        router.addCallback(
            castSelector,
            routeCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        refreshRoutes()
    }

    fun stopDiscovery() {
        emptyStateJob?.cancel()
        emptyStateJob = null
        callback?.let(router::removeCallback)
        callback = null
        selector = null
        _screenState.value = CastRoutePickerScreenState.Loading
    }

    fun selectRoute(routeId: String) {
        val castSelector = selector ?: return
        val route =
            router.routes.firstOrNull {
                it.id == routeId && it.isEnabled && it.matchesSelector(castSelector)
            } ?: return
        router.selectRoute(route)
        refreshRoutes()
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    private fun refreshRoutes() {
        val castSelector = selector ?: return
        val routes =
            router.routes
                .asSequence()
                .filter { it.isEnabled && !it.isDefault && !it.isBluetooth && it.matchesSelector(castSelector) }
                .map { it.toUiModel(router.selectedRoute == it) }
                .sortedWith(compareByDescending<CastRouteUiModel> { it.selected }.thenBy { it.name.lowercase() })
                .toList()

        if (routes.isEmpty()) {
            if (_screenState.value !is CastRoutePickerScreenState.Empty) {
                _screenState.value = CastRoutePickerScreenState.Loading
                scheduleEmptyState()
            }
        } else {
            emptyStateJob?.cancel()
            emptyStateJob = null
            _screenState.value = CastRoutePickerScreenState.Success(routes)
        }
    }

    private fun scheduleEmptyState() {
        if (emptyStateJob?.isActive == true) return
        emptyStateJob =
            viewModelScope.launch {
                delay(3_500)
                val castSelector = selector ?: return@launch
                if (router.routes.none { it.isEnabled && !it.isDefault && !it.isBluetooth && it.matchesSelector(castSelector) }) {
                    _screenState.value = CastRoutePickerScreenState.Empty
                }
            }
    }

    private fun MediaRouter.RouteInfo.toUiModel(selected: Boolean) =
        CastRouteUiModel(
            id = id,
            name = name.toString(),
            description = description?.toString()?.takeIf(String::isNotBlank),
            selected = selected,
            enabled = isEnabled,
            connecting = connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
        )
}
