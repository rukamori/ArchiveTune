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
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiCustomPromptKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiRequestParametersKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsResult
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.LyricsUtils.displayLyricsText
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.translation.TranslateLyricsResult
import moe.rukamori.archivetune.lyrics.translation.TranslateLyricsUseCase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val formatCounts: ImmutableMap<LyricsSearchFormat, Int>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

enum class LyricsSearchFormat {
    WORD_SYNCED,
    LINE_SYNCED,
    PLAIN,
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val providerUrl: String?,
    val author: String?,
    val authorUrl: String?,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val format: LyricsSearchFormat,
)

@Immutable
sealed interface LyricsTranslationError {
    data object Failed : LyricsTranslationError

    data class UnsupportedLanguage(
        val languageName: String,
    ) : LyricsTranslationError
}

@Immutable
sealed interface LyricsTranslationScreenState {
    data object Loading : LyricsTranslationScreenState

    data object Success : LyricsTranslationScreenState

    data object Empty : LyricsTranslationScreenState

    data class Error(
        val error: LyricsTranslationError,
    ) : LyricsTranslationScreenState
}

enum class LyricsRefetchResult {
    Updated,
    NotFound,
    Unavailable,
}

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
        private val translateLyricsUseCase: TranslateLyricsUseCase,
    ) : ViewModel() {
        private var job: Job? = null
        private var translationJob: Job? = null
        private var aiTranslationJob: Job? = null
        private val searchGeneration = AtomicLong(0L)
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        private val _isRefetching = MutableStateFlow(false)
        val isRefetching: StateFlow<Boolean> = _isRefetching.asStateFlow()
        private val _refetchCompletionEvents = MutableSharedFlow<LyricsRefetchResult>(extraBufferCapacity = 1)
        val refetchCompletionEvents: SharedFlow<LyricsRefetchResult> = _refetchCompletionEvents.asSharedFlow()
        val isAiTranslating = MutableStateFlow(false)

        private val _lyricsTranslationState =
            MutableStateFlow<LyricsTranslationScreenState>(LyricsTranslationScreenState.Empty)
        val lyricsTranslationState: StateFlow<LyricsTranslationScreenState> = _lyricsTranslationState.asStateFlow()

        private val _aiTranslationEvents = MutableSharedFlow<String>()
        val aiTranslationEvents: SharedFlow<String> = _aiTranslationEvents.asSharedFlow()

        private val _isNetworkAvailable = MutableStateFlow(false)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        init {
            viewModelScope.launch {
                networkConnectivity.networkStatus.collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                }
            }

            _isNetworkAvailable.value =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
        }

        fun search(
            mediaId: String,
            title: String,
            artist: String,
            album: String?,
            duration: Int,
        ) {
            val generation = searchGeneration.incrementAndGet()
            job?.cancel()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(
                            mediaId = mediaId,
                            songTitle = title,
                            songArtists = artist,
                            songAlbum = album,
                            duration = duration,
                        ) { result ->
                            if (generation != searchGeneration.get()) return@getAllLyrics
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value = resultModels.toSuccessState(isSearching = true)
                        }
                        if (generation != searchGeneration.get()) return@launch
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                resultModels.toSuccessState(isSearching = false)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (generation == searchGeneration.get()) {
                            _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                        }
                    }
                }
        }

        fun cancelSearch() {
            searchGeneration.incrementAndGet()
            job?.cancel()
            job = null
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            if (!_isRefetching.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                val result =
                    try {
                        val fetched = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                        when {
                            fetched.isFound -> {
                                database.withTransaction {
                                    replaceLyrics(
                                        id = mediaMetadata.id,
                                        lyrics = fetched.lyrics,
                                        source = LyricsEntity.Source.REMOTE.value,
                                        provider = fetched.providerName,
                                        providerUrl = fetched.providerUrl,
                                        providerAuthor = fetched.author,
                                        providerAuthorUrl = fetched.authorUrl,
                                    )
                                }
                                LyricsRefetchResult.Updated
                            }

                            fetched.isConclusive -> {
                                database.withTransaction {
                                    saveFetchedLyrics(
                                        id = mediaMetadata.id,
                                        lyrics = fetched.lyrics,
                                        provider = fetched.providerName,
                                        providerUrl = fetched.providerUrl,
                                        providerAuthor = fetched.author,
                                        providerAuthorUrl = fetched.authorUrl,
                                    )
                                }
                                LyricsRefetchResult.NotFound
                            }

                            else -> {
                                LyricsRefetchResult.Unavailable
                            }
                        }
                    } catch (e: CancellationException) {
                        _isRefetching.value = false
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        LyricsRefetchResult.Unavailable
                    }
                _isRefetching.value = false
                _refetchCompletionEvents.tryEmit(result)
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
            provider: String? = null,
            providerUrl: String? = null,
            providerAuthor: String? = null,
            providerAuthorUrl: String? = null,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        LyricsEntity.Source.USER_SELECTION,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        -> lyrics

                        LyricsEntity.Source.TRANSLATION,
                        LyricsEntity.Source.AI_TRANSLATION ->
                            usableTranslatedLyrics(lyrics) ?: return@launch
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                        provider = provider,
                        providerUrl = providerUrl,
                        providerAuthor = providerAuthor,
                        providerAuthorUrl = providerAuthorUrl,
                    )
                }
            }
        }

        fun translateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            targetLanguage: String,
            targetLanguageName: String,
        ) {
            if (translationJob?.isActive == true) return
            if (lyrics.isBlank()) {
                _lyricsTranslationState.value =
                    LyricsTranslationScreenState.Error(LyricsTranslationError.Failed)
                return
            }

            _lyricsTranslationState.value = LyricsTranslationScreenState.Loading
            translationJob =
                viewModelScope.launch {
                    val runningJob = coroutineContext[Job]
                    try {
                        _lyricsTranslationState.value =
                            when (
                                translateLyricsUseCase(
                                    mediaId = mediaMetadata.id,
                                    lyrics = lyrics,
                                    targetLanguage = targetLanguage,
                                )
                            ) {
                                TranslateLyricsResult.Success -> LyricsTranslationScreenState.Success
                                TranslateLyricsResult.Empty ->
                                    LyricsTranslationScreenState.Error(LyricsTranslationError.Failed)
                                TranslateLyricsResult.UnsupportedLanguage ->
                                    LyricsTranslationScreenState.Error(
                                        LyricsTranslationError.UnsupportedLanguage(targetLanguageName),
                                    )
                            }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        _lyricsTranslationState.value =
                            LyricsTranslationScreenState.Error(LyricsTranslationError.Failed)
                    } finally {
                        if (translationJob === runningJob) {
                            translationJob = null
                        }
                    }
                }
        }

        fun cancelLyricsTranslation() {
            translationJob?.cancel()
            translationJob = null
            _lyricsTranslationState.value = LyricsTranslationScreenState.Empty
        }

        fun clearLyricsTranslationResult() {
            if (_lyricsTranslationState.value !is LyricsTranslationScreenState.Loading) {
                _lyricsTranslationState.value = LyricsTranslationScreenState.Empty
            }
        }

        fun translateLyricsWithAi(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            targetLanguage: String,
        ) {
            if (isAiTranslating.value || lyrics.isBlank()) return
            aiTranslationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    isAiTranslating.value = true
                    try {
                        val prefs = context.dataStore.data.first()
                        val translatedLyrics =
                            AiLyricsTranslator().translate(
                                config =
                                    AiServiceConfig(
                                        provider = prefs[AiProviderKey].toEnum(AiProvider.NONE),
                                        apiKey = prefs[AiApiKeyKey].orEmpty(),
                                        customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                                        requestParameters = prefs[AiRequestParametersKey].orEmpty(),
                                        model =
                                            if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                                                prefs[AiCustomModelKey].orEmpty()
                                            } else {
                                                prefs[AiSelectedModelKey].orEmpty()
                                            },
                                    ),
                                lyrics = lyrics,
                                targetLanguage = targetLanguage.ifBlank { "ENGLISH" },
                                customPrompt = prefs[AiCustomPromptKey].orEmpty(),
                            )
                        val usableLyrics = usableTranslatedLyrics(translatedLyrics)
                        if (usableLyrics == null) {
                            _aiTranslationEvents.emit(context.getString(R.string.translation_failed))
                            return@launch
                        }
                        database.query {
                            replaceLyricsKeepingProvider(
                                id = mediaMetadata.id,
                                lyrics = usableLyrics,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        }
                        val msg = context.getString(R.string.translation_success)
                        _aiTranslationEvents.emit(msg)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString())
                        _aiTranslationEvents.emit(msg)
                    } finally {
                        isAiTranslating.value = false
                        aiTranslationJob = null
                    }
                }
        }

        private fun usableTranslatedLyrics(lyrics: String): String? =
            LyricsUtils
                .normalizeLyricsText(lyrics)
                .takeIf(LyricsUtils::hasMeaningfulLyricsContent)

        fun cancelAiTranslation() {
            aiTranslationJob?.cancel()
            aiTranslationJob = null
            isAiTranslating.value = false
        }

        private fun List<LyricsSearchResultUiModel>.toSuccessState(isSearching: Boolean): LyricsSearchScreenState.Success {
            val counts = ImmutableMap.builder<LyricsSearchFormat, Int>()
            LyricsSearchFormat.entries.forEach { format ->
                counts.put(format, count { it.format == format })
            }
            return LyricsSearchScreenState.Success(
                results = ImmutableList.copyOf(this),
                formatCounts = counts.build(),
                isSearching = isSearching,
            )
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val format =
                when {
                    LyricsUtils.hasWordSyncedLyrics(lyrics) -> LyricsSearchFormat.WORD_SYNCED
                    isTtml(lyrics) ->
                        if (runCatching { LyricsUtils.parseTtml(lyrics) }.getOrDefault(emptyList()).isNotEmpty()) {
                            LyricsSearchFormat.LINE_SYNCED
                        } else {
                            LyricsSearchFormat.PLAIN
                        }
                    isLineSyncedLrc(lyrics) -> LyricsSearchFormat.LINE_SYNCED
                    else -> LyricsSearchFormat.PLAIN
                }

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                providerUrl = providerUrl,
                author = author,
                authorUrl = authorUrl,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                format = format,
            )
        }
    }
