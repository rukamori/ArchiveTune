/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.DefaultLyricsSyncOffsetMode
import moe.rukamori.archivetune.constants.LyricsSyncOffsetMode
import moe.rukamori.archivetune.constants.LyricsSyncOffsetModeKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.LyricsSyncOffsetDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.utils.TranslatorLang
import moe.rukamori.archivetune.utils.TranslatorLanguages
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.viewmodels.LyricsRefetchResult
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchFormat
import moe.rukamori.archivetune.viewmodels.LyricsTranslationError
import moe.rukamori.archivetune.viewmodels.LyricsTranslationScreenState
import java.util.Locale

private enum class LyricsTranslationSource {
    AI_TRANSLATION,
    TRANSLATION,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    autoHideControlsState: State<Boolean>,
    onAutoHideControlsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    lyricsRenderViewModel: LyricsRenderViewModel,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val autoHideControls by autoHideControlsState
    val renderState by lyricsRenderViewModel.state.collectAsStateWithLifecycle()
    val preparedLyrics = (renderState as? LyricsRenderScreenState.Success)?.lyrics
    val hasTranslations = preparedLyrics?.hasTranslations == true
    val showTranslations = hasTranslations && preparedLyrics?.preferences?.showTranslations == true
    val onShowTranslationsChange = remember(lyricsRenderViewModel) {
        lyricsRenderViewModel::onShowTranslationsChanged
    }
    val translationSwitchModifier = remember(hasTranslations, showTranslations, onShowTranslationsChange) {
        Modifier
            .padding(horizontal = 8.dp)
            .toggleable(
                value = showTranslations,
                enabled = hasTranslations,
                role = Role.Switch,
                onValueChange = onShowTranslationsChange,
            )
    }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showTranslateDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSyncOffsetDialog by rememberSaveable { mutableStateOf(false) }
    val (lyricsSyncOffsetMode) = rememberEnumPreference(LyricsSyncOffsetModeKey, DefaultLyricsSyncOffsetMode)
    val isRefetching by viewModel.isRefetching.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.refetchCompletionEvents.collect { result ->
            val messageResId =
                when (result) {
                    LyricsRefetchResult.Updated -> null
                    LyricsRefetchResult.NotFound -> R.string.lyrics_not_found
                    LyricsRefetchResult.Unavailable -> R.string.lyrics_refetch_failed
                }
            messageResId?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
            onDismiss()
        }
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                viewModel.updateLyrics(mediaMetadataProvider(), it)
            },
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadata =
        remember(showSearchDialog) {
            mediaMetadataProvider()
        }
    val searchMediaArtists = remember(searchMediaMetadata) { searchMediaMetadata.artists.joinToString { it.name } }
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().title,
                ),
            )
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().artists.joinToString { it.name },
                ),
            )
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val lyricsSearchState by viewModel.lyricsSearchState.collectAsStateWithLifecycle()
    val lyricsTranslationState by viewModel.lyricsTranslationState.collectAsStateWithLifecycle()
    val isAiTranslating by viewModel.isAiTranslating.collectAsStateWithLifecycle()
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    var expandedSearchResultId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchFormatFilter by rememberSaveable { mutableStateOf<LyricsSearchFormat?>(null) }
    val currentLyrics = lyricsProvider()?.lyrics.orEmpty()
    val isTranslateEnabled =
        currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND
    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled =
        currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
            isAiProviderConfigured &&
            aiApiKey.isNotBlank() &&
            (aiProvider != AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) &&
            aiValidationStatus != AiApiValidationStatus.FAILED

    val isStandardTranslating = lyricsTranslationState is LyricsTranslationScreenState.Loading
    var isDialogAiTranslationRunning by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.aiTranslationEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(lyricsTranslationState) {
        when (val state = lyricsTranslationState) {
            LyricsTranslationScreenState.Success -> {
                showTranslateDialog = false
                viewModel.clearLyricsTranslationResult()
            }

            is LyricsTranslationScreenState.Error -> {
                val message =
                    when (val error = state.error) {
                        LyricsTranslationError.Failed -> context.getString(R.string.translation_failed)
                        is LyricsTranslationError.UnsupportedLanguage ->
                            context.getString(R.string.unsupported_language, error.languageName)
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearLyricsTranslationResult()
            }

            LyricsTranslationScreenState.Empty,
            LyricsTranslationScreenState.Loading,
            -> Unit
        }
    }

    if (isRefetching) {
        DefaultDialog(onDismiss = {}) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }

    if (showSearchDialog) {
        SearchLyricsInputDialog(
            songTitle = searchMediaMetadata.title,
            songArtists = searchMediaArtists,
            thumbnailUrl = searchMediaMetadata.thumbnailUrl,
            titleField = titleField,
            onTitleFieldChange = onTitleFieldChange,
            artistField = artistField,
            onArtistFieldChange = onArtistFieldChange,
            onDismiss = { showSearchDialog = false },
            onSearchOnline = {
                val query = "${artistField.text} ${titleField.text} lyrics".trim()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                        },
                    )
                    showSearchDialog = false
                    onDismiss()
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.lyrics_search_online_failed), Toast.LENGTH_SHORT).show()
                }
            },
            onSearch = {
                expandedSearchResultId = null
                searchFormatFilter = null
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration,
                )
                showSearchResultDialog = true

                if (!isNetworkAvailable) {
                    Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showSearchResultDialog) {
        LyricsSearchResultDialog(
            state = lyricsSearchState,
            queryTitle = titleField.text,
            queryArtists = artistField.text,
            selectedFormat = searchFormatFilter,
            onFormatSelected = { format ->
                searchFormatFilter = format
                expandedSearchResultId = null
            },
            expandedResultId = expandedSearchResultId,
            onExpandedResultChange = { resultId ->
                expandedSearchResultId = if (expandedSearchResultId == resultId) null else resultId
            },
            onRefetch = {
                expandedSearchResultId = null
                searchFormatFilter = null
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration,
                )
            },
            onResultSelected = { result ->
                onDismiss()
                viewModel.cancelSearch()
                viewModel.updateLyrics(
                    mediaMetadata = searchMediaMetadata,
                    lyrics = result.lyrics,
                    source = LyricsEntity.Source.USER_SELECTION,
                    provider = result.providerName,
                    providerUrl = result.providerUrl,
                    providerAuthor = result.author,
                    providerAuthorUrl = result.authorUrl,
                )
            },
            onDismiss = {
                expandedSearchResultId = null
                searchFormatFilter = null
                showSearchResultDialog = false
                viewModel.resetSearchState()
            },
        )
    }

    if (showLyricsSyncOffsetDialog) {
        LyricsSyncOffsetDialog(
            initialOffsetMs = lyricsSyncOffset,
            supportingText =
                stringResource(
                    when (lyricsSyncOffsetMode) {
                        LyricsSyncOffsetMode.GLOBAL -> R.string.lyrics_sync_offset_applies_global
                        LyricsSyncOffsetMode.PER_SONG -> R.string.lyrics_sync_offset_applies_per_song
                    },
                ),
            onDismiss = { showLyricsSyncOffsetDialog = false },
            onConfirm = { offsetMs ->
                onLyricsSyncOffsetChange(offsetMs)
                showLyricsSyncOffsetDialog = false
                onDismiss()
            },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val defaultLanguageCode =
        remember(configuration) {
            configuration.locales
                .get(0)
                .getDisplayLanguage(Locale.ENGLISH)
                .uppercase(Locale.US)
                .replace(' ', '_')
        }
    val (targetLanguage, setTargetLanguage) = rememberPreference(TranslatorTargetLangKey, defaultLanguageCode)
    val isTranslationInProgress = isStandardTranslating || isAiTranslating

    if (showTranslateDialog) {
        val initialText = lyricsProvider()?.lyrics.orEmpty()
        val (textFieldValue, setTextFieldValue) =
            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(text = initialText))
            }

        val languages by produceState(initialValue = emptyList<TranslatorLang>()) {
            withContext(Dispatchers.IO) {
                value = TranslatorLanguages.load(context)
            }
        }
        var sourceExpanded by remember { mutableStateOf(false) }
        var languageExpanded by remember { mutableStateOf(false) }
        var selectedSource by rememberSaveable {
            mutableStateOf(
                if (isAiTranslationEnabled) {
                    LyricsTranslationSource.AI_TRANSLATION
                } else {
                    LyricsTranslationSource.TRANSLATION
                },
            )
        }
        var selectedLanguageCode by rememberSaveable { mutableStateOf(targetLanguage.ifBlank { defaultLanguageCode }) }
        val selectedLanguageName =
            languages.firstOrNull { it.code == selectedLanguageCode }?.name ?: selectedLanguageCode
        val canUseSelectedSource = selectedSource != LyricsTranslationSource.AI_TRANSLATION || isAiTranslationEnabled

        LaunchedEffect(isAiTranslationEnabled) {
            if (!isAiTranslationEnabled && selectedSource == LyricsTranslationSource.AI_TRANSLATION) {
                selectedSource = LyricsTranslationSource.TRANSLATION
            }
        }

        LaunchedEffect(isAiTranslating, isDialogAiTranslationRunning) {
            if (isDialogAiTranslationRunning && !isAiTranslating) {
                isDialogAiTranslationRunning = false
                showTranslateDialog = false
            }
        }

        BasicAlertDialog(
            onDismissRequest = {},
            properties =
                DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            modifier =
                Modifier
                    .padding(24.dp)
                    .navigationBarsPadding()
                    .imePadding(),
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.translate),
                        contentDescription = null,
                        tint = AlertDialogDefaults.iconContentColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.translate),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = setTextFieldValue,
                            enabled = !isTranslationInProgress,
                            singleLine = false,
                            label = { Text(stringResource(R.string.lyrics)) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 220.dp),
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.source),
                                modifier = Modifier.width(96.dp),
                            )

                            ExposedDropdownMenuBox(
                                expanded = sourceExpanded,
                                onExpandedChange = {
                                    if (!isTranslationInProgress) sourceExpanded = it
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                OutlinedTextField(
                                    value =
                                        when (selectedSource) {
                                            LyricsTranslationSource.AI_TRANSLATION -> stringResource(R.string.ai_translation_menu)
                                            LyricsTranslationSource.TRANSLATION -> stringResource(R.string.translate)
                                        },
                                    onValueChange = {},
                                    enabled = !isTranslationInProgress,
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                                    },
                                    modifier =
                                        Modifier
                                            .menuAnchor(
                                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                                enabled = !isTranslationInProgress,
                                            )
                                            .fillMaxWidth(),
                                )

                                ExposedDropdownMenu(
                                    expanded = sourceExpanded,
                                    onDismissRequest = { sourceExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_translation_menu)) },
                                        enabled = isAiTranslationEnabled,
                                        onClick = {
                                            selectedSource = LyricsTranslationSource.AI_TRANSLATION
                                            sourceExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.translate)) },
                                        onClick = {
                                            selectedSource = LyricsTranslationSource.TRANSLATION
                                            sourceExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.language_label),
                                modifier = Modifier.width(96.dp),
                            )

                            ExposedDropdownMenuBox(
                                expanded = languageExpanded,
                                onExpandedChange = {
                                    if (!isTranslationInProgress) languageExpanded = it
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                OutlinedTextField(
                                    value = selectedLanguageName,
                                    onValueChange = {},
                                    enabled = !isTranslationInProgress,
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                                    },
                                    modifier =
                                        Modifier
                                            .menuAnchor(
                                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                                enabled = !isTranslationInProgress,
                                            )
                                            .fillMaxWidth(),
                                )

                                ExposedDropdownMenu(
                                    expanded = languageExpanded,
                                    onDismissRequest = { languageExpanded = false },
                                ) {
                                    languages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.name) },
                                            onClick = {
                                                selectedLanguageCode = lang.code
                                                setTargetLanguage(lang.code)
                                                languageExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.cancelLyricsTranslation()
                                if (isAiTranslating) {
                                    viewModel.cancelAiTranslation()
                                }
                                isDialogAiTranslationRunning = false
                                showTranslateDialog = false
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            enabled = !isTranslationInProgress && canUseSelectedSource,
                            onClick = {
                                val inputText = textFieldValue.text
                                val languageCode = selectedLanguageCode
                                val languageName = selectedLanguageName
                                setTargetLanguage(languageCode)

                                when (selectedSource) {
                                    LyricsTranslationSource.AI_TRANSLATION -> {
                                        isDialogAiTranslationRunning = true
                                        viewModel.translateLyricsWithAi(
                                            mediaMetadata = mediaMetadataProvider(),
                                            lyrics = inputText,
                                            targetLanguage = languageCode,
                                        )
                                    }

                                    LyricsTranslationSource.TRANSLATION -> {
                                        viewModel.translateLyrics(
                                            mediaMetadata = mediaMetadataProvider(),
                                            lyrics = inputText,
                                            targetLanguage = languageCode,
                                            targetLanguageName = languageName,
                                        )
                                    }
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            if (isTranslationInProgress) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.translate))
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions =
                        listOf(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.edit),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.edit),
                                onClick = { showEditDialog = true },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.cached),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.refetch),
                                onClick = {
                                    viewModel.refetchLyrics(mediaMetadataProvider())
                                },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.translate),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.translate),
                                onClick = { showTranslateDialog = true },
                                enabled = isTranslateEnabled,
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.speed),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.lyrics_sync_offset),
                                onClick = { showLyricsSyncOffsetDialog = true },
                            ),
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.search),
                                onClick = { showSearchDialog = true },
                            ),
                        ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
                NewMenuItem(
                    headlineContent = { Text(stringResource(R.string.show_lyrics_translation)) },
                    trailingContent = {
                        Switch(
                            checked = showTranslations,
                            onCheckedChange = null,
                            enabled = hasTranslations,
                        )
                    },
                    enabled = hasTranslations,
                    modifier = translationSwitchModifier,
                )
                NewMenuItem(
                    headlineContent = {
                        Text(stringResource(R.string.lyrics_auto_hide_controls))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.lyrics_auto_hide_controls_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = autoHideControls,
                            onCheckedChange = onAutoHideControlsChange,
                        )
                    },
                    onClick = {
                        onAutoHideControlsChange(!autoHideControls)
                    },
                    modifier =
                        Modifier.padding(
                            start = 8.dp,
                            end = 8.dp,
                            bottom = 8.dp,
                        ),
                )
            }
        }
    }
}
