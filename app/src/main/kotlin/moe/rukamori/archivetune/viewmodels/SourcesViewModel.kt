package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sources.SourceException
import moe.rukamori.archivetune.sources.SourceKind
import moe.rukamori.archivetune.sources.SourceProblem
import moe.rukamori.archivetune.sources.SourceSettingsUseCases
import javax.inject.Inject

@Immutable
sealed interface SourcesState {
    data object Loading : SourcesState
    data class Success(val model: SourcesUiModel) : SourcesState
    data object Empty : SourcesState
    data class Error(@param:StringRes val message: Int) : SourcesState
}

@Immutable
data class SourceRow(val id: String, val name: String, val kind: SourceKind, val enabled: Boolean,
    val moduleCount: Int, @param:StringRes val health: Int?, val canMoveUp: Boolean, val canMoveDown: Boolean)

@Immutable
data class SourceRows(val values: List<SourceRow>)

@Immutable
data class SourceEditor(val id: String? = null, val name: String = "", val url: String = "")

@Immutable
data class SourcesUiModel(val rows: SourceRows, val editor: SourceEditor?, val busy: Boolean,
    @param:StringRes val error: Int?)

sealed interface SourcesAction {
    data object Add : SourcesAction
    data class Edit(val id: String) : SourcesAction
    data class Enable(val id: String, val enabled: Boolean) : SourcesAction
    data class Move(val id: String, val offset: Int) : SourcesAction
    data class Remove(val id: String) : SourcesAction
    data class Check(val id: String) : SourcesAction
    data class Name(val value: String) : SourcesAction
    data class Url(val value: String) : SourcesAction
    data object Save : SourcesAction
    data object Dismiss : SourcesAction
}

@HiltViewModel
class SourcesViewModel @Inject constructor(private val useCases: SourceSettingsUseCases) : ViewModel() {
    private data class Controls(val editor: SourceEditor? = null, val busy: Boolean = false, val error: Int? = null)
    private val controls = MutableStateFlow(Controls())
    private var actionJob: Job? = null
    val state = combine(useCases.settings, useCases.health, controls) { settings, health, controls ->
        val rows = settings.sources.mapIndexed { index, source ->
            val checked = health[source.id]
            SourceRow(source.id, source.name, source.kind, source.enabled, source.moduleCount,
                if (checked?.checked == true) checked.problem?.message() ?: R.string.source_available else null,
                index > 0 && source.kind != SourceKind.YOUTUBE,
                index < settings.sources.lastIndex - 1 && source.kind != SourceKind.YOUTUBE)
        }
        if (rows.isEmpty()) SourcesState.Empty else SourcesState.Success(SourcesUiModel(SourceRows(rows), controls.editor, controls.busy, controls.error))
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(SourcesState.Error(R.string.sources_storage_error))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesState.Loading)

    fun onAction(action: SourcesAction) {
        when (action) {
            SourcesAction.Add -> controls.update { it.copy(editor = SourceEditor(), error = null) }
            SourcesAction.Dismiss -> {
                actionJob?.cancel()
                controls.value = Controls()
            }
            is SourcesAction.Name -> controls.update { it.copy(editor = it.editor?.copy(name = action.value), error = null) }
            is SourcesAction.Url -> controls.update { it.copy(editor = it.editor?.copy(url = action.value), error = null) }
            else -> {
                if (actionJob?.isActive == true) return
                actionJob = viewModelScope.launch {
                    controls.update { it.copy(busy = true, error = null) }
                    try {
                        when (action) {
                            is SourcesAction.Edit -> useCases.source(action.id)?.let { source ->
                                controls.update { it.copy(editor = SourceEditor(source.id, source.name, source.url)) }
                            }
                            is SourcesAction.Enable -> useCases.enable(action.id, action.enabled)
                            is SourcesAction.Move -> useCases.move(action.id, action.offset)
                            is SourcesAction.Remove -> useCases.remove(action.id)
                            is SourcesAction.Check -> useCases.check(action.id)
                            SourcesAction.Save -> controls.value.editor?.let { editor ->
                                useCases.save(editor.id, editor.url, editor.name)
                                controls.update { it.copy(editor = null) }
                            }
                            else -> Unit
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        controls.update { it.copy(error = ((failure as? SourceException)?.problem ?: SourceProblem.STORAGE).message()) }
                    } finally {
                        controls.update { it.copy(busy = false) }
                    }
                }
            }
        }
    }
}

@StringRes
private fun SourceProblem.message(): Int = when (this) {
    SourceProblem.INVALID_ADDRESS -> R.string.source_invalid_address
    SourceProblem.DUPLICATE -> R.string.source_duplicate
    SourceProblem.UNAVAILABLE -> R.string.source_unavailable
    SourceProblem.INVALID_RESPONSE -> R.string.source_invalid_response
    SourceProblem.UNSUPPORTED -> R.string.source_unsupported
    SourceProblem.NO_MATCH -> R.string.source_no_match
    SourceProblem.STORAGE -> R.string.sources_storage_error
}
