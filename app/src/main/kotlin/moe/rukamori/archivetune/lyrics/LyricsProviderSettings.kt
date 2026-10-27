/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableBetterLyricsPortatoKey
import moe.rukamori.archivetune.constants.EnableBinimumLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableMegalobizLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

data class LyricsProviderPreferences(
    val order: ImmutableList<PreferredLyricsProvider>,
    val enabledProviders: ImmutableSet<PreferredLyricsProvider>,
    val paxsenixEnabled: Boolean,
)

data class LyricsProviderSelection(
    val providers: ImmutableList<PreferredLyricsProvider>,
    val selectedProviders: ImmutableList<PreferredLyricsProvider>,
)

@Singleton
class LyricsProviderSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dataStore
    private val providerKeys = mapOf(
        PreferredLyricsProvider.BETTER_LYRICS to EnableBetterLyricsKey,
        PreferredLyricsProvider.BETTER_LYRICS_PORTATO to EnableBetterLyricsPortatoKey,
        PreferredLyricsProvider.YOULY_PLUS to EnableYouLyPlusLyricsKey,
        PreferredLyricsProvider.BINIMUM to EnableBinimumLyricsKey,
        PreferredLyricsProvider.LRCLIB to EnableLrcLibKey,
        PreferredLyricsProvider.KUGOU to EnableKugouKey,
        PreferredLyricsProvider.MEGALOBIZ to EnableMegalobizLyricsKey,
        PreferredLyricsProvider.SIMPMUSIC to EnableSimpMusicLyricsKey,
        PreferredLyricsProvider.UNISON to EnableUnisonLyricsKey,
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC to EnablePaxsenixAppleMusicLyricsKey,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY to EnablePaxsenixSpotifyLyricsKey,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH to EnablePaxsenixMusixmatchLyricsKey,
    )

    fun observe(): Flow<LyricsProviderPreferences> = dataStore.data.map { preferences ->
        LyricsProviderPreferences(
            order = ImmutableList.copyOf(deserializeLyricsProviderOrder(preferences[LyricsProviderOrderKey])),
            enabledProviders = ImmutableSet.copyOf(providerKeys.keys.filter { preferences[providerKeys.getValue(it)] ?: true }),
            paxsenixEnabled = preferences[EnablePaxsenixLyricsKey] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setEnabledProviders(
        providers: Set<PreferredLyricsProvider>,
        paxsenixEnabled: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                providerKeys.forEach { (provider, key) ->
                    preferences[key] = provider in providers
                }
                preferences[EnablePaxsenixLyricsKey] = paxsenixEnabled
            }
        }
    }
}

class LyricsProviderSettingsUseCase @Inject constructor(
    private val repository: LyricsProviderSettingsRepository,
) {
    private val paxsenixProviders = setOf(
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
    )

    fun observe(): Flow<LyricsProviderSelection> = repository.observe().map { preferences ->
        LyricsProviderSelection(
            providers = preferences.order,
            selectedProviders = ImmutableList.copyOf(
                preferences.order.filter { provider ->
                    provider in preferences.enabledProviders &&
                        (provider !in paxsenixProviders || preferences.paxsenixEnabled)
                },
            ),
        )
    }.distinctUntilChanged()

    suspend fun setEnabledProviders(providers: ImmutableList<PreferredLyricsProvider>) {
        repository.setEnabledProviders(
            providers = providers.toSet(),
            paxsenixEnabled = providers.any { it in paxsenixProviders },
        )
    }
}
