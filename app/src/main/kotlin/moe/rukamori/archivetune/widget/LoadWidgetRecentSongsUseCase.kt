/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
internal data class WidgetRecentSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

internal class LoadWidgetRecentSongsUseCase
    @Inject
    constructor(
        private val repository: WidgetInsightsRepository,
    ) {
        suspend operator fun invoke(limit: Int = WidgetRecentSongLimit): List<WidgetRecentSong> =
            repository
                .recentSongs(limit = limit)
                .map { song ->
                    WidgetRecentSong(
                        id = song.id,
                        title = song.song.title,
                        artist = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl?.takeIf { it.isNotBlank() },
                    )
                }
    }

internal const val WidgetRecentSongLimit = 10
