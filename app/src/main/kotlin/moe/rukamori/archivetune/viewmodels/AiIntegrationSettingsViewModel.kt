/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiIntegrationSettingsData
import moe.rukamori.archivetune.ai.AiModelOption
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.ai.FetchAiModelsUseCase
import moe.rukamori.archivetune.ai.ObserveAiIntegrationSettingsUseCase
import moe.rukamori.archivetune.ai.TestAiIntegrationUseCase
import moe.rukamori.archivetune.ai.UpdateAiIntegrationSettingsUseCase
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiProvider
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

sealed interface AiIntegrationSettingsScreenState {
    data object Loading : AiIntegrationSettingsScreenState

    data class Success(
        val model: AiIntegrationSettingsUiModel,
    ) : AiIntegrationSettingsScreenState

    data object Empty : AiIntegrationSettingsScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AiIntegrationSettingsScreenState
}

enum class AiSettingsEditorField {
    API_KEY,
    CUSTOM_ENDPOINT,
    CUSTOM_MODEL,
    CUSTOM_PROMPT,
}

@Immutable
data class AiSettingsEditorUiModel(
    val field: AiSettingsEditorField? = null,
    val value: String = "",
    val canSave: Boolean = false,
) {
    val visible: Boolean
        get() = this.field != null
}

@Immutable
data class AiModelPickerUiModel(
    val visible: Boolean = false,
    val searchQuery: String = "",
    val filteredModels: ImmutableList<AiModelOption> = ImmutableList.of(),
)

@Immutable
data class AiIntegrationSettingsUiModel(
    val provider: AiProvider,
    val apiKey: String,
    val customEndpoint: String,
    val validationStatus: AiApiValidationStatus,
    val selectedModel: String,
    val customModel: String,
    val customPrompt: String,
    val availableModels: ImmutableList<AiModelOption>,
    val isTesting: Boolean,
    val isFetchingModels: Boolean,
    val errorMessage: String?,
    val editor: AiSettingsEditorUiModel,
    val modelPicker: AiModelPickerUiModel,
) {
    val canUseModelPicker: Boolean
        get() =
            provider != AiProvider.NONE &&
                provider != AiProvider.CUSTOM &&
                apiKey.isNotBlank()

    val canFetchModels: Boolean
        get() = apiKey.isNotBlank() && !isFetchingModels

    val canTestApi: Boolean
        get() {
            val hasEndpoint = provider != AiProvider.CUSTOM || customEndpoint.isNotBlank()
            val hasModel =
                when (provider) {
                    AiProvider.CUSTOM -> customModel.isNotBlank()
                    AiProvider.NONE -> false
                    else -> selectedModel.isNotBlank()
                }
            return provider != AiProvider.NONE &&
                apiKey.isNotBlank() &&
                hasEndpoint &&
                hasModel &&
                !isTesting
        }
}

@Immutable
private data class AiIntegrationTransientState(
    val availableModels: ImmutableList<AiModelOption> = ImmutableList.of(),
    val isTesting: Boolean = false,
    val isFetchingModels: Boolean = false,
    val errorMessage: String? = null,
    val editor: AiSettingsEditorUiModel = AiSettingsEditorUiModel(),
    val modelPickerVisible: Boolean = false,
    val modelSearchQuery: String = "",
)

private const val MaxInlineErrorLength = 140

