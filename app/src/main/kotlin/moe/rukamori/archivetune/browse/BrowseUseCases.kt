/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.browse

import androidx.annotation.StringRes
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.PODCAST_LIBRARY_BROWSE_ID
import moe.rukamori.archivetune.innertube.models.YTItem
import javax.inject.Inject

data class BrowseLoadResult(
    val uiState: BrowseUiState,
    val continuation: String?,
)

data class BrowseContinuationResult(
    val items: ImmutableList<YTItem>,
    val continuation: String?,
)

class LoadBrowseUseCase
    @Inject
    constructor(
        private val repository: BrowseRepository,
    ) {
        suspend operator fun invoke(browseId: String): Result<BrowseLoadResult> {
            val validatedBrowseId =
                browseId
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?: return Result.failure(IllegalArgumentException())
            return repository.load(validatedBrowseId).map { page ->
                BrowseLoadResult(
                    uiState =
                        BrowseUiState(
                            title = page.title?.takeIf(String::isNotBlank),
                            fallbackTitleResId = validatedBrowseId.fallbackTitleResId(),
                            items = page.items.toImmutableBrowseItems(),
                            isLoadingMore = false,
                            canLoadMore = !page.continuation.isNullOrBlank(),
                        ),
                    continuation = page.continuation?.takeIf(String::isNotBlank),
                )
            }
        }
    }

class LoadBrowseContinuationUseCase
    @Inject
    constructor(
        private val repository: BrowseRepository,
    ) {
        suspend operator fun invoke(continuation: String): Result<BrowseContinuationResult> {
            val validatedContinuation =
                continuation
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?: return Result.failure(IllegalArgumentException())
            return repository.loadContinuation(validatedContinuation).map { page ->
                BrowseContinuationResult(
                    items = page.items.toImmutableBrowseItems(),
                    continuation = page.continuation?.takeIf(String::isNotBlank),
                )
            }
        }
    }

private fun List<YTItem>.toImmutableBrowseItems(): ImmutableList<YTItem> =
    ImmutableList.copyOf(
        asSequence()
            .filter { item -> item.id.isNotBlank() && item.title.isNotBlank() }
            .distinctBy(YTItem::id)
            .toList(),
    )

@StringRes
private fun String.fallbackTitleResId(): Int? =
    when (this) {
        PODCAST_LIBRARY_BROWSE_ID -> R.string.your_shows
        else -> null
    }
