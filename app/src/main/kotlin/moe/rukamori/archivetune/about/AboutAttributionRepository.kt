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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GitHubTranslationContributorsJsonKey
import moe.rukamori.archivetune.constants.GitHubTranslationContributorsLastCheckedAtKey
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
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

    fun forEach(action: (String) -> Unit) {
        values.forEach(action)
    }

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
        fun from(values: List<AboutDependencyLicense>): AboutDependencyLicenseCollection = AboutDependencyLicenseCollection(values.toList())
    }
}

class FetchAboutTranslationContributorsUseCase
    @Inject
    constructor(
        private val repository: AboutAttributionRepository,
    ) {
        suspend operator fun invoke(): Result<AboutTranslationContributorCollection> = repository.translationContributors()
    }

class FetchAboutDependencyLicensesUseCase
    @Inject
    constructor(
        private val repository: AboutAttributionRepository,
    ) {
        suspend operator fun invoke(): Result<AboutDependencyLicenseCollection> = repository.dependencyLicenses()
    }

@Singleton
class AboutAttributionRepository
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

        suspend fun translationContributors(): Result<AboutTranslationContributorCollection> =
            withContext(Dispatchers.IO) {
                val preferences = context.dataStore.data.first()
                val now = System.currentTimeMillis()
                val cachedContributors =
                    preferences[GitHubTranslationContributorsJsonKey]
                        ?.takeIf(String::isNotBlank)
                        ?.let(::parseTranslationContributorCollectionSafely)
                        ?.takeIf { contributors -> !contributors.isEmpty }
                val lastCheckedAt = preferences[GitHubTranslationContributorsLastCheckedAtKey] ?: 0L

                if (cachedContributors != null && now - lastCheckedAt < TranslationContributorCacheTtlMs) {
                    return@withContext Result.success(cachedContributors)
                }

                try {
                    val languages = getTranslationLanguages()
                    val contributorsByLanguage = getTranslationCommitContributors(languages)
                    val contributors =
                        buildTranslationContributorCollection(
                            languages = languages,
                            contributorsByLanguage = contributorsByLanguage,
                        )
                    if (contributors.isEmpty) {
                        cachedContributors?.let { cached -> Result.success(cached) }
                            ?: Result.failure(IllegalStateException("No translation contributors found"))
                    } else {
                        context.dataStore.edit { cache ->
                            cache[GitHubTranslationContributorsJsonKey] = contributors.toCacheJson()
                            cache[GitHubTranslationContributorsLastCheckedAtKey] = now
                        }
                        Result.success(contributors)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    cachedContributors?.let { cached -> Result.success(cached) } ?: Result.failure(throwable)
                }
            }

        suspend fun dependencyLicenses(): Result<AboutDependencyLicenseCollection> =
            withContext(Dispatchers.IO) {
                try {
                    val libs =
                        Libs
                            .Builder()
                            .withContext(context)
                            .build()
                    val licenses =
                        libs.libraries
                            .map { library ->
                                AboutDependencyLicense(
                                    name = library.name.ifBlank { library.uniqueId },
                                    version = library.artifactVersion?.takeIf(String::isNotBlank),
                                    licenses =
                                        library.licenses
                                            .map { license -> license.name }
                                            .filter { license -> license.isNotBlank() }
                                            .distinct()
                                            .joinToString(separator = ", ")
                                            .takeIf(String::isNotBlank),
                                )
                            }.filter { library -> library.name.isNotBlank() }
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

        private suspend fun getGitHubCommitsJson(
            path: String,
            page: Int,
        ): String {
            val response: HttpResponse =
                client.get(GitHubCommitsUrl) {
                    headers {
                        append("Accept", "application/vnd.github+json")
                        append("User-Agent", "ArchiveTune")
                    }
                    parameter("path", path)
                    parameter("per_page", GitHubCommitsPageSize)
                    parameter("page", page)
                }
            if (response.status.value !in SuccessStatusCodes) {
                throw IllegalStateException("GitHub commits request failed with HTTP ${response.status.value}")
            }
            return response.bodyAsText()
        }

        private suspend fun getGitHubTranslationResourceJson(): String {
            val response: HttpResponse =
                client.get(GitHubTranslationResourceUrl) {
                    headers {
                        append("Accept", "application/vnd.github+json")
                        append("User-Agent", "ArchiveTune")
                    }
                }
            if (response.status.value !in SuccessStatusCodes) {
                throw IllegalStateException("GitHub resource request failed with HTTP ${response.status.value}")
            }
            return response.bodyAsText()
        }

        private suspend fun getTranslationLanguages(): List<TranslationLanguage> {
            val resources = JSONArray(getGitHubTranslationResourceJson())
            val languages = ArrayList<TranslationLanguage>(resources.length())
            for (index in 0 until resources.length()) {
                val resource = resources.getJSONObject(index)
                val name = resource.optString("name")
                if (resource.optString("type") != GitHubDirectoryType || !name.startsWith(TranslationResourcePrefix)) {
                    continue
                }
                val resourceQualifier = name.removePrefix(TranslationResourcePrefix)
                if (resourceQualifier.isBlank()) continue
                val resourcePath =
                    resource
                        .optString("path")
                        .ifBlank { "$TranslationResourceRoot/$name" }
                languages.add(
                    TranslationLanguage(
                        resourceQualifier = resourceQualifier,
                        name = resourceQualifier.toLanguageDisplayName(),
                        resourcePath = resourcePath,
                    ),
                )
            }
            return languages.sortedBy { language -> language.name.lowercase() }
        }

        private suspend fun getTranslationCommitContributors(languages: List<TranslationLanguage>): Map<String, List<String>> {
            val contributorsByLanguage = LinkedHashMap<String, LinkedHashSet<String>>()
            for (language in languages) {
                mergeContributorMaps(
                    target = contributorsByLanguage,
                    source = mapOf(language.resourceQualifier to getTranslationCommitContributors(language)),
                )
            }
            return contributorsByLanguage.toLimitedContributorMap()
        }

        private suspend fun getTranslationCommitContributors(language: TranslationLanguage): List<String> {
            val contributors = LinkedHashSet<String>()
            var page = 1
            while (contributors.size < MaxContributorsPerLanguage && page <= MaxCommitPagesPerLanguage) {
                val commits =
                    JSONArray(
                        getGitHubCommitsJson(
                            path = language.resourcePath,
                            page = page,
                        ),
                    )
                if (commits.length() == 0) break

                for (index in 0 until commits.length()) {
                    if (contributors.size == MaxContributorsPerLanguage) break
                    val commit = commits.getJSONObject(index)
                    if (!commit.isTranslationCommit()) continue
                    val contributor =
                        commit
                            .translationCommitAuthorName()
                            ?.takeUnless(::isIgnoredTranslationContributor)
                            ?: continue
                    contributors.add(contributor)
                }
                page++
            }
            return contributors.toList()
        }

        private fun buildTranslationContributorCollection(
            languages: List<TranslationLanguage>,
            contributorsByLanguage: Map<String, List<String>>,
        ): AboutTranslationContributorCollection {
            val values = ArrayList<AboutTranslationContributor>(languages.size)
            for (language in languages) {
                val contributors = contributorsByLanguage[language.resourceQualifier].orEmpty()
                if (contributors.isEmpty()) continue
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

        private fun AboutTranslationContributorCollection.toCacheJson(): String {
            val cachedContributors = JSONArray()
            for (index in 0 until size) {
                val contributor = this[index]
                val contributorNames = JSONArray()
                contributor.contributors.forEach { name ->
                    contributorNames.put(name)
                }
                cachedContributors.put(
                    JSONObject()
                        .put(CacheLanguageKey, contributor.language)
                        .put(CacheContributorsKey, contributorNames),
                )
            }
            return cachedContributors.toString()
        }

        private fun parseTranslationContributorCollectionSafely(json: String): AboutTranslationContributorCollection =
            try {
                val cachedContributors = JSONArray(json)
                val contributors = ArrayList<AboutTranslationContributor>(cachedContributors.length())
                for (index in 0 until cachedContributors.length()) {
                    val cachedContributor = cachedContributors.getJSONObject(index)
                    val language = cachedContributor.optString(CacheLanguageKey).takeIf(String::isNotBlank) ?: continue
                    val cachedContributorNames = cachedContributor.optJSONArray(CacheContributorsKey) ?: continue
                    val contributorNames = ArrayList<String>(cachedContributorNames.length())
                    for (nameIndex in 0 until cachedContributorNames.length()) {
                        val contributorName =
                            cachedContributorNames
                                .optString(nameIndex)
                                .trim()
                                .takeIf(String::isNotBlank)
                                ?.takeUnless(::isIgnoredTranslationContributor)
                                ?: continue
                        contributorNames.add(contributorName)
                    }
                    if (contributorNames.isEmpty()) continue
                    contributors.add(
                        AboutTranslationContributor(
                            language = language,
                            contributors = AboutTranslationContributorNameCollection.from(contributorNames.distinct()),
                        ),
                    )
                }
                AboutTranslationContributorCollection.from(contributors)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                AboutTranslationContributorCollection.from(emptyList())
            }

        private fun mergeContributorMaps(
            target: LinkedHashMap<String, LinkedHashSet<String>>,
            source: Map<String, List<String>>,
        ) {
            for ((languageCode, contributors) in source) {
                val targetContributors = target.getOrPut(languageCode) { LinkedHashSet() }
                for (contributor in contributors) {
                    val cleanContributor =
                        contributor
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?.takeUnless(::isIgnoredTranslationContributor)
                            ?: continue
                    targetContributors.add(cleanContributor)
                    if (targetContributors.size == MaxContributorsPerLanguage) break
                }
            }
        }

        private fun Map<String, LinkedHashSet<String>>.toLimitedContributorMap(): Map<String, List<String>> =
            mapValues { (_, contributors) ->
                contributors.take(MaxContributorsPerLanguage)
            }.filterValues { contributors ->
                contributors.isNotEmpty()
            }

        private fun isIgnoredTranslationContributor(name: String): Boolean =
            IgnoredTranslationContributors.any { ignoredName ->
                name.equals(ignoredName, ignoreCase = true)
            }

        private fun JSONObject.isTranslationCommit(): Boolean =
            optJSONObject("commit")
                ?.optString("message")
                ?.startsWith(TranslationCommitMessagePrefix, ignoreCase = true)
                ?: false

        private fun JSONObject.translationCommitAuthorName(): String? {
            val authorLogin =
                optJSONObject("author")
                    ?.optString("login")
                    ?.takeIf(String::isNotBlank)
            val commitAuthorName =
                optJSONObject("commit")
                    ?.optJSONObject("author")
                    ?.optString("name")
                    ?.takeIf(String::isNotBlank)
            return (authorLogin ?: commitAuthorName)
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }

        private fun String.toLanguageDisplayName(): String {
            val languageTag = toLanguageTag()
            val displayName =
                Locale
                    .forLanguageTag(languageTag)
                    .getDisplayName(Locale.ENGLISH)
                    .trim()
            return displayName
                .takeIf { name -> name.isNotBlank() && !name.equals(languageTag, ignoreCase = true) }
                ?: this
        }

        private fun String.toLanguageTag(): String {
            if (startsWith(Bcp47ResourceQualifierPrefix)) {
                return removePrefix(Bcp47ResourceQualifierPrefix).replace('+', '-')
            }
            val segments = split('-').filter(String::isNotBlank)
            if (segments.isEmpty()) return this
            val tagSegments = ArrayList<String>(segments.size)
            tagSegments.add(segments.first().toModernLanguageCode())
            for (segment in segments.drop(1)) {
                tagSegments.add(
                    if (segment.startsWith(RegionQualifierPrefix) && segment.length > 1) {
                        segment.drop(1)
                    } else {
                        segment
                    },
                )
            }
            return tagSegments.joinToString(separator = "-")
        }

        private fun String.toModernLanguageCode(): String =
            when (this) {
                LegacyIndonesianLanguageCode -> IndonesianLanguageCode
                LegacyHebrewLanguageCode -> HebrewLanguageCode
                LegacyYiddishLanguageCode -> YiddishLanguageCode
                else -> this
            }

        private data class TranslationLanguage(
            val resourceQualifier: String,
            val name: String,
            val resourcePath: String,
        )

        private companion object {
            const val GitHubCommitsUrl = "https://api.github.com/repos/rukamori/ArchiveTune/commits"
            const val GitHubTranslationResourceUrl =
                "https://api.github.com/repos/rukamori/ArchiveTune/contents/app/src/main/res"
            const val TranslationResourceRoot = "app/src/main/res"
            const val TranslationResourcePrefix = "values-"
            const val TranslationCommitMessagePrefix = "Translated using Weblate"
            const val GitHubDirectoryType = "dir"
            const val Bcp47ResourceQualifierPrefix = "b+"
            const val RegionQualifierPrefix = "r"
            const val LegacyIndonesianLanguageCode = "in"
            const val IndonesianLanguageCode = "id"
            const val LegacyHebrewLanguageCode = "iw"
            const val HebrewLanguageCode = "he"
            const val LegacyYiddishLanguageCode = "ji"
            const val YiddishLanguageCode = "yi"
            const val GitHubCommitsPageSize = 100
            const val MaxCommitPagesPerLanguage = 2
            const val TranslationContributorCacheTtlMs = 7L * 24L * 60L * 60L * 1000L
            const val CacheLanguageKey = "language"
            const val CacheContributorsKey = "contributors"
            const val WeblateCommitUser = "weblate:commit"
            const val CodebergTranslateUser = "Codeberg Translate"
            const val AnonymousUser = "anonymous"
            const val MisspelledAnonymousUser = "anynymous"
            const val MaxContributorsPerLanguage = 6
            val SuccessStatusCodes = 200..299
            val IgnoredTranslationContributors =
                setOf(WeblateCommitUser, CodebergTranslateUser, AnonymousUser, MisspelledAnonymousUser)
        }
    }
