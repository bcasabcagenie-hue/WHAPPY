package com.whappy.chat

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps local WHAPPY data encrypted with a hardware-backed Android Keystore key. */
object WhappyCryptoVault {
    private const val KEY_ALIAS = "whappy_local_vault_v1"
    private const val BOOTSTRAP_PREFERENCES = "__whappy_secure_bootstrap"
    private const val MMKV_KEY = "mmkv_crypt_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    @SuppressLint("ApplySharedPref") // The bootstrap key must be persisted before it is returned.
    fun mmkvCryptKey(context: Context): String {
        val preferences = context.getSharedPreferences(BOOTSTRAP_PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(MMKV_KEY, null)?.let { encrypted ->
            runCatching { return decrypt(encrypted) }
        }
        val raw = ByteArray(8).also(SecureRandom()::nextBytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        check(preferences.edit().putString(MMKV_KEY, encrypt(raw)).commit()) {
            "Impossible de sécuriser les données locales WAPI"
        }
        return raw
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val envelope = ByteArray(IV_BYTES + encrypted.size)
        cipher.iv.copyInto(envelope, endIndex = IV_BYTES)
        encrypted.copyInto(envelope, destinationOffset = IV_BYTES)
        return Base64.encodeToString(envelope, Base64.NO_WRAP)
    }

    fun decrypt(envelope: String): String {
        val bytes = Base64.decode(envelope, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Enveloppe chiffrée WAPI invalide" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
