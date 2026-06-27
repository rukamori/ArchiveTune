/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SavedAccountsKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.decodeSavedAccounts
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.putLegacyPoToken
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeLoginSession(
    val authState: PlaybackAuthState,
    val accountName: String,
    val accountEmail: String,
    val accountChannelHandle: String,
)

class MissingYouTubeDataSyncIdException : IllegalStateException("YouTube DataSyncId is missing")

@Singleton
class YouTubeLoginRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun completeLogin(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    val normalizedCookie = cookie.trim()
                    check(hasYouTubeLoginCookie(normalizedCookie)) { "YouTube login cookie is missing" }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = normalizedCookie,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                        ).normalized()
                    YouTube.authState = initialAuthState

                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState.dataSyncId)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    YouTube.authState = resolvedAuthState

                    val accountInfo = YouTube.accountInfo().getOrThrow()
                    persistLoginSession(
                        authState = resolvedAuthState,
                        accountInfo = accountInfo,
                    )

                    YouTubeLoginSession(
                        authState = resolvedAuthState,
                        accountName = accountInfo.name,
                        accountEmail = accountInfo.email.orEmpty(),
                        accountChannelHandle = accountInfo.channelHandle.orEmpty(),
                    )
                }
            }

        suspend fun switchSavedAccount(account: SavedAccount): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    check(hasYouTubeLoginCookie(account.innerTubeCookie)) { "Saved account login cookie is missing" }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = account.innerTubeCookie,
                            visitorData = account.visitorData,
                            dataSyncId = account.dataSyncId,
                        ).normalized()
                    YouTube.authState = initialAuthState

                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState.dataSyncId)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    YouTube.authState = resolvedAuthState

                    context.dataStore.edit { preferences ->
                        preferences[InnerTubeCookieKey] = account.innerTubeCookie
                        account.visitorData
                            .normalizeAuthValue()
                            ?.let { preferences[VisitorDataKey] = it }
                            ?: preferences.remove(VisitorDataKey)
                        preferences[DataSyncIdKey] = resolvedDataSyncId
                        preferences[AccountNameKey] = account.name
                        preferences[AccountEmailKey] = account.email
                        preferences[AccountChannelHandleKey] = account.channelHandle
                        preferences[YtmSyncKey] = account.ytmSync
                        preferences[SelectedYtmPlaylistsKey] = account.selectedYtmPlaylists

                        val savedAccounts = decodeSavedAccounts(preferences[SavedAccountsKey].orEmpty())
                        val repairedAccounts =
                            savedAccounts.map { savedAccount ->
                                if (savedAccount.id == account.id && savedAccount.dataSyncId != resolvedDataSyncId) {
                                    savedAccount.copy(dataSyncId = resolvedDataSyncId)
                                } else {
                                    savedAccount
                                }
                            }
                        if (repairedAccounts != savedAccounts) {
                            preferences[SavedAccountsKey] = encodeSavedAccounts(repairedAccounts)
                        }
                    }

                    context.dataStore.data.first().toPlaybackAuthState()
                }
            }

        suspend fun saveLoginContext(
            visitorData: String? = null,
            dataSyncId: String? = null,
        ) {
            withContext(Dispatchers.IO) {
                val normalizedVisitorData = visitorData.normalizeAuthValue()
                val normalizedDataSyncId = dataSyncId.normalizeDataSyncId()
                if (normalizedVisitorData == null && normalizedDataSyncId == null) return@withContext

                context.dataStore.edit { preferences ->
                    normalizedVisitorData?.let { preferences[VisitorDataKey] = it }
                    normalizedDataSyncId?.let { preferences[DataSyncIdKey] = it }
                }

                normalizedVisitorData?.let { YouTube.visitorData = it }
                normalizedDataSyncId?.let { YouTube.dataSyncId = it }
            }
        }

        suspend fun savePoToken(value: String?) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    preferences.putLegacyPoToken(value)
                }
            }
        }

        private suspend fun persistLoginSession(
            authState: PlaybackAuthState,
            accountInfo: AccountInfo,
        ) {
            val dataSyncId = authState.dataSyncId ?: throw MissingYouTubeDataSyncIdException()
            context.dataStore.edit { preferences ->
                preferences[InnerTubeCookieKey] = authState.cookie.orEmpty()
                authState.visitorData
                    ?.let { preferences[VisitorDataKey] = it }
                    ?: preferences.remove(VisitorDataKey)
                preferences[DataSyncIdKey] = dataSyncId
                preferences[AccountNameKey] = accountInfo.name
                preferences[AccountEmailKey] = accountInfo.email.orEmpty()
                preferences[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
            }
        }

        private suspend fun resolveRequiredDataSyncId(candidate: String?): String {
            val networkDataSyncId =
                YouTube
                    .accountDataSyncId()
                    .getOrNull()
                    .normalizeDataSyncId()

            return networkDataSyncId
                ?: candidate.normalizeDataSyncId()
                ?: throw MissingYouTubeDataSyncIdException()
        }
    }

class CompleteYouTubeLoginUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            repository.completeLogin(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
    }

class SwitchSavedYouTubeAccountUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(account: SavedAccount): Result<PlaybackAuthState> = repository.switchSavedAccount(account)
    }

class UpdateYouTubeLoginContextUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            visitorData: String? = null,
            dataSyncId: String? = null,
        ) {
            repository.saveLoginContext(
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
        }
    }

class SaveYouTubePoTokenUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(value: String?) {
            repository.savePoToken(value)
        }
    }

private suspend inline fun <T> runCatchingPreservingCancellation(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        Result.failure(throwable)
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? =
    PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
