package com.ironlog.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session storage.
 *
 * Tokens are the one credential that must survive an app restart, so they cannot
 * live in memory only -- but a plain SharedPreferences XML file is readable by
 * anyone with adb backup or root. [EncryptedSharedPreferences] encrypts keys with
 * AES256-SIV and values with AES256-GCM under a master key that is generated
 * inside the Android Keystore and, on devices with a secure element, never leaves
 * the hardware.
 *
 * If the keystore entry is ever damaged (a known issue after some OEM restores)
 * the file is deleted and recreated rather than crashing the launch: worst case
 * the user signs in again.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy { openOrRecreate() }

    private fun openOrRecreate(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        context.deleteSharedPreferences(FILE_NAME)
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(userId: String, pair: TokenPair) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS, pair.accessToken)
            .putString(KEY_REFRESH, pair.refreshToken)
            .putLong(KEY_ACCESS_EXPIRY, pair.accessExpiresAt)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)
    fun accessExpiry(): Long = prefs.getLong(KEY_ACCESS_EXPIRY, 0L)

    fun signingSecret(): String? = prefs.getString(KEY_SIGNING_SECRET, null)
    fun saveSigningSecret(value: String) {
        prefs.edit().putString(KEY_SIGNING_SECRET, value).apply()
    }

    /** Clears the session but keeps the signing secret so old tokens stay invalid-checkable. */
    /**
     * The user's own Claude API key, for the optional cloud assistant.
     *
     * Kept here rather than in DataStore because it is a credential: this file
     * is encrypted with a key held in the Android keystore, so the API key gets
     * the same protection as the session tokens beside it.
     */
    fun assistantKey(): String? = prefs.getString(KEY_ASSISTANT, null)

    fun saveAssistantKey(value: String?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_ASSISTANT) else putString(KEY_ASSISTANT, value)
        }.apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_ACCESS_EXPIRY)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "ironlog_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXPIRY = "access_expiry"
        const val KEY_SIGNING_SECRET = "signing_secret"
        const val KEY_ASSISTANT = "assistant_api_key"
    }
}
