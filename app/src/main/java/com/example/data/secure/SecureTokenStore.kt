package com.example.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.syncPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_bebas_prefs")

data class AppPrefs(
    val googleEmail: String = "",
    val googleDisplayName: String = "",
    val driveConnected: Boolean = false
)

class SecureTokenStore(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sync_bebas_pat_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_email")
        private val KEY_GOOGLE_NAME = stringPreferencesKey("google_name")
        private val KEY_DRIVE_CONNECTED = booleanPreferencesKey("drive_connected")
        private val KEY_DRIVE_ACCESS_TOKEN_ENC = stringPreferencesKey("drive_access_token_enc")

        private fun githubPatKey(accountId: Long) = stringPreferencesKey("gh_pat_enc_$accountId")
    }

    val appPrefs: Flow<AppPrefs> = context.syncPrefsDataStore.data.map { prefs ->
        AppPrefs(
            googleEmail = prefs[KEY_GOOGLE_EMAIL].orEmpty(),
            googleDisplayName = prefs[KEY_GOOGLE_NAME].orEmpty(),
            driveConnected = prefs[KEY_DRIVE_CONNECTED] ?: false
        )
    }

    suspend fun getAppPrefsSnapshot(): AppPrefs = appPrefs.first()

    suspend fun saveGoogleAccount(email: String, displayName: String, accessToken: String?) {
        context.syncPrefsDataStore.edit { prefs ->
            prefs[KEY_GOOGLE_EMAIL] = email
            prefs[KEY_GOOGLE_NAME] = displayName
            prefs[KEY_DRIVE_CONNECTED] = true
            if (!accessToken.isNullOrBlank()) {
                prefs[KEY_DRIVE_ACCESS_TOKEN_ENC] = encryptString(accessToken)
            }
        }
    }

    suspend fun saveDriveAccessToken(accessToken: String) {
        context.syncPrefsDataStore.edit { prefs ->
            prefs[KEY_DRIVE_ACCESS_TOKEN_ENC] = encryptString(accessToken)
            prefs[KEY_DRIVE_CONNECTED] = true
        }
    }

    suspend fun getDriveAccessToken(): String? {
        val enc = context.syncPrefsDataStore.data.first()[KEY_DRIVE_ACCESS_TOKEN_ENC] ?: return null
        return decryptString(enc)
    }

    suspend fun clearGoogleAccount() {
        context.syncPrefsDataStore.edit { prefs ->
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_NAME)
            prefs[KEY_DRIVE_CONNECTED] = false
            prefs.remove(KEY_DRIVE_ACCESS_TOKEN_ENC)
        }
    }

    suspend fun saveGithubPat(accountId: Long, plainPat: String) {
        val encrypted = encryptString(plainPat.trim())
        context.syncPrefsDataStore.edit { prefs ->
            prefs[githubPatKey(accountId)] = encrypted
        }
    }

    suspend fun getGithubPat(accountId: Long): String? {
        val encrypted = context.syncPrefsDataStore.data.first()[githubPatKey(accountId)] ?: return null
        return decryptString(encrypted)
    }

    suspend fun deleteGithubPat(accountId: Long) {
        context.syncPrefsDataStore.edit { prefs ->
            prefs.remove(githubPatKey(accountId))
        }
    }

    private fun getOrCreateKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }

    internal fun encryptString(plainText: String): String {
        val key = getOrCreateKey()
        if (key == null) {
            // Fallback only for JVM Robolectric tests where AndroidKeyStore is unavailable
            val encoded = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "RAW:$encoded"
        }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return "GCM:${iv.size}:" + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    internal fun decryptString(stored: String): String? {
        return runCatching {
            when {
                stored.startsWith("GCM:") -> {
                    val rest = stored.removePrefix("GCM:")
                    val sep = rest.indexOf(':')
                    if (sep <= 0) return null
                    val ivLen = rest.substring(0, sep).toInt()
                    val combined = Base64.decode(rest.substring(sep + 1), Base64.NO_WRAP)
                    val iv = combined.copyOfRange(0, ivLen)
                    val ciphertext = combined.copyOfRange(ivLen, combined.size)
                    val key = getOrCreateKey() ?: return null
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(ciphertext), Charsets.UTF_8)
                }
                stored.startsWith("RAW:") -> {
                    val bytes = Base64.decode(stored.removePrefix("RAW:"), Base64.NO_WRAP)
                    String(bytes, Charsets.UTF_8)
                }
                else -> null
            }
        }.getOrNull()
    }
}
