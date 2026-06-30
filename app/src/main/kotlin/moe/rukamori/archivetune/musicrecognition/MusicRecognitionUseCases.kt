/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class RecognizeMusicUseCase
    @Inject
    constructor(
        private val repository: MusicRecognitionRepository,
    ) {
        suspend operator fun invoke(
            onPhaseChanged: (RecognitionPhase) -> Unit,
        ): Result<RecognizedTrack> {
            onPhaseChanged(RecognitionPhase.Listening)
            val samples =
                try {
                    repository.captureAudio()
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    return Result.failure(
                        MusicRecognitionException(
                            failure = MusicRecognitionFailure.RecordingFailed,
                            cause = throwable,
                        ),
                    )
                }

            if (samples.isEmpty()) {
                return Result.failure(
                    MusicRecognitionException(MusicRecognitionFailure.RecordingFailed),
                )
            }

            onPhaseChanged(RecognitionPhase.Processing)
            val recognitionResult =
                try {
                    repository.recognize(samples)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    Result.failure(
                        MusicRecognitionException(
                            failure = MusicRecognitionFailure.RecognitionFailed,
                            cause = throwable,
                        ),
                    )
                }
            recognitionResult.getOrNull()?.let { track ->
                try {
                    repository.saveToHistory(track)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to persist music recognition history")
                }
            }
            return recognitionResult
        }
    }

class ObserveRecognitionHistoryUseCase
    @Inject
    constructor(
        private val repository: MusicRecognitionRepository,
    ) {
        operator fun invoke(): Flow<List<RecognitionHistoryEntry>> = repository.observeHistory()
    }

class FilterRecognitionHistoryUseCase
    @Inject
    constructor() {
        operator fun invoke(
            history: List<RecognitionHistoryEntry>,
            query: String,
        ): List<RecognitionHistoryEntry> {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) return history

            return history.filter { entry ->
                listOf(
                    entry.title,
                    entry.artist,
                    entry.album,
                    entry.genre,
                    entry.releaseDate,
                    entry.isrc,
                ).filterNotNull().any { value ->
                    value.contains(normalizedQuery, ignoreCase = true)
                }
            }
        }
    }
