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
import moe.rukamori.archivetune.constants.GitHubContributorsJsonKey
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

    fun take(count: Int): AboutContributorCollection = AboutContributorCollection(values.take(count))

    fun forEach(action: (AboutContributor) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorCollection(emptyList())

        fun from(values: List<AboutContributor>): AboutContributorCollection = AboutContributorCollection(values.toList())
    }
}

class FetchAboutContributorsUseCase
    @Inject
    constructor(
        private val repository: AboutContributorsRepository,
    ) {
        suspend operator fun invoke(): Result<AboutContributorCollection> = repository.contributors()
    }

@Singleton
class AboutContributorsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val client =
            HttpClient(OkHttp) {
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
                val cachedContributors =
                    cachedJson
                        ?.takeIf { json -> json.isNotBlank() }
                        ?.let { json -> parseContributorsJsonSafely(json, maxContributors = ContributorsLimit) }
                        ?.takeIf { contributors -> !contributors.isEmpty }

                if (cachedContributors != null) {
                    return@withContext cachedContributors.success()
                }

                val networkResult =
                    try {
                        fetchRepoContributorsNetwork(
                            owner = GitHubOwner,
                            repo = GitHubRepo,
                        )
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        return@withContext Result.failure(throwable)
                    }

                when {
                    networkResult.status.value in 200..299 && networkResult.body.isNotBlank() -> {
                        val contributors =
                            parseContributorsJsonSafely(
                                json = networkResult.body,
                                maxContributors = ContributorsLimit,
                            )
                        when {
                            !contributors.isEmpty -> {
                                context.dataStore.edit { preferences ->
                                    preferences[GitHubContributorsJsonKey] = networkResult.body
                                }
                                contributors.success()
                            }

                            else -> {
                                Result.failure(IllegalStateException("No contributors found"))
                            }
                        }
                    }

                    else -> {
                        Result.failure(IllegalStateException("GitHub contributors request failed"))
                    }
                }
            }

        private suspend fun fetchRepoContributorsNetwork(
            owner: String,
            repo: String,
            perPage: Int = ContributorsLimit,
        ): ContributorsNetworkResult {
            val response: HttpResponse =
                client.get("https://api.github.com/repos/$owner/$repo/contributors?per_page=$perPage") {
                    headers {
                        append("Accept", "application/vnd.github+json")
                        append("User-Agent", "ArchiveTune")
                    }
                }
            return ContributorsNetworkResult(
                status = response.status,
                body = response.bodyAsText(),
            )
        }

        private fun parseContributorsJsonSafely(
            json: String,
            maxContributors: Int,
        ): AboutContributorCollection =
            try {
                val jsonArray = JSONArray(json)
                val contributors = ArrayList<AboutContributor>(minOf(jsonArray.length(), maxContributors))
                for (index in 0 until jsonArray.length()) {
                    if (contributors.size >= maxContributors) break
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

        private fun AboutContributorCollection.success(): Result<AboutContributorCollection> = Result.success(this)

        private data class ContributorsNetworkResult(
            val status: HttpStatusCode,
            val body: String,
        )

        private companion object {
            const val ContributorsLimit = 20
            const val GitHubOwner = "rukamori"
            const val GitHubRepo = "ArchiveTune"
        }
    }
