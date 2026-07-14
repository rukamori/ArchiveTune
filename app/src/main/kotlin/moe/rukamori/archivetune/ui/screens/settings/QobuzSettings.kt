/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Qobuz proxy source management for the Integration section: enable toggle, lossless quality,
 * and manual proxy-instance management. Instances are never auto-fetched — the user taps
 * "Test instances" to probe them on demand, each showing an "online — <ping>" (full),
 * "deprecated — <ping>" (preview/unsubscribed) or "connection failed" label. Mirrors TidalSettings.
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
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.QobuzEnabledKey
import moe.rukamori.archivetune.constants.QobuzInstancesKey
import moe.rukamori.archivetune.constants.QobuzLastProbeTrackKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.toFormatId
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.qobuz.SourceInputParsing
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QobuzSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (qobuzEnabled, onQobuzEnabledChange) = rememberPreference(QobuzEnabledKey, false)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(QobuzAudioQualityKey, QobuzAudioQuality.FLAC)
    val probeTrack by rememberPreference(QobuzLastProbeTrackKey, "")

    // Instances stored as a newline-separated string. No bundled defaults: blank == disabled.
    val (storedInstances, onStoredInstancesChange) = rememberPreference(QobuzInstancesKey, "")
    val effectiveInstances =
        remember(storedInstances) {
            storedInstances
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    fun persistInstances(list: List<String>) {
        onStoredInstancesChange(list.distinct().joinToString("\n"))
    }

    // baseUrl -> scan status (null while untested) and last measured latency (ms).
    val healthStatus = remember { mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>() }
    val healthLatency = remember { mutableStateMapOf<String, Long>() }
    var testingInstances by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }

    // Direct-API tokens, stored as a JSON list. Tried before proxy instances during resolution.
    val (storedTokens, onStoredTokensChange) = rememberPreference(QobuzTokensKey, "")
    val tokens = remember(storedTokens) { QobuzToken.listFromJson(storedTokens) }
    fun persistTokens(list: List<QobuzToken>) {
        val deduped = list.distinctBy { it.token }
        onStoredTokensChange(QobuzToken.listToJson(deduped))
    }
    // token id -> health status.
    val tokenHealth = remember { mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>() }
    var testingTokens by remember { mutableStateOf(false) }
    var showAddTokensDialog by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun labelFor(
        status: TidalAudioProvider.InstanceHealth,
        latencyMs: Long?,
    ): String =
        when (status) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                context.getString(R.string.tidal_instance_healthy, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                context.getString(R.string.tidal_instance_preview_only, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                context.getString(R.string.tidal_instance_unreachable)
        }

    // Manual, on-demand probe of every configured instance (reachability AND full-vs-preview),
    // done inline via the provider (no separate health manager). Runs one instance at a time.
    fun runInstanceTest() {
        if (testingInstances) return
        testingInstances = true
        val formatId = audioQuality.toFormatId()
        val probe = probeTrack
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                effectiveInstances.forEach { instance ->
                    val start = System.currentTimeMillis()
                    val status = QobuzAudioProvider.verifyInstance(instance, probe, formatId)
                    val latency = System.currentTimeMillis() - start
                    QobuzAudioProvider.applyHealthResult(
                        instance,
                        healthy = status == TidalAudioProvider.InstanceHealth.HEALTHY,
                    )
                    withContext(Dispatchers.Main) {
                        healthStatus[instance] = status
                        if (status != TidalAudioProvider.InstanceHealth.UNREACHABLE) {
                            healthLatency[instance] = latency
                        }
                    }
                }
            }
            testingInstances = false
        }
    }

    fun runTokenTest() {
        if (testingTokens) return
        testingTokens = true
        val formatId = audioQuality.toFormatId()
        val probe = probeTrack
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                tokens.forEach { token ->
                    val status = QobuzAudioProvider.verifyToken(token, probe, formatId)
                    withContext(Dispatchers.Main) { tokenHealth[token.id] = status }
                }
            }
            testingTokens = false
        }
    }

    // Removes every instance whose last scan matched [statuses], returning the count removed.
    fun removeInstancesWithStatus(statuses: Set<TidalAudioProvider.InstanceHealth>): Int {
        val doomed = effectiveInstances.filter { healthStatus[it] in statuses }
        if (doomed.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return 0
        }
        doomed.forEach {
            healthStatus.remove(it)
            healthLatency.remove(it)
        }
        persistInstances(effectiveInstances - doomed.toSet())
        toast(context.getString(R.string.source_removed, doomed.size))
        return doomed.size
    }

    fun copyOnlineInstances() {
        val online = effectiveInstances.filter { healthStatus[it] == TidalAudioProvider.InstanceHealth.HEALTHY }
        if (online.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return
        }
        copyToClipboard(context, "Qobuz instances", online)
    }

    if (showBulkDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            title = { Text(stringResource(R.string.source_bulk_add)) },
            placeholder = { Text(stringResource(R.string.source_bulk_hint)) },
            singleLine = false,
            isInputValid = { it.isNotBlank() },
            onDone = { raw ->
                val parsed = SourceInputParsing.parseUrls(raw).mapNotNull { QobuzAudioProvider.normalizeInstanceUrl(it) }
                val added = parsed.filterNot { effectiveInstances.contains(it) }
                if (added.isEmpty()) {
                    toast(context.getString(R.string.source_bulk_none))
                } else {
                    persistInstances(effectiveInstances + added)
                    toast(context.getString(R.string.source_bulk_added, added.size))
                }
            },
            onDismiss = { showBulkDialog = false },
        )
    }

    if (showAddTokensDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.qobuz_add_tokens)) },
            placeholder = { Text(stringResource(R.string.qobuz_add_tokens_hint)) },
            singleLine = false,
            isInputValid = { it.isNotBlank() },
            onDone = { raw ->
                val parsed = SourceInputParsing.parseQobuzTokens(raw)
                if (parsed.isEmpty()) {
                    toast(context.getString(R.string.qobuz_tokens_none_parsed))
                } else {
                    persistTokens(tokens + parsed)
                    toast(context.getString(R.string.qobuz_tokens_parsed, parsed.size))
                }
            },
            onDismiss = { showAddTokensDialog = false },
        )
    }

    if (showAddDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.add), null) },
            title = { Text(stringResource(R.string.qobuz_add_instance)) },
            placeholder = { Text(stringResource(R.string.tidal_instance_url_hint)) },
            isInputValid = { QobuzAudioProvider.normalizeInstanceUrl(it) != null },
            onDone = { raw ->
                val normalized = QobuzAudioProvider.normalizeInstanceUrl(raw)
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
                title = { Text(stringResource(R.string.qobuz_integration)) },
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
            PreferenceGroup(title = stringResource(R.string.qobuz_integration)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.qobuz_enable)) },
                        description = stringResource(R.string.qobuz_enable_description),
                        icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                        checked = qobuzEnabled,
                        onCheckedChange = onQobuzEnabledChange,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.qobuz_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = qobuzEnabled,
                        valueText = { quality ->
                            when (quality) {
                                QobuzAudioQuality.FLAC -> stringResource(R.string.qobuz_quality_flac)
                                QobuzAudioQuality.HI_RES -> stringResource(R.string.qobuz_quality_hires)
                                QobuzAudioQuality.MAX -> stringResource(R.string.qobuz_quality_max)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.qobuz_tokens)) {
                item {
                    InfoLabel(text = stringResource(R.string.qobuz_tokens_description))
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_login_web)) },
                        icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                        onClick = { navController.navigate(QOBUZ_LOGIN_ROUTE) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_add_tokens)) },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = { showAddTokensDialog = true },
                    )
                }

                tokens.forEach { token ->
                    item {
                        val status = tokenHealth[token.id]
                        val statusColor =
                            when (status) {
                                TidalAudioProvider.InstanceHealth.HEALTHY -> Color(0xFF4FC3F7)
                                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> Color(0xFFB388FF)
                                TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                                    MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        val statusLabel =
                            when (status) {
                                TidalAudioProvider.InstanceHealth.HEALTHY ->
                                    stringResource(R.string.qobuz_token_status_ok)
                                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                                    stringResource(R.string.qobuz_token_status_preview)
                                TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                                    stringResource(R.string.qobuz_token_status_invalid)
                                null -> stringResource(R.string.qobuz_token_status_unknown)
                            }
                        val displayName = token.label.ifBlank { token.userId.ifBlank { "Qobuz account" } }
                        PreferenceEntry(
                            title = {
                                Column {
                                    Text(stringResource(R.string.qobuz_token_subtitle, displayName, token.id))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                    )
                                }
                            },
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        tokenHealth.remove(token.id)
                                        persistTokens(tokens - token)
                                    },
                                    onLongClick = {},
                                ) {
                                    Icon(painterResource(R.drawable.delete), null)
                                }
                            },
                        )
                    }
                }

                if (tokens.isNotEmpty()) {
                    item {
                        PreferenceEntry(
                            title = {
                                Text(
                                    if (testingTokens) {
                                        stringResource(R.string.qobuz_checking_tokens)
                                    } else {
                                        stringResource(R.string.qobuz_check_tokens)
                                    },
                                )
                            },
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                            isEnabled = !testingTokens,
                            onClick = { runTokenTest() },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.qobuz_reset_tokens)) },
                            icon = { Icon(painterResource(R.drawable.close), null) },
                            onClick = {
                                tokenHealth.clear()
                                onStoredTokensChange("")
                            },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.qobuz_instances)) {
                item {
                    InfoLabel(text = stringResource(R.string.qobuz_instances_description))
                }

                effectiveInstances.forEach { instance ->
                    item {
                        val status = healthStatus[instance]
                        // online = light blue, deprecated/preview-only = purple, failed = error red.
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
                        title = { Text(stringResource(R.string.qobuz_add_instance)) },
                        icon = { Icon(painterResource(R.drawable.add), null) },
                        onClick = { showAddDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.source_bulk_add)) },
                        description = stringResource(R.string.source_bulk_hint),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { showBulkDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (testingInstances) {
                                    stringResource(R.string.qobuz_checking_instances)
                                } else {
                                    stringResource(R.string.qobuz_check_instances)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        isEnabled = !testingInstances && effectiveInstances.isNotEmpty(),
                        onClick = { runInstanceTest() },
                    )
                }

                if (effectiveInstances.isNotEmpty()) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_copy_online)) },
                            icon = { Icon(painterResource(R.drawable.copy), null) },
                            onClick = { copyOnlineInstances() },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_remove_dead)) },
                            icon = { Icon(painterResource(R.drawable.delete), null) },
                            onClick = {
                                removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.UNREACHABLE))
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_remove_deprecated)) },
                            icon = { Icon(painterResource(R.drawable.delete), null) },
                            onClick = {
                                removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.PREVIEW_ONLY))
                            },
                        )
                    }
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_reset_instances)) },
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
