/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cipher

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.MoriCipherManualRefreshHistoryKey
import moe.rukamori.archivetune.morideobfuscator.CipherRefreshResult
import moe.rukamori.archivetune.morideobfuscator.CipherSnapshot
import moe.rukamori.archivetune.morideobfuscator.MoriCipherRuntime
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CipherSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val runtimeSnapshot: Flow<CipherSnapshot> = MoriCipherRuntime.snapshot

        val manualRefreshHistory: Flow<List<Long>> =
            context.dataStore.data.map { preferences ->
                preferences[MoriCipherManualRefreshHistoryKey]
                    .orEmpty()
                    .mapNotNull(String::toLongOrNull)
                    .sorted()
            }

        suspend fun refresh(): Result<CipherRefreshResult> =
            MoriCipherRuntime.refresh(force = true)

        suspend fun recordSuccessfulManualRefresh(
            timestampMillis: Long,
            validAfterMillis: Long,
        ) {
            context.dataStore.edit { preferences ->
                val retained =
                    preferences[MoriCipherManualRefreshHistoryKey]
                        .orEmpty()
                        .mapNotNull(String::toLongOrNull)
                        .filter { it in validAfterMillis..timestampMillis }
                        .plus(timestampMillis)
                        .sorted()
                        .takeLast(MAX_MANUAL_REFRESHES)
                        .map(Long::toString)
                        .toSet()
                preferences[MoriCipherManualRefreshHistoryKey] = retained
            }
        }

        private companion object {
            const val MAX_MANUAL_REFRESHES = 3
        }
    }
