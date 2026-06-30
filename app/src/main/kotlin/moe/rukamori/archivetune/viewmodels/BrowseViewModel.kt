/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val browseId: String? = savedStateHandle.get<String>("browseId")

        val items = MutableStateFlow<List<YTItem>?>(emptyList())
        val title = MutableStateFlow<String?>("")

        init {
            viewModelScope.launch {
                browseId?.let {
                    YouTube
                        .browse(browseId, null)
                        .onSuccess { result ->
                            // Store the title
                            title.value = result.title

                            // Flatten the nested structure to get all YTItems
                            val allItems = result.items.flatMap { it.items }
                            items.value = allItems
                        }.onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }
