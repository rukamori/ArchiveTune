/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.storage.ClearStorageCacheUseCase
import moe.rukamori.archivetune.storage.ObserveStorageFoldersUseCase
import moe.rukamori.archivetune.storage.SetStorageFolderUseCase
import moe.rukamori.archivetune.storage.StorageCacheClearProgress
import moe.rukamori.archivetune.storage.StorageCacheClearResult
import moe.rukamori.archivetune.storage.StorageCacheKind
import moe.rukamori.archivetune.storage.StorageFolderSelection
import moe.rukamori.archivetune.storage.StorageFolderUpdateResult
import moe.rukamori.archivetune.storage.StorageLocationKind
import moe.rukamori.archivetune.storage.StorageLocationOption
import moe.rukamori.archivetune.storage.StorageLocationOptions
import moe.rukamori.archivetune.storage.StorageMigrationPhase
import moe.rukamori.archivetune.storage.StorageMigrationProgress
import javax.inject.Inject

sealed interface StorageSettingsScreenState {
    data object Loading : StorageSettingsScreenState

    data class Success(
        val model: StorageSettingsUiModel,
    ) : StorageSettingsScreenState

    data object Empty : StorageSettingsScreenState

    data class Error(
        val messageResId: Int,
    ) : StorageSettingsScreenState
}

@Immutable
data class StorageSettingsUiModel(
    val folder: StorageFolderUiModel,
    val storageOptions: StorageLocationUiOptions,
    val picker: StorageLocationPickerUiModel,
    val migration: StorageMigrationUiModel?,
    val cacheClear: StorageCacheClearUiModel?,
)

@Immutable
data class StorageFolderUiModel(
    val selectedOptionId: String,
    val kind: StorageLocationKind,
    val volumeLabel: String?,
    val availableBytes: Long,
)

@Immutable
data class StorageLocationUiOptions(
    private val values: List<StorageLocationUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): StorageLocationUiModel = values[index]

    fun firstOrNull(predicate: (StorageLocationUiModel) -> Boolean): StorageLocationUiModel? = values.firstOrNull(predicate)

    fun forEach(action: (StorageLocationUiModel) -> Unit) {
        values.forEach(action)
    }
}

@Immutable
data class StorageLocationUiModel(
    val id: String,
    val kind: StorageLocationKind,
    val volumeLabel: String?,
    val availableBytes: Long,
    val isSelected: Boolean,
)

@Immutable
data class StorageLocationPickerUiModel(
    val visible: Boolean = false,
    val selectedOptionId: String? = null,
)

@Immutable
data class StorageMigrationUiModel(
    val phase: StorageMigrationUiPhase,
    val percent: Int,
)

enum class StorageMigrationUiPhase {
    CACHE,
    DOWNLOADS,
}

@Immutable
data class StorageCacheClearUiModel(
    val kind: StorageCacheClearUiKind,
    val percent: Int,
)

enum class StorageCacheClearUiKind {
    SONGS,
    DOWNLOADS,
    IMAGES,
    CANVAS,
}

@Immutable
data class StorageSettingsEffect(
    val messageResId: Int,
    val restartApp: Boolean,
)

