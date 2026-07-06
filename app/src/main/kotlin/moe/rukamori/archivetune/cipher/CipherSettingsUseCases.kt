/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cipher

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import moe.rukamori.archivetune.morideobfuscator.CipherSnapshot
import javax.inject.Inject

data class CipherSettingsDomainState(
    val runtime: CipherSnapshot,
    val nowMillis: Long,
    val remainingRefreshes: Int,
    val rateLimitResetsAtMillis: Long?,
)

class ObserveCipherSettingsUseCase
    @Inject
    constructor(
        private val repository: CipherSettingsRepository,
    ) {
        operator fun invoke(): Flow<CipherSettingsDomainState> =
            combine(
                repository.runtimeSnapshot,
                repository.manualRefreshHistory,
                ticker(),
            ) { runtime, history, now ->
                val validHistory = history.activeManualRefreshWindow(now)
                CipherSettingsDomainState(
                    runtime = runtime,
                    nowMillis = now,
                    remainingRefreshes = (MAX_MANUAL_REFRESHES - validHistory.size).coerceAtLeast(0),
                    rateLimitResetsAtMillis =
                        validHistory
                            .takeIf { it.size >= MAX_MANUAL_REFRESHES }
                            ?.firstOrNull()
                            ?.plus(RATE_LIMIT_WINDOW_MILLIS),
                )
            }

        private fun ticker(): Flow<Long> =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(1_000L)
                }
            }
    }

sealed interface ManualCipherRefreshResult {
    data object Updated : ManualCipherRefreshResult

    data class RateLimited(
        val resetsAtMillis: Long,
    ) : ManualCipherRefreshResult

    data class Failed(
        val cause: Throwable,
    ) : ManualCipherRefreshResult
}

class RefreshCipherUseCase
    @Inject
    constructor(
        private val repository: CipherSettingsRepository,
    ) {
        suspend operator fun invoke(): ManualCipherRefreshResult {
            val nowMillis = System.currentTimeMillis()
            val activeHistory =
                repository
                    .getManualRefreshHistory()
                    .activeManualRefreshWindow(nowMillis)
            if (activeHistory.size >= MAX_MANUAL_REFRESHES) {
                return ManualCipherRefreshResult.RateLimited(
                    resetsAtMillis = activeHistory.first().rateLimitWindowEnd(),
                )
            }
            val completedAtMillis =
                repository
                    .refresh()
                    .getOrElse {
                        return ManualCipherRefreshResult.Failed(it)
                    }.refreshedAtMillis
            val completedHistory =
                repository
                    .getManualRefreshHistory()
                    .activeManualRefreshWindow(completedAtMillis)
            val windowStartsAtMillis = completedHistory.firstOrNull() ?: completedAtMillis
            repository.recordSuccessfulManualRefresh(
                timestampMillis = completedAtMillis,
                windowStartsAtMillis = windowStartsAtMillis,
                windowEndsAtMillis = windowStartsAtMillis.rateLimitWindowEnd(),
                maximumEntries = MAX_MANUAL_REFRESHES,
            )
            return ManualCipherRefreshResult.Updated
        }
    }

private fun List<Long>.activeManualRefreshWindow(nowMillis: Long): List<Long> {
    val chronological =
        asSequence()
            .filter { it in 0..nowMillis }
            .distinct()
            .sorted()
            .toList()
    val windowStartsAtMillis = chronological.firstOrNull() ?: return emptyList()
    return chronological.takeIf { nowMillis < windowStartsAtMillis.rateLimitWindowEnd() }.orEmpty()
}

private fun Long.rateLimitWindowEnd(): Long =
    if (this > Long.MAX_VALUE - RATE_LIMIT_WINDOW_MILLIS) {
        Long.MAX_VALUE
    } else {
        this + RATE_LIMIT_WINDOW_MILLIS
    }

internal const val MAX_MANUAL_REFRESHES = 3
internal const val RATE_LIMIT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
