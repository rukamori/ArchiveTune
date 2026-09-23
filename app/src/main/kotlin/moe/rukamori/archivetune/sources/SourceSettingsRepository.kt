package moe.rukamori.archivetune.sources

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cipher = SourceSecretCipher()
    private val store = PreferenceDataStoreFactory.create {
        File(context.noBackupFilesDir, "external-sources.preferences_pb")
    }
    val settings: Flow<SourceSettings> = store.data.map { preferences ->
        withContext(Dispatchers.IO) {
            preferences[KEY]?.let { json.decodeFromString<SourceSettings>(cipher.decrypt(it)) } ?: SourceSettings()
        }
    }

    suspend fun update(transform: (SourceSettings) -> SourceSettings) = withContext(Dispatchers.IO) {
        store.edit { preferences ->
            val current = preferences[KEY]?.let { json.decodeFromString<SourceSettings>(cipher.decrypt(it)) } ?: SourceSettings()
            val next = transform(current).copy(revision = current.revision + 1)
            preferences[KEY] = cipher.encrypt(json.encodeToString(SourceSettings.serializer(), next))
        }
        Unit
    }

    companion object {
        const val PREFERENCE_NAME = "externalSourcesEncrypted"
        private val KEY = stringPreferencesKey(PREFERENCE_NAME)
    }
}

internal class SourceSecretCipher {
    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return@synchronized it }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "archivetune.external.sources"
        val KEY_LOCK = Any()
    }
}
