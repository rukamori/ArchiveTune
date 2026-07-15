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
        private var cachedResult: GatekeeperResult? = null

        suspend fun checkAccess(): GatekeeperResult {
            cachedResult?.let { return it }
            return checkMutex.withLock {
                cachedResult?.let { return@withLock it }
                performCheck().also { cachedResult = it }
            }
        }

        private suspend fun performCheck(): GatekeeperResult {
            val fallbackMessage = context.getString(R.string.gatekeeper_connection_blocked)
            val bearerToken = BuildConfig.API_BEARER_TOKEN.trim()
            if (bearerToken.isEmpty()) {
                Timber.w("Gatekeeper bearer token is not configured")
                return blocked(fallbackMessage)
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
                    blocked(resolveWarningMessage(response.bodyAsText(), fallbackMessage))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.w(exception, "Gatekeeper request failed")
                blocked(fallbackMessage)
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

        private fun blocked(message: String): GatekeeperResult.Blocked {
            NetworkGatekeeper.setConnectionBlocked(true)
            return GatekeeperResult.Blocked(message)
        }

        private fun resolveWarningMessage(
            responseBody: String,
            fallbackMessage: String,
        ): String {
            val body = responseBody.trim()
            if (body.isEmpty()) return fallbackMessage

            val jsonMessage =
                runCatching {
                    val json = JSONObject(body)
                    MESSAGE_KEYS
                        .asSequence()
                        .map { json.optString(it).trim() }
                        .firstOrNull { it.isNotEmpty() }
                }.getOrNull()
            if (!jsonMessage.isNullOrEmpty()) return jsonMessage

            return body.takeIf {
                it.length <= MAX_PLAIN_TEXT_MESSAGE_LENGTH &&
                    !it.contains('<') &&
                    !it.contains('>')
            } ?: fallbackMessage
        }

        private companion object {
            const val HEADER_PACKAGE_NAME = "X-Package-Name"
            const val HEADER_VERSION_CODE = "X-Version-Code"
            const val CONNECT_TIMEOUT_MILLIS = 10_000L
            const val REQUEST_TIMEOUT_MILLIS = 15_000L
            const val MAX_PLAIN_TEXT_MESSAGE_LENGTH = 500
            val MESSAGE_KEYS = listOf("message", "error", "detail")
        }
    }
