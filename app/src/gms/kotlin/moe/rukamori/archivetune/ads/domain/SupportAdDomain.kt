package moe.rukamori.archivetune.ads.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

internal enum class SupportAdAvailability {
    Preparing,
    Ready,
    ConsentRequired,
    Unavailable,
    Failed,
}

internal enum class SupportAdEvent {
    RewardEarned,
    AdFailed,
    ActivityUnavailable,
    PrivacyOptionsUpdated,
    PrivacyOptionsFailed,
}

internal enum class SupportAdRequestResult {
    Accepted,
    AlreadyPending,
    ActivityUnavailable,
}

internal interface SupportAdRepository {
    val availability: StateFlow<SupportAdAvailability>
    val events: Flow<SupportAdEvent>
    val privacyOptionsRequired: StateFlow<Boolean>

    fun requestSupportAd(): SupportAdRequestResult

    fun showPrivacyOptions(): SupportAdRequestResult
}

internal class ObserveSupportAdAvailabilityUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): StateFlow<SupportAdAvailability> = repository.availability
    }

internal class ObserveSupportAdEventsUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): Flow<SupportAdEvent> = repository.events
    }

internal class ObserveAdPrivacyOptionsUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): StateFlow<Boolean> = repository.privacyOptionsRequired
    }

internal class ShowSupportAdUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): SupportAdRequestResult = repository.requestSupportAd()
    }

internal class ShowAdPrivacyOptionsUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): SupportAdRequestResult = repository.showPrivacyOptions()
    }
