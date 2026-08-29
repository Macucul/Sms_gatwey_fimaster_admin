package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecurePrefs helper for Sms_gatwey_fimaster_admin.
 * Uses EncryptedSharedPreferences to store sensitive values (githubToken, fastApiToken, etc.).
 */
object SecurePrefs {
    private const val FILE_NAME = "secure_prefs"

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback for JVM/Robolectric test environments or devices without KeyStore provider
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrate sensitive keys from an old plain SharedPreferences file into encrypted prefs.
     */
    fun migrateFromOldPrefs(
        context: Context,
        oldPrefsName: String = "sms_gateway_prefs",
        sensitiveKeys: List<String> = listOf("github_token", "fastapi_token", "settings_password")
    ) {
        try {
            val plain = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
            if (plain.all.isEmpty()) return
            val secure = getEncryptedPrefs(context)
            val editor = secure.edit()

            for (key in sensitiveKeys) {
                if (plain.contains(key)) {
                    when (val value = plain.all[key]) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        else -> { /* ignore unsupported types */ }
                    }
                }
            }
            editor.apply()

            // Remove migrated keys from legacy plain prefs
            val oldEditor = plain.edit()
            for (key in sensitiveKeys) {
                if (plain.contains(key)) oldEditor.remove(key)
            }
            oldEditor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
