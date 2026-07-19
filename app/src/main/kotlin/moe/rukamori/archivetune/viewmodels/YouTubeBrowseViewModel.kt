/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        private val params = savedStateHandle.get<String>("params")

        val result = MutableStateFlow<BrowseResult?>(null)

        init {
            viewModelScope.launch {
                YouTube
                    .browse(browseId, params)
                    .onSuccess {
                        val hideVideo = context.dataStore.get(HideVideoKey, false)
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredResult =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(hideVideo)
                                .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                        result.value =
                            contentFilteredResult.copy(
                                items =
                                    contentFilteredResult.items.mapNotNull { section ->
                                        section
                                            .copy(items = filterAiContent(section.items, aiContentFilterPolicy))
                                            .takeIf { filteredSection -> filteredSection.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }
