/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.tidal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Manages live Tidal streaming instances (HiFi API proxies).
 * Fetches the curated list from monochrome.tf and allows user override.
 * When multiple instances are available, races them to find the first working endpoint.
 */
internal object TidalInstanceManager {
    private const val INSTANCES_URL = "https://monochrome.tf/instances.json"
    private const val CACHE_DURATION_MS = 3600_000L // 1 hour

    private val healthClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

    private var cachedInstances: List<String> = emptyList()
    private var cacheTimestampMs: Long = 0L

    /**
     * Get the list of available streaming instances in order of preference.
     * Returns: user-override URL (if set) → freshly-fetched list → cached list → hardcoded defaults
     */
    suspend fun getOrderedInstances(userInstanceUrl: String?): List<String> =
        withContext(Dispatchers.IO) {
            val instances = mutableListOf<String>()

            // User override is always first (highest priority)
            if (!userInstanceUrl.isNullOrBlank() && userInstanceUrl != "https://") {
                instances += userInstanceUrl.removeSuffix("/")
            }

            // Try to fetch fresh list
            val freshList = fetchLiveInstances()
            if (freshList.isNotEmpty()) {
                instances += freshList
                cachedInstances = freshList
                cacheTimestampMs = System.currentTimeMillis()
            } else if (cachedInstances.isNotEmpty()) {
                // Fall back to cache if fresh fetch failed
                instances += cachedInstances
            } else {
                // Fall back to hardcoded defaults
                instances += DEFAULT_FALLBACK_INSTANCES
            }

            instances.distinct()
        }

    private suspend fun fetchLiveInstances(): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(INSTANCES_URL).get().build()
                healthClient
                    .newCall(request)
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return@use emptyList()
                        val payload = response.body?.string() ?: return@use emptyList()
                        val json = JSONObject(payload)

                        // Extract all URLs from the "api" and "streaming" keys
                        val urls = mutableListOf<String>()
                        listOf("api", "streaming")
                            .forEach { category ->
                                json.optJSONObject(category)
                                    ?.keys()
                                    ?.forEach { name ->
                                        json
                                            .getJSONObject(category)
                                            .getJSONObject(name)
                                            .optJSONArray("urls")
                                            ?.let { array ->
                                                for (i in 0 until array.length()) {
                                                    urls += array.getString(i).removeSuffix("/")
                                                }
                                            }
                                    }
                            }
                        urls
                    }
            }
                .onFailure {
                    Timber.tag("TidalInstance").d(it, "fetch live instances failed")
                }
                .getOrNull()
                ?: emptyList()
        }

    private val DEFAULT_FALLBACK_INSTANCES =
        listOf(
            "https://eu-central.monochrome.tf",
            "https://us-west.monochrome.tf",
            "https://api.monochrome.tf",
            "https://arran.monochrome.tf",
        )
}
