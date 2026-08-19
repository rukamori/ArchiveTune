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
import moe.rukamori.archivetune.utils.dataStore

internal object LeakCanaryVariant {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val watchersInstalled = AtomicBoolean(false)
  private val leakCanaryEnabledKey = booleanPreferencesKey(LeakCanaryToggle.PREFERENCE_KEY)

  @JvmStatic
  fun initialize(application: Application) {
    applyEnabled(application, false)
    scope.launch {
      application.dataStore.data
        .map { preferences -> preferences[leakCanaryEnabledKey] ?: false }
        .distinctUntilChanged()
        .collect { enabled ->
          application.mainExecutor.execute { applyEnabled(application, enabled) }
        }
    }
  }

  @JvmStatic
  fun setEnabled(context: Context, enabled: Boolean) {
    (context.applicationContext as? Application)?.let { applyEnabled(it, enabled) }
  }

  private fun applyEnabled(application: Application, enabled: Boolean) {
    AppWatcher.config = AppWatcher.config.copy(enabled = enabled)
    LeakCanary.config = LeakCanary.config.copy(dumpHeap = enabled)
    if (enabled && watchersInstalled.compareAndSet(false, true)) {
      AppWatcher.manualInstall(application)
    }
  }
}
