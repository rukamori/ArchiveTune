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
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
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
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val isLineSynced: Boolean,
    val isWordSynced: Boolean,
)

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) : ViewModel() {
        private var job: Job? = null
        private var aiTranslationJob: Job? = null
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        val isRefetching = MutableStateFlow(false)
        val isAiTranslating = MutableStateFlow(false)

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
            job?.cancel()
            lyricsHelper.cancelCurrentLyricsJob()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(mediaId, title, artist, album, duration) { result ->
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value =
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = true,
                                )
                        }
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = false,
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                    }
                }
        }

        fun cancelSearch() {
            job?.cancel()
            job = null
            lyricsHelper.cancelCurrentLyricsJob()
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            viewModelScope.launch(Dispatchers.IO) {
                isRefetching.value = true
                try {
                    val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        replaceLyrics(
                            id = mediaMetadata.id,
                            lyrics = lyrics,
                            source = LyricsEntity.Source.REMOTE.value,
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    isRefetching.value = false
                }
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        LyricsEntity.Source.USER_SELECTION,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        LyricsEntity.Source.AI_TRANSLATION,
                        -> lyrics
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                    )
                }
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
                                        model =
                                            if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                                                prefs[AiCustomModelKey].orEmpty()
                                            } else {
                                                prefs[AiSelectedModelKey].orEmpty()
                                            },
                                    ),
                                lyrics = lyrics,
                                targetLanguage = targetLanguage.ifBlank { "ENGLISH" },
                            )
                        database.query {
                            replaceLyrics(
                                id = mediaMetadata.id,
                                lyrics = translatedLyrics,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        }
                        context.dataStore.edit { settings ->
                            settings[AiApiValidationStatusKey] = AiApiValidationStatus.SUCCESS.name
                        }
                        val msg = context.getString(R.string.translation_success)
                        _aiTranslationEvents.emit(msg)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        context.dataStore.edit { settings ->
                            settings[AiApiValidationStatusKey] = AiApiValidationStatus.FAILED.name
                        }
                        val msg = context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString())
                        _aiTranslationEvents.emit(msg)
                    } finally {
                        isAiTranslating.value = false
                        aiTranslationJob = null
                    }
                }
        }

        fun cancelAiTranslation() {
            aiTranslationJob?.cancel()
            aiTranslationJob = null
            isAiTranslating.value = false
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val isTtmlLyrics = isTtml(lyrics)

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                isLineSynced = !isTtmlLyrics && isLineSyncedLrc(lyrics),
                isWordSynced = isTtmlLyrics,
            )
        }
    }
