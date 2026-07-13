/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Tidal account login/logout for the Integration section.
 * Streaming source settings (quality, instances, ordering)
 * live under Player & Audio → Streaming sources.
 * Ported from MetroFuse (github.com/956tris/MetroFuse) under GPL-3.0.
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalAccountNameKey
import moe.rukamori.archivetune.constants.TidalAuthFlowKey
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.constants.TidalNeedsReloginKey
import moe.rukamori.archivetune.constants.TidalRefreshTokenKey
import moe.rukamori.archivetune.constants.TidalSubscriptionKey
import moe.rukamori.archivetune.constants.TidalSubscriptionStatus
import moe.rukamori.archivetune.constants.TidalTokenExpiryKey
import moe.rukamori.archivetune.constants.TidalUserIdKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidalSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accessToken by rememberPreference(TidalAccessTokenKey, "")
    val accountName by rememberPreference(TidalAccountNameKey, "")
    val subscriptionRaw by rememberPreference(TidalSubscriptionKey, TidalSubscriptionStatus.UNKNOWN.name)
    val needsRelogin by rememberPreference(TidalNeedsReloginKey, false)

    val subscription =
        remember(subscriptionRaw) {
            runCatching { TidalSubscriptionStatus.valueOf(subscriptionRaw) }
                .getOrDefault(TidalSubscriptionStatus.UNKNOWN)
        }
    val accountConfigured = accessToken.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tidal_integration)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.tidal_account)) {
                item {
                    InfoLabel(text = stringResource(R.string.tidal_login_description))
                }

                item {
                    InfoLabel(text = stringResource(R.string.tidal_source_settings_moved))
                }

                if (accountConfigured) {
                    item {
                        PreferenceEntry(
                            title = {
                                Text(stringResource(R.string.tidal_account_connected, accountName.ifBlank { "Tidal" }))
                            },
                            description =
                                when (subscription) {
                                    TidalSubscriptionStatus.PREMIUM ->
                                        stringResource(R.string.tidal_account_premium)
                                    TidalSubscriptionStatus.FREE ->
                                        stringResource(R.string.tidal_account_free)
                                    TidalSubscriptionStatus.UNKNOWN ->
                                        stringResource(R.string.tidal_account_checking)
                                },
                            icon = {
                                Icon(
                                    painterResource(
                                        if (subscription == TidalSubscriptionStatus.FREE) {
                                            R.drawable.error
                                        } else {
                                            R.drawable.token
                                        },
                                    ),
                                    null,
                                )
                            },
                        )
                    }

                    if (subscription == TidalSubscriptionStatus.FREE) {
                        item {
                            InfoLabel(text = stringResource(R.string.tidal_account_free_warning))
                        }
                    }

                    if (needsRelogin) {
                        item {
                            InfoLabel(text = stringResource(R.string.tidal_reconnect_description))
                        }
                        item {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.tidal_reconnect)) },
                                icon = { Icon(painterResource(R.drawable.error), null) },
                                onClick = { navController.navigate(TIDAL_LOGIN_ROUTE) },
                            )
                        }
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tidal_disconnect)) },
                            icon = { Icon(painterResource(R.drawable.logout), null) },
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.edit { prefs ->
                                        prefs.remove(TidalAccessTokenKey)
                                        prefs.remove(TidalRefreshTokenKey)
                                        prefs.remove(TidalTokenExpiryKey)
                                        prefs.remove(TidalAccountNameKey)
                                        prefs.remove(TidalAuthFlowKey)
                                        prefs.remove(TidalCountryCodeKey)
                                        prefs.remove(TidalUserIdKey)
                                        prefs.remove(TidalNeedsReloginKey)
                                        prefs[TidalSubscriptionKey] = TidalSubscriptionStatus.UNKNOWN.name
                                    }
                                }
                            },
                        )
                    }
                } else {
                    item {
                        InfoLabel(text = stringResource(R.string.tidal_login_web_description))
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tidal_login_web)) },
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            onClick = { navController.navigate(TIDAL_LOGIN_ROUTE) },
                        )
                    }
                }
            }
        }
    }
}
