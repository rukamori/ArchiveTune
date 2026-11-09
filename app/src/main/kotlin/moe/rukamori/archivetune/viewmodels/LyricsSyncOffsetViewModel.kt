/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.DefaultLyricsSyncOffsetMode
import moe.rukamori.archivetune.constants.LyricsGlobalSyncOffsetKey
import moe.rukamori.archivetune.constants.LyricsSyncOffsetLimitMs
import moe.rukamori.archivetune.constants.LyricsSyncOffsetMode
import moe.rukamori.archivetune.constants.LyricsSyncOffsetModeKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsSyncOffsetEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

@Immutable
data class LyricsSyncOffsetState(
    val mode: LyricsSyncOffsetMode,
    val songId: String?,
    val offsetMs: Int,
) {
    fun offsetFor(currentSongId: String?): Int =
        if (mode == LyricsSyncOffsetMode.GLOBAL || songId == currentSongId) offsetMs else 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsSyncOffsetViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val songId = MutableStateFlow<String?>(null)

        private val mode =
            context.dataStore.data
                .map { it[LyricsSyncOffsetModeKey].toEnum(DefaultLyricsSyncOffsetMode) }
                .distinctUntilChanged()

        private val globalOffset =
            context.dataStore.data
                .map { (it[LyricsGlobalSyncOffsetKey] ?: 0).coerceToLimit() }
                .distinctUntilChanged()

        val state: StateFlow<LyricsSyncOffsetState> =
            combine(mode, songId) { offsetMode, id -> offsetMode to id }
                .flatMapLatest { (offsetMode, id) ->
                    when {
                        offsetMode == LyricsSyncOffsetMode.GLOBAL ->
                            globalOffset.map { LyricsSyncOffsetState(offsetMode, id, it) }

                        id == null ->
                            flowOf(LyricsSyncOffsetState(offsetMode, null, 0))

                        else ->
                            database
                                .lyricsSyncOffset(id)
                                .map { LyricsSyncOffsetState(offsetMode, id, (it ?: 0).coerceToLimit()) }
                    }
                }.distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LyricsSyncOffsetState(DefaultLyricsSyncOffsetMode, null, 0),
                )

        fun setSongId(id: String?) {
            songId.value = id
        }

        fun setOffset(
            songId: String,
            offsetMs: Int,
        ) {
            val value = offsetMs.coerceToLimit()
            viewModelScope.launch {
                val currentMode = context.dataStore.data.first()[LyricsSyncOffsetModeKey].toEnum(DefaultLyricsSyncOffsetMode)
                when (currentMode) {
                    LyricsSyncOffsetMode.GLOBAL ->
                        context.dataStore.edit { it[LyricsGlobalSyncOffsetKey] = value }

                    LyricsSyncOffsetMode.PER_SONG ->
                        if (value == 0) {
                            database.deleteLyricsSyncOffset(songId)
                        } else {
                            database.upsert(LyricsSyncOffsetEntity(songId = songId, offsetMs = value))
                        }
                }
            }
        }

        private fun Int.coerceToLimit(): Int = coerceIn(-LyricsSyncOffsetLimitMs, LyricsSyncOffsetLimitMs)
    }
