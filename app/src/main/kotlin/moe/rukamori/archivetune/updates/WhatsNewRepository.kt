/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.constants.WhatsNewPendingKey
import moe.rukamori.archivetune.constants.WhatsNewVersionKey
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

class WhatsNewRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun registerVersion(version: String): Boolean {
        val registered = context.dataStore.edit { preferences ->
            val previousVersion = preferences[WhatsNewVersionKey]
            if (previousVersion == null || !Updater.isSameVersion(previousVersion, version)) {
                preferences[WhatsNewVersionKey] = version
                preferences[WhatsNewPendingKey] = true
            }
        }
        return registered[WhatsNewPendingKey] == true
    }

    suspend fun acknowledge(version: String) {
        context.dataStore.edit { preferences ->
            val registeredVersion = preferences[WhatsNewVersionKey]
            check(registeredVersion != null && Updater.isSameVersion(registeredVersion, version)) {
                "Cannot acknowledge a different installed version"
            }
            preferences[WhatsNewPendingKey] = false
        }
    }

    suspend fun getRelease(version: String) = Updater.getReleaseForVersion(version).getOrThrow()

    companion object {
        val LOCAL_PREFERENCE_KEYS = setOf(WhatsNewVersionKey.name, WhatsNewPendingKey.name)
    }
}
