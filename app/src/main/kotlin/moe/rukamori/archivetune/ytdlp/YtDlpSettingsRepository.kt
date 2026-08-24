/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ytdlp

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.YtDlpManualUpdateHistoryKey
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeSnapshot
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeStore
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeUpdater
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpUpdateResult
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val updater = YtDlpRuntimeUpdater(context)

        val runtimeSnapshot: Flow<YtDlpRuntimeSnapshot> =
            flow {
                YtDlpRuntimeStore.initializeForProcess(context)
                emitAll(YtDlpRuntimeStore.snapshot)
            }

        val manualUpdateHistory: Flow<List<Long>> =
            context.dataStore.data.map { preferences ->
                preferences[YtDlpManualUpdateHistoryKey].toManualUpdateHistory()
            }

        suspend fun getManualUpdateHistory(): List<Long> =
            context.dataStore.data
                .first()[YtDlpManualUpdateHistoryKey]
                .toManualUpdateHistory()

        suspend fun updateRuntime(): Result<YtDlpUpdateResult> =
            try {
                Result.success(updater.update())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

        suspend fun recordSuccessfulManualUpdate(
            timestampMillis: Long,
            windowStartsAtMillis: Long,
            windowEndsAtMillis: Long,
            maximumEntries: Int,
        ) {
            context.dataStore.edit { preferences ->
                val retained =
                    preferences[YtDlpManualUpdateHistoryKey]
                        .toManualUpdateHistory()
                        .filter { it >= windowStartsAtMillis && it < windowEndsAtMillis }
                        .plus(timestampMillis)
                        .distinct()
                        .sorted()
                        .take(maximumEntries.coerceAtLeast(1))
                        .map(Long::toString)
                        .toSet()
                preferences[YtDlpManualUpdateHistoryKey] = retained
            }
        }
    }

private fun Set<String>?.toManualUpdateHistory(): List<Long> =
    orEmpty()
        .mapNotNull(String::toLongOrNull)
        .distinct()
        .sorted()
