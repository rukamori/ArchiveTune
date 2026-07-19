/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.gatekeeper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GatekeeperResult {
    data object Allowed : GatekeeperResult

    data class Blocked(
        val message: String,
        val retryable: Boolean,
    ) : GatekeeperResult
}

@Singleton
class GatekeeperRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val checkMutex = Mutex()
        private val client =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
            }

        @Volatile
        private var accessGranted = false

        suspend fun checkAccess(): GatekeeperResult {
            if (accessGranted) return GatekeeperResult.Allowed
            return checkMutex.withLock {
                if (accessGranted) return@withLock GatekeeperResult.Allowed
                performCheck().also { result ->
                    if (result is GatekeeperResult.Allowed) {
                        accessGranted = true
                    }
                }
            }
        }

        private suspend fun performCheck(): GatekeeperResult {
            val fallbackMessage = context.getString(R.string.gatekeeper_connection_blocked)
            val bearerToken = BuildConfig.API_BEARER_TOKEN.trim()
            if (bearerToken.isEmpty()) {
                Timber.w("Gatekeeper bearer token is not configured")
                return blocked(fallbackMessage, retryable = false)
            }

            return try {
                val packageName = context.packageName
                val versionCode = currentVersionCode(packageName)
                val response =
                    client.get("${BuildConfig.DATA_SERVER_URL.trimEnd('/')}/api/data") {
                        header(HttpHeaders.Authorization, "Bearer $bearerToken")
                        header(HEADER_PACKAGE_NAME, packageName)
                        header(HEADER_VERSION_CODE, versionCode.toString())
                    }

                if (response.status == HttpStatusCode.OK) {
                    NetworkGatekeeper.setConnectionBlocked(false)
                    GatekeeperResult.Allowed
                } else {
                    Timber.w("Gatekeeper denied network access with HTTP ${response.status.value}")
                    blocked(
                        message = resolveWarningMessage(response.bodyAsText(), fallbackMessage),
                        retryable = response.status.value >= HTTP_SERVER_ERROR_MINIMUM,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.w(exception, "Gatekeeper request failed")
                blocked(fallbackMessage, retryable = true)
            }
        }

        private fun currentVersionCode(packageName: String): Long {
            val packageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(packageName, 0)
                }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        }

        private fun blocked(
            message: String,
            retryable: Boolean,
        ): GatekeeperResult.Blocked {
            NetworkGatekeeper.setConnectionBlocked(true)
            return GatekeeperResult.Blocked(
                message = message,
                retryable = retryable,
            )
        }

        private fun resolveWarningMessage(
            responseBody: String,
            fallbackMessage: String,
        ): String {
            val body = responseBody.trim()
            if (body.isEmpty()) return fallbackMessage

            val parsedJson =
                runCatching {
                    val json = JSONObject(body)
                    json.firstStringValue(TOP_LEVEL_MESSAGE_KEYS)
                        ?: json.optJSONObject(ERROR_KEY)?.firstStringValue(NESTED_ERROR_MESSAGE_KEYS)
                }.getOrNull()
            if (!parsedJson.isNullOrEmpty()) return parsedJson
            if (body.startsWith('{') || body.startsWith('[')) return fallbackMessage

            return body.takeIf {
                it.length <= MAX_PLAIN_TEXT_MESSAGE_LENGTH &&
                    !it.contains('<') &&
                    !it.contains('>')
            } ?: fallbackMessage
        }

        private fun JSONObject.firstStringValue(keys: List<String>): String? =
            keys
                .asSequence()
                .mapNotNull { key -> opt(key) as? String }
                .map { value -> value.trim() }
                .firstOrNull { value ->
                    value.isNotEmpty() && value.length <= MAX_PLAIN_TEXT_MESSAGE_LENGTH
                }

        private companion object {
            const val HEADER_PACKAGE_NAME = "x-client-package-name"
            const val HEADER_VERSION_CODE = "x-client-version-code"
            const val HTTP_SERVER_ERROR_MINIMUM = 500
            const val CONNECT_TIMEOUT_MILLIS = 10_000L
            const val REQUEST_TIMEOUT_MILLIS = 15_000L
            const val MAX_PLAIN_TEXT_MESSAGE_LENGTH = 500
            const val ERROR_KEY = "error"
            val TOP_LEVEL_MESSAGE_KEYS = listOf("message", "detail", ERROR_KEY)
            val NESTED_ERROR_MESSAGE_KEYS = listOf("message", "detail")
        }
    }
