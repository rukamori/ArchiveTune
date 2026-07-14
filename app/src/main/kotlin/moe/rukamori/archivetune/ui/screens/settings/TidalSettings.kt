/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Tidal account login/logout for the Integration section, plus manual HiFi instance
 * management. Instances are never auto-fetched: the user taps "Test instances" to probe
 * them on demand, and each result shows an "online — <ping>" (full/premium) or
 * "deprecated — <ping>" (preview-only/non-premium) label.
 * Ported from MetroFuse (github.com/956tris/MetroFuse) under GPL-3.0.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalAccountNameKey
import moe.rukamori.archivetune.constants.TidalAuthFlowKey
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.constants.TidalInstancesKey
import moe.rukamori.archivetune.constants.TidalNeedsReloginKey
import moe.rukamori.archivetune.constants.TidalRefreshTokenKey
import moe.rukamori.archivetune.constants.TidalSubscriptionKey
import moe.rukamori.archivetune.constants.TidalSubscriptionStatus
import moe.rukamori.archivetune.constants.TidalTokenExpiryKey
import moe.rukamori.archivetune.constants.TidalUserIdKey
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.TextFieldDialog
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

    // ----- HiFi instance management (moved here from Streaming sources) -----
    // Instances stored as a newline-separated string; blank means "use built-in defaults".
    val (storedInstances, onStoredInstancesChange) = rememberPreference(TidalInstancesKey, "")

    val defaults = remember { TidalAudioProvider.defaultInstanceUrls }
    val effectiveInstances =
        remember(storedInstances) {
            storedInstances
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { defaults }
        }

    fun persistInstances(list: List<String>) {
        val distinct = list.distinct()
        onStoredInstancesChange(if (distinct == defaults) "" else distinct.joinToString("\n"))
    }

    // baseUrl -> scan status (null while untested). Nothing is probed until the user taps Test.
    val healthStatus = remember { mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>() }
    // baseUrl -> last measured latency (ms), used for the "— <ping> ms" suffix.
    val healthLatency = remember { mutableStateMapOf<String, Long>() }
    var testingInstances by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Turns a scan status into its ping label:
    //  - HEALTHY (full stream, premium account) -> "online — <ping> ms"
    //  - PREVIEW_ONLY (free / non-premium account) -> "deprecated — <ping> ms"
    //  - UNREACHABLE -> "connection failed"
    fun labelFor(status: TidalAudioProvider.InstanceHealth, latencyMs: Long?): String =
        when (status) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                context.getString(R.string.tidal_instance_healthy, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                context.getString(R.string.tidal_instance_preview_only, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                context.getString(R.string.tidal_instance_unreachable)
        }

    fun applyRecords(records: List<TidalInstanceHealthManager.InstanceRecord>) {
        records.forEach { record ->
            healthStatus[record.url] = record.status
            record.latencyMs?.let { healthLatency[record.url] = it }
        }
    }

    // Manual, on-demand probe of every configured instance (reachability AND full-vs-preview).
    // Triggered only by the user tapping "Test instances" — never automatically.
    fun runInstanceTest() {
        if (testingInstances) return
        testingInstances = true
        coroutineScope.launch {
            val records =
                withContext(Dispatchers.IO) {
                    TidalInstanceHealthManager.refresh(context, includeDiscovery = false, staggered = false)
                }
            applyRecords(records)
            testingInstances = false
        }
    }

    if (showAddDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.add), null) },
            title = { Text(stringResource(R.string.tidal_add_instance)) },
            placeholder = { Text(stringResource(R.string.tidal_instance_url_hint)) },
            isInputValid = { TidalAudioProvider.normalizeInstanceUrl(it) != null },
            onDone = { raw ->
                val normalized = TidalAudioProvider.normalizeInstanceUrl(raw)
                when {
                    normalized == null -> toast(context.getString(R.string.tidal_instance_invalid_url))
                    effectiveInstances.contains(normalized) ->
                        toast(context.getString(R.string.tidal_instance_duplicate))
                    else -> persistInstances(effectiveInstances + normalized)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

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
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tidal_login_web)) },
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            onClick = { navController.navigate(TIDAL_LOGIN_ROUTE) },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_instances)) {
                item {
                    InfoLabel(text = stringResource(R.string.tidal_instances_description))
                }

                effectiveInstances.forEach { instance ->
                    item {
                        val status = healthStatus[instance]
                        // Status colors: online = light blue, deprecated/preview-only = purple,
                        // connection failed = error red. Untested falls back to the muted default.
                        val statusColor =
                            when (status) {
                                TidalAudioProvider.InstanceHealth.HEALTHY -> Color(0xFF4FC3F7)
                                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> Color(0xFFB388FF)
                                TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                                    MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        val statusLabel =
                            if (status != null) {
                                labelFor(status, healthLatency[instance])
                            } else {
                                stringResource(R.string.tidal_instance_unknown)
                            }
                        PreferenceEntry(
                            title = {
                                Column {
                                    Text(instance)
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                    )
                                }
                            },
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val remaining = effectiveInstances - instance
                                        healthStatus.remove(instance)
                                        healthLatency.remove(instance)
                                        persistInstances(remaining)
                                    },
                                    onLongClick = {},
                                ) {
                                    Icon(painterResource(R.drawable.delete), null)
                                }
                            },
                        )
                    }
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.tidal_add_instance)) },
                        icon = { Icon(painterResource(R.drawable.add), null) },
                        onClick = { showAddDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (testingInstances) {
                                    stringResource(R.string.tidal_checking_instances)
                                } else {
                                    stringResource(R.string.tidal_check_instances)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        isEnabled = !testingInstances,
                        onClick = { runInstanceTest() },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.tidal_reset_instances)) },
                        icon = { Icon(painterResource(R.drawable.close), null) },
                        onClick = {
                            healthStatus.clear()
                            healthLatency.clear()
                            onStoredInstancesChange("")
                        },
                    )
                }
            }
        }
    }
}
