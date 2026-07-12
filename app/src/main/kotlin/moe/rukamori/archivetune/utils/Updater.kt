/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.edit
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.CanaryReleasesEtagKey
import moe.rukamori.archivetune.constants.CanaryReleasesFingerprintKey
import moe.rukamori.archivetune.constants.CanaryReleasesJsonKey
import moe.rukamori.archivetune.constants.CanaryReleasesLastCheckedAtKey
import moe.rukamori.archivetune.constants.GitHubReleasesEtagKey
import moe.rukamori.archivetune.constants.GitHubReleasesFingerprintKey
import moe.rukamori.archivetune.constants.GitHubReleasesJsonKey
import moe.rukamori.archivetune.constants.GitHubReleasesLastCheckedAtKey
import org.json.JSONArray

data class GitCommit(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val url: String,
    val authorAvatarUrl: String? = null,
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String?,
    val publishedAt: String,
    val htmlUrl: String,
)

private data class ReleasesNetworkResult(
    val status: HttpStatusCode,
    val body: String?,
    val etag: String?,
)

object Updater {
    private val client = HttpClient()
    private const val ReleaseCacheCheckIntervalMs: Long = 6 * 60 * 60 * 1000L
    private const val StableReleaseBaseUrl = "https://github.com/rukamori/ArchiveTune/releases"
    private const val CanaryReleaseBaseUrl =
        "https://github.com/rukamori/canary/releases"
    var lastCheckTime = -1L
        private set
    private var latestReleaseTag: String? = null
    private var latestCanaryReleaseTag: String? = null

    private val isUpdaterDistribution: Boolean
        get() =
            BuildConfig.UPDATER_AVAILABLE &&
                when (BuildConfig.DISTRIBUTION) {
                    "gms", "foss" -> true
                    else -> false
                }

    private val canDownloadUpdatesDirectly: Boolean
        get() = BuildConfig.DISTRIBUTION == "gms"

    private val releaseArtifactPrefix: String
        get() =
            when (BuildConfig.DISTRIBUTION) {
                "gms" -> "gms-"
                "foss" -> "foss-"
                else -> ""
            }

    private fun stableReleaseArtifactName(): String =
        "app-$releaseArtifactPrefix${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-release.apk"

    private fun canaryReleaseArtifactName(): String =
        "app-$releaseArtifactPrefix${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-nightly.apk"

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<PreReleaseIdentifier>,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val majorCompare = major.compareTo(other.major)
            if (majorCompare != 0) return majorCompare
            val minorCompare = minor.compareTo(other.minor)
            if (minorCompare != 0) return minorCompare
            val patchCompare = patch.compareTo(other.patch)
            if (patchCompare != 0) return patchCompare

            val thisIsStable = preRelease.isEmpty()
            val otherIsStable = other.preRelease.isEmpty()
            if (thisIsStable && !otherIsStable) return 1
            if (!thisIsStable && otherIsStable) return -1

