/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

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
}

internal enum class SupportAdRequestResult {
    Accepted,
    AlreadyPending,
    ActivityUnavailable,
    ConsentRequired,
    ConfigurationMissing,
}

internal interface SupportAdRepository {
    val availability: StateFlow<SupportAdAvailability>
    val events: Flow<SupportAdEvent>

    fun requestSupportAd(): SupportAdRequestResult

    fun setPersonalizedAdsConsent(personalized: Boolean)
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

internal class ShowSupportAdUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(): SupportAdRequestResult = repository.requestSupportAd()
    }

internal class SetPersonalizedAdsConsentUseCase
    @Inject
    constructor(
        private val repository: SupportAdRepository,
    ) {
        operator fun invoke(personalized: Boolean) {
            repository.setPersonalizedAdsConsent(personalized)
        }
    }
