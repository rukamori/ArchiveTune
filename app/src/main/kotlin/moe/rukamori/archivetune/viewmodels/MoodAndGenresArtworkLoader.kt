/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.repository.MoodAndGenresArtworkRepository
import moe.rukamori.archivetune.repository.moodAndGenresArtworkKey
import moe.rukamori.archivetune.utils.reportException

@Immutable
sealed interface MoodAndGenresArtworkState {
    data object Loading : MoodAndGenresArtworkState

    data class Available(val url: String) : MoodAndGenresArtworkState

    data object Unavailable : MoodAndGenresArtworkState

    data object Failed : MoodAndGenresArtworkState
}

internal class MoodAndGenresArtworkLoader(
    private val repository: MoodAndGenresArtworkRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<Map<String, MoodAndGenresArtworkState>>(emptyMap())
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    fun setVisible(endpoints: List<BrowseEndpoint>) {
        val visible = endpoints.associateBy { it.moodAndGenresArtworkKey() }
        val removed = jobs.keys - visible.keys
        removed.forEach { key -> jobs.remove(key)?.cancel() }
        mutableState.update { state ->
            state.filterNot { (key, value) -> key in removed && value == MoodAndGenresArtworkState.Loading }
        }

        visible.forEach { (key, endpoint) ->
            if (key in mutableState.value) return@forEach
            mutableState.update { it + (key to MoodAndGenresArtworkState.Loading) }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val result = repository.load(endpoint)
                ensureActive()
                val artwork = result.fold(
                    onSuccess = { url ->
                        if (url == null) MoodAndGenresArtworkState.Unavailable else MoodAndGenresArtworkState.Available(url)
                    },
                    onFailure = { exception ->
                        reportException(exception)
                        MoodAndGenresArtworkState.Failed
                    },
                )
                mutableState.update { it + (key to artwork) }
            }
            jobs[key] = job
            job.start()
        }
    }
}
