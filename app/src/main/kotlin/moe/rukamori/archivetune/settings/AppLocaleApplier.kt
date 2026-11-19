/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

object AppLocaleApplier {
    private const val PREFERENCES_NAME = "app_locale"
    private const val LANGUAGE_TAG_KEY = "language_tag"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val languageTag =
            base
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(LANGUAGE_TAG_KEY, null)
                ?: return base
        val overrideConfiguration = Configuration().apply { setLocale(Locale.forLanguageTag(languageTag)) }
        return base.createConfigurationContext(overrideConfiguration)
    }

    fun apply(
        activity: Activity,
        languageTag: String?,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyPlatformLocales(activity, languageTag)
            false
        } else {
            applyStoredLocale(activity, languageTag)
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyPlatformLocales(
        activity: Activity,
        languageTag: String?,
    ) {
        val localeManager = activity.getSystemService(LocaleManager::class.java) ?: return
        val desiredLocales = languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        if (localeManager.applicationLocales != desiredLocales) {
            localeManager.applicationLocales = desiredLocales
        }
    }

    private fun applyStoredLocale(
        activity: Activity,
        languageTag: String?,
    ): Boolean {
        val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(LANGUAGE_TAG_KEY, null) == languageTag) return false
        preferences
            .edit()
            .apply {
                if (languageTag == null) remove(LANGUAGE_TAG_KEY) else putString(LANGUAGE_TAG_KEY, languageTag)
            }.apply()
        return true
    }
}
