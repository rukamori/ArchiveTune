/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.about

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GitHubContributorsEtagKey
import moe.rukamori.archivetune.constants.GitHubContributorsJsonKey
import moe.rukamori.archivetune.constants.GitHubContributorsLastCheckedAtKey
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AboutContributor(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

@Immutable
data class AboutContributorCollection private constructor(
    private val values: List<AboutContributor>,
) {
    val isEmpty: Boolean get() = values.isEmpty()

    fun take(count: Int): AboutContributorCollection =
        AboutContributorCollection(values.take(count))

    fun forEach(action: (AboutContributor) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorCollection(emptyList())

        fun from(values: List<AboutContributor>): AboutContributorCollection =
            AboutContributorCollection(values.toList())
    }
}

class FetchAboutContributorsUseCase
@Inject
constructor(
    private val repository: AboutContributorsRepository,
) {
    suspend operator fun invoke(): Result<AboutContributorCollection> =
        repository.contributors()
}

@Singleton
class AboutContributorsRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
                retryOnConnectionFailure(false)
            }
        }
    }

    suspend fun contributors(): Result<AboutContributorCollection> =
        withContext(Dispatchers.IO) {
            val preferences = context.dataStore.data.first()
            val cachedJson = preferences[GitHubContributorsJsonKey]
            val cachedEtag = preferences[GitHubContributorsEtagKey]
            val lastCheckedAt = preferences[GitHubContributorsLastCheckedAtKey] ?: 0L
            val cachedContributors = cachedJson
                ?.takeIf { json -> json.isNotBlank() }
                ?.let { json -> parseContributorsJsonSafely(json) }
                ?.takeIf { contributors -> !contributors.isEmpty }
            val now = System.currentTimeMillis()
            val shouldCheckNetwork =
                cachedJson.isNullOrBlank() || (now - lastCheckedAt) >= ContributorsCacheCheckIntervalMs

            if (!shouldCheckNetwork) {
                return@withContext cachedContributors?.success()
                    ?: Result.failure(IllegalStateException("No cached contributors"))
            }

            val networkResult = try {
                fetchRepoContributorsNetwork(
                    owner = GitHubOwner,
                    repo = GitHubRepo,
                    cachedEtag = cachedEtag,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                return@withContext cachedContributors?.success() ?: Result.failure(throwable)
            }

            context.dataStore.edit { preferences ->
                preferences[GitHubContributorsLastCheckedAtKey] = now
                networkResult.etag?.let { etag -> preferences[GitHubContributorsEtagKey] = etag }
                networkResult.body?.let { body -> preferences[GitHubContributorsJsonKey] = body }
            }

            when {
                networkResult.status == HttpStatusCode.NotModified -> {
                    cachedContributors?.success()
                        ?: Result.failure(IllegalStateException("No cached contributors"))
                }

                networkResult.status.value in 200..299 && !networkResult.body.isNullOrBlank() -> {
                    val contributors = parseContributorsJsonSafely(networkResult.body)
                    when {
                        !contributors.isEmpty -> contributors.success()
                        cachedContributors != null -> cachedContributors.success()
                        else -> Result.failure(IllegalStateException("No contributors found"))
                    }
                }

                cachedContributors != null -> cachedContributors.success()

                else -> Result.failure(IllegalStateException("GitHub contributors request failed"))
            }
        }

    private suspend fun fetchRepoContributorsNetwork(
        owner: String,
        repo: String,
        perPage: Int = 100,
        cachedEtag: String?,
    ): ContributorsNetworkResult {
        val response: HttpResponse =
            client.get("https://api.github.com/repos/$owner/$repo/contributors?per_page=$perPage") {
                headers {
                    append("Accept", "application/vnd.github+json")
                    append("User-Agent", "ArchiveTune")
                    if (!cachedEtag.isNullOrBlank()) {
                        append("If-None-Match", cachedEtag)
                    }
                }
            }
        val etag = response.headers["ETag"]
        return when (response.status) {
            HttpStatusCode.NotModified ->
                ContributorsNetworkResult(
                    status = response.status,
                    body = null,
                    etag = cachedEtag ?: etag,
                )

            else ->
                ContributorsNetworkResult(
                    status = response.status,
                    body = response.bodyAsText(),
                    etag = etag,
                )
        }
    }

    private fun parseContributorsJsonSafely(json: String): AboutContributorCollection =
        try {
            val jsonArray = JSONArray(json)
            val contributors = ArrayList<AboutContributor>(jsonArray.length())
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val login = item.optString("login", "")
                val type = item.optString("type", "")
                val avatarUrl = item.optString("avatar_url", "")
                val profileUrl = item.optString("html_url", "")
                val isBot =
                    type.equals("Bot", ignoreCase = true) ||
                        login.lowercase().endsWith("[bot]")

                if (!isBot && login.isNotBlank() && avatarUrl.isNotBlank()) {
                    contributors.add(
                        AboutContributor(
                            login = login,
                            avatarUrl = avatarUrl,
                            profileUrl = profileUrl,
                        ),
                    )
                }
            }
            AboutContributorCollection.from(contributors)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            AboutContributorCollection.Empty
        }

    private fun AboutContributorCollection.success(): Result<AboutContributorCollection> =
        Result.success(this)

    private data class ContributorsNetworkResult(
        val status: HttpStatusCode,
        val body: String?,
        val etag: String?,
    )

    private companion object {
        const val ContributorsCacheCheckIntervalMs: Long = 24 * 60 * 60 * 1000L
        const val GitHubOwner = "ArchiveTuneApp"
        const val GitHubRepo = "ArchiveTune"
    }
}
