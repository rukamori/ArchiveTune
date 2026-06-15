/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.about

import android.content.Context
import androidx.compose.runtime.Immutable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AboutTranslationContributor(
    val language: String,
    val contributors: AboutTranslationContributorNameCollection,
)

@Immutable
data class AboutTranslationContributorCollection private constructor(
    private val values: List<AboutTranslationContributor>,
) {
    val isEmpty: Boolean get() = values.isEmpty()
    val size: Int get() = values.size

    operator fun get(index: Int): AboutTranslationContributor = values[index]

    companion object {
        fun from(values: List<AboutTranslationContributor>): AboutTranslationContributorCollection =
            AboutTranslationContributorCollection(values.toList())
    }
}

@Immutable
data class AboutTranslationContributorNameCollection private constructor(
    private val values: List<String>,
) {
    val isEmpty: Boolean get() = values.isEmpty()

    fun joinToString(): String = values.joinToString(separator = ", ")

    companion object {
        fun from(values: List<String>): AboutTranslationContributorNameCollection =
            AboutTranslationContributorNameCollection(values.toList())
    }
}

@Immutable
data class AboutDependencyLicense(
    val name: String,
    val version: String?,
    val licenses: String?,
)

@Immutable
data class AboutDependencyLicenseCollection private constructor(
    private val values: List<AboutDependencyLicense>,
) {
    val isEmpty: Boolean get() = values.isEmpty()
    val size: Int get() = values.size

    operator fun get(index: Int): AboutDependencyLicense = values[index]

    companion object {
        fun from(values: List<AboutDependencyLicense>): AboutDependencyLicenseCollection =
            AboutDependencyLicenseCollection(values.toList())
    }
}

class FetchAboutTranslationContributorsUseCase
@Inject
constructor(
    private val repository: AboutAttributionRepository,
) {
    suspend operator fun invoke(): Result<AboutTranslationContributorCollection> =
        repository.translationContributors()
}

class FetchAboutDependencyLicensesUseCase
@Inject
constructor(
    private val repository: AboutAttributionRepository,
) {
    suspend operator fun invoke(): Result<AboutDependencyLicenseCollection> =
        repository.dependencyLicenses()
}

