/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiModelOption
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AiIntegrationSettingsViewModel

private enum class TestApiVisualState { Idle, Testing, Success, Failed }

@Composable
fun AiIntegrationSettings(
    navController: NavController,
    viewModel: AiIntegrationSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val (provider, setProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (apiKey, setApiKey) = rememberPreference(AiApiKeyKey, "")
    val (customEndpoint, setCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (validationStatus, setValidationStatus) =
        rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    val (selectedModel, setSelectedModel) = rememberPreference(AiSelectedModelKey, "")
    val (customModel, setCustomModel) = rememberPreference(AiCustomModelKey, "")
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val hasCustomEndpoint = provider != AiProvider.CUSTOM || customEndpoint.isNotBlank()
    val hasApiConfiguration = provider != AiProvider.NONE && apiKey.isNotBlank() && hasCustomEndpoint
    val hasModelConfiguration =
        when (provider) {
            AiProvider.CUSTOM -> customModel.isNotBlank()
            AiProvider.NONE -> false
            else -> selectedModel.isNotBlank()
        }
    val canUseModelPicker =
        provider != AiProvider.NONE &&
            provider != AiProvider.CUSTOM &&
            apiKey.isNotBlank()
    val canTestApi = hasApiConfiguration && hasModelConfiguration && !actionState.isTesting

    if (showApiKeyDialog) {
        ApiKeyDialog(
            value = apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { value ->
                setApiKey(value.trim())
                setValidationStatus(AiApiValidationStatus.UNKNOWN)
                viewModel.clearAvailableModels()
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        PreferenceGroup(title = stringResource(R.string.ai_provider_settings)) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.ai_provider)) },
                    description = stringResource(R.string.ai_provider_desc),
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    selectedValue = provider,
                    values =
                        listOf(
                            AiProvider.CHATGPT,
                            AiProvider.GEMINI,
                            AiProvider.CLAUDE,
                            AiProvider.OPENROUTER,
                            AiProvider.CUSTOM,
                            AiProvider.NONE,
                        ),
                    valueText = { it.label() },
                    onValueSelected = { selectedProvider ->
                        if (provider != selectedProvider) {
                            setSelectedModel("")
                            viewModel.clearAvailableModels()
                        }
                        setProvider(selectedProvider)
                        setValidationStatus(AiApiValidationStatus.UNKNOWN)
                    },
                )
            }

            item(visible = provider == AiProvider.CUSTOM) {
                EditTextPreference(
                    title = { Text(stringResource(R.string.ai_custom_endpoint)) },
                    icon = { Icon(painterResource(R.drawable.website), null) },
                    value = customEndpoint,
                    onValueChange = {
                        setCustomEndpoint(it.trim())
                        setValidationStatus(AiApiValidationStatus.UNKNOWN)
                        viewModel.clearError()
                    },
                    isInputValid = { it.startsWith("https://") || it.startsWith("http://") },
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_api_key)) },
                    description =
                        if (apiKey.isBlank()) {
                            stringResource(R.string.ai_api_key_missing)
                        } else {
                            stringResource(R.string.ai_api_key_configured)
                        },
                    icon = { Icon(painterResource(R.drawable.token), null) },
                    onClick = { showApiKeyDialog = true },
                    isEnabled = provider != AiProvider.NONE,
                )
            }

            item(visible = provider != AiProvider.NONE && provider != AiProvider.CUSTOM) {
                ModelPickerPreference(
                    selectedModel = selectedModel,
                    availableModels = availableModels,
                    isFetching = actionState.isFetchingModels,
                    isEnabled = canUseModelPicker,
                    canFetch = apiKey.isNotBlank() && !actionState.isFetchingModels,
                    onModelSelected = {
                        setSelectedModel(it)
                        setValidationStatus(AiApiValidationStatus.UNKNOWN)
                        viewModel.clearError()
                    },
                    onFetch = { viewModel.fetchModels(provider, apiKey, customEndpoint) },
                )
            }

            item(visible = provider == AiProvider.CUSTOM) {
                EditTextPreference(
                    title = { Text(stringResource(R.string.ai_model)) },
                    icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                    value = customModel,
                    onValueChange = {
                        setCustomModel(it)
                        setValidationStatus(AiApiValidationStatus.UNKNOWN)
                        viewModel.clearError()
                    },
                )
            }

            item {
                val testVisualState =
                    when {
                        actionState.isTesting -> TestApiVisualState.Testing
                        validationStatus == AiApiValidationStatus.SUCCESS -> TestApiVisualState.Success
                        validationStatus == AiApiValidationStatus.FAILED -> TestApiVisualState.Failed
                        else -> TestApiVisualState.Idle
                    }
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ai_test_api)) },
                    icon = {
                        AnimatedContent(
                            targetState = testVisualState,
                            transitionSpec = {
                                (
                                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
                                        fadeIn(tween(200))
                                ) togetherWith
                                    (scaleOut(tween(100)) + fadeOut(tween(100)))
                            },
                            label = "testApiIcon",
                        ) { state ->
                            when (state) {
                                TestApiVisualState.Success -> {
                                    Icon(painterResource(R.drawable.done), null)
                                }

                                TestApiVisualState.Failed -> {
                                    Icon(painterResource(R.drawable.error), null, tint = MaterialTheme.colorScheme.error)
                                }

                                else -> {
                                    Icon(painterResource(R.drawable.sync), null)
                                }
                            }
                        }
                    },
                    content = {
                        Spacer(Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = testVisualState,
                            transitionSpec = {
                                (slideInVertically { -it } + fadeIn(tween(250))) togetherWith
                                    (slideOutVertically { it } + fadeOut(tween(150)))
                            },
                            label = "testApiDesc",
                        ) { state ->
                            Text(
                                text =
                                    when (state) {
                                        TestApiVisualState.Testing -> stringResource(R.string.ai_api_testing)
                                        else -> validationStatus.label()
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    when (state) {
                                        TestApiVisualState.Success -> MaterialTheme.colorScheme.primary
                                        TestApiVisualState.Failed -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        actionState.errorMessage?.let { message ->
                            Spacer(Modifier.height(10.dp))
                            AiErrorHintRow(message = message)
                        }
                    },
                    trailingContent = {
                        AnimatedContent(
                            targetState = actionState.isTesting,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(tween(150)) + fadeOut(tween(150)))
                            },
                            label = "testApiTrailing",
                        ) { isTesting ->
                            if (isTesting) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    },
                    onClick = viewModel::testApi,
                    isEnabled = canTestApi,
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
    )
}

@Composable
private fun ApiKeyDialog(
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.token), contentDescription = null) },
        title = { Text(stringResource(R.string.ai_api_key)) },
        buttons = {
            ApiKeyDialogButtons(
                canSave = field.text.isNotBlank(),
                onDismiss = onDismiss,
                onSave = {
                    onSave(field.text)
                    onDismiss()
                },
            )
        },
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            label = { Text(stringResource(R.string.ai_api_key)) },
        )
    }
}

