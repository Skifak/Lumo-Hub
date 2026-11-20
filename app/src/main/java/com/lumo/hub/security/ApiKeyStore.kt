package com.lumo.hub.security

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small Android Keystore-backed secret store. The ciphertext may be kept in preferences.
 *  Each named alias holds its own key, so several secrets can live side by side. */
class ApiKeyStore(
    private val context: Context,
    private val alias: String = DEFAULT_ALIAS,
    private val prefKey: String = DEFAULT_PREF_KEY,
) {
    private val preferences = context.getSharedPreferences("secure_api_key", Context.MODE_PRIVATE)

    fun get(): String? = preferences.getString(prefKey, null)?.let { decrypt(it) }

    fun put(value: String) {
        if (value.isBlank()) {
            preferences.edit().remove(prefKey).apply()
        } else {
            preferences.edit().putString(prefKey, encrypt(value)).apply()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(StandardCharsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val DEFAULT_ALIAS = "lumo_api_key"
        const val DEFAULT_PREF_KEY = "value"
    }
}
