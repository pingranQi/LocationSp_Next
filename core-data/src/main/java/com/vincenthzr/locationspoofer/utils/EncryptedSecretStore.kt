package com.vincenthzr.locationspoofer.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores API credentials encrypted with an AES-GCM key held by Android Keystore.
 * This protects credentials at rest; it does not protect a rooted OS or a process
 * already running with this application's privileges.
 */
internal class EncryptedSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val encryptedPrefs = appContext.getSharedPreferences("encrypted_secrets", Context.MODE_PRIVATE)
    private val legacyPrefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    @Synchronized
    fun get(name: String): String {
        val stored = encryptedPrefs.getString(name, null)
        if (stored != null) return decrypt(name, stored)

        // One-time migration from legacy plaintext preferences.
        val legacy = legacyPrefs.getString(name, "") ?: ""
        if (legacy.isEmpty()) return ""
        return try {
            val encrypted = encrypt(name, legacy)
            check(encryptedPrefs.edit().putString(name, encrypted).commit()) {
                "Unable to persist encrypted credential"
            }
            if (!legacyPrefs.edit().remove(name).commit()) {
                Log.w(TAG, "Encrypted credential migrated, but legacy value could not be removed")
            }
            legacy
        } catch (error: Exception) {
            Log.e(TAG, "Unable to migrate stored credential; refusing plaintext fallback", error)
            ""
        }
    }

    @Synchronized
    fun set(name: String, value: String) {
        if (value.isEmpty()) {
            check(encryptedPrefs.edit().remove(name).commit()) { "Unable to clear encrypted credential" }
            legacyPrefs.edit().remove(name).commit()
            return
        }
        val encrypted = encrypt(name, value)
        check(encryptedPrefs.edit().putString(name, encrypted).commit()) {
            "Unable to persist encrypted credential"
        }
        if (!legacyPrefs.edit().remove(name).commit()) {
            Log.w(TAG, "Encrypted credential saved, but legacy plaintext value could not be removed")
        }
    }

    private fun encrypt(name: String, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return PREFIX + iv + ":" + body
    }

    private fun decrypt(name: String, stored: String): String {
        if (!stored.startsWith(PREFIX)) {
            Log.e(TAG, "Credential has an unknown storage format")
            return ""
        }
        return try {
            val parts = stored.removePrefix(PREFIX).split(':', limit = 2)
            require(parts.size == 2) { "Malformed encrypted credential" }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to decrypt credential '$name'; re-enter it in settings", error)
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "LocationSp_Next"
        const val PREFIX = "enc:v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "locationsp_api_credentials_v1"
    }
}
