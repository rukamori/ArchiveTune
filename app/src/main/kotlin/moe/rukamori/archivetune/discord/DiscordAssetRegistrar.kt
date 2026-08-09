/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DiscordAssetRegistrar {
    private const val TAG = "DiscordAssetRegistrar"
    private const val API_BASE = "https://discord.com/api/v10"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    private val cache = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()

    suspend fun resolveImage(
        accessToken: String,
        imageUrl: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (imageUrl == null) return@withContext null

            val parsed = parseImageType(imageUrl)
            when (parsed) {
                is ImageType.Snowflake,
                is ImageType.MpPrefix,
                is ImageType.DiscordCdn,
                -> {
                    return@withContext parsed.value
                }

                is ImageType.ExternalUrl -> {
                    cache[imageUrl]?.let { return@withContext "mp:$it" }
                    val registered = registerExternal(accessToken, imageUrl) ?: return@withContext null
                    cache[imageUrl] = registered
                    return@withContext "mp:$registered"
                }

                is ImageType.Raw -> {
                    return@withContext parsed.value
                }
            }
        }

    suspend fun resolveImages(
        accessToken: String,
        largeImage: String?,
        smallImage: String?,
    ): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val urlsToRegister = mutableListOf<Pair<String, String>>()

                val largeType = largeImage?.let { parseImageType(it) }
                val smallType = smallImage?.let { parseImageType(it) }

                if (largeType is ImageType.ExternalUrl && !cache.containsKey(largeImage)) {
                    urlsToRegister.add("large" to largeImage)
                }
                if (smallType is ImageType.ExternalUrl && !cache.containsKey(smallImage)) {
                    urlsToRegister.add("small" to smallImage)
                }

                val registrationMap = mutableMapOf<String, String>()

                if (urlsToRegister.isNotEmpty()) {
                    val urls = urlsToRegister.map { it.second }
                    try {
                        val results = registerExternalBatch(accessToken, urls)
                        for (i in results.indices) {
                            val key = urlsToRegister[i].first
                            val registeredPath = results[i]
                            if (registeredPath != null) {
                                cache[urlsToRegister[i].second] = registeredPath
                                registrationMap[key] = registeredPath
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Failed to register external assets")
                    }
                }

                val resolvedLarge =
                    when (largeType) {
                        is ImageType.ExternalUrl -> {
                            val cached = cache[largeImage]
                            if (cached != null) "mp:$cached" else null
                        }

                        else -> {
                            largeType?.value
                        }
                    }

                val resolvedSmall =
                    when (smallType) {
                        is ImageType.ExternalUrl -> {
                            val cached = cache[smallImage]
                            if (cached != null) "mp:$cached" else null
                        }

                        else -> {
                            smallType?.value
                        }
                    }

                resolvedLarge to resolvedSmall
            }
        }

    fun clearCache() {
        cache.clear()
    }

    private sealed class ImageType {
        abstract val value: String

        data class Snowflake(
            override val value: String,
        ) : ImageType()

        data class MpPrefix(
            override val value: String,
        ) : ImageType()

        data class DiscordCdn(
            override val value: String,
        ) : ImageType()

        data class ExternalUrl(
            override val value: String,
        ) : ImageType()

        data class Raw(
            override val value: String,
        ) : ImageType()
    }

    private fun parseImageType(image: String): ImageType {
        if (Regex("^[0-9]{17,19}$").matches(image)) {
            return ImageType.Snowflake(image)
        }
        if (listOf("mp:", "youtube:", "spotify:", "twitch:").any { image.startsWith(it) }) {
            return ImageType.MpPrefix(image)
        }
        if (image.startsWith("external/")) {
            return ImageType.MpPrefix("mp:$image")
        }
        val isValidUrl =
            try {
                val uri = URI(image)
                uri.scheme == "http" || uri.scheme == "https"
            } catch (_: Exception) {
                false
            }
        if (!isValidUrl) {
            return ImageType.Raw(image)
        }
        val isDiscordCdn =
            listOf(
                "https://cdn.discordapp.com/",
                "http://cdn.discordapp.com/",
                "https://media.discordapp.net/",
                "http://media.discordapp.net/",
            ).any { image.startsWith(it) }

        if (isDiscordCdn) {
            var result =
                image
                    .replace("https://cdn.discordapp.com/", "mp:")
                    .replace("http://cdn.discordapp.com/", "mp:")
                    .replace("https://media.discordapp.net/", "mp:")
                    .replace("http://media.discordapp.net/", "mp:")
            return ImageType.DiscordCdn(result)
        }
        return ImageType.ExternalUrl(image)
    }

    private fun registerExternal(
        accessToken: String,
        imageUrl: String,
    ): String? {
        val json =
            JSONObject().apply {
                put("urls", JSONArray(listOf(imageUrl)))
            }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request =
            Request
                .Builder()
                .url("$API_BASE/applications/${BuildConfig.DISCORD_APPLICATION_ID}/external-assets")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Timber.tag(TAG).w("external-assets API error %d: %s", response.code, responseBody)
            return null
        }

        val arr = JSONArray(responseBody)
        if (arr.length() == 0) return null
        val obj = arr.getJSONObject(0)
        return obj.optString("external_asset_path", null)
    }

    private suspend fun registerExternalBatch(
        accessToken: String,
        urls: List<String>,
    ): List<String?> =
        withContext(Dispatchers.IO) {
            if (urls.isEmpty()) return@withContext emptyList()

            val json =
                JSONObject().apply {
                    put("urls", JSONArray(urls))
                }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request =
                Request
                    .Builder()
                    .url("$API_BASE/applications/${BuildConfig.DISCORD_APPLICATION_ID}/external-assets")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext urls.map { null }

                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("external-assets API error %d: %s", response.code, responseBody)
                    return@withContext urls.map { null }
                }

                val arr = JSONArray(responseBody)
                return@withContext (0 until arr.length()).map { i ->
                    arr.getJSONObject(i).optString("external_asset_path", null)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "external-assets API call failed")
                urls.map { null }
            }
        }
}
