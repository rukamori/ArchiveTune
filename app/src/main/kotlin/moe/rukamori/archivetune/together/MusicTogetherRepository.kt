/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TogetherAllowGuestsToAddTracksKey
import moe.rukamori.archivetune.constants.TogetherAllowGuestsToControlPlaybackKey
import moe.rukamori.archivetune.constants.TogetherDefaultPortKey
import moe.rukamori.archivetune.constants.TogetherDisplayNameKey
import moe.rukamori.archivetune.constants.TogetherLastJoinLinkKey
import moe.rukamori.archivetune.constants.TogetherRequireHostApprovalToJoinKey
import moe.rukamori.archivetune.constants.TogetherWelcomeShownKey
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

enum class MusicTogetherConnectionMode {
    LAN,
    ONLINE,
}

data class MusicTogetherPreferences(
    val displayName: String,
    val port: Int,
    val allowGuestsToAddTracks: Boolean,
    val allowGuestsToControlPlayback: Boolean,
    val requireHostApprovalToJoin: Boolean,
    val lastJoinLink: String,
    val welcomeShown: Boolean,
)

data class MusicTogetherSnapshot(
    val preferences: MusicTogetherPreferences,
    val sessionState: TogetherSessionState,
)

@Singleton
class MusicTogetherRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val serviceFlow = MutableStateFlow<MusicService?>(null)

        val preferences: Flow<MusicTogetherPreferences> =
            context.dataStore.data
                .map { preferences ->
                    MusicTogetherPreferences(
                        displayName =
                            preferences[TogetherDisplayNameKey]
                                ?: Build.MODEL?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.app_name),
                        port = preferences[TogetherDefaultPortKey] ?: 42117,
                        allowGuestsToAddTracks = preferences[TogetherAllowGuestsToAddTracksKey] ?: true,
                        allowGuestsToControlPlayback = preferences[TogetherAllowGuestsToControlPlaybackKey] ?: false,
                        requireHostApprovalToJoin = preferences[TogetherRequireHostApprovalToJoinKey] ?: false,
                        lastJoinLink = preferences[TogetherLastJoinLinkKey] ?: "",
                        welcomeShown = preferences[TogetherWelcomeShownKey] ?: false,
                    )
                }.distinctUntilChanged()

        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionState: Flow<TogetherSessionState> =
            serviceFlow.flatMapLatest { service ->
                service?.togetherSessionState ?: flowOf(TogetherSessionState.Idle)
            }

        fun attachService(service: MusicService?) {
            serviceFlow.value = service
        }

        suspend fun setDisplayName(displayName: String) {
            context.dataStore.edit { preferences ->
                preferences[TogetherDisplayNameKey] = displayName
            }
        }

        suspend fun setPort(port: Int) {
            context.dataStore.edit { preferences ->
                preferences[TogetherDefaultPortKey] = port
            }
        }

        suspend fun setAllowGuestsToAddTracks(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherAllowGuestsToAddTracksKey] = value
            }
        }

        suspend fun setAllowGuestsToControlPlayback(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherAllowGuestsToControlPlaybackKey] = value
            }
        }

        suspend fun setRequireHostApprovalToJoin(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherRequireHostApprovalToJoinKey] = value
            }
        }

        suspend fun setLastJoinLink(value: String) {
            context.dataStore.edit { preferences ->
                preferences[TogetherLastJoinLinkKey] = value
            }
        }

        suspend fun setWelcomeShown(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherWelcomeShownKey] = value
            }
        }

        fun startSession(
            mode: MusicTogetherConnectionMode,
            displayName: String,
            port: Int,
            settings: TogetherRoomSettings,
        ) {
            val service = serviceFlow.value ?: return
            when (mode) {
                MusicTogetherConnectionMode.LAN -> {
                    service.startTogetherHost(
                        port = port,
                        displayName = displayName,
                        settings = settings,
                    )
                }

                MusicTogetherConnectionMode.ONLINE -> {
                    service.startTogetherOnlineHost(
                        displayName = displayName,
                        settings = settings,
                    )
                }
            }
        }

        fun joinSession(
            mode: MusicTogetherConnectionMode,
            rawInput: String,
            displayName: String,
        ) {
            val service = serviceFlow.value ?: return
            when (mode) {
                MusicTogetherConnectionMode.LAN -> service.joinTogether(rawInput, displayName)
                MusicTogetherConnectionMode.ONLINE -> service.joinTogetherOnline(rawInput, displayName)
            }
        }

        fun leaveSession() {
            serviceFlow.value?.leaveTogether()
        }

        fun updateSettings(settings: TogetherRoomSettings) {
            serviceFlow.value?.updateTogetherSettings(settings)
        }

        fun approveParticipant(
            participantId: String,
            approved: Boolean,
        ) {
            serviceFlow.value?.approveTogetherParticipant(participantId, approved)
        }

        fun kickParticipant(participantId: String) {
            serviceFlow.value?.kickTogetherParticipant(participantId)
        }

        fun banParticipant(participantId: String) {
            serviceFlow.value?.banTogetherParticipant(participantId)
        }

        fun transferHostOwnership(participantId: String) {
            serviceFlow.value?.transferTogetherHostOwnership(participantId)
        }
    }
