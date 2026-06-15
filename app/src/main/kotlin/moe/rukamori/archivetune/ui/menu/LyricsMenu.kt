/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.app.SearchManager
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.TranslatorLang
import moe.rukamori.archivetune.utils.TranslatorLanguages
import moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showTranslateDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSyncOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showRefetchLoadingDialog by rememberSaveable { mutableStateOf(false) }
    val isRefetching by viewModel.isRefetching.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isRefetching) {
        if (!isRefetching && showRefetchLoadingDialog) {
            showRefetchLoadingDialog = false
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
    val isAiTranslating by viewModel.isAiTranslating.collectAsStateWithLifecycle()
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    var expandedSearchResultId by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedSearchResult = (lyricsSearchState as? LyricsSearchScreenState.Success)
        ?.results
        ?.firstOrNull { result -> result.id == expandedSearchResultId }
    val isTranslateEnabled = !isTtml(lyricsProvider()?.lyrics.orEmpty()) &&
        (expandedSearchResult?.let { !it.isWordSynced } ?: true)
    val currentLyrics = lyricsProvider()?.lyrics.orEmpty()
    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled = currentLyrics.isNotBlank() &&
        currentLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
        isAiProviderConfigured &&
        aiApiKey.isNotBlank() &&
        (aiProvider != AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) &&
        aiValidationStatus != AiApiValidationStatus.FAILED

    LaunchedEffect(Unit) {
        viewModel.aiTranslationEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (isAiTranslating) {
        DefaultDialog(onDismiss = {}) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(12.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.ai_translating_lyrics),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showRefetchLoadingDialog) {
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
            titleField = titleField,
            onTitleFieldChange = onTitleFieldChange,
            artistField = artistField,
            onArtistFieldChange = onArtistFieldChange,
            onDismiss = { showSearchDialog = false },
            onSearchOnline = {
                showSearchDialog = false
                onDismiss()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(
                                SearchManager.QUERY,
                                "${artistField.text} ${titleField.text} lyrics"
                            )
                        },
                    )
                } catch (_: Exception) {
                }
            },
            onSearch = {
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration
                )
                showSearchResultDialog = true

                if (!isNetworkAvailable) {
                    Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSearchResultDialog) {
        LyricsSearchResultDialog(
            state = lyricsSearchState,
            expandedResultId = expandedSearchResultId,
            onExpandedResultChange = { resultId ->
                expandedSearchResultId = if (expandedSearchResultId == resultId) null else resultId
            },
            onResultSelected = { result ->
                onDismiss()
                viewModel.cancelSearch()
                viewModel.updateLyrics(
                    mediaMetadata = searchMediaMetadata,
                    lyrics = result.lyrics,
                    source = LyricsEntity.Source.USER_SELECTION,
                )
            },
            onDismiss = {
                expandedSearchResultId = null
                showSearchResultDialog = false
                viewModel.resetSearchState()
            },
        )
    }

    if (showLyricsSyncOffsetDialog) {
        var tempLyricsSyncOffset by remember { mutableFloatStateOf(lyricsSyncOffset.toFloat()) }

        DefaultDialog(
            onDismiss = {
                tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
                showLyricsSyncOffsetDialog = false
            },
            icon = {
                Icon(painter = painterResource(R.drawable.speed), contentDescription = null)
            },
            title = { Text(stringResource(R.string.lyrics_sync_offset)) },
            buttons = {
                TextButton(
                    onClick = { tempLyricsSyncOffset = 0f },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
                        showLyricsSyncOffsetDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onLyricsSyncOffsetChange(tempLyricsSyncOffset.roundToInt())
                        showLyricsSyncOffsetDialog = false
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = formatLyricsSyncOffset(tempLyricsSyncOffset.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Slider(
                    value = tempLyricsSyncOffset,
                    onValueChange = { tempLyricsSyncOffset = it },
                    valueRange = -1000f..1000f,
                    steps = 79,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
            var expanded by remember { mutableStateOf(false) }
            val defaultLanguageCode = remember(configuration) {
                configuration.locales.get(0)
                    .getDisplayLanguage(Locale.ENGLISH)
                    .uppercase(Locale.US)
                    .replace(' ', '_')
            }
            var selectedLanguageCode by rememberSaveable { mutableStateOf(defaultLanguageCode) }
            var isTranslating by remember { mutableStateOf(false) }
            val selectedLanguageName =
                languages.firstOrNull { it.code == selectedLanguageCode }?.name ?: selectedLanguageCode

            DefaultDialog(
                onDismiss = { showTranslateDialog = false },
                icon = {
                    Icon(painter = painterResource(R.drawable.translate), contentDescription = null)
                },
                title = { Text(stringResource(R.string.translate)) },
                buttons = {
                    TextButton(onClick = { showTranslateDialog = false }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isTranslating) {
                        CircularWavyProgressIndicator(
                        modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterVertically),
                        )
                    } else {
                        TextButton(onClick = {
                            isTranslating = true
                            val inputText = textFieldValue.text
                            val languageCode = selectedLanguageCode
                            val languageName = selectedLanguageName
                            coroutineScope.launch {
                                try {
                                    val lang = try {
                                        Language(languageCode)
                                    } catch (e: Exception) {
                                        try { Language(languageName) } catch (_: Exception) { null }
                                    }

                                    if (lang == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.unsupported_language, languageName),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }

                                    val translatedLyrics = withContext(Dispatchers.IO) {
                                        val translator = Translator()

                                        val lines = inputText.split("\n")
                                        val tsRegex =
                                            Regex("^((?:\\[[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?\\])+)")
                                        val contents = mutableListOf<String?>()
                                        val stampsFor = mutableListOf<String?>()

                                        for (line in lines) {
                                            val trimmed = line.trimEnd()
                                            val m = tsRegex.find(trimmed)
                                            if (m != null) {
                                                val stamps = m.groupValues[1]
                                                val content =
                                                    trimmed.substring(m.range.last + 1).trimStart()
                                                stampsFor.add(stamps)
                                                contents.add(if (content.isBlank()) null else content)
                                            } else {
                                                stampsFor.add(null)
                                                contents.add(if (trimmed.isBlank()) null else trimmed)
                                            }
                                        }

                                        val translatableIndices =
                                            contents.mapIndexedNotNull { idx, c -> if (c != null) idx else null }
                                        val translatedMap = mutableMapOf<Int, String>()

                                        if (translatableIndices.isNotEmpty()) {
                                            var sep = "<<<SEP-${UUID.randomUUID()}>>>"
                                            while (contents.any { it?.contains(sep) == true }) {
                                                sep = "<<<SEP-${UUID.randomUUID()}>>>"
                                            }

                                            val maxCharsPerRequest = 4000
                                            val maxItemsPerBatch = 50

                                            var cursor = 0
                                            while (cursor < translatableIndices.size) {
                                                var currentChars = 0
                                                val batchIndices = mutableListOf<Int>()
                                                while (cursor < translatableIndices.size && batchIndices.size < maxItemsPerBatch) {
                                                    val idx = translatableIndices[cursor]
                                                    val pieceLen = contents[idx]!!.length
                                                    if (batchIndices.isEmpty() || currentChars + pieceLen + sep.length <= maxCharsPerRequest) {
                                                        batchIndices.add(idx)
                                                        currentChars += pieceLen + sep.length
                                                        cursor++
                                                    } else break
                                                }

                                                val batchTexts = batchIndices.map { contents[it]!! }
                                                val joined = batchTexts.joinToString(separator = sep)
                                                val translatedJoined =
                                                    translator.translateBlocking(joined, lang).translatedText

                                                val parts = translatedJoined.split(sep)
                                                if (parts.size == batchTexts.size) {
                                                    for (i in batchIndices.indices) {
                                                        translatedMap[batchIndices[i]] = parts[i]
                                                    }
                                                } else {
                                                    for (idx in batchIndices) {
                                                        val original = contents[idx]!!
                                                        val singleTranslated = runCatching {
                                                            translator.translateBlocking(original, lang).translatedText
                                                        }.getOrNull() ?: original
                                                        translatedMap[idx] = singleTranslated
                                                    }
                                                }
                                            }
                                        }

                                        val out = mutableListOf<String>()
                                        for (i in contents.indices) {
                                            val stamp = stampsFor[i]
                                            val c = contents[i]
                                            if (c == null) {
                                                if (stamp != null) out.add(stamp) else out.add("")
                                            } else {
                                                val translatedText = translatedMap[i] ?: c
                                                if (stamp != null) out.add("$stamp $translatedText") else out.add(translatedText)
                                            }
                                        }

                                        out.joinToString("\n")
                                    }
                                    viewModel.updateLyrics(
                                        mediaMetadata = mediaMetadataProvider(),
                                        lyrics = translatedLyrics,
                                        source = LyricsEntity.Source.AI_TRANSLATION,
                                    )
                                    showTranslateDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString()),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isTranslating = false
                                }
                            }
                        }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.translate))
                        }
                    }
                }
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = setTextFieldValue,
                        singleLine = false,
                        label = { Text(stringResource(R.string.lyrics)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 220.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.language_label),
                            modifier = Modifier.width(96.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = selectedLanguageName,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.name) },
                                        onClick = {
                                            selectedLanguageCode = lang.code
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    LazyColumn(
        userScrollEnabled = true,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions = listOf(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.edit),
                            onClick = { showEditDialog = true }
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.cached),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.refetch),
                            onClick = {
                                showRefetchLoadingDialog = true
                                viewModel.refetchLyrics(mediaMetadataProvider())
                            }
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.translate),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.translate),
                            onClick = { showTranslateDialog = true },
                            enabled = isTranslateEnabled
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.auto_awesome),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.ai_translation_menu),
                            onClick = {
                                if (isAiProviderConfigured) {
                                    viewModel.translateLyricsWithAi(
                                        mediaMetadata = mediaMetadataProvider(),
                                        lyrics = lyricsProvider()?.lyrics.orEmpty(),
                                    )
                                }
                            },
                            enabled = isAiProviderConfigured && isAiTranslationEnabled && !isAiTranslating
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.speed),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.lyrics_sync_offset),
                            onClick = { showLyricsSyncOffsetDialog = true }
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.search),
                            onClick = { showSearchDialog = true }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultDialog(
    state: LyricsSearchScreenState,
    expandedResultId: String?,
    onExpandedResultChange: (String) -> Unit,
    onResultSelected: (LyricsSearchResultUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val listContentPadding = remember {
                PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
            }
            val listArrangement = remember { Arrangement.spacedBy(10.dp) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LyricsSearchResultHeader(
                        state = state,
                        onDismiss = onDismiss,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = listContentPadding,
                        verticalArrangement = listArrangement,
                    ) {
                        when (state) {
                            LyricsSearchScreenState.Loading -> {
                                item(contentType = "lyrics_search_loading") {
                                    LyricsSearchLoadingContent()
                                }
                            }

                            LyricsSearchScreenState.Empty -> {
                                item(contentType = "lyrics_search_empty") {
                                    LyricsSearchEmptyContent()
                                }
                            }

                            is LyricsSearchScreenState.Error -> {
                                item(contentType = "lyrics_search_error") {
                                    LyricsSearchErrorContent(messageResId = state.messageResId)
                                }
                            }

                            is LyricsSearchScreenState.Success -> {
                                itemsIndexed(
                                    items = state.results,
                                    key = { _, result -> result.id },
                                    contentType = { _, _ -> "lyrics_search_result" },
                                ) { _, result ->
                                    LyricsSearchResultItem(
                                        result = result,
                                        isExpanded = result.id == expandedResultId,
                                        onExpandedChange = { onExpandedResultChange(result.id) },
                                        onResultSelected = { onResultSelected(result) },
                                    )
                                }

                                if (state.isSearching) {
                                    item(contentType = "lyrics_search_footer_loading") {
                                        LyricsSearchFooterLoading()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchResultHeader(
    state: LyricsSearchScreenState,
    onDismiss: () -> Unit,
) {
    val subtitle = when (state) {
        LyricsSearchScreenState.Loading -> stringResource(R.string.lyrics_searching_providers)
        LyricsSearchScreenState.Empty -> stringResource(R.string.lyrics_not_found)
        is LyricsSearchScreenState.Error -> stringResource(state.messageResId)
        is LyricsSearchScreenState.Success -> stringResource(
            R.string.lyrics_search_results_count,
            state.results.size,
        )
    }
    val isSearching = state == LyricsSearchScreenState.Loading ||
        state is LyricsSearchScreenState.Success && state.isSearching
    val rowArrangement = remember { Arrangement.spacedBy(16.dp) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = rowArrangement,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.manage_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_lyrics),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSearching) {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultItem(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onResultSelected: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val lyricsType = when {
        result.isWordSynced -> stringResource(R.string.lyrics_word_sync)
        result.isLineSynced -> stringResource(R.string.lyrics_synced_badge)
        else -> stringResource(R.string.lyrics_search_plain_badge)
    }
    val stats = stringResource(
        R.string.lyrics_search_result_stats,
        result.lineCount,
        result.characterCount,
    )
    val metadataArrangement = remember { Arrangement.spacedBy(8.dp) }
    val containerColor = if (isExpanded) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (isExpanded) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val outlineColor = if (isExpanded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val itemArrangement = remember { Arrangement.spacedBy(14.dp) }

    Surface(
        onClick = onResultSelected,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = itemArrangement,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = metadataArrangement,
            ) {
                LyricsSearchTypeIcon(
                    result = result,
                    isExpanded = isExpanded,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.providerName,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lyricsType,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isExpanded) R.drawable.expand_less else R.drawable.expand_more,
                        ),
                        contentDescription = stringResource(R.string.details),
                        tint = contentColor,
                    )
                }
            }
            LyricsSearchResultSupportingContent(
                preview = result.preview,
                isExpanded = isExpanded,
                contentColor = contentColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = metadataArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsSearchMetadataPill(
                    icon = R.drawable.info,
                    text = lyricsType,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
                LyricsSearchMetadataPill(
                    icon = R.drawable.text_fields,
                    text = stats,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchTypeIcon(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
) {
    val icon = when {
        result.isWordSynced -> R.drawable.lyrics
        result.isLineSynced -> R.drawable.sync
        else -> R.drawable.format_align_left
    }
    val containerColor = if (isExpanded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isExpanded) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LyricsSearchResultSupportingContent(
    preview: String,
    isExpanded: Boolean,
    contentColor: Color,
) {
    Text(
        text = preview,
        modifier = Modifier.fillMaxWidth(),
        maxLines = if (isExpanded) 8 else 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
    )
}

@Composable
private fun LyricsSearchMetadataPill(
    icon: Int,
    text: String,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isExpanded) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isExpanded) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pillArrangement = remember { Arrangement.spacedBy(6.dp) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = pillArrangement,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsSearchLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.lyrics_searching_providers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsSearchFooterLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.lyrics_search_still_searching),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsSearchEmptyContent() {
    LyricsSearchMessageContent(
        icon = R.drawable.search_off,
        text = stringResource(R.string.lyrics_not_found),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun LyricsSearchErrorContent(
    messageResId: Int,
) {
    LyricsSearchMessageContent(
        icon = R.drawable.error,
        text = stringResource(messageResId),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun LyricsSearchMessageContent(
    icon: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatLyricsSyncOffset(offsetMs: Int): String {
    return if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLyricsInputDialog(
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val useStackedActions = configuration.screenWidthDp < 600

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LyricsSearchInputHeader(onDismiss = onDismiss)

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LyricsSearchTextField(
                            value = titleField,
                            onValueChange = onTitleFieldChange,
                            label = stringResource(R.string.song_title),
                            iconResId = R.drawable.music_note,
                        )
                        LyricsSearchTextField(
                            value = artistField,
                            onValueChange = onArtistFieldChange,
                            label = stringResource(R.string.song_artists),
                            iconResId = R.drawable.artist,
                        )
                    }
                }

                LyricsSearchInputActions(
                    useStackedActions = useStackedActions,
                    onDismiss = onDismiss,
                    onSearchOnline = onSearchOnline,
                    onSearch = onSearch,
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchInputHeader(
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = stringResource(R.string.search_lyrics),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(android.R.string.cancel),
            )
        }
    }
}

@Composable
private fun LyricsSearchTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    iconResId: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
}

@Composable
private fun LyricsSearchInputActions(
    useStackedActions: Boolean,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    if (useStackedActions) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSearch,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search))
            }

            FilledTonalButton(
                onClick = onSearchOnline,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.language),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_online))
            }

            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }

            Spacer(modifier = Modifier.weight(1f))

            FilledTonalButton(
                onClick = onSearchOnline,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.language),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_online))
            }

            Button(
                onClick = onSearch,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search))
            }
        }
    }
}
