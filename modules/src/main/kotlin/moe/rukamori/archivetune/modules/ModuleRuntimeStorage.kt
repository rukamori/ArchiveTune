/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class ModuleRuntimeStorage(context: Context, installation: String) {
    private val directory = File(context.noBackupFilesDir, "module-runtime/${hash(installation)}")

    fun read(scope: String): JSONObject = synchronized(fileLock) {
        val file = AtomicFile(File(directory, hash(scope)))
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized JSONObject()
        val bytes = file.openRead().use { it.readModuleBytes(LIMIT + 64) }
        if (bytes.size < 29 || bytes[0] != 1.toByte()) throw ModuleException(ModuleFailure.INTEGRITY)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        val plain = try { cipher.doFinal(bytes.copyOfRange(13, bytes.size)) }
        catch (error: java.security.GeneralSecurityException) { throw ModuleException(ModuleFailure.INTEGRITY, error, ModuleLoadStage.STORAGE) }
        ModulePackageReader.manifest(plain)
    }

    fun write(scope: String, value: JSONObject): Unit = synchronized(fileLock) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > LIMIT) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
        if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Module storage unavailable")
        val file = AtomicFile(File(directory, hash(scope)))
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    fun remove(): Unit = synchronized(fileLock) {
        directory.listFiles()?.forEach { if (!it.delete() && it.exists()) throw java.io.IOException("Module storage removal failed") }
        if (directory.exists() && !directory.delete()) throw java.io.IOException("Module storage removal failed")
    }

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        private const val LIMIT = 1024 * 1024
        private const val ALIAS = "archivetune.module.runtime"
        private val keyLock = Any()
        private val fileLock = Any()
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
