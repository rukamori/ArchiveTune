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
import android.util.Base64
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

fun interface ModuleRequestSigner {
    fun headers(method: String, url: HttpUrl, body: ByteArray): Map<String, String>
}

internal class ModuleAppAttestation(private val context: Context, installation: String) {
    private val alias = ALIAS_PREFIX + ModuleRuntimeStorage.hash(installation).take(40)

    fun attest(moduleId: String, nonce: String): JSONObject {
        if (!NONCE.matches(nonce)) fail("ATTESTATION_NONCE", ModuleFailure.INVALID_CONFIGURATION)
        val challenge = sha256(listOf(ATTESTATION_CONTEXT, moduleId, nonce).joinToString("\n").toByteArray(Charsets.UTF_8))
        val chain = keystore {
            synchronized(lock) {
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                    initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAttestationChallenge(challenge)
                        .build())
                }.generateKeyPair()
                store().getCertificateChain(alias)
            }
        }
        if (chain == null || chain.size < 2) fail("ATTESTATION_UNAVAILABLE")
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("moduleId", moduleId)
            .put("packageName", context.packageName)
            .put("keyId", keyId(chain.first().publicKey.encoded))
            .put("certificateChain", JSONArray(chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }))
    }

    fun headers(moduleId: String, method: String, url: HttpUrl, body: ByteArray): Map<String, String> {
        if (!url.isHttps) fail("ATTESTATION_HTTPS", ModuleFailure.UNSUPPORTED)
        val timestamp = System.currentTimeMillis().toString()
        val nonce = ByteArray(16).also(random::nextBytes).hex()
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        val target = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val canonical = listOf(REQUEST_CONTEXT, moduleId, method, "${url.scheme}://${url.host}$port", target,
            sha256(body).hex(), timestamp, nonce).joinToString("\n")
        val (keyId, signature) = keystore {
            synchronized(lock) {
                val store = store()
                val key = store.getKey(alias, null) as? PrivateKey
                val certificate = store.getCertificate(alias)
                if (key == null || certificate == null) fail("ATTESTATION_REQUIRED", ModuleFailure.INVALID_CONFIGURATION)
                keyId(certificate.publicKey.encoded) to Signature.getInstance("SHA256withECDSA").run {
                    initSign(key)
                    update(canonical.toByteArray(Charsets.UTF_8))
                    sign()
                }
            }
        }
        return linkedMapOf(
            "$HEADER_PREFIX-Module" to moduleId,
            "$HEADER_PREFIX-Key" to keyId,
            "$HEADER_PREFIX-Timestamp" to timestamp,
            "$HEADER_PREFIX-Nonce" to nonce,
            "$HEADER_PREFIX-Signature" to Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        )
    }

    fun remove() {
        keystore { synchronized(lock) { store().run { if (containsAlias(alias)) deleteEntry(alias) } } }
    }

    private fun store(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private inline fun <T> keystore(operation: () -> T): T = try { operation() }
    catch (error: ModuleException) { throw error }
    catch (error: GeneralSecurityException) { throw ModuleException(ModuleFailure.UNSUPPORTED, error, ModuleLoadStage.PROVIDER, "ATTESTATION_UNAVAILABLE") }
    catch (error: ProviderException) { throw ModuleException(ModuleFailure.UNSUPPORTED, error, ModuleLoadStage.PROVIDER, "ATTESTATION_UNAVAILABLE") }

    private fun fail(code: String, failure: ModuleFailure = ModuleFailure.UNSUPPORTED): Nothing =
        throw ModuleException(failure, stage = ModuleLoadStage.PROVIDER, diagnosticCode = code)

    companion object {
        const val FEATURE = "appAttestation@1"
        const val FORMAT = "android-key-attestation"
        const val HEADER_PREFIX = "X-ArchiveTune-Attestation"
        private const val ATTESTATION_CONTEXT = "ARCHIVETUNE-MODULE-ATTESTATION-V1"
        private const val REQUEST_CONTEXT = "ARCHIVETUNE-MODULE-REQUEST-V1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS_PREFIX = "archivetune.module.attestation."
        private val NONCE = Regex("[A-Za-z0-9._~+/=-]{16,512}")
        private val lock = Any()
        private val random = SecureRandom()

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun keyId(publicKey: ByteArray): String =
            Base64.encodeToString(sha256(publicKey), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
