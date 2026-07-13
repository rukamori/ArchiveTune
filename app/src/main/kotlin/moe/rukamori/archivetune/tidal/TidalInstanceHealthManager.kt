/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.tidal

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.TidalLastProbeTrackKey
import moe.rukamori.archivetune.constants.TidalVerifiedInstancesKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Scans the configured/public Tidal instances, remembers which ones actually work, and feeds the
 * result into the resolver's runtime health map so playback prioritises verified instances.
 *
 * Design goals (per the app's needs):
 *  - Run subtly on app open: instances are probed **sequentially with a small delay** so a burst of
 *    parallel requests never degrades startup performance.
 *  - Detect account status: an instance whose backing account is unsubscribed only serves 30s
 *    previews; those are classified [TidalAudioProvider.InstanceHealth.PREVIEW_ONLY] and dropped
 *    from the working set just like dead instances.
 *  - Persist working instances across launches ([TidalVerifiedInstancesKey]) so we can show status
 *    instantly and avoid re-probing everything every time.
 *  - The manual "fetch public" action reuses [refresh] with discovery enabled: fetch → verify →
 *    save.
 */
object TidalInstanceHealthManager {
    private const val STAGGER_DELAY_MS = 350L

    // Only one scan runs at a time; a second caller (e.g. settings screen opening during the
    // startup scan) simply waits for / skips the in-flight scan instead of doubling the load.
    private val scanMutex = Mutex()

    @Volatile
    private var scanInProgress = false

    data class InstanceRecord(
        val url: String,
        val status: TidalAudioProvider.InstanceHealth,
        val latencyMs: Long?,
        val checkedAt: Long,
    ) {
        val isHealthy: Boolean get() = status == TidalAudioProvider.InstanceHealth.HEALTHY
    }

    /** Reads the persisted scan results (may be empty / stale). Never throws. */
    fun cachedRecords(context: Context): List<InstanceRecord> {
        val raw = context.dataStore.get(TidalVerifiedInstancesKey).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { parse(raw) }.getOrElse { emptyList() }
    }

    /** Base URLs of instances that served a FULL track on the last scan. */
    fun healthyUrls(context: Context): List<String> = cachedRecords(context).filter { it.isHealthy }.map { it.url }

    /**
     * Probes every candidate instance sequentially, persists the results, and applies them to the
     * resolver's runtime health map. Returns the fresh records. Safe to call from a background
     * coroutine; callers on the startup path should pass [staggered] = true.
     *
     * @param includeDiscovery when true, also auto-discovers additional public instances first
     *                          (used by the manual "fetch public" action).
     */
    suspend fun refresh(
        context: Context,
        includeDiscovery: Boolean = false,
        staggered: Boolean = true,
    ): List<InstanceRecord> =
        withContext(Dispatchers.IO) {
            if (scanInProgress) {
                Timber.tag("TidalHealth").d("Scan already in progress; returning cached records")
                return@withContext cachedRecords(context)
            }
            scanMutex.withLock {
                scanInProgress = true
                try {
                    val candidates = LinkedHashSet<String>()
                    candidates += TidalAudioProvider.activeInstanceUrls
                    if (includeDiscovery) {
                        val discovered = TidalAudioProvider.discoverInstances()
                        Timber.tag("TidalHealth").d("Discovery returned %d instance(s)", discovered.size)
                        candidates += discovered
                    }

                    val probeTrackId =
                        TidalAudioProvider.lastResolvedTrackId
                            ?: context.dataStore.get(TidalLastProbeTrackKey)
                    // Without a probe track we can only test reachability, which cannot tell a
                    // fully-working instance from a preview-only (unsubscribed) one. In that case we
                    // must NOT promote "reachable" instances as healthy in the resolver, or dead-end
                    // preview mirrors get tried first. We still demote confirmed-unreachable ones.
                    val verified = !probeTrackId.isNullOrBlank()
                    Timber.tag("TidalHealth").d(
                        "Scanning %d instance(s) | probeTrack=%s verified=%s staggered=%s",
                        candidates.size,
                        probeTrackId ?: "<none, reachability-only>",
                        verified,
                        staggered,
                    )

                    val records = mutableListOf<InstanceRecord>()
                    for ((index, url) in candidates.withIndex()) {
                        if (staggered && index > 0) delay(STAGGER_DELAY_MS)
                        val start = System.currentTimeMillis()
                        val status = TidalAudioProvider.verifyInstance(url, probeTrackId)
                        val latency = System.currentTimeMillis() - start
                        // Apply to the resolver's health map: always demote unreachable/preview-only;
                        // only promote to healthy when we actually verified a FULL stream.
                        when (status) {
                            TidalAudioProvider.InstanceHealth.UNREACHABLE,
                            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY,
                            -> TidalAudioProvider.applyHealthResult(url, healthy = false)
                            TidalAudioProvider.InstanceHealth.HEALTHY ->
                                if (verified) TidalAudioProvider.applyHealthResult(url, healthy = true)
                        }
                        records +=
                            InstanceRecord(
                                url = url,
                                status = status,
                                latencyMs = latency.takeIf { status != TidalAudioProvider.InstanceHealth.UNREACHABLE },
                                checkedAt = start,
                            )
                        Timber.tag("TidalHealth").d("  %s -> %s (%dms)", url, status, latency)
                    }

                    persist(context, records)
                    val healthyCount = records.count { it.isHealthy }
                    Timber.tag("TidalHealth").i(
                        "Scan complete: %d healthy, %d preview-only, %d unreachable",
                        healthyCount,
                        records.count { it.status == TidalAudioProvider.InstanceHealth.PREVIEW_ONLY },
                        records.count { it.status == TidalAudioProvider.InstanceHealth.UNREACHABLE },
                    )
                    records
                } finally {
                    scanInProgress = false
                }
            }
        }

    private suspend fun persist(
        context: Context,
        records: List<InstanceRecord>,
    ) {
        val json =
            JSONArray().apply {
                records.forEach { record ->
                    put(
                        JSONObject().apply {
                            put("url", record.url)
                            put("status", record.status.name)
                            record.latencyMs?.let { put("latencyMs", it) }
                            put("checkedAt", record.checkedAt)
                        },
                    )
                }
            }.toString()
        context.dataStore.edit { prefs -> prefs[TidalVerifiedInstancesKey] = json }
    }

    private fun parse(raw: String): List<InstanceRecord> {
        val array = JSONArray(raw)
        val out = mutableListOf<InstanceRecord>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val status =
                runCatching { TidalAudioProvider.InstanceHealth.valueOf(obj.optString("status")) }
                    .getOrDefault(TidalAudioProvider.InstanceHealth.UNREACHABLE)
            out +=
                InstanceRecord(
                    url = url,
                    status = status,
                    latencyMs = obj.optLong("latencyMs", -1L).takeIf { it >= 0L },
                    checkedAt = obj.optLong("checkedAt", 0L),
                )
        }
        return out
    }
}