@Composable
private fun RowScope.ApiKeyDialogButtons(
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(android.R.string.cancel))
    }
    TextButton(
        enabled = canSave,
        onClick = onSave,
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(stringResource(R.string.save))
    }
}

@Composable
private fun AiProvider.label(): String =
    when (this) {
        AiProvider.CHATGPT -> "ChatGPT"
        AiProvider.GEMINI -> "Gemini"
        AiProvider.CLAUDE -> "Claude"
        AiProvider.OPENROUTER -> "OpenRouter"
        AiProvider.CUSTOM -> stringResource(R.string.custom)
        AiProvider.NONE -> stringResource(R.string.ai_provider_none)
    }

@Composable
private fun AiApiValidationStatus.label(): String =
    when (this) {
        AiApiValidationStatus.UNKNOWN -> stringResource(R.string.ai_api_status_unknown)
        AiApiValidationStatus.SUCCESS -> stringResource(R.string.ai_api_status_success)
        AiApiValidationStatus.FAILED -> stringResource(R.string.ai_api_status_failed)
    }

@Composable
private fun AiErrorHintRow(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModelPickerPreference(
    selectedModel: String,
    availableModels: List<AiModelOption>,
    isFetching: Boolean,
    isEnabled: Boolean,
    canFetch: Boolean,
    onModelSelected: (String) -> Unit,
    onFetch: () -> Unit,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val filteredModels by remember(availableModels) {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isBlank()) {
                availableModels
            } else {
                availableModels.filter { model ->
                    model.displayName.contains(query, ignoreCase = true) ||
                        model.id.contains(query, ignoreCase = true)
                }
            }
        }
    }

    val description =
        when {
            isFetching && availableModels.isEmpty() -> stringResource(R.string.ai_model_loading)
            availableModels.isEmpty() && !canFetch -> stringResource(R.string.ai_model_api_key_required)
            availableModels.isEmpty() -> stringResource(R.string.ai_model_fetch_hint)
            selectedModel.isBlank() -> stringResource(R.string.ai_model_not_selected)
            else -> availableModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel
        }

    LaunchedEffect(showSheet) {
        if (!showSheet) {
            searchQuery = ""
        }
    }

    LaunchedEffect(isEnabled) {
        if (!isEnabled) {
            showSheet = false
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = stringResource(R.string.ai_model),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .padding(horizontal = 26.dp)
                        .padding(top = 18.dp, bottom = 22.dp),
            )
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.ai_model_search)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon =
                            if (searchQuery.isNotBlank()) {
                                {
                                    androidx.compose.material3.IconButton(
                                        onClick = { searchQuery = "" },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp)
                        .padding(bottom = 18.dp),
            ) {}
            LazyColumn(
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
            ) {
                if (filteredModels.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.ai_model_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                        )
                    }
                }
                items(
                    items = filteredModels,
                    key = { it.id },
                    contentType = { "model" },
                ) { model ->
                    val id = model.id
                    val displayName = model.displayName
                    val selected = id == selectedModel
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ).selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onModelSelected(id)
                                        coroutineScope
                                            .launch {
                                                sheetState.hide()
                                            }.invokeOnCompletion {
                                                showSheet = false
                                            }
                                    },
                                ).padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (displayName != id) {
                                Text(
                                    text = id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.ai_model)) },
        description = description,
        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
        trailingContent = {
            if (isFetching) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                FilledTonalIconButton(
                    onClick = onFetch,
                    enabled = canFetch,
                ) {
                    Icon(
                        painterResource(R.drawable.sync),
                        contentDescription = stringResource(R.string.ai_fetch_models),
                    )
                }
            }
        },
        onClick = if (isEnabled && availableModels.isNotEmpty()) ({ showSheet = true }) else null,
        isEnabled = isEnabled,
    )
}
