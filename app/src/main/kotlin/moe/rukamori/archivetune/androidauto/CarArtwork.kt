/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.androidauto

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object CarArtwork {
    const val MIME_TYPE = "image/jpeg"

    private const val AUTHORITY_SUFFIX = ".carartwork"
    private const val PATH_SEGMENT = "artwork"
    private const val KEY_FILE_NAME = "car_artwork_key"
    private const val KEY_SIZE_BYTES = 32
    private const val SIGNATURE_HEX_LENGTH = 32
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val CACHE_DIRECTORY_NAME = "car_artwork"
    private const val ARTWORK_SIZE_PX = 512
    private const val JPEG_QUALITY = 90
    private const val MAX_CACHED_FILES = 400
    private const val PRUNED_CACHE_FILES = 300
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private val keyLock = Any()
    private val cacheLock = Any()

    @Volatile
    private var signingKey: ByteArray? = null

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun requiresLocalUri(
        context: Context,
        uri: Uri,
    ): Boolean =
        when (uri.scheme?.lowercase()) {
            "http", "https", ContentResolver.SCHEME_FILE -> true
            ContentResolver.SCHEME_CONTENT -> uri.authority != authority(context)
            else -> false
        }

    fun contentUri(
        context: Context,
        source: Uri,
    ): Uri {
        val encodedSource = Base64.encodeToString(source.toString().toByteArray(Charsets.UTF_8), BASE64_FLAGS)
        return Uri
            .Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(authority(context))
            .appendPath(PATH_SEGMENT)
            .appendPath(signature(context, encodedSource))
            .appendPath(encodedSource)
            .build()
    }

    fun openArtworkFile(
        context: Context,
        uri: Uri,
    ): File {
        val source = verifiedSource(context, uri) ?: throw FileNotFoundException("Unknown artwork")
        val directory = File(context.cacheDir, CACHE_DIRECTORY_NAME).apply { mkdirs() }
        val target = File(directory, sha256Hex(source.toString()) + ".jpg")
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val bitmap = loadBitmap(context, source)
        val temporary = File.createTempFile("artwork", ".tmp", directory)
        try {
            temporary.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Could not encode artwork"
                }
            }
            if (!temporary.renameTo(target) && !(target.isFile && target.length() > 0L)) {
                throw FileNotFoundException("Could not cache artwork")
            }
        } finally {
            temporary.delete()
        }
        pruneCache(directory)
        return target
    }

    private fun verifiedSource(
        context: Context,
        uri: Uri,
    ): Uri? {
        if (uri.authority != authority(context)) return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != PATH_SEGMENT) return null
        val providedSignature = segments[1]
        val encodedSource = segments[2]
        val expectedSignature = signature(context, encodedSource)
        if (!MessageDigest.isEqual(
                providedSignature.toByteArray(Charsets.US_ASCII),
                expectedSignature.toByteArray(Charsets.US_ASCII),
            )
        ) {
            return null
        }
        val source =
            try {
                Uri.parse(String(Base64.decode(encodedSource, BASE64_FLAGS), Charsets.UTF_8))
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (!requiresLocalUri(context, source)) return null
        if (source.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) && !isAppOwnedFile(context, source)) {
            return null
        }
        return source
    }

    private fun isAppOwnedFile(
        context: Context,
        source: Uri,
    ): Boolean {
        val path = source.path ?: return false
        val file = File(path).canonicalFile
        return listOf(context.filesDir, context.cacheDir).any { root ->
            file.path.startsWith(root.canonicalPath + File.separator)
        }
    }

    private fun loadBitmap(
        context: Context,
        source: Uri,
    ): Bitmap {
        val request =
            ImageRequest
                .Builder(context)
                .data(source)
                .size(ARTWORK_SIZE_PX, ARTWORK_SIZE_PX)
                .allowHardware(false)
                .build()
        return when (val result = context.imageLoader.executeBlocking(request)) {
            is SuccessResult -> result.image.toBitmap()
            is ErrorResult -> throw FileNotFoundException("Artwork unavailable").apply { initCause(result.throwable) }
        }
    }

    private fun pruneCache(directory: File) {
        synchronized(cacheLock) {
            val files = directory.listFiles { file -> file.isFile && file.extension == "jpg" } ?: return
            if (files.size <= MAX_CACHED_FILES) return
            files
                .sortedBy(File::lastModified)
                .take(files.size - PRUNED_CACHE_FILES)
                .forEach(File::delete)
        }
    }

    private fun signature(
        context: Context,
        value: String,
    ): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey(context), HMAC_ALGORITHM))
        return mac
            .doFinal(value.toByteArray(Charsets.UTF_8))
            .toHex()
            .take(SIGNATURE_HEX_LENGTH)
    }

    private fun signingKey(context: Context): ByteArray {
        signingKey?.let { return it }
        return synchronized(keyLock) {
            signingKey ?: readOrCreateKey(context).also { signingKey = it }
        }
    }

    private fun readOrCreateKey(context: Context): ByteArray {
        val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
        keyFile
            .takeIf(File::isFile)
            ?.readBytes()
            ?.takeIf { it.size == KEY_SIZE_BYTES }
            ?.let { return it }
        val key = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val temporary = File.createTempFile(KEY_FILE_NAME, ".tmp", context.noBackupFilesDir)
        try {
            temporary.writeBytes(key)
            if (!temporary.renameTo(keyFile)) {
                keyFile.writeBytes(key)
            }
        } finally {
            temporary.delete()
        }
        return key
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
