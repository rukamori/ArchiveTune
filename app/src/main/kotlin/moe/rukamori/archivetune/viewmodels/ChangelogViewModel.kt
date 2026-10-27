/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.ObserveChangelogUseCase
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

@Immutable
sealed interface ChangelogScreenState {
    data object Loading : ChangelogScreenState
    data class Success(val model: ChangelogUiModel) : ChangelogScreenState
    data object Empty : ChangelogScreenState
    data class Error(
        @param:StringRes val messageRes: Int,
        val lastKnownModel: ChangelogUiModel? = null,
    ) : ChangelogScreenState
}

@Immutable
data class ChangelogReleaseUiModel(
    val tag: String,
    val version: String,
    val title: String,
    val publishedDate: String?,
    val notes: String?,
)

@Immutable
class ChangelogReleaseCollection(releases: List<ChangelogReleaseUiModel>) {
    private val values = releases.toList()
    val size: Int get() = values.size

    operator fun get(index: Int): ChangelogReleaseUiModel = values[index]

    fun indexOfTag(tag: String?): Int = values.indexOfFirst { it.tag == tag }
}

@Immutable
data class ChangelogUiModel(
    val releases: ChangelogReleaseCollection,
    val selectedIndex: Int,
) {
    val selectedRelease: ChangelogReleaseUiModel get() = releases[selectedIndex]
}

@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val observeChangelog: ObserveChangelogUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ChangelogScreenState>(ChangelogScreenState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        mutableState.value = currentModel()?.let(ChangelogScreenState::Success) ?: ChangelogScreenState.Loading
        loadJob = viewModelScope.launch {
            val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())
            try {
                observeChangelog()
                    .map { releases ->
                        ChangelogReleaseCollection(
                            releases.map { release ->
                                ChangelogReleaseUiModel(
                                    tag = release.tag,
                                    version = release.version,
                                    title = release.title,
                                    publishedDate = release.publishedDate?.format(dateFormatter),
                                    notes = release.notes,
                                )
                            },
                        )
                    }.flowOn(Dispatchers.Default)
                    .collect { releases ->
                        if (releases.size == 0) {
                            mutableState.value = ChangelogScreenState.Empty
                        } else {
                            val selectedIndex = releases.indexOfTag(savedStateHandle[SelectedReleaseKey]).coerceAtLeast(0)
                            savedStateHandle[SelectedReleaseKey] = releases[selectedIndex].tag
                            mutableState.value = ChangelogScreenState.Success(ChangelogUiModel(releases, selectedIndex))
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to load changelog")
                mutableState.value = ChangelogScreenState.Error(R.string.error_loading_changelog, currentModel())
            }
        }
    }

    fun selectRelease(tag: String) {
        val currentState = mutableState.value
        val model = currentModel() ?: return
        val selectedIndex = model.releases.indexOfTag(tag)
        if (selectedIndex < 0 || selectedIndex == model.selectedIndex) return
        savedStateHandle[SelectedReleaseKey] = tag
        val selectedModel = model.copy(selectedIndex = selectedIndex)
        mutableState.value =
            when (currentState) {
                is ChangelogScreenState.Error -> currentState.copy(lastKnownModel = selectedModel)
                else -> ChangelogScreenState.Success(selectedModel)
            }
    }

    private fun currentModel(): ChangelogUiModel? =
        when (val currentState = mutableState.value) {
            is ChangelogScreenState.Success -> currentState.model
            is ChangelogScreenState.Error -> currentState.lastKnownModel
            ChangelogScreenState.Loading, ChangelogScreenState.Empty -> null
        }

    private companion object {
        const val SelectedReleaseKey = "changelog_selected_release"
    }
}