@Singleton
class AboutAttributionRepository
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

    suspend fun translationContributors(): Result<AboutTranslationContributorCollection> =
        withContext(Dispatchers.IO) {
            try {
                val languageResponse = getJson(TranslationLanguagesUrl)
                val languages = parseTranslationLanguages(languageResponse)
                val contributorsByLanguage = getTranslationContributors(languages)
                val contributors = buildTranslationContributorCollection(
                    languages = languages,
                    contributorsByLanguage = contributorsByLanguage,
                )
                if (contributors.isEmpty) {
                    Result.failure(IllegalStateException("No translation contributors found"))
                } else {
                    Result.success(contributors)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.failure(throwable)
            }
        }

    suspend fun dependencyLicenses(): Result<AboutDependencyLicenseCollection> =
        withContext(Dispatchers.IO) {
            try {
                val libs = Libs.Builder()
                    .withContext(context)
                    .build()
                val licenses = libs.libraries
                    .map { library ->
                        AboutDependencyLicense(
                            name = library.name.ifBlank { library.uniqueId },
                            version = library.artifactVersion?.takeIf(String::isNotBlank),
                            licenses = library.licenses
                                .map { license -> license.name }
                                .filter { license -> license.isNotBlank() }
                                .distinct()
                                .joinToString(separator = ", ")
                                .takeIf(String::isNotBlank),
                        )
                    }
                    .filter { library -> library.name.isNotBlank() }
                val collection = AboutDependencyLicenseCollection.from(licenses)
                if (collection.isEmpty) {
                    Result.failure(IllegalStateException("No dependency licenses found"))
                } else {
                    Result.success(collection)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.failure(throwable)
            }
        }

    private suspend fun getJson(url: String): String {
        val response: HttpResponse = client.get(url) {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "ArchiveTune")
                BuildConfig.WEBLATE_API_TOKEN.trim().takeIf(String::isNotBlank)?.let { token ->
                    append("Authorization", "Token $token")
                }
            }
        }
        if (response.status.value !in SuccessStatusCodes) {
            throw IllegalStateException("Weblate request failed with HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    private suspend fun getTranslationContributors(
        languages: List<TranslationLanguage>,
    ): Map<String, List<String>> {
        val token = BuildConfig.WEBLATE_API_TOKEN.trim()
        if (token.isNotBlank()) {
            val credits = getTranslationCredits(languages)
            if (credits.isNotEmpty()) return credits
        }
        return parseTranslationContributors(getJson(TranslationChangesUrl))
    }

    private suspend fun getTranslationCredits(
        languages: List<TranslationLanguage>,
    ): Map<String, List<String>> {
        val end = Instant.now().toString()
        val contributorsByLanguage = LinkedHashMap<String, List<String>>()
        for (language in languages) {
            val contributors = try {
                parseTranslationCredits(getJson(translationCreditsUrl(language.code, end)))
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Timber.w(throwable, "Failed to load Weblate translation credits")
                return emptyMap()
            }
            if (contributors.isNotEmpty()) {
                contributorsByLanguage[language.code] = contributors
            }
        }
        return contributorsByLanguage
    }

    private fun parseTranslationLanguages(json: String): List<TranslationLanguage> {
        val languages = JSONArray(json)
        val values = ArrayList<TranslationLanguage>(languages.length())
        for (index in 0 until languages.length()) {
            val language = languages.getJSONObject(index)
            val code = language.optString("code").takeIf(String::isNotBlank) ?: continue
            values.add(
                TranslationLanguage(
                    code = code,
                    name = language.optString("name", code).ifBlank { code },
                ),
            )
        }
        return values
    }

    private fun buildTranslationContributorCollection(
        languages: List<TranslationLanguage>,
        contributorsByLanguage: Map<String, List<String>>,
    ): AboutTranslationContributorCollection {
        val values = ArrayList<AboutTranslationContributor>(languages.size)
        for (language in languages) {
            val contributors = contributorsByLanguage[language.code].orEmpty()
            values.add(
                AboutTranslationContributor(
                    language = language.name,
                    contributors = AboutTranslationContributorNameCollection.from(contributors),
                ),
            )
        }
        return AboutTranslationContributorCollection.from(
            values.sortedBy { contributor -> contributor.language.lowercase() },
        )
    }

    private fun parseTranslationContributors(json: String): Map<String, List<String>> {
        val result = JSONObject(json).optJSONArray("results") ?: return emptyMap()
        val contributorsByLanguage = LinkedHashMap<String, LinkedHashSet<String>>()
        for (index in 0 until result.length()) {
            val item = result.getJSONObject(index)
            val languageCode = item.optString("translation").translationLanguageCode() ?: continue
            val contributor = item.optString("author")
                .takeIf(String::isNotBlank)
                ?.translationContributorName()
                ?.takeUnless(::isIgnoredTranslationContributor)
                ?: continue
            contributorsByLanguage
                .getOrPut(languageCode) { LinkedHashSet() }
                .add(contributor)
        }
        return contributorsByLanguage.mapValues { (_, contributors) ->
            contributors.take(MaxContributorsPerLanguage)
        }
    }

    private fun parseTranslationCredits(json: String): List<String> {
        val credits = when (val trimmedJson = json.trim()) {
            "" -> JSONArray()
            else -> if (trimmedJson.startsWith("[")) {
                JSONArray(trimmedJson)
            } else {
                JSONObject(trimmedJson).optJSONArray("results") ?: JSONArray()
            }
        }
        val contributors = LinkedHashSet<String>()
        for (index in 0 until credits.length()) {
            val contributor = credits.getJSONObject(index)
                .optString("full_name")
                .takeIf(String::isNotBlank)
                ?.takeUnless(::isIgnoredTranslationContributor)
                ?: continue
            contributors.add(contributor)
            if (contributors.size == MaxContributorsPerLanguage) break
        }
        return contributors.toList()
    }

    private fun isIgnoredTranslationContributor(name: String): Boolean =
        IgnoredTranslationContributors.any { ignoredName ->
            name.equals(ignoredName, ignoreCase = true)
        }

    private fun translationCreditsUrl(
        languageCode: String,
        end: String,
    ): String =
        "$TranslationCreditsUrl?start=$TranslationCreditsStart&end=${end.urlEncoded()}&lang=${languageCode.urlEncoded()}"

    private fun String.translationLanguageCode(): String? {
        val segments = trimEnd('/').split('/')
        val translationsIndex = segments.indexOf("translations")
        if (translationsIndex < 0 || segments.size <= translationsIndex + 3) return null
        return segments[translationsIndex + 3].takeIf(String::isNotBlank)
    }

    private fun String.translationContributorName(): String? {
        val encodedName = trimEnd('/').substringAfterLast('/').takeIf(String::isNotBlank) ?: return null
        return URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
    }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private data class TranslationLanguage(
        val code: String,
        val name: String,
    )

    private companion object {
        const val TranslationLanguagesUrl = "https://translate.codeberg.org/api/projects/archivetune/languages/"
        const val TranslationChangesUrl = "https://translate.codeberg.org/api/projects/archivetune/changes/?page_size=1000"
        const val TranslationCreditsUrl = "https://translate.codeberg.org/api/projects/archivetune/credits/"
        const val TranslationCreditsStart = "1970-01-01T00%3A00%3A00Z"
        const val WeblateCommitUser = "weblate:commit"
        const val AnonymousUser = "anonymous"
        const val MisspelledAnonymousUser = "anynymous"
        const val MaxContributorsPerLanguage = 6
        val SuccessStatusCodes = 200..299
        val IgnoredTranslationContributors = setOf(WeblateCommitUser, AnonymousUser, MisspelledAnonymousUser)
    }
}
