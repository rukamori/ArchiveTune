/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ObserveMediaInfoUseCase @Inject constructor(private val repository: MediaInfoRepository) {
    operator fun invoke(videoId: String): Flow<MediaInfoData> = flow {
        val local = repository.observeLocal(videoId)
            .map<LocalMediaInfo, MediaInfoState<LocalMediaInfo>> { MediaInfoState.Success(it) }
            .catch { failure -> emit(failure.asMediaInfoError()) }
        val initialLocal = local.first()
        val isLocal = (initialLocal as? MediaInfoState.Success)?.data?.isLocal == true
        val metadata = if (isLocal) {
            flowOf<MediaInfoState<MediaInfoMetadata>>(MediaInfoState.Empty)
        } else {
            load(
                request = { repository.metadata(videoId) },
                isEmpty = {
                    it.title.isNullOrBlank() && it.author.isNullOrBlank() &&
                        it.description.isNullOrBlank() && it.subscribers.isNullOrBlank()
                },
            )
        }
        val statistics = if (isLocal) {
            flowOf<MediaInfoState<MediaInfoStatistics>>(MediaInfoState.Empty)
        } else {
            load(
                request = { repository.statistics(videoId) },
                isEmpty = { it.views == null && it.likes == null && it.dislikes == null },
            )
        }
        emitAll(
            combine(local.onStart { emit(initialLocal) }, metadata, statistics) { details, info, numbers ->
                MediaInfoData(details, info, numbers)
            },
        )
    }

    private fun <T> load(request: suspend () -> T, isEmpty: (T) -> Boolean): Flow<MediaInfoState<T>> =
        flow<MediaInfoState<T>> {
            emit(MediaInfoState.Loading)
            val data = request()
            emit(if (isEmpty(data)) MediaInfoState.Empty else MediaInfoState.Success(data))
        }.catch { failure -> emit(failure.asMediaInfoError()) }
}

internal fun Throwable.asMediaInfoError(): MediaInfoState.Error {
    if (this is CancellationException) throw this
    return MediaInfoState.Error(
        if (this is IOException) MediaInfoError.Network else MediaInfoError.Unavailable,
    )
}
