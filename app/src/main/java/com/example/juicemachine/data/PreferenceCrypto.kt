package com.example.juicemachine.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * AES-GCM encryption helper using Android Keystore.
 * Stores and retrieves a per-app symmetric key under alias [KEY_ALIAS].
 * Payload format: Base64(IV || CipherTextWithTag), IV length = 12 bytes.
 */
object PreferenceCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "prefs_aes_key"
    private const val IV_SIZE = 12

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptToBase64(plain: String): String {
        if (plain.isEmpty()) return plain
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // 12 bytes
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(IV_SIZE + ciphertext.size)
        System.arraycopy(iv, 0, payload, 0, IV_SIZE)
        System.arraycopy(ciphertext, 0, payload, IV_SIZE, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decryptFromBase64(enc: String): String {
        if (enc.isEmpty()) return enc
        val data = Base64.decode(enc, Base64.NO_WRAP)
        require(data.size > IV_SIZE) { "Invalid ciphertext" }
        val iv = data.copyOfRange(0, IV_SIZE)
        val cipherText = data.copyOfRange(IV_SIZE, data.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plain = cipher.doFinal(cipherText)
        return String(plain, Charsets.UTF_8)
    }
}