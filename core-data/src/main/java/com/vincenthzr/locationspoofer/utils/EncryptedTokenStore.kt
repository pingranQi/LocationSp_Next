package com.vincenthzr.locationspoofer.utils

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM token-at-rest encryption backed by AndroidKeyStore. */
internal object EncryptedTokenStore {
    private const val KEY_ALIAS = "locationspoofer.api.tokens.v1"
    private const val PREFIX = "enc:v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun read(prefs: SharedPreferences, name: String): String {
        val stored = prefs.getString(name, null) ?: return ""
        if (!stored.startsWith(PREFIX)) {
            // One-time plaintext migration. Do not fall back to plaintext if encryption fails.
            return try {
                val encrypted = encrypt(name, stored)
                if (prefs.edit().putString(name, encrypted).commit()) {
                    stored
                } else {
                    // If migration cannot be committed, do not leave a plaintext credential at rest.
                    prefs.edit().remove(name).commit()
                    ""
                }
            } catch (_: Exception) {
                // Keystore unavailable / invalidated: fail closed and remove the legacy plaintext value.
                prefs.edit().remove(name).commit()
                ""
            }
        }
        return try {
            decrypt(name, stored.removePrefix(PREFIX))
        } catch (_: Exception) {
            ""
        }
    }

    fun write(prefs: SharedPreferences, name: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val encoded = try {
            encrypt(name, value)
        } catch (_: Exception) {
            // Preserve a previously encrypted credential on transient Keystore errors.
            // Never retain a legacy plaintext value when encryption is unavailable.
            if (prefs.getString(name, null)?.startsWith(PREFIX) != true) {
                prefs.edit().remove(name).apply()
            }
            return
        }
        prefs.edit().putString(name, encoded).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(name: String, value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(name: String, encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "Invalid encrypted token payload" }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
