/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppLanguageKey
import moe.rukamori.archivetune.constants.UseSystemLanguageKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

data class AppLanguageSettings(
    val useSystemLanguage: Boolean,
    val languageTag: String,
)

@Singleton
class AppLanguageSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun observeSettings(): Flow<AppLanguageSettings> = context.dataStore.data.map { preferences ->
        AppLanguageSettings(
            useSystemLanguage = preferences[UseSystemLanguageKey] ?: true,
            languageTag = preferences[AppLanguageKey] ?: "en",
        )
    }.distinctUntilChanged()

    suspend fun setUseSystemLanguage(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[UseSystemLanguageKey] = enabled
            }
        }
    }

    suspend fun setLanguage(languageTag: String) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[AppLanguageKey] = languageTag
                preferences[UseSystemLanguageKey] = false
            }
        }
    }
}
