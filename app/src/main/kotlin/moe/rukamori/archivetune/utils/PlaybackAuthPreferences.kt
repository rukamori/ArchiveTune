/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.PoTokenSourceUrlKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.WebClientPoTokenEnabledKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube

fun Preferences.toPlaybackAuthState(): PlaybackAuthState =
    PlaybackAuthState(
        cookie = this[InnerTubeCookieKey],
        visitorData = this[VisitorDataKey],
        dataSyncId = this[DataSyncIdKey],
        poToken = this[PoTokenKey],
        poTokenGvs = this[PoTokenGvsKey],
        poTokenPlayer = this[PoTokenPlayerKey],
        webClientPoTokenEnabled = this[WebClientPoTokenEnabledKey] ?: false,
    ).normalized()

fun MutablePreferences.clearPlaybackAuthSession(clearAccountIdentity: Boolean = true) {
    remove(InnerTubeCookieKey)
    remove(VisitorDataKey)
    remove(DataSyncIdKey)
    remove(PoTokenKey)
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
    remove(PoTokenSourceUrlKey)
    if (clearAccountIdentity) {
        remove(AccountNameKey)
        remove(AccountEmailKey)
        remove(AccountChannelHandleKey)
    }
}

fun MutablePreferences.clearPlaybackLoginContext() {
    remove(DataSyncIdKey)
}

fun PlaybackAuthState.withoutPlaybackLoginContext(): PlaybackAuthState = copy(dataSyncId = null).normalized()

fun MutablePreferences.putLegacyPoToken(value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    if (normalized == null) {
        remove(PoTokenKey)
    } else {
        this[PoTokenKey] = normalized
    }
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
}

suspend fun Context.resetPlaybackLoginContext(): PlaybackAuthState {
    dataStore.edit { preferences ->
        preferences.clearPlaybackLoginContext()
    }
    val authState = dataStore.data.first().toPlaybackAuthState()
    YouTube.authState = authState
    YTPlayerUtils.clearPlaybackAuthCaches()
    return authState
}

suspend fun <T> Context.retryWithoutPlaybackLoginContext(block: suspend () -> Result<T>): Result<T> {
    val initialAuthState = YouTube.currentPlaybackAuthState()
    val initialResult = block()
    if (initialResult.isSuccess) {
        val repairedAuthState = YouTube.currentPlaybackAuthState()
        restoreTemporaryPlaybackLoginContext(initialAuthState, repairedAuthState)
        persistPlaybackAuthRepair(
            initialAuthState = initialAuthState,
            repairedAuthState = repairedAuthState,
        )
        return initialResult
    }
    val failure = initialResult.exceptionOrNull()

    val currentAuthState = YouTube.currentPlaybackAuthState()
    if (!shouldRetryWithoutPlaybackLoginContext(initialAuthState, currentAuthState, failure)) {
        return initialResult
    }

    YouTube.authState = currentAuthState.withoutPlaybackLoginContext()
    YTPlayerUtils.clearPlaybackAuthCaches()
    var retryAuthState: PlaybackAuthState? = null
    val retryResult =
        try {
            block().also {
                retryAuthState = YouTube.currentPlaybackAuthState()
            }
        } finally {
            restoreTemporaryPlaybackLoginContext(
                initialAuthState = currentAuthState,
                repairedAuthState = retryAuthState ?: YouTube.currentPlaybackAuthState(),
            )
        }
    if (retryResult.isSuccess) {
        persistPlaybackAuthRepair(
            initialAuthState = currentAuthState,
            repairedAuthState = requireNotNull(retryAuthState),
        )
    }
    return retryResult
}

private fun restoreTemporaryPlaybackLoginContext(
    initialAuthState: PlaybackAuthState,
    repairedAuthState: PlaybackAuthState,
) {
    val dataSyncId = initialAuthState.dataSyncId ?: return
    if (repairedAuthState.dataSyncId != null) return
    if (repairedAuthState.cookie != initialAuthState.cookie) return
    if (YouTube.currentPlaybackAuthState().fingerprint != repairedAuthState.fingerprint) return
    YouTube.authState = repairedAuthState.copy(dataSyncId = dataSyncId).normalized()
}

private suspend fun Context.persistPlaybackAuthRepair(
    initialAuthState: PlaybackAuthState,
    repairedAuthState: PlaybackAuthState,
) {
    if (initialAuthState.cookie != repairedAuthState.cookie) return
    if (initialAuthState.fingerprint == repairedAuthState.fingerprint) return

    dataStore.edit { preferences ->
        repairedAuthState.visitorData
            ?.takeIf { it.isNotBlank() && it != initialAuthState.visitorData }
            ?.let { preferences[VisitorDataKey] = it }
        repairedAuthState.dataSyncId
            ?.takeIf { it.isNotBlank() && it != initialAuthState.dataSyncId }
            ?.let { preferences[DataSyncIdKey] = it }
    }
}

internal fun shouldRetryWithoutPlaybackLoginContext(
    initialAuthState: PlaybackAuthState,
    currentAuthState: PlaybackAuthState,
    failure: Throwable?,
): Boolean {
    if (failure !is YTPlayerUtils.InvalidPlaybackLoginContextException) return false
    if (!initialAuthState.hasPlaybackLoginContext) return false
    if (!currentAuthState.hasPlaybackLoginContext) return false
    if (currentAuthState.cookie != initialAuthState.cookie) return false
    if (currentAuthState.dataSyncId != initialAuthState.dataSyncId) return false
    return true
}
