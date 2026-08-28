/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import leakcanary.ReachabilityWatcher
import moe.rukamori.archivetune.utils.dataStore

internal object LeakCanaryVariant {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val watchersInstalled = AtomicBoolean(false)
  private val trackingEnabled = AtomicBoolean(false)
  private val leakCanaryEnabledKey = booleanPreferencesKey(LeakCanaryToggle.PREFERENCE_KEY)
  private val gatedReachabilityWatcher = ReachabilityWatcher { watchedObject, description ->
    if (trackingEnabled.get()) {
      AppWatcher.objectWatcher.expectWeaklyReachable(watchedObject, description)
    }
  }

  @JvmStatic
  fun initialize(application: Application) {
    installWatchers(application)
    applyTrackingEnabled(false)
    scope.launch {
      application.dataStore.data
        .map { preferences -> preferences[leakCanaryEnabledKey] ?: false }
        .distinctUntilChanged()
        .collect { enabled -> application.mainExecutor.execute { applyTrackingEnabled(enabled) } }
    }
  }

  @JvmStatic
  fun setEnabled(context: Context, enabled: Boolean) {
    (context.applicationContext as? Application)?.let { applyTrackingEnabled(enabled) }
  }

  private fun installWatchers(application: Application) {
    if (watchersInstalled.compareAndSet(false, true)) {
      AppWatcher.manualInstall(
        application = application,
        watchersToInstall = AppWatcher.appDefaultWatchers(application, gatedReachabilityWatcher),
      )
    }
  }

  private fun applyTrackingEnabled(enabled: Boolean) {
    trackingEnabled.set(enabled)
    LeakCanary.config = LeakCanary.config.copy(dumpHeap = enabled)
  }
}