@HiltViewModel
class StorageSettingsViewModel
    @Inject
    constructor(
        observeStorageFolders: ObserveStorageFoldersUseCase,
        private val setStorageFolder: SetStorageFolderUseCase,
        private val clearStorageCache: ClearStorageCacheUseCase,
    ) : ViewModel() {
        private val _effects = MutableSharedFlow<StorageSettingsEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()
        private val pickerState = MutableStateFlow(StorageLocationPickerUiModel())
        private val migrationState = MutableStateFlow<StorageMigrationUiModel?>(null)
        private val cacheClearState = MutableStateFlow<StorageCacheClearUiModel?>(null)
        private val activeCacheClearKinds = mutableSetOf<StorageCacheKind>()

        val state: StateFlow<StorageSettingsScreenState> =
            combine(
                observeStorageFolders(),
                pickerState,
                migrationState,
                cacheClearState,
            ) { selection, picker, migration, cacheClear ->
                val selectedOptionId =
                    picker.selectedOptionId
                        ?.takeIf { optionId ->
                            selection.options.firstOrNull { option -> option.id == optionId } != null
                        }
                        ?: selection.selectedOption.id
                val normalizedPicker = picker.copy(selectedOptionId = selectedOptionId)
                StorageSettingsStatePayload(
                    selection = selection,
                    picker = normalizedPicker,
                    migration = migration,
                    cacheClear = cacheClear,
                )
            }.map<StorageSettingsStatePayload, StorageSettingsScreenState> { payload ->
                StorageSettingsScreenState.Success(
                    StorageSettingsUiModel(
                        folder = payload.selection.toUiModel(),
                        storageOptions = payload.selection.options.toUiOptions(),
                        picker = payload.picker,
                        migration = payload.migration,
                        cacheClear = payload.cacheClear,
                    ),
                )
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(StorageSettingsScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StorageSettingsScreenState.Loading,
            )

        fun openStorageLocationPicker() {
            val selectedOptionId =
                (state.value as? StorageSettingsScreenState.Success)
                    ?.model
                    ?.folder
                    ?.selectedOptionId
                    ?: return
            pickerState.value =
                StorageLocationPickerUiModel(
                    visible = true,
                    selectedOptionId = selectedOptionId,
                )
        }

        fun chooseStorageLocation(optionId: String) {
            pickerState.update { picker ->
                picker.copy(selectedOptionId = optionId)
            }
        }

        fun dismissStorageLocationPicker() {
            pickerState.update { picker ->
                picker.copy(visible = false)
            }
        }

        fun applyStorageLocationSelection() {
            val model = (state.value as? StorageSettingsScreenState.Success)?.model ?: return
            val optionId = model.picker.selectedOptionId ?: model.folder.selectedOptionId
            pickerState.update { picker ->
                picker.copy(visible = false)
            }
            selectStorageLocation(optionId)
        }

        fun clearSongCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.SONGS, showFeedback)
        }

        fun clearDownloads(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.DOWNLOADS, showFeedback)
        }

        fun clearImageCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.IMAGES, showFeedback)
        }

        fun clearCanvasCache(showFeedback: Boolean = true) {
            clearCache(StorageCacheKind.CANVAS, showFeedback)
        }

        private fun selectStorageLocation(optionId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                migrationState.value =
                    StorageMigrationUiModel(
                        phase = StorageMigrationUiPhase.CACHE,
                        percent = 0,
                    )
                val result =
                    withContext(NonCancellable + Dispatchers.IO) {
                        setStorageFolder(optionId) { progress ->
                            migrationState.value = progress.toUiModel()
                        }
                    }
                migrationState.value = null
                val messageResId =
                    when (result) {
                        StorageFolderUpdateResult.Success -> R.string.storage_folder_selected_restart
                        StorageFolderUpdateResult.InvalidTree -> R.string.storage_folder_invalid
                        StorageFolderUpdateResult.UnsupportedProvider -> R.string.storage_folder_unsupported
                        StorageFolderUpdateResult.NotWritable -> R.string.storage_folder_not_writable
                    }
                _effects.emit(
                    StorageSettingsEffect(
                        messageResId = messageResId,
                        restartApp = result == StorageFolderUpdateResult.Success,
                    ),
                )
            }
        }

        private fun clearCache(
            kind: StorageCacheKind,
            showFeedback: Boolean,
        ) {
            synchronized(activeCacheClearKinds) {
                if (!activeCacheClearKinds.add(kind)) return
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (showFeedback) {
                        cacheClearState.value =
                            StorageCacheClearUiModel(
                                kind = kind.toUiKind(),
                                percent = 0,
                            )
                    }
                    val result =
                        clearStorageCache(kind) { progress ->
                            if (showFeedback) {
                                cacheClearState.value = progress.toUiModel()
                            }
                        }
                    if (showFeedback) {
                        val messageResId =
                            when (result) {
                                StorageCacheClearResult.Success -> R.string.storage_cache_cleared
                                StorageCacheClearResult.Failed -> R.string.storage_cache_clear_failed
                            }
                        _effects.emit(
                            StorageSettingsEffect(
                                messageResId = messageResId,
                                restartApp = false,
                            ),
                        )
                    }
                } finally {
                    synchronized(activeCacheClearKinds) {
                        activeCacheClearKinds.remove(kind)
                    }
                    if (showFeedback) {
                        cacheClearState.value = null
                    }
                }
            }
        }

        private fun StorageFolderSelection.toUiModel(): StorageFolderUiModel =
            StorageFolderUiModel(
                selectedOptionId = selectedOption.id,
                kind = selectedOption.kind,
                volumeLabel = selectedOption.volumeLabel,
                availableBytes = selectedOption.availableBytes,
            )

        private fun StorageLocationOptions.toUiOptions(): StorageLocationUiOptions {
            val items = mutableListOf<StorageLocationUiModel>()
            forEach { option ->
                items += option.toUiModel()
            }
            return StorageLocationUiOptions(items)
        }

        private fun StorageLocationOption.toUiModel(): StorageLocationUiModel =
            StorageLocationUiModel(
                id = id,
                kind = kind,
                volumeLabel = volumeLabel,
                availableBytes = availableBytes,
                isSelected = isSelected,
            )

        private fun StorageMigrationProgress.toUiModel(): StorageMigrationUiModel =
            StorageMigrationUiModel(
                phase =
                    when (phase) {
                        StorageMigrationPhase.CACHE -> StorageMigrationUiPhase.CACHE
                        StorageMigrationPhase.DOWNLOADS -> StorageMigrationUiPhase.DOWNLOADS
                    },
                percent = percent.coerceIn(0, 100),
            )

        private fun StorageCacheClearProgress.toUiModel(): StorageCacheClearUiModel =
            StorageCacheClearUiModel(
                kind = kind.toUiKind(),
                percent = percent.coerceIn(0, 100),
            )

        private fun StorageCacheKind.toUiKind(): StorageCacheClearUiKind =
            when (this) {
                StorageCacheKind.SONGS -> StorageCacheClearUiKind.SONGS
                StorageCacheKind.DOWNLOADS -> StorageCacheClearUiKind.DOWNLOADS
                StorageCacheKind.IMAGES -> StorageCacheClearUiKind.IMAGES
                StorageCacheKind.CANVAS -> StorageCacheClearUiKind.CANVAS
            }
    }

private data class StorageSettingsStatePayload(
    val selection: StorageFolderSelection,
    val picker: StorageLocationPickerUiModel,
    val migration: StorageMigrationUiModel?,
    val cacheClear: StorageCacheClearUiModel?,
)
