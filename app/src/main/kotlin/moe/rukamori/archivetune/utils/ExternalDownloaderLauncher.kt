/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

enum class ExternalDownloaderLaunchResult {
    STARTED,
    NOT_CONFIGURED,
    NOT_INSTALLED,
}

fun Context.openExternalDownloader(
    configuredIdentifier: String,
    targetUrl: String,
): ExternalDownloaderLaunchResult {
    val identifier =
        configuredIdentifier
            .trim()
            .removePrefix("package:")
            .trim()
            .takeIf(String::isNotBlank)
            ?: return ExternalDownloaderLaunchResult.NOT_CONFIGURED

    val url =
        targetUrl
            .trim()
            .takeIf(String::isNotBlank)
            ?: return ExternalDownloaderLaunchResult.NOT_INSTALLED

    val packageNames =
        buildList {
            add(identifier)
            addAll(resolveMatchingPackageNames(identifier, url))
        }.distinct()

    val uri = Uri.parse(url)
    return packageNames
        .asSequence()
        .flatMap { packageName ->
            createLaunchIntents(packageName, uri).asSequence()
        }.firstOrNull(::tryStartActivity)
        ?.let { ExternalDownloaderLaunchResult.STARTED }
        ?: ExternalDownloaderLaunchResult.NOT_INSTALLED
}

private fun Context.resolveMatchingPackageNames(
    identifier: String,
    targetUrl: String,
): List<String> {
    val packageManager = packageManager
    val normalizedIdentifier = normalizeIdentifier(identifier)
    val queryIntents =
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)),
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )

    val activities =
        queryIntents
            .flatMap { intent ->
                val flags =
                    if (intent.action == Intent.ACTION_MAIN) {
                        0
                    } else {
                        PackageManager.MATCH_DEFAULT_ONLY
                    }
                packageManager.queryIntentActivities(intent, flags)
            }.asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .filter { activityInfo ->
                val applicationLabel =
                    activityInfo.applicationInfo
                        ?.loadLabel(packageManager)
                        ?.toString()
                        .orEmpty()
                identifier.equals(activityInfo.packageName, ignoreCase = true) ||
                    normalizedIdentifier == normalizeIdentifier(activityInfo.packageName) ||
                    normalizedIdentifier == normalizeIdentifier(applicationLabel)
            }.map { it.packageName }
            .toList()

    return activities
}

private fun normalizeIdentifier(identifier: String): String =
    identifier
        .trim()
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

private fun createLaunchIntents(
    packageName: String,
    uri: Uri,
): List<Intent> =
    listOf(
        Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uri.toString())
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )

private fun Context.tryStartActivity(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
