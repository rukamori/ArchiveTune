/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ytdlp

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeSnapshot
import javax.inject.Inject

data class YtDlpSettingsDomainState(
    val runtime: YtDlpRuntimeSnapshot,
    val nowMillis: Long,
    val remainingUpdates: Int,
    val rateLimitResetsAtMillis: Long?,
)

class ObserveYtDlpSettingsUseCase
    @Inject
    constructor(
        private val repository: YtDlpSettingsRepository,
    ) {
        operator fun invoke(): Flow<YtDlpSettingsDomainState> =
            combine(
                repository.runtimeSnapshot,
                repository.manualUpdateHistory,
                ticker(),
            ) { runtime, history, now ->
                val validHistory = history.activeManualUpdateWindow(now)
                YtDlpSettingsDomainState(
                    runtime = runtime,
                    nowMillis = now,
                    remainingUpdates = (MAX_MANUAL_UPDATES - validHistory.size).coerceAtLeast(0),
                    rateLimitResetsAtMillis =
                        validHistory
                            .takeIf { it.size >= MAX_MANUAL_UPDATES }
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

sealed interface ManualYtDlpUpdateResult {
    data class Updated(
        val version: String,
    ) : ManualYtDlpUpdateResult

    data object UpToDate : ManualYtDlpUpdateResult

    data class RateLimited(
        val resetsAtMillis: Long,
    ) : ManualYtDlpUpdateResult

    data class Failed(
        val cause: Throwable,
    ) : ManualYtDlpUpdateResult
}

class CheckForYtDlpUpdateUseCase
    @Inject
    constructor(
        private val repository: YtDlpSettingsRepository,
    ) {
        suspend operator fun invoke(): ManualYtDlpUpdateResult {
            val nowMillis = System.currentTimeMillis()
            val activeHistory =
                repository
                    .getManualUpdateHistory()
                    .activeManualUpdateWindow(nowMillis)
            if (activeHistory.size >= MAX_MANUAL_UPDATES) {
                return ManualYtDlpUpdateResult.RateLimited(
                    resetsAtMillis = activeHistory.first().rateLimitWindowEnd(),
                )
            }
            val updateResult =
                repository
                    .updateRuntime()
                    .getOrElse {
                        return ManualYtDlpUpdateResult.Failed(it)
                    }
            val completedAtMillis = updateResult.checkedAtMillis
            val completedHistory =
                repository
                    .getManualUpdateHistory()
                    .activeManualUpdateWindow(completedAtMillis)
            val windowStartsAtMillis = completedHistory.firstOrNull() ?: completedAtMillis
            repository.recordSuccessfulManualUpdate(
                timestampMillis = completedAtMillis,
                windowStartsAtMillis = windowStartsAtMillis,
                windowEndsAtMillis = windowStartsAtMillis.rateLimitWindowEnd(),
                maximumEntries = MAX_MANUAL_UPDATES,
            )
            return updateResult.installedVersion
                ?.let(ManualYtDlpUpdateResult::Updated)
                ?: ManualYtDlpUpdateResult.UpToDate
        }
    }

private fun List<Long>.activeManualUpdateWindow(nowMillis: Long): List<Long> {
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

private const val MAX_MANUAL_UPDATES = 3
private const val RATE_LIMIT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
