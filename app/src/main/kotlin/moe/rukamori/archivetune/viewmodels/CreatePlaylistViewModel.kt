/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.CreatePlaylistUseCase
import moe.rukamori.archivetune.playlist.GetCreatePlaylistOptionsUseCase
import javax.inject.Inject

sealed interface CreatePlaylistScreenState {
    data object Loading : CreatePlaylistScreenState

    @Immutable
    data class Success(
        val data: CreatePlaylistUiData,
    ) : CreatePlaylistScreenState

    data object Empty : CreatePlaylistScreenState

    @Immutable
    data class Error(
        val data: CreatePlaylistUiData,
        @StringRes val messageResId: Int,
    ) : CreatePlaylistScreenState
}

@Immutable
data class CreatePlaylistUiData(
    val name: String,
    val allowSyncing: Boolean,
    val isSignedIn: Boolean,
    val isSyncEnabled: Boolean,
    val syncRequested: Boolean,
    val isSubmitting: Boolean,
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && !isSubmitting
}

sealed interface CreatePlaylistEvent {
    data object Created : CreatePlaylistEvent
}

@HiltViewModel
class CreatePlaylistViewModel
    @Inject
    constructor(
        private val getOptions: GetCreatePlaylistOptionsUseCase,
        private val createPlaylist: CreatePlaylistUseCase,
    ) : ViewModel() {
        private val mutableScreenState =
            MutableStateFlow<CreatePlaylistScreenState>(CreatePlaylistScreenState.Loading)
        val screenState: StateFlow<CreatePlaylistScreenState> = mutableScreenState.asStateFlow()

        private val mutableEvents = MutableSharedFlow<CreatePlaylistEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<CreatePlaylistEvent> = mutableEvents.asSharedFlow()

        private var loadJob: Job? = null
        private var createJob: Job? = null

        fun open(
            initialName: String,
            allowSyncing: Boolean,
        ) {
            if (createJob?.isActive == true) return
            loadJob?.cancel()
            mutableScreenState.value = CreatePlaylistScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    val initialData =
                        CreatePlaylistUiData(
                            name = initialName,
                            allowSyncing = allowSyncing,
                            isSignedIn = false,
                            isSyncEnabled = false,
                            syncRequested = false,
                            isSubmitting = false,
                        )
                    try {
                        val options = getOptions()
                        mutableScreenState.value =
                            CreatePlaylistScreenState.Success(
                                initialData.copy(
                                    isSignedIn = options.isSignedIn,
                                    isSyncEnabled = options.isSyncEnabled,
                                ),
                            )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        mutableScreenState.value =
                            CreatePlaylistScreenState.Error(
                                data = initialData,
                                messageResId = R.string.error_unknown,
                            )
                    }
                }
        }

        fun updateName(name: String) {
            updateData { data -> data.copy(name = name) }
        }

        fun updateSyncRequested(requested: Boolean) {
            val data = currentData() ?: return
            if (!requested) {
                mutableScreenState.value =
                    CreatePlaylistScreenState.Success(
                        data.copy(syncRequested = false),
                    )
                return
            }
            if (!data.allowSyncing) return

            mutableScreenState.value =
                when {
                    !data.isSignedIn -> {
                        CreatePlaylistScreenState.Error(
                            data = data.copy(syncRequested = false),
                            messageResId = R.string.not_logged_in_youtube,
                        )
                    }

                    !data.isSyncEnabled -> {
                        CreatePlaylistScreenState.Error(
                            data = data.copy(syncRequested = false),
                            messageResId = R.string.sync_disabled,
                        )
                    }

                    else -> {
                        CreatePlaylistScreenState.Success(
                            data.copy(syncRequested = true),
                        )
                    }
                }
        }

        fun submit() {
            if (createJob?.isActive == true) return
            val data = currentData() ?: return
            if (!data.canSubmit) return

            mutableScreenState.value =
                CreatePlaylistScreenState.Success(
                    data.copy(isSubmitting = true),
                )
            createJob =
                viewModelScope.launch {
                    try {
                        createPlaylist(
                            name = data.name,
                            syncRequested = data.syncRequested,
                        )
                        mutableScreenState.value =
                            CreatePlaylistScreenState.Success(
                                data.copy(isSubmitting = false),
                            )
                        mutableEvents.emit(CreatePlaylistEvent.Created)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        mutableScreenState.value =
                            CreatePlaylistScreenState.Error(
                                data = data.copy(isSubmitting = false),
                                messageResId = R.string.error_unknown,
                            )
                    } finally {
                        createJob = null
                    }
                }
        }

        fun close() {
            loadJob?.cancel()
            loadJob = null
            if (createJob?.isActive != true) {
                mutableScreenState.value = CreatePlaylistScreenState.Loading
            }
        }

        private fun currentData(): CreatePlaylistUiData? =
            when (val state = mutableScreenState.value) {
                is CreatePlaylistScreenState.Success -> state.data
                is CreatePlaylistScreenState.Error -> state.data
                CreatePlaylistScreenState.Empty,
                CreatePlaylistScreenState.Loading,
                -> null
            }

        private inline fun updateData(transform: (CreatePlaylistUiData) -> CreatePlaylistUiData) {
            val data = currentData() ?: return
            mutableScreenState.value = CreatePlaylistScreenState.Success(transform(data))
        }
    }