@HiltViewModel
class AiIntegrationSettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        observeSettings: ObserveAiIntegrationSettingsUseCase,
        private val updateSettings: UpdateAiIntegrationSettingsUseCase,
        private val fetchAiModels: FetchAiModelsUseCase,
        private val testAiIntegration: TestAiIntegrationUseCase,
    ) : ViewModel() {
        private val transientState = MutableStateFlow(AiIntegrationTransientState())
        private val _events = MutableSharedFlow<Int>()
        val events: SharedFlow<Int> = _events.asSharedFlow()
        private var fetchModelsJob: Job? = null
        private var testApiJob: Job? = null
        private val fetchModelsRequestId = AtomicInteger()

        val state: StateFlow<AiIntegrationSettingsScreenState> =
            combine(observeSettings(), transientState) { settings, transient ->
                val screenState: AiIntegrationSettingsScreenState =
                    AiIntegrationSettingsScreenState.Success(settings.toUiModel(transient))
                screenState
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to load AI integration settings")
                emit(AiIntegrationSettingsScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AiIntegrationSettingsScreenState.Loading,
            )

        fun selectProvider(provider: AiProvider) {
            val model = currentModel() ?: return
            if (model.provider != provider) {
                clearAvailableModels()
            }
            updateSetting { updateSettings.setProvider(provider) }
        }

        fun openEditor(field: AiSettingsEditorField) {
            val model = currentModel() ?: return
            val value =
                when (field) {
                    AiSettingsEditorField.API_KEY -> model.apiKey
                    AiSettingsEditorField.CUSTOM_ENDPOINT -> model.customEndpoint
                    AiSettingsEditorField.CUSTOM_MODEL -> model.customModel
                    AiSettingsEditorField.CUSTOM_PROMPT -> model.customPrompt
                }
            transientState.update { state ->
                state.copy(editor = editorModel(field = field, value = value))
            }
        }

        fun updateEditorValue(value: String) {
            transientState.update { state ->
                val field = state.editor.field ?: return@update state
                state.copy(
                    editor = editorModel(field = field, value = value),
                    errorMessage = null,
                )
            }
        }

        fun dismissEditor() {
            transientState.update { it.copy(editor = AiSettingsEditorUiModel()) }
        }

        fun saveEditor() {
            val editor = transientState.value.editor
            val field = editor.field ?: return
            if (!editor.canSave) return
            dismissEditor()
            when (field) {
                AiSettingsEditorField.API_KEY -> {
                    clearAvailableModels()
                    updateSetting { updateSettings.setApiKey(editor.value) }
                }

                AiSettingsEditorField.CUSTOM_ENDPOINT -> {
                    clearAvailableModels()
                    updateSetting { updateSettings.setCustomEndpoint(editor.value) }
                }

                AiSettingsEditorField.CUSTOM_MODEL -> {
                    updateSetting { updateSettings.setCustomModel(editor.value) }
                }

                AiSettingsEditorField.CUSTOM_PROMPT -> {
                    updateSetting { updateSettings.setCustomPrompt(editor.value) }
                }
            }
        }

        fun openModelPicker() {
            val model = currentModel() ?: return
            if (model.canUseModelPicker && model.availableModels.isNotEmpty()) {
                transientState.update {
                    it.copy(modelPickerVisible = true, modelSearchQuery = "")
                }
            }
        }

        fun dismissModelPicker() {
            transientState.update {
                it.copy(modelPickerVisible = false, modelSearchQuery = "")
            }
        }

        fun updateModelSearchQuery(value: String) {
            transientState.update { it.copy(modelSearchQuery = value) }
        }

        fun selectModel(modelId: String) {
            dismissModelPicker()
            updateSetting { updateSettings.setSelectedModel(modelId) }
        }

        fun fetchModels() {
            if (fetchModelsJob?.isActive == true) return
            val model = currentModel() ?: return
            if (!model.canUseModelPicker || !model.canFetchModels) return
            val requestId = fetchModelsRequestId.incrementAndGet()
            val config =
                AiServiceConfig(
                    provider = model.provider,
                    apiKey = model.apiKey,
                    customEndpoint = model.customEndpoint,
                    model = "",
                )
            fetchModelsJob =
                viewModelScope.launch(Dispatchers.IO) {
                    transientState.update {
                        it.copy(
                            isFetchingModels = true,
                            errorMessage = null,
                            availableModels = ImmutableList.of(),
                        )
                    }
                    try {
                        val models = ImmutableList.copyOf(fetchAiModels(config))
                        if (requestId == fetchModelsRequestId.get()) {
                            transientState.update { it.copy(availableModels = models) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (requestId == fetchModelsRequestId.get()) {
                            transientState.update {
                                it.copy(errorMessage = e.shortMessage(R.string.ai_model_fetch_failed))
                            }
                        }
                    } finally {
                        if (requestId == fetchModelsRequestId.get()) {
                            transientState.update { it.copy(isFetchingModels = false) }
                            fetchModelsJob = null
                        }
                    }
                }
        }

        fun testApi() {
            if (testApiJob?.isActive == true || currentModel()?.canTestApi != true) return
            testApiJob =
                viewModelScope.launch(Dispatchers.IO) {
                    transientState.update { it.copy(isTesting = true, errorMessage = null) }
                    try {
                        testAiIntegration()
                        _events.emit(R.string.ai_api_connected)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        transientState.update {
                            it.copy(errorMessage = e.shortMessage(R.string.ai_api_test_failed))
                        }
                    } finally {
                        transientState.update { it.copy(isTesting = false) }
                        testApiJob = null
                    }
                }
        }

        private fun clearAvailableModels() {
            fetchModelsRequestId.incrementAndGet()
            fetchModelsJob?.cancel()
            fetchModelsJob = null
            transientState.update {
                it.copy(
                    availableModels = ImmutableList.of(),
                    isFetchingModels = false,
                    modelPickerVisible = false,
                    modelSearchQuery = "",
                    errorMessage = null,
                )
            }
        }

        private fun updateSetting(update: suspend () -> Unit) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    update()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update AI integration setting")
                    transientState.update {
                        it.copy(errorMessage = e.shortMessage(R.string.error_unknown))
                    }
                }
            }
        }

        private fun currentModel(): AiIntegrationSettingsUiModel? =
            (state.value as? AiIntegrationSettingsScreenState.Success)?.model

        private fun editorModel(
            field: AiSettingsEditorField,
            value: String,
        ): AiSettingsEditorUiModel =
            AiSettingsEditorUiModel(
                field = field,
                value = value,
                canSave =
                    when (field) {
                        AiSettingsEditorField.API_KEY,
                        AiSettingsEditorField.CUSTOM_MODEL,
                        -> value.isNotBlank()

                        AiSettingsEditorField.CUSTOM_ENDPOINT ->
                            value.startsWith("https://") || value.startsWith("http://")

                        AiSettingsEditorField.CUSTOM_PROMPT -> true
                    },
            )

        private fun Throwable.shortMessage(@StringRes fallbackResId: Int): String {
            val fallback = context.getString(fallbackResId)
            val raw = localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
            val message =
                raw
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { fallback }
                    .removePrefix("AI API failed ")
                    .replace(Regex("^\\((\\d{3})\\):\\s*"), "HTTP $1: ")
            return if (message.length <= MaxInlineErrorLength) {
                message
            } else {
                message.take(MaxInlineErrorLength).trimEnd() + "..."
            }
        }
    }

private fun AiIntegrationSettingsData.toUiModel(
    transient: AiIntegrationTransientState,
): AiIntegrationSettingsUiModel {
    val query = transient.modelSearchQuery.trim()
    val filteredModels =
        if (query.isBlank()) {
            transient.availableModels
        } else {
            ImmutableList.copyOf(
                transient.availableModels.filter { model ->
                    model.displayName.contains(query, ignoreCase = true) ||
                        model.id.contains(query, ignoreCase = true)
                },
            )
        }
    return AiIntegrationSettingsUiModel(
        provider = provider,
        apiKey = apiKey,
        customEndpoint = customEndpoint,
        validationStatus = validationStatus,
        selectedModel = selectedModel,
        customModel = customModel,
        customPrompt = customPrompt,
        availableModels = transient.availableModels,
        isTesting = transient.isTesting,
        isFetchingModels = transient.isFetchingModels,
        errorMessage = transient.errorMessage,
        editor = transient.editor,
        modelPicker =
            AiModelPickerUiModel(
                visible = transient.modelPickerVisible,
                searchQuery = transient.modelSearchQuery,
                filteredModels = filteredModels,
            ),
    )
}
