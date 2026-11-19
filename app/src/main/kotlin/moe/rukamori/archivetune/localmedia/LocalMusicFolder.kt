/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

internal data class LocalMusicFolder(
    val path: String,
    val volume: String? = null,
) {
    fun contains(folder: LocalMusicFolder): Boolean =
        (volume == null || volume.equals(folder.volume, ignoreCase = true)) &&
            (path.isEmpty() || path.equals(folder.path, ignoreCase = true) ||
                folder.path.startsWith("$path/", ignoreCase = true))

    fun fileIdentity(displayName: String): String =
        "${volume.orEmpty().lowercase(Locale.ROOT)}:$path/$displayName"

    companion object {
        private const val ExternalStorageAuthority = "com.android.externalstorage.documents"
        private val EmulatedStoragePrefix = Regex("^/?(?:storage/emulated/[0-9]+|storage/self/primary|sdcard)(?:/|$)")
        private val StorageVolumePrefix = Regex("^/?storage/([^/]+)(?:/|$)")
        private val DuplicateSlashRegex = Regex("/+")

        fun treeUri(entry: String): Uri? =
            entry.takeIf { it.startsWith("content://") }?.let(Uri::parse)
                ?.takeIf(DocumentsContract::isTreeUri)

        fun fromEntry(entry: String): LocalMusicFolder {
            val uri = treeUri(entry)
            return if (uri != null) fromDocumentId(uri, DocumentsContract.getTreeDocumentId(uri))
            else fromPath(entry)
        }

        fun fromMediaStore(relativePath: String?, absolutePath: String?, volumeName: String?): LocalMusicFolder {
            val absoluteFolder = absolutePath?.substringBeforeLast('/', missingDelimiterValue = "")
                ?.takeIf(String::isNotEmpty)?.let(::fromPath)
            val volume = if (volumeName == "external_primary") "primary" else volumeName
            return if (relativePath != null) {
                LocalMusicFolder(cleanPath(relativePath), volume ?: absoluteFolder?.volume)
            } else {
                absoluteFolder ?: LocalMusicFolder("", volume)
            }
        }

        fun fromDocumentId(uri: Uri, documentId: String): LocalMusicFolder =
            if (uri.authority == ExternalStorageAuthority && ':' in documentId) {
                LocalMusicFolder(cleanPath(documentId.substringAfter(':')), documentId.substringBefore(':'))
            } else {
                LocalMusicFolder(cleanPath(documentId), uri.authority)
            }

        fun documentIdentity(uri: Uri): String? {
            if (uri.scheme != "content" || "document" !in uri.pathSegments) return null
            val documentId = DocumentsContract.getDocumentId(uri)
            val file = fromDocumentId(uri, documentId)
            return file.copy(path = file.path.substringBeforeLast('/', ""))
                .fileIdentity(file.path.substringAfterLast('/'))
        }

        private fun fromPath(raw: String): LocalMusicFolder {
            val path = raw.trim().replace('\\', '/').replace(DuplicateSlashRegex, "/")
            EmulatedStoragePrefix.find(path)?.let { prefix ->
                return LocalMusicFolder(cleanPath(path.removeRange(prefix.range)), "primary")
            }
            StorageVolumePrefix.find(path)?.let { prefix ->
                return LocalMusicFolder(cleanPath(path.removeRange(prefix.range)), prefix.groupValues[1])
            }
            return LocalMusicFolder(cleanPath(path))
        }

        private fun cleanPath(path: String): String =
            path.trim().replace('\\', '/').replace(DuplicateSlashRegex, "/").trim('/')
    }
}
