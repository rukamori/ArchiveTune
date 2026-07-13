/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Streaming source settings (Tidal / Deezer / Amazon) surfaced under Player & Audio.
 * Account login/logout for Tidal lives separately in the Integration section.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AmazonBypassTokenKey
import moe.rukamori.archivetune.constants.AmazonEnabledKey
import moe.rukamori.archivetune.constants.AmazonInstanceKey
import moe.rukamori.archivetune.constants.AudioSearchSourceKey
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.DeezerEnabledKey
import moe.rukamori.archivetune.constants.DeezerInstanceKey
import moe.rukamori.archivetune.constants.TidalAccountFirstKey
import moe.rukamori.archivetune.constants.TidalAnimatedCoversEnabledKey
import moe.rukamori.archivetune.constants.TidalArtworkFallbackEnabledKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.constants.TidalInstancesKey
import moe.rukamori.archivetune.audiosource.AmazonAudioProvider
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.audiosource.DeezerAudioProvider
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.ui.component.EditTextPreference
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
fun StreamingSourcesSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (tidalEnabled, onTidalEnabledChange) = rememberPreference(TidalEnabledKey, true)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(TidalAudioQualityKey, TidalAudioQuality.FLAC)
    val (artworkFallback, onArtworkFallbackChange) =
        rememberPreference(TidalArtworkFallbackEnabledKey, true)
    val (animatedCovers, onAnimatedCoversChange) =
        rememberPreference(TidalAnimatedCoversEnabledKey, false)

    // ----- Multi-source framework state -----
    val (searchSource, onSearchSourceChange) =
        rememberEnumPreference(AudioSearchSourceKey, AudioSourceType.TIDAL)
    val (sourceOrderRaw, onSourceOrderChange) = rememberPreference(AudioSourceOrderKey, "")
    val (tidalAccountFirst, onTidalAccountFirstChange) = rememberPreference(TidalAccountFirstKey, true)
    val (deezerEnabled, onDeezerEnabledChange) = rememberPreference(DeezerEnabledKey, true)
    val (deezerInstance, onDeezerInstanceChange) =
        rememberPreference(DeezerInstanceKey, DeezerAudioProvider.DEFAULT_INSTANCE)
    val (amazonEnabled, onAmazonEnabledChange) = rememberPreference(AmazonEnabledKey, false)
    val (amazonInstance, onAmazonInstanceChange) =
        rememberPreference(AmazonInstanceKey, AmazonAudioProvider.DEFAULT_INSTANCE)
    val (amazonBypassToken, onAmazonBypassTokenChange) = rememberPreference(AmazonBypassTokenKey, "")

    // Reorderable, non-YouTube sources in priority order (YouTube is the fixed final fallback).
    val orderedSources =
        remember(sourceOrderRaw) {
            AudioSourceConfig.parseOrder(sourceOrderRaw.ifBlank { null })
                .filter { it != AudioSourceType.YOUTUBE }
        }

    fun moveSource(
        from: Int,
        to: Int,
    ) {
        if (to < 0 || to >= orderedSources.size) return
        val mutable = orderedSources.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        onSourceOrderChange((mutable + AudioSourceType.YOUTUBE).joinToString(",") { it.name })
    }

    fun sourceLabel(source: AudioSourceType): String =
        when (source) {
            AudioSourceType.TIDAL -> context.getString(R.string.source_tidal)
            AudioSourceType.DEEZER -> context.getString(R.string.source_deezer)
            AudioSourceType.AMAZON -> context.getString(R.string.source_amazon)
            AudioSourceType.YOUTUBE -> context.getString(R.string.source_youtube)
        }

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

    // baseUrl -> health label (null while unchecked)
    val healthStatus = remember { mutableStateMapOf<String, String>() }
    var checkingInstances by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Turns a scan record into a user-facing status label (healthy latency / preview-only / dead).
    fun labelFor(record: TidalInstanceHealthManager.InstanceRecord): String =
        when (record.status) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                context.getString(R.string.tidal_instance_healthy, (record.latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                context.getString(R.string.tidal_instance_preview_only)
            TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                context.getString(R.string.tidal_instance_unreachable)
        }

    fun applyRecords(records: List<TidalInstanceHealthManager.InstanceRecord>) {
        records.forEach { record -> healthStatus[record.url] = labelFor(record) }
    }

    // Deep-verifies every configured instance (reachability AND full-vs-preview) and saves the
    // result so working instances are remembered for next time.
    fun runHealthCheck() {
        if (checkingInstances) return
        checkingInstances = true
        coroutineScope.launch {
            val records =
                withContext(Dispatchers.IO) {
                    TidalInstanceHealthManager.refresh(context, includeDiscovery = false, staggered = false)
                }
            applyRecords(records)
            checkingInstances = false
        }
    }

    // Show the last saved scan instantly when the screen opens; only run a fresh scan if we have
    // nothing cached yet, so we don't hammer the instances on every visit.
    LaunchedEffect(Unit) {
        if (!tidalEnabled) return@LaunchedEffect
        val cached = withContext(Dispatchers.IO) { TidalInstanceHealthManager.cachedRecords(context) }
        if (cached.isNotEmpty()) {
            applyRecords(cached)
        } else if (healthStatus.isEmpty()) {
            runHealthCheck()
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
                title = { Text(stringResource(R.string.streaming_sources)) },
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
            PreferenceGroup(title = stringResource(R.string.audio_sources)) {
                item {
                    InfoLabel(text = stringResource(R.string.audio_sources_description))
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.audio_search_source)) },
                        icon = { Icon(painterResource(R.drawable.search), null) },
                        selectedValue = searchSource,
                        onValueSelected = onSearchSourceChange,
                        valueText = { sourceLabel(it) },
                    )
                }

                // Priority list: each source shows enabled state + up/down reordering controls.
                orderedSources.forEachIndexed { index, source ->
                    item {
                        val enabled =
                            when (source) {
                                AudioSourceType.TIDAL -> tidalEnabled
                                AudioSourceType.DEEZER -> deezerEnabled
                                AudioSourceType.AMAZON -> amazonEnabled
                                AudioSourceType.YOUTUBE -> true
                            }
                        PreferenceEntry(
                            title = { Text("${index + 1}. ${sourceLabel(source)}") },
                            description =
                                if (enabled) {
                                    stringResource(R.string.audio_source_enabled)
                                } else {
                                    stringResource(R.string.audio_source_disabled)
                                },
                            icon = { Icon(painterResource(R.drawable.play), null) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { moveSource(index, index - 1) },
                                        onLongClick = {},
                                        enabled = index > 0,
                                    ) {
                                        Icon(painterResource(R.drawable.arrow_upward), null)
                                    }
                                    IconButton(
                                        onClick = { moveSource(index, index + 1) },
                                        onLongClick = {},
                                        enabled = index < orderedSources.lastIndex,
                                    ) {
                                        Icon(painterResource(R.drawable.arrow_downward), null)
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    InfoLabel(text = stringResource(R.string.audio_source_youtube_fallback))
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_integration)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_enable)) },
                        description = stringResource(R.string.tidal_enable_description),
                        icon = { Icon(painterResource(R.drawable.provider_tidal), null) },
                        checked = tidalEnabled,
                        onCheckedChange = onTidalEnabledChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_account_first)) },
                        description = stringResource(R.string.tidal_account_first_description),
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        checked = tidalAccountFirst,
                        onCheckedChange = onTidalAccountFirstChange,
                        isEnabled = tidalEnabled,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.tidal_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = tidalEnabled,
                        valueText = { quality ->
                            when (quality) {
                                TidalAudioQuality.AAC_320 -> stringResource(R.string.tidal_quality_aac_320)
                                TidalAudioQuality.FLAC -> stringResource(R.string.tidal_quality_flac)
                                TidalAudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.tidal_quality_hires)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_instances)) {
                item {
                    InfoLabel(text = stringResource(R.string.tidal_instances_description))
                }

                effectiveInstances.forEach { instance ->
                    item {
                        PreferenceEntry(
                            title = { Text(instance) },
                            description =
                                healthStatus[instance]
                                    ?: stringResource(R.string.tidal_instance_unknown),
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val remaining = effectiveInstances - instance
                                        healthStatus.remove(instance)
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
                                if (checkingInstances) {
                                    stringResource(R.string.tidal_checking_instances)
                                } else {
                                    stringResource(R.string.tidal_check_instances)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        isEnabled = !checkingInstances,
                        onClick = { runHealthCheck() },
                    )
                }

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (discovering) {
                                    stringResource(R.string.tidal_discovering)
                                } else {
                                    stringResource(R.string.tidal_discover)
                                },
                            )
                        },
                        description = stringResource(R.string.tidal_discover_description),
                        icon = { Icon(painterResource(R.drawable.search), null) },
                        isEnabled = !discovering,
                        onClick = {
                            discovering = true
                            coroutineScope.launch {
                                // Fetch public instances, verify each (reachability + full-vs-preview),
                                // then save the results. Newly discovered working instances are added
                                // to the list so they persist for next launch.
                                val records =
                                    withContext(Dispatchers.IO) {
                                        TidalInstanceHealthManager.refresh(
                                            context,
                                            includeDiscovery = true,
                                            staggered = false,
                                        )
                                    }
                                applyRecords(records)
                                val newHealthy =
                                    records
                                        .filter { it.isHealthy && it.url !in effectiveInstances }
                                        .map { it.url }
                                when {
                                    newHealthy.isNotEmpty() -> {
                                        persistInstances(effectiveInstances + newHealthy)
                                        toast(context.getString(R.string.tidal_discover_result, newHealthy.size))
                                    }
                                    records.isEmpty() -> toast(context.getString(R.string.tidal_discover_failed))
                                    else -> toast(context.getString(R.string.tidal_discover_none))
                                }
                                discovering = false
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.tidal_reset_instances)) },
                        icon = { Icon(painterResource(R.drawable.close), null) },
                        onClick = {
                            healthStatus.clear()
                            onStoredInstancesChange("")
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.tidal_artwork)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_artwork_fallback)) },
                        description = stringResource(R.string.tidal_artwork_fallback_description),
                        checked = artworkFallback,
                        onCheckedChange = onArtworkFallbackChange,
                        isEnabled = tidalEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.tidal_animated_covers)) },
                        description = stringResource(R.string.tidal_animated_covers_description),
                        checked = animatedCovers,
                        onCheckedChange = onAnimatedCoversChange,
                        isEnabled = tidalEnabled,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.source_deezer)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.deezer_enable)) },
                        description = stringResource(R.string.deezer_enable_description),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = deezerEnabled,
                        onCheckedChange = onDeezerEnabledChange,
                    )
                }
                item {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.deezer_instance)) },
                        icon = { Icon(painterResource(R.drawable.link), null) },
                        value = deezerInstance,
                        onValueChange = onDeezerInstanceChange,
                        isEnabled = deezerEnabled,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.source_amazon)) {
                item {
                    InfoLabel(text = stringResource(R.string.amazon_experimental_note))
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.amazon_enable)) },
                        description = stringResource(R.string.amazon_enable_description),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = amazonEnabled,
                        onCheckedChange = onAmazonEnabledChange,
                    )
                }
                item {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.amazon_instance)) },
                        icon = { Icon(painterResource(R.drawable.link), null) },
                        value = amazonInstance,
                        onValueChange = onAmazonInstanceChange,
                        isEnabled = amazonEnabled,
                    )
                }
                item {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.amazon_bypass_token)) },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        value = amazonBypassToken,
                        onValueChange = onAmazonBypassTokenChange,
                        isInputValid = { true },
                        isEnabled = amazonEnabled,
                    )
                }
            }
        }
    }
}