            val maxIndex = minOf(preRelease.size, other.preRelease.size)
            for (i in 0 until maxIndex) {
                val c = preRelease[i].compareTo(other.preRelease[i])
                if (c != 0) return c
            }
            return preRelease.size.compareTo(other.preRelease.size)
        }

        fun normalizedName(): String =
            if (preRelease.isEmpty()) {
                "$major.$minor.$patch"
            } else {
                "$major.$minor.$patch-" + preRelease.joinToString(".") { it.raw }
            }
    }

    private sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
        val raw: String
    }

    private data class NumericIdentifier(
        override val raw: String,
        val value: Long,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> value.compareTo(other.value)
                is AlphaIdentifier -> -1
            }
    }

    private data class AlphaIdentifier(
        override val raw: String,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> 1
                is AlphaIdentifier -> raw.compareTo(other.raw)
            }
    }

    private val semVerRegex =
        Regex("""(?i)\bv?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\b""")

    private fun parseSemVerOrNull(text: String): SemVer? {
        val match = semVerRegex.find(text) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        val preReleaseText = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
        val preRelease =
            preReleaseText
                ?.split('.')
                ?.filter { it.isNotBlank() }
                ?.map { identifier ->
                    if (identifier.all { it.isDigit() }) {
                        NumericIdentifier(raw = identifier, value = identifier.toLong())
                    } else {
                        AlphaIdentifier(raw = identifier)
                    }
                }
                ?: emptyList()
        return SemVer(
            major = major,
            minor = minor,
            patch = patch,
            preRelease = preRelease,
        )
    }

    private fun parseReleaseSemVerOrNull(release: ReleaseInfo): SemVer? =
        parseSemVerOrNull(release.tagName) ?: parseSemVerOrNull(release.name)

    internal fun isSameVersion(
        a: String,
        b: String,
    ): Boolean {
        val aSemVer = parseSemVerOrNull(a)
        val bSemVer = parseSemVerOrNull(b)
        return if (aSemVer != null && bSemVer != null) {
            aSemVer.major == bSemVer.major &&
                aSemVer.minor == bSemVer.minor &&
                aSemVer.patch == bSemVer.patch &&
                aSemVer.preRelease == bSemVer.preRelease
        } else {
            a.trim() == b.trim()
        }
    }

    internal fun isUpdateAvailable(
        latestVersion: String,
        currentVersion: String,
    ): Boolean {
        val latestSemVer = parseSemVerOrNull(latestVersion)
        val currentSemVer = parseSemVerOrNull(currentVersion)
        return if (latestSemVer != null && currentSemVer != null) {
            latestSemVer > currentSemVer
        } else {
            !isSameVersion(latestVersion, currentVersion)
        }
    }

    internal fun findLatestRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        val parsed =
            releases.mapNotNull { release ->
                parseReleaseSemVerOrNull(release)?.let { version -> version to release }
            }

        if (parsed.isEmpty()) return releases.firstOrNull()

        val stable = parsed.filter { it.first.preRelease.isEmpty() }
        val candidates = stable.ifEmpty { parsed }
        return candidates.maxWithOrNull(compareBy({ it.first }, { it.second.publishedAt }))?.second
    }

    internal fun findLatestCanaryRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        return releases.maxByOrNull { release ->
            val dateTag = release.tagName.removePrefix("N").takeWhile { it.isDigit() }
            dateTag.toLongOrNull() ?: 0L
        }
    }

    private fun preferredReleaseVersionNameOrNull(release: ReleaseInfo): String? = parseReleaseSemVerOrNull(release)?.normalizedName()

    private fun parseReleasesJson(json: String): List<ReleaseInfo> {
        val jsonArray = JSONArray(json)
        val releases = ArrayList<ReleaseInfo>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            releases.add(
                ReleaseInfo(
                    tagName = item.optString("tag_name", ""),
                    name = item.optString("name", ""),
                    body = if (item.has("body")) item.optString("body") else null,
                    publishedAt = item.optString("published_at", ""),
                    htmlUrl = item.optString("html_url", ""),
                ),
            )
        }
        return releases
    }

    private fun getTopReleaseFingerprint(releases: List<ReleaseInfo>): String {
        val latest = findLatestRelease(releases) ?: return ""
        return listOf(
            latest.tagName,
            latest.name,
            latest.publishedAt,
            latest.body.orEmpty(),
            latest.htmlUrl,
        ).joinToString("||")
    }

    private suspend fun fetchReleasesNetwork(
        perPage: Int,
        cachedEtag: String?,
    ): ReleasesNetworkResult {
        val response: HttpResponse =
            client.get("https://api.github.com/repos/rukamori/ArchiveTune/releases?per_page=$perPage") {
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
            HttpStatusCode.NotModified -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = null,
                    etag = cachedEtag ?: etag,
                )
            }

            else -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = response.bodyAsText(),
                    etag = etag,
                )
            }
        }
    }

    suspend fun getCachedReleases(): List<ReleaseInfo> {
        if (!isUpdaterDistribution) {
            return emptyList()
        }

        val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
        return cachedJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun getLatestVersionName(): Result<String> =
        getLatestReleaseInfo().map { latest ->
            preferredReleaseVersionNameOrNull(latest) ?: latest.name.ifBlank { latest.tagName }
        }

    suspend fun getLatestReleaseNotes(): Result<String?> = getLatestReleaseInfo().map { it.body }

    suspend fun getLatestReleaseInfo(): Result<ReleaseInfo> =
        runCatching {
            if (!isUpdaterDistribution) {
                throw IllegalStateException("Updater is not available for this distribution")
            }

            val releases = getAllReleases().getOrThrow()
            val latest =
                findLatestRelease(releases)
                    ?: throw IllegalStateException("No releases found")
            lastCheckTime = System.currentTimeMillis()
            latestReleaseTag = latest.tagName
            latest
        }

    suspend fun getCommitHistory(
        count: Int = 20,
        branch: String = "dev",
    ): Result<List<GitCommit>> =
        runCatching {
            if (!isUpdaterDistribution) {
                return@runCatching emptyList()
            }

            val response =
                client
                    .get("https://api.github.com/repos/rukamori/ArchiveTune/commits?sha=$branch&per_page=$count")
                    .bodyAsText()
            val jsonArray = JSONArray(response)
            val commits = mutableListOf<GitCommit>()
            for (i in 0 until jsonArray.length()) {
                val commitObj = jsonArray.getJSONObject(i)
                val commit = commitObj.getJSONObject("commit")
                val authorObj = commit.optJSONObject("author")
                val githubAuthorObj = commitObj.optJSONObject("author")
                commits.add(
                    GitCommit(
                        sha = commitObj.optString("sha", "").take(7),
                        message = commit.optString("message", "").lines().firstOrNull() ?: "",
                        author = authorObj?.optString("name", "Unknown") ?: "Unknown",
                        date = authorObj?.optString("date", "") ?: "",
                        url = commitObj.optString("html_url", ""),
                        authorAvatarUrl = githubAuthorObj?.optString("avatar_url")?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            commits
        }

    fun getLatestDownloadUrl(): String {
        if (!isUpdaterDistribution) {
            return ""
        }

        if (!canDownloadUpdatesDirectly) {
            return "$StableReleaseBaseUrl/latest"
        }

        val artifactName = stableReleaseArtifactName()
        val tag = latestReleaseTag
        if (tag != null) {
            return "$StableReleaseBaseUrl/download/$tag/$artifactName"
        }
        return "$StableReleaseBaseUrl/latest/download/$artifactName"
    }

    suspend fun getLatestCanaryVersionName(): Result<String> =
        getLatestCanaryReleaseInfo().map { latest ->
            latest.tagName.ifBlank { latest.name }
        }

    suspend fun getLatestCanaryReleaseNotes(): Result<String?> = getLatestCanaryReleaseInfo().map { it.body }

    suspend fun getLatestCanaryReleaseInfo(): Result<ReleaseInfo> =
        runCatching {
            if (!isUpdaterDistribution) {
                throw IllegalStateException("Updater is not available for this distribution")
            }

            val releases = getAllCanaryReleases().getOrThrow()
            val latest =
                findLatestCanaryRelease(releases)
                    ?: throw IllegalStateException("No Canary releases found")
            lastCheckTime = System.currentTimeMillis()
            latestCanaryReleaseTag = latest.tagName
            latest
        }

    suspend fun getCachedCanaryReleases(): List<ReleaseInfo> {
        if (!isUpdaterDistribution) {
            return emptyList()
        }

        val cachedJson = App.instance.dataStore.getAsync(CanaryReleasesJsonKey)
        return cachedJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun getAllCanaryReleases(
        perPage: Int = 10,
        forceRefresh: Boolean = false,
    ): Result<List<ReleaseInfo>> {
        if (!isUpdaterDistribution) {
            return Result.success(emptyList())
        }

        return runCatching {
            val now = System.currentTimeMillis()
            val cachedJson = App.instance.dataStore.getAsync(CanaryReleasesJsonKey)
            val cachedEtag = App.instance.dataStore.getAsync(CanaryReleasesEtagKey)
            val lastCheckedAt = App.instance.dataStore.getAsync(CanaryReleasesLastCheckedAtKey, 0L)
            val cachedFingerprint = App.instance.dataStore.getAsync(CanaryReleasesFingerprintKey)

            val cachedReleases =
                cachedJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }

            val shouldCheckNetwork =
                forceRefresh || cachedJson.isNullOrBlank() || (now - lastCheckedAt) >= ReleaseCacheCheckIntervalMs

            if (!shouldCheckNetwork) {
                return@runCatching cachedReleases ?: emptyList()
            }

            val networkResult =
                runCatching {
                    fetchCanaryReleasesNetwork(
                        perPage = perPage,
                        cachedEtag = cachedEtag,
                    )
                }.getOrNull()

            if (networkResult == null) {
                val fallback = cachedReleases
                if (fallback != null) {
                    return@runCatching fallback
                }
                throw IllegalStateException("Failed to fetch Canary releases")
            }

            when {
                networkResult.status == HttpStatusCode.NotModified -> {
                    App.instance.dataStore.edit { settings ->
                        settings[CanaryReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[CanaryReleasesEtagKey] = it }
                    }
                    val fallback = cachedReleases
                    if (fallback != null) {
                        return@runCatching fallback
                    }
                    throw IllegalStateException("Canary release cache is empty")
                }

                networkResult.status.value in 200..299 && !networkResult.body.isNullOrBlank() -> {
                    val networkBody = networkResult.body
                    val releases = parseReleasesJson(networkBody)
                    val newFingerprint = getCanaryTopReleaseFingerprint(releases)
                    val hasPayloadChanged = cachedJson != networkBody
                    val hasTopReleaseChanged = cachedFingerprint != newFingerprint

                    App.instance.dataStore.edit { settings ->
                        settings[CanaryReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[CanaryReleasesEtagKey] = it }
                        if (hasPayloadChanged || hasTopReleaseChanged || cachedJson.isNullOrBlank()) {
                            settings[CanaryReleasesJsonKey] = networkBody
                            settings[CanaryReleasesFingerprintKey] = newFingerprint
                        }
                    }
                    releases
                }

                else -> {
                    val fallback = cachedReleases
                    if (fallback != null) {
                        fallback
                    } else {
                        throw IllegalStateException("Failed to fetch Canary releases: HTTP ${networkResult.status.value}")
                    }
                }
            }
        }
    }

    private suspend fun fetchCanaryReleasesNetwork(
        perPage: Int,
        cachedEtag: String?,
    ): ReleasesNetworkResult {
        val response: HttpResponse =
            client.get("https://api.github.com/repos/rukamori/daily-nightly/releases?per_page=$perPage") {
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
            HttpStatusCode.NotModified -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = null,
                    etag = cachedEtag ?: etag,
                )
            }

            else -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = response.bodyAsText(),
                    etag = etag,
                )
            }
        }
    }

    private fun getCanaryTopReleaseFingerprint(releases: List<ReleaseInfo>): String {
        val latest = findLatestCanaryRelease(releases) ?: return ""
        return listOf(
            latest.tagName,
            latest.name,
            latest.publishedAt,
            latest.body.orEmpty(),
            latest.htmlUrl,
        ).joinToString("||")
    }

    fun getLatestCanaryDownloadUrl(): String {
        if (!isUpdaterDistribution) {
            return ""
        }

        if (!canDownloadUpdatesDirectly) {
            return "$CanaryReleaseBaseUrl/latest"
        }

        val artifactName = canaryReleaseArtifactName()
        val tag = latestCanaryReleaseTag
        if (tag != null) {
            return "$CanaryReleaseBaseUrl/download/$tag/$artifactName"
        }
        return "$CanaryReleaseBaseUrl/latest/download/$artifactName"
    }

    suspend fun getAllReleases(
        perPage: Int = 30,
        forceRefresh: Boolean = false,
    ): Result<List<ReleaseInfo>> {
        if (!isUpdaterDistribution) {
            return Result.success(emptyList())
        }

        return runCatching {
            val now = System.currentTimeMillis()
            val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
            val cachedEtag = App.instance.dataStore.getAsync(GitHubReleasesEtagKey)
            val lastCheckedAt = App.instance.dataStore.getAsync(GitHubReleasesLastCheckedAtKey, 0L)
            val cachedFingerprint = App.instance.dataStore.getAsync(GitHubReleasesFingerprintKey)

            val cachedReleases =
                cachedJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }

            val shouldCheckNetwork =
                forceRefresh || cachedJson.isNullOrBlank() || (now - lastCheckedAt) >= ReleaseCacheCheckIntervalMs

            if (!shouldCheckNetwork) {
                lastCheckTime = now
                return@runCatching cachedReleases ?: emptyList()
            }

            val networkResult =
                runCatching {
                    fetchReleasesNetwork(
                        perPage = perPage,
                        cachedEtag = cachedEtag,
                    )
                }.getOrNull()

            if (networkResult == null) {
                val fallback = cachedReleases
                if (fallback != null) {
                    lastCheckTime = now
                    return@runCatching fallback
                }
                throw IllegalStateException("Failed to fetch releases")
            }

            when {
                networkResult.status == HttpStatusCode.NotModified -> {
                    App.instance.dataStore.edit { settings ->
                        settings[GitHubReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                    }
                    val fallback = cachedReleases
                    if (fallback != null) {
                        lastCheckTime = now
                        return@runCatching fallback
                    }
                    throw IllegalStateException("Release cache is empty")
                }

                networkResult.status.value in 200..299 && !networkResult.body.isNullOrBlank() -> {
                    val networkBody = networkResult.body
                    val releases = parseReleasesJson(networkBody)
                    val newFingerprint = getTopReleaseFingerprint(releases)
                    val hasPayloadChanged = cachedJson != networkBody
                    val hasTopReleaseChanged = cachedFingerprint != newFingerprint

                    App.instance.dataStore.edit { settings ->
                        settings[GitHubReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                        if (hasPayloadChanged || hasTopReleaseChanged || cachedJson.isNullOrBlank()) {
                            settings[GitHubReleasesJsonKey] = networkBody
                            settings[GitHubReleasesFingerprintKey] = newFingerprint
                        }
                    }
                    lastCheckTime = now
                    releases
                }

                else -> {
                    val fallback = cachedReleases
                    if (fallback != null) {
                        lastCheckTime = now
                        fallback
                    } else {
                        throw IllegalStateException("Failed to fetch releases: HTTP ${networkResult.status.value}")
                    }
                }
            }
        }
    }
}
