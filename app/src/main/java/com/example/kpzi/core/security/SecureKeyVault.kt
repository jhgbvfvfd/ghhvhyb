package com.example.kpzi.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.kpzi.domain.model.NexusUserProfile
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android KeyStore AES-256-GCM vault for storing the user's
 * personal Nexus Mods API key and authenticated session metadata locally.
 * No developer API key is ever bundled in the APK.
 */
class SecureKeyVault(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
            .putInt(iv.size)
            .put(iv)
            .put(cipherBytes)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? {
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val ivSize = buffer.int
            if (ivSize !in 12..32) return null
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearSession()
            return
        }
        prefs.edit().putString(KEY_ENCRYPTED_API_KEY, encrypt(trimmed)).apply()
    }

    fun getApiKey(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
        return decrypt(encrypted)
    }

    fun saveProfile(profile: NexusUserProfile) {
        prefs.edit()
            .putLong(KEY_USER_ID, profile.userId)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_PROFILE_URL, profile.profileUrl)
            .putBoolean(KEY_IS_PREMIUM, profile.isPremium)
            .putBoolean(KEY_IS_SUPPORTER, profile.isSupporter)
            .apply()
    }

    fun getSavedProfile(): NexusUserProfile? {
        if (getApiKey().isNullOrBlank()) return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val userId = prefs.getLong(KEY_USER_ID, -1L)
        if (userId <= 0L) return null
        return NexusUserProfile(
            userId = userId,
            username = username,
            profileUrl = prefs.getString(KEY_PROFILE_URL, null),
            isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false),
            isSupporter = prefs.getBoolean(KEY_IS_SUPPORTER, false),
            isConnected = true,
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kpzi_nexus_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "kpzi_secure_vault"
        const val KEY_ENCRYPTED_API_KEY = "enc_nexus_api_key"
        const val KEY_USER_ID = "nexus_user_id"
        const val KEY_USERNAME = "nexus_username"
        const val KEY_PROFILE_URL = "nexus_profile_url"
        const val KEY_IS_PREMIUM = "nexus_is_premium"
        const val KEY_IS_SUPPORTER = "nexus_is_supporter"
    }
}
