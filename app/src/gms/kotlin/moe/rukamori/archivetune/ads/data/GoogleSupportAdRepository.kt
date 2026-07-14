package moe.rukamori.archivetune.ads.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.ads.domain.SupportAdAvailability
import moe.rukamori.archivetune.ads.domain.SupportAdEvent
import moe.rukamori.archivetune.ads.domain.SupportAdRepository
import moe.rukamori.archivetune.ads.domain.SupportAdRequestResult
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GoogleSupportAdRepository
    @Inject
    constructor() : SupportAdRepository,
        Application.ActivityLifecycleCallbacks {
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val lock = Any()
        private val initialized = AtomicBoolean(false)
        private val consentUpdateStarted = AtomicBoolean(false)
        private val consentUpdateComplete = AtomicBoolean(false)
        private val consentFormShowing = AtomicBoolean(false)
        private val privacyFormShowing = AtomicBoolean(false)
        private val mobileAdsInitializationStarted = AtomicBoolean(false)
        private val mobileAdsInitialized = AtomicBoolean(false)
        private val adLoading = AtomicBoolean(false)
        private val pendingUserRequest = AtomicBoolean(false)
        private val adShowing = AtomicBoolean(false)

        private val _availability = MutableStateFlow(SupportAdAvailability.Preparing)
        override val availability: StateFlow<SupportAdAvailability> = _availability.asStateFlow()

        private val _events =
            MutableSharedFlow<SupportAdEvent>(
                extraBufferCapacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: Flow<SupportAdEvent> = _events.asSharedFlow()

        private val _privacyOptionsRequired = MutableStateFlow(false)
        override val privacyOptionsRequired: StateFlow<Boolean> =
            _privacyOptionsRequired.asStateFlow()

        private lateinit var application: Application
        private lateinit var consentInformation: ConsentInformation
        private var resumedActivity = WeakReference<Activity>(null)
        private var rewardedAd: RewardedAd? = null
        private var rewardedAdLoadedAtMillis: Long = 0L

        fun initialize(application: Application) {
            if (!initialized.compareAndSet(false, true)) return
            this.application = application
            consentInformation = UserMessagingPlatform.getConsentInformation(application)
            application.registerActivityLifecycleCallbacks(this)
        }

        override fun requestSupportAd(): SupportAdRequestResult {
            val activity = currentActivity() ?: return SupportAdRequestResult.ActivityUnavailable
            if (adShowing.get()) return SupportAdRequestResult.AlreadyPending
            if (!pendingUserRequest.compareAndSet(false, true)) {
                return SupportAdRequestResult.AlreadyPending
            }
            mainHandler.post { continuePendingUserRequest(activity) }
            return SupportAdRequestResult.Accepted
        }

        override fun showPrivacyOptions(): SupportAdRequestResult {
            val activity = currentActivity() ?: return SupportAdRequestResult.ActivityUnavailable
            if (pendingUserRequest.get() || adShowing.get()) {
                return SupportAdRequestResult.AlreadyPending
            }
            if (!privacyFormShowing.compareAndSet(false, true)) {
                return SupportAdRequestResult.AlreadyPending
            }
            mainHandler.post {
                UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                    privacyFormShowing.set(false)
                    updatePrivacyOptionsRequirement()
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds()
                    } else {
                        clearLoadedAd()
                        _availability.value = SupportAdAvailability.Unavailable
                    }
                    if (formError != null) {
                        Timber.w("Ad privacy options failed: %s", formError.message)
                        _events.tryEmit(SupportAdEvent.PrivacyOptionsFailed)
                        return@showPrivacyOptionsForm
                    }
                    _events.tryEmit(SupportAdEvent.PrivacyOptionsUpdated)
                }
            }
            return SupportAdRequestResult.Accepted
        }

        private fun requestConsentUpdate(activity: Activity) {
            if (!consentUpdateStarted.compareAndSet(false, true)) return
            val parameters = ConsentRequestParameters.Builder().build()
            consentInformation.requestConsentInfoUpdate(
                activity,
                parameters,
                {
                    consentUpdateComplete.set(true)
                    updatePrivacyOptionsRequirement()
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds()
                    } else {
                        clearLoadedAd()
                        _availability.value = SupportAdAvailability.ConsentRequired
                    }
                    if (pendingUserRequest.get()) {
                        currentActivity()?.let(::continuePendingUserRequest)
                            ?: clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                    }
                },
                { requestConsentError ->
                    consentUpdateComplete.set(true)
                    updatePrivacyOptionsRequirement()
                    Timber.w("Consent information update failed: %s", requestConsentError.message)
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds()
                        if (pendingUserRequest.get()) {
                            currentActivity()?.let(::continuePendingUserRequest)
                                ?: clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                        }
                    } else {
                        consentUpdateComplete.set(false)
                        consentUpdateStarted.set(false)
                        clearLoadedAd()
                        _availability.value = SupportAdAvailability.Failed
                        clearPendingRequest(SupportAdEvent.AdFailed)
                    }
                },
            )
        }

        private fun continuePendingUserRequest(activity: Activity) {
            if (!pendingUserRequest.get()) return
            if (!isActivityUsable(activity)) {
                clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                return
            }
            if (!consentUpdateComplete.get()) {
                requestConsentUpdate(activity)
                return
            }
            if (!consentInformation.canRequestAds()) {
                showConsentFormForPendingRequest(activity)
                return
            }
            initializeMobileAds()
            showLoadedAdOrLoad(activity)
        }

        private fun showConsentFormForPendingRequest(activity: Activity) {
            if (!consentFormShowing.compareAndSet(false, true)) return
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                consentFormShowing.set(false)
                updatePrivacyOptionsRequirement()
                if (formError != null) {
                    Timber.w("Consent form failed: %s", formError.message)
                }
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds()
                    currentActivity()?.let(::showLoadedAdOrLoad)
                        ?: clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                } else {
                    clearLoadedAd()
                    _availability.value = SupportAdAvailability.Unavailable
                    clearPendingRequest(SupportAdEvent.AdFailed)
                }
            }
        }

        private fun initializeMobileAds() {
            if (mobileAdsInitialized.get()) {
                loadRewardedAdIfNecessary()
                return
            }
            if (!mobileAdsInitializationStarted.compareAndSet(false, true)) return
            _availability.value = SupportAdAvailability.Preparing
            backgroundScope.launch {
                runCatching {
                    MobileAds.initialize(
                        application,
                        InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build(),
                    ) {
                        mobileAdsInitialized.set(true)
                        loadRewardedAdIfNecessary()
                    }
                }.onFailure { throwable ->
                    mobileAdsInitializationStarted.set(false)
                    _availability.value = SupportAdAvailability.Failed
                    Timber.e(throwable, "Google Mobile Ads initialization failed")
                    clearPendingRequest(SupportAdEvent.AdFailed)
                }
            }
        }

        private fun showLoadedAdOrLoad(activity: Activity) {
            if (!mobileAdsInitialized.get()) return
            val hasFreshAd =
                synchronized(lock) {
                    if (rewardedAd != null && isLoadedAdExpired()) {
                        rewardedAd?.destroy()
                        rewardedAd = null
                        rewardedAdLoadedAtMillis = 0L
                    }
                    rewardedAd != null
                }
            if (hasFreshAd) {
                showRewardedAd(activity)
            } else {
                loadRewardedAdIfNecessary()
            }
        }

        private fun loadRewardedAdIfNecessary() {
            if (
                !mobileAdsInitialized.get() ||
                adShowing.get() ||
                !consentInformation.canRequestAds()
            ) {
                return
            }
            synchronized(lock) {
                if (rewardedAd != null && !isLoadedAdExpired()) {
                    _availability.value = SupportAdAvailability.Ready
                    if (pendingUserRequest.get()) {
                        currentActivity()?.let { activity ->
                            mainHandler.post { showRewardedAd(activity) }
                        } ?: clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                    }
                    return
                }
                rewardedAd?.destroy()
                rewardedAd = null
                rewardedAdLoadedAtMillis = 0L
            }
            if (!adLoading.compareAndSet(false, true)) return
            _availability.value = SupportAdAvailability.Preparing
            RewardedAd.load(
                AdRequest.Builder(BuildConfig.ADMOB_REWARDED_AD_UNIT_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (!consentInformation.canRequestAds()) {
                            ad.destroy()
                            adLoading.set(false)
                            _availability.value = SupportAdAvailability.Unavailable
                            clearPendingRequest(SupportAdEvent.AdFailed)
                            return
                        }
                        synchronized(lock) {
                            rewardedAd = ad
                            rewardedAdLoadedAtMillis = SystemClock.elapsedRealtime()
                        }
                        adLoading.set(false)
                        _availability.value = SupportAdAvailability.Ready
                        if (pendingUserRequest.get()) {
                            currentActivity()?.let { activity ->
                                mainHandler.post { showRewardedAd(activity) }
                            } ?: clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        synchronized(lock) {
                            rewardedAd = null
                            rewardedAdLoadedAtMillis = 0L
                        }
                        adLoading.set(false)
                        _availability.value = SupportAdAvailability.Failed
                        Timber.w("Rewarded ad failed to load: %s", adError.message)
                        clearPendingRequest(SupportAdEvent.AdFailed)
                    }
                },
            )
        }

        private fun showRewardedAd(activity: Activity) {
            if (!pendingUserRequest.get() || !isActivityUsable(activity)) {
                if (!isActivityUsable(activity)) {
                    clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                }
                return
            }
            if (!adShowing.compareAndSet(false, true)) return
            val ad =
                synchronized(lock) {
                    rewardedAd.also {
                        rewardedAd = null
                        rewardedAdLoadedAtMillis = 0L
                    }
                }
            if (ad == null) {
                adShowing.set(false)
                loadRewardedAdIfNecessary()
                return
            }
            pendingUserRequest.set(false)
            val rewardEarned = AtomicBoolean(false)
            ad.adEventCallback =
                object : RewardedAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        _availability.value = SupportAdAvailability.Preparing
                    }

                    override fun onAdDismissedFullScreenContent() {
                        adShowing.set(false)
                        ad.destroy()
                        if (rewardEarned.get()) {
                            _events.tryEmit(SupportAdEvent.RewardEarned)
                        }
                        loadRewardedAdIfNecessary()
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        fullScreenContentError: FullScreenContentError,
                    ) {
                        adShowing.set(false)
                        ad.destroy()
                        Timber.w("Rewarded ad failed to show: %s", fullScreenContentError.message)
                        _events.tryEmit(SupportAdEvent.AdFailed)
                        loadRewardedAdIfNecessary()
                    }
                }
            mainHandler.post {
                if (!isActivityUsable(activity)) {
                    adShowing.set(false)
                    ad.destroy()
                    _events.tryEmit(SupportAdEvent.ActivityUnavailable)
                    loadRewardedAdIfNecessary()
                    return@post
                }
                runCatching {
                    ad.show(
                        activity,
                        OnUserEarnedRewardListener { rewardEarned.set(true) },
                    )
                }.onFailure { throwable ->
                    adShowing.set(false)
                    ad.destroy()
                    Timber.e(throwable, "Rewarded ad show call failed")
                    _events.tryEmit(SupportAdEvent.AdFailed)
                    loadRewardedAdIfNecessary()
                }
            }
        }

        private fun clearPendingRequest(event: SupportAdEvent) {
            if (pendingUserRequest.getAndSet(false)) {
                _events.tryEmit(event)
            }
        }

        private fun clearLoadedAd() {
            synchronized(lock) {
                rewardedAd?.destroy()
                rewardedAd = null
                rewardedAdLoadedAtMillis = 0L
            }
        }

        private fun updatePrivacyOptionsRequirement() {
            _privacyOptionsRequired.value =
                consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }

        private fun currentActivity(): Activity? = resumedActivity.get()?.takeIf(::isActivityUsable)

        private fun isActivityUsable(activity: Activity): Boolean =
            !activity.isFinishing && !activity.isDestroyed

        private fun isLoadedAdExpired(): Boolean =
            rewardedAdLoadedAtMillis == 0L ||
                SystemClock.elapsedRealtime() - rewardedAdLoadedAtMillis >= MAX_AD_AGE_MILLIS

        override fun onActivityResumed(activity: Activity) {
            resumedActivity = WeakReference(activity)
            requestConsentUpdate(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumedActivity.get() === activity) {
                resumedActivity.clear()
            }
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        companion object {
            private const val MAX_AD_AGE_MILLIS = 55L * 60L * 1000L
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SupportAdDataModule {
    @Binds
    @Singleton
    abstract fun bindSupportAdRepository(
        repository: GoogleSupportAdRepository,
    ): SupportAdRepository
}
