/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.playback.MusicService
import javax.inject.Inject

class AttachMusicTogetherServiceUseCase
    @Inject
    constructor(
        private val repository: MusicTogetherRepository,
    ) {
        operator fun invoke(service: MusicService?) {
            repository.attachService(service)
        }
    }

class ObserveMusicTogetherStateUseCase
    @Inject
    constructor(
        private val repository: MusicTogetherRepository,
    ) {
        operator fun invoke(): Flow<MusicTogetherSnapshot> =
            combine(repository.preferences, repository.sessionState) { preferences, sessionState ->
                MusicTogetherSnapshot(
                    preferences = preferences,
                    sessionState = sessionState,
                )
            }
    }

class UpdateMusicTogetherPreferencesUseCase
    @Inject
    constructor(
        private val repository: MusicTogetherRepository,
    ) {
        suspend fun setDisplayName(displayName: String) {
            repository.setDisplayName(displayName)
        }

        suspend fun setPort(port: Int) {
            repository.setPort(port)
        }

        suspend fun setAllowGuestsToAddTracks(value: Boolean) {
            repository.setAllowGuestsToAddTracks(value)
        }

        suspend fun setAllowGuestsToControlPlayback(value: Boolean) {
            repository.setAllowGuestsToControlPlayback(value)
        }

        suspend fun setRequireHostApprovalToJoin(value: Boolean) {
            repository.setRequireHostApprovalToJoin(value)
        }

        suspend fun setLastJoinLink(value: String) {
            repository.setLastJoinLink(value)
        }

        suspend fun setWelcomeShown(value: Boolean) {
            repository.setWelcomeShown(value)
        }
    }

class MusicTogetherSessionActionsUseCase
    @Inject
    constructor(
        private val repository: MusicTogetherRepository,
    ) {
        fun startSession(
            mode: MusicTogetherConnectionMode,
            displayName: String,
            port: Int,
            settings: TogetherRoomSettings,
        ) {
            repository.startSession(
                mode = mode,
                displayName = displayName,
                port = port,
                settings = settings,
            )
        }

        fun joinSession(
            mode: MusicTogetherConnectionMode,
            rawInput: String,
            displayName: String,
        ) {
            repository.joinSession(
                mode = mode,
                rawInput = rawInput,
                displayName = displayName,
            )
        }

        fun leaveSession() {
            repository.leaveSession()
        }

        fun updateSettings(settings: TogetherRoomSettings) {
            repository.updateSettings(settings)
        }

        fun approveParticipant(
            participantId: String,
            approved: Boolean,
        ) {
            repository.approveParticipant(participantId, approved)
        }

        fun kickParticipant(participantId: String) {
            repository.kickParticipant(participantId)
        }

        fun banParticipant(participantId: String) {
            repository.banParticipant(participantId)
        }

        fun transferHostOwnership(participantId: String) {
            repository.transferHostOwnership(participantId)
        }
    }
