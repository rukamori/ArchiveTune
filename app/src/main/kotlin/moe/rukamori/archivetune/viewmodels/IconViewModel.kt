/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.appicon.AppIconCatalog
import moe.rukamori.archivetune.appicon.LoadAppIconsUseCase
import moe.rukamori.archivetune.appicon.SelectAppIconUseCase
import javax.inject.Inject

sealed interface IconScreenState {
    data object Loading : IconScreenState

    data class Success(
        val model: IconScreenUiModel,
    ) : IconScreenState

    data object Empty : IconScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : IconScreenState
}

sealed interface IconScreenEffect {
    data class OpenUri(
        val uri: String,
    ) : IconScreenEffect
}

@Immutable
data class IconScreenUiModel(
    val icons: AppIconUiCollection,
    val selectedIcon: AppIconUiModel,
    val selectionInProgressId: String?,
    val hasCommunityIcons: Boolean,
)

@Immutable
data class AppIconUiModel(
    val id: String,
    val name: String?,
    @StringRes val nameResId: Int?,
    val author: String?,
    val githubAuthorUrl: String?,
    @DrawableRes val previewDrawableResId: Int,
    val isSelected: Boolean,
    val isDefault: Boolean,
)

@Immutable
data class AppIconUiCollection private constructor(
    private val values: List<AppIconUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): AppIconUiModel = values[index]

    companion object {
        fun from(values: List<AppIconUiModel>): AppIconUiCollection =
            AppIconUiCollection(values.toList())
    }
}

@HiltViewModel
class IconViewModel
    @Inject
    constructor(
        private val loadAppIcons: LoadAppIconsUseCase,
        private val selectAppIcon: SelectAppIconUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<IconScreenState>(IconScreenState.Loading)
        val state: StateFlow<IconScreenState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<IconScreenEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()

        private var loadJob: Job? = null
        private var selectionJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        fun selectIcon(iconId: String) {
            val currentModel = (_state.value as? IconScreenState.Success)?.model ?: return
            if (selectionJob?.isActive == true ||
                currentModel.selectionInProgressId != null ||
                currentModel.icons.findById(iconId)?.isSelected != false
            ) {
                return
            }

            _state.value =
                IconScreenState.Success(
                    currentModel.copy(selectionInProgressId = iconId),
                )
            selectionJob =
                viewModelScope.launch {
                    try {
                        _state.value = selectAppIcon(iconId).toScreenState()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _state.value = IconScreenState.Error(R.string.app_icon_change_failed)
                    }
                }
        }

        fun openAuthorProfile(iconId: String) {
            val icon =
                (_state.value as? IconScreenState.Success)
                    ?.model
                    ?.icons
                    ?.findById(iconId)
                    ?: return
            val githubAuthorUrl = icon.githubAuthorUrl?.takeIf(String::isNotBlank) ?: return
            _effects.tryEmit(IconScreenEffect.OpenUri(githubAuthorUrl))
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            _state.value = IconScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    try {
                        _state.value = loadAppIcons().toScreenState()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _state.value = IconScreenState.Error(R.string.app_icon_load_failed)
                    }
                }
        }

        private fun AppIconCatalog.toScreenState(): IconScreenState {
            if (icons.isEmpty()) return IconScreenState.Empty
            val uiIcons =
                icons.map { icon ->
                    AppIconUiModel(
                        id = icon.id,
                        name = icon.name,
                        nameResId = if (icon.isDefault) R.string.app_icon_default else null,
                        author = icon.author,
                        githubAuthorUrl = icon.githubAuthorUrl,
                        previewDrawableResId = icon.previewDrawableResId,
                        isSelected = icon.id == selectedIconId,
                        isDefault = icon.isDefault,
                    )
                }
            return IconScreenState.Success(
                IconScreenUiModel(
                    icons = AppIconUiCollection.from(uiIcons),
                    selectedIcon = uiIcons.firstOrNull(AppIconUiModel::isSelected) ?: uiIcons.first(),
                    selectionInProgressId = null,
                    hasCommunityIcons = uiIcons.any { icon -> !icon.isDefault },
                ),
            )
        }

        private fun AppIconUiCollection.findById(iconId: String): AppIconUiModel? {
            for (index in 0 until size) {
                val icon = this[index]
                if (icon.id == iconId) return icon
            }
            return null
        }
    }
