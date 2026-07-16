/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ads.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
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
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.ads.domain.SupportAdAvailability
import moe.rukamori.archivetune.ads.domain.SupportAdEvent
import moe.rukamori.archivetune.ads.domain.SupportAdRepository
import moe.rukamori.archivetune.ads.domain.SupportAdRequestResult
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class StartIoSupportAdRepository
    @Inject
    constructor() :
    SupportAdRepository,
        Application.ActivityLifecycleCallbacks {
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val initialized = AtomicBoolean(false)
        private val sdkInitializationStarted = AtomicBoolean(false)
        private val sdkInitialized = AtomicBoolean(false)
        private val adLoading = AtomicBoolean(false)
        private val pendingUserRequest = AtomicBoolean(false)
        private val adShowing = AtomicBoolean(false)
        private val privacyRevision = AtomicLong(0L)

        private val _availability = MutableStateFlow(SupportAdAvailability.Preparing)
        override val availability: StateFlow<SupportAdAvailability> = _availability.asStateFlow()

        private val _events =
            MutableSharedFlow<SupportAdEvent>(
                extraBufferCapacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: Flow<SupportAdEvent> = _events.asSharedFlow()

        private lateinit var application: Application
        private var consentRecorded = false
        private var personalizedAds = false
        private var consentTimestamp = 0L
        private var resumedActivity = WeakReference<Activity>(null)
        private var adActivity = WeakReference<Activity>(null)
        private var startAppAd: StartAppAd? = null
        private var loadTimeoutRunnable: Runnable? = null

        fun initialize(application: Application) {
            if (!initialized.compareAndSet(false, true)) return
            this.application = application
            application.registerActivityLifecycleCallbacks(this)
            backgroundScope.launch {
                val revision = privacyRevision.get()
                val preferences =
                    application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                val hasConsent = preferences.contains(KEY_PERSONALIZED_ADS)
                val personalized = preferences.getBoolean(KEY_PERSONALIZED_ADS, false)
                val timestamp = preferences.getLong(KEY_CONSENT_TIMESTAMP, 0L)
                mainHandler.post {
                    if (privacyRevision.get() != revision) return@post
                    consentRecorded = hasConsent
                    personalizedAds = personalized
                    consentTimestamp = timestamp
                    when {
                        BuildConfig.START_IO_APP_ID.isBlank() -> {
                            _availability.value = SupportAdAvailability.Failed
                        }

                        hasConsent -> {
                            initializeSdk()
                        }

                        else -> {
                            _availability.value = SupportAdAvailability.ConsentRequired
                        }
                    }
                }
            }
        }

        override fun requestSupportAd(): SupportAdRequestResult {
            if (BuildConfig.START_IO_APP_ID.isBlank()) {
                return SupportAdRequestResult.ConfigurationMissing
            }
            if (!consentRecorded) return SupportAdRequestResult.ConsentRequired
            val activity = currentActivity() ?: return SupportAdRequestResult.ActivityUnavailable
            if (adShowing.get() || !pendingUserRequest.compareAndSet(false, true)) {
                return SupportAdRequestResult.AlreadyPending
            }
            mainHandler.post {
                initializeSdk()
                showLoadedAdOrLoad(activity)
            }
            return SupportAdRequestResult.Accepted
        }

        override fun setPersonalizedAdsConsent(personalized: Boolean) {
            privacyRevision.incrementAndGet()
            consentRecorded = true
            personalizedAds = personalized
            consentTimestamp = System.currentTimeMillis()
            application
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERSONALIZED_ADS, personalized)
                .putLong(KEY_CONSENT_TIMESTAMP, consentTimestamp)
                .apply()
            clearLoadedAd()
            initializeSdk()
        }

        private fun initializeSdk() {
            if (!consentRecorded || BuildConfig.START_IO_APP_ID.isBlank()) return
            if (sdkInitialized.get()) {
                configurePrivacySignals()
                loadSupportAdIfNecessary()
                return
            }
            if (!sdkInitializationStarted.compareAndSet(false, true)) return
            _availability.value = SupportAdAvailability.Preparing
            runCatching {
                StartAppSDK.enableConsent(application, false)
                StartAppAd.disableSplash()
                StartAppAd.disableAutoInterstitial()
                StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
                configurePrivacySignals()
                StartAppSDK
                    .initParams(application, BuildConfig.START_IO_APP_ID)
                    .setReturnAdsEnabled(false)
                    .setCallback {
                        sdkInitialized.set(true)
                        mainHandler.post(::loadSupportAdIfNecessary)
                    }.init()
                StartAppSDK.setUserConsent(
                    application,
                    PERSONALIZED_ADS_CONSENT,
                    consentTimestamp,
                    personalizedAds,
                )
            }.onFailure { throwable ->
                sdkInitializationStarted.set(false)
                _availability.value = SupportAdAvailability.Failed
                Timber.e(throwable, "Start.io SDK initialization failed")
                clearPendingRequest(SupportAdEvent.AdFailed)
            }
        }

        private fun configurePrivacySignals() {
            if (!consentRecorded) return
            StartAppSDK
                .getExtras(application)
                .edit()
                .putString(
                    IAB_US_PRIVACY_STRING,
                    if (personalizedAds) CCPA_PERSONALIZED else CCPA_OPT_OUT,
                ).apply()
            if (sdkInitialized.get()) {
                StartAppSDK.setUserConsent(
                    application,
                    PERSONALIZED_ADS_CONSENT,
                    consentTimestamp,
                    personalizedAds,
                )
            }
        }

        private fun showLoadedAdOrLoad(activity: Activity) {
            if (!pendingUserRequest.get() || !isActivityUsable(activity)) {
                if (!isActivityUsable(activity)) {
                    clearPendingRequest(SupportAdEvent.ActivityUnavailable)
                }
                return
            }
            if (!sdkInitialized.get()) return
            val ad = startAppAd
            if (ad != null && adActivity.get() === activity && ad.isReady) {
                showSupportAd(ad)
            } else {
                clearLoadedAd()
                loadSupportAdIfNecessary()
            }
        }

        private fun loadSupportAdIfNecessary() {
            if (!sdkInitialized.get() || adShowing.get() || !consentRecorded) return
            val activity = currentActivity() ?: return
            val existingAd = startAppAd
            if (existingAd != null && adActivity.get() === activity && existingAd.isReady) {
                _availability.value = SupportAdAvailability.Ready
                if (pendingUserRequest.get()) showSupportAd(existingAd)
                return
            }
            if (!adLoading.compareAndSet(false, true)) return
            clearLoadedAd(closeLoadingAd = false)
            _availability.value = SupportAdAvailability.Preparing
            val ad = StartAppAd(activity)
            startAppAd = ad
            adActivity = WeakReference(activity)
            scheduleLoadTimeout(ad)
            runCatching {
                ad.loadAd(
                    StartAppAd.AdMode.AUTOMATIC,
                    object : AdEventListener {
                        override fun onReceiveAd(receivedAd: Ad) {
                            mainHandler.post {
                                if (startAppAd !== ad) {
                                    ad.close()
                                    return@post
                                }
                                cancelLoadTimeout()
                                adLoading.set(false)
                                _availability.value = SupportAdAvailability.Ready
                                if (pendingUserRequest.get()) showSupportAd(ad)
                            }
                        }

                        override fun onFailedToReceiveAd(failedAd: Ad?) {
                            mainHandler.post {
                                if (startAppAd !== ad) return@post
                                _availability.value = SupportAdAvailability.Preparing
                                Timber.w(
                                    "Start.io support ad load failed; SDK retry remains active: %s",
                                    failedAd?.errorMessage,
                                )
                            }
                        }
                    },
                )
            }.onFailure { throwable ->
                failAdLoad(ad, throwable)
            }
        }

        private fun showSupportAd(ad: StartAppAd) {
            if (!pendingUserRequest.get() || !ad.isReady) {
                loadSupportAdIfNecessary()
                return
            }
            if (!adShowing.compareAndSet(false, true)) return
            pendingUserRequest.set(false)
            val completionHandled = AtomicBoolean(false)
            val displayListener =
                object : AdDisplayListener {
                    override fun adDisplayed(displayedAd: Ad) {
                        _availability.value = SupportAdAvailability.Preparing
                    }

                    override fun adHidden(hiddenAd: Ad) {
                        finishDisplayedAd(ad, completionHandled, failed = false)
                    }

                    override fun adClicked(clickedAd: Ad) = Unit

                    override fun adNotDisplayed(hiddenAd: Ad) {
                        finishDisplayedAd(ad, completionHandled, failed = true)
                    }
                }
            val shown =
                runCatching { ad.showAd(displayListener) }.getOrElse { throwable ->
                    Timber.e(throwable, "Start.io support ad show call failed")
                    false
                }
            if (!shown) finishDisplayedAd(ad, completionHandled, failed = true)
        }

        private fun finishDisplayedAd(
            ad: StartAppAd,
            completionHandled: AtomicBoolean,
            failed: Boolean,
        ) {
            if (!completionHandled.compareAndSet(false, true)) return
            mainHandler.post {
                adShowing.set(false)
                if (startAppAd === ad) {
                    startAppAd = null
                    adActivity.clear()
                }
                ad.close()
                if (failed) {
                    _events.tryEmit(SupportAdEvent.AdFailed)
                } else {
                    _events.tryEmit(SupportAdEvent.RewardEarned)
                }
                loadSupportAdIfNecessary()
            }
        }

        private fun scheduleLoadTimeout(ad: StartAppAd) {
            cancelLoadTimeout()
            val timeoutRunnable =
                Runnable {
                    if (startAppAd !== ad || !adLoading.compareAndSet(true, false)) return@Runnable
                    loadTimeoutRunnable = null
                    startAppAd = null
                    adActivity.clear()
                    ad.close()
                    _availability.value = SupportAdAvailability.Failed
                    Timber.w("Start.io support ad load timed out after SDK retries")
                    clearPendingRequest(SupportAdEvent.AdFailed)
                }
            loadTimeoutRunnable = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, SUPPORT_AD_LOAD_TIMEOUT_MILLIS)
        }

        private fun cancelLoadTimeout() {
            loadTimeoutRunnable?.let(mainHandler::removeCallbacks)
            loadTimeoutRunnable = null
        }

        private fun failAdLoad(
            ad: StartAppAd,
            throwable: Throwable,
        ) {
            if (startAppAd !== ad) return
            cancelLoadTimeout()
            startAppAd = null
            adActivity.clear()
            ad.close()
            adLoading.set(false)
            _availability.value = SupportAdAvailability.Failed
            Timber.e(throwable, "Start.io support ad load call failed")
            clearPendingRequest(SupportAdEvent.AdFailed)
        }

        private fun clearPendingRequest(event: SupportAdEvent) {
            if (pendingUserRequest.getAndSet(false)) _events.tryEmit(event)
        }

        private fun clearLoadedAd(closeLoadingAd: Boolean = true) {
            cancelLoadTimeout()
            val ad = startAppAd
            startAppAd = null
            adActivity.clear()
            if (closeLoadingAd) adLoading.set(false)
            ad?.close()
        }

        private fun currentActivity(): Activity? = resumedActivity.get()?.takeIf(::isActivityUsable)

        private fun isActivityUsable(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

        private fun isArchiveTuneActivity(activity: Activity): Boolean = activity is MainActivity

        override fun onActivityResumed(activity: Activity) {
            if (!isArchiveTuneActivity(activity)) return
            val previousActivity = resumedActivity.get()
            resumedActivity = WeakReference(activity)
            if (previousActivity !== activity && !adShowing.get()) clearLoadedAd()
            startAppAd?.onResume()
            if (sdkInitialized.get()) loadSupportAdIfNecessary()
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedActivity.get() === activity) startAppAd?.onPause()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumedActivity.get() === activity) resumedActivity.clear()
            if (adActivity.get() === activity && !adShowing.get()) clearLoadedAd()
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        companion object {
            private const val PREFERENCES_NAME = "start_io_privacy"
            private const val KEY_PERSONALIZED_ADS = "personalized_ads"
            private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
            private const val PERSONALIZED_ADS_CONSENT = "pas"
            private const val IAB_US_PRIVACY_STRING = "IABUSPrivacy_String"
            private const val CCPA_PERSONALIZED = "1YNN"
            private const val CCPA_OPT_OUT = "1YYN"
            private const val SUPPORT_AD_LOAD_TIMEOUT_MILLIS = 30_000L
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SupportAdDataModule {
    @Binds
    @Singleton
    abstract fun bindSupportAdRepository(repository: StartIoSupportAdRepository): SupportAdRepository
}
