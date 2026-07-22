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
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
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
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
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
        private val pendingUserRequest = AtomicBoolean(false)

        private val _availability = MutableStateFlow(SupportAdAvailability.Ready)
        override val availability: StateFlow<SupportAdAvailability> = _availability.asStateFlow()

        private val _events =
            MutableSharedFlow<SupportAdEvent>(
                extraBufferCapacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: Flow<SupportAdEvent> = _events.asSharedFlow()

        private lateinit var application: Application
        private var consentRecorded = true
        private var personalizedAds = true
        private var consentTimestamp = System.currentTimeMillis()
        private var resumedActivity = WeakReference<Activity>(null)

        fun initialize(application: Application) {
            if (!initialized.compareAndSet(false, true)) return
            this.application = application
            application.registerActivityLifecycleCallbacks(this)
            backgroundScope.launch {
                val preferences =
                    application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                val hasConsent = preferences.contains(KEY_PERSONALIZED_ADS)
                val personalized = preferences.getBoolean(KEY_PERSONALIZED_ADS, true)
                val timestamp = preferences.getLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
                mainHandler.post {
                    consentRecorded = true
                    personalizedAds = personalized
                    consentTimestamp = timestamp
                    _availability.value = SupportAdAvailability.Ready
                }
            }
        }

        override fun requestSupportAd(): SupportAdRequestResult {
            val activity = currentActivity() ?: return SupportAdRequestResult.ActivityUnavailable
            if (!pendingUserRequest.compareAndSet(false, true)) {
                return SupportAdRequestResult.AlreadyPending
            }
            consentRecorded = true
            _availability.value = SupportAdAvailability.Ready
            
            // Web Ad container URL hosted on ArchiveTune data server
            val appId = BuildConfig.START_IO_APP_ID.ifBlank { DEFAULT_TEST_APP_ID }
            val adUrl = "https://archivetune.koiiverse.cloud/support.html?appId=$appId"

            mainHandler.post {
                runCatching {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(false)
                        .build()
                    customTabsIntent.launchUrl(activity, Uri.parse(adUrl))
                }
                _events.tryEmit(SupportAdEvent.RewardEarned)
                pendingUserRequest.set(false)
            }
            return SupportAdRequestResult.Accepted
        }

        override fun setPersonalizedAdsConsent(personalized: Boolean) {
            consentRecorded = true
            personalizedAds = personalized
            consentTimestamp = System.currentTimeMillis()
            application
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERSONALIZED_ADS, personalized)
                .putLong(KEY_CONSENT_TIMESTAMP, consentTimestamp)
                .apply()
            _availability.value = SupportAdAvailability.Ready
        }

        private fun currentActivity(): Activity? = resumedActivity.get()?.takeIf(::isActivityUsable)

        private fun isActivityUsable(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

        private fun isArchiveTuneActivity(activity: Activity): Boolean = activity is MainActivity

        override fun onActivityResumed(activity: Activity) {
            if (!isArchiveTuneActivity(activity)) return
            resumedActivity = WeakReference(activity)
            _availability.value = SupportAdAvailability.Ready
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedActivity.get() === activity) {
                resumedActivity.clear()
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumedActivity.get() === activity) resumedActivity.clear()
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
            private const val DEFAULT_TEST_APP_ID = "200424565"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SupportAdDataModule {
    @Binds
    @Singleton
    abstract fun bindSupportAdRepository(repository: StartIoSupportAdRepository): SupportAdRepository
}
