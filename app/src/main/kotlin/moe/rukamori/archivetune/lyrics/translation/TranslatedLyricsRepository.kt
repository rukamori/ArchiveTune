/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslatedLyricsRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun replaceLyrics(
            mediaId: String,
            lyrics: String,
        ) {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    replaceLyrics(
                        id = mediaId,
                        lyrics = lyrics,
                        source = LyricsEntity.Source.TRANSLATION.value,
                    )
                }
            }
        }
    }
