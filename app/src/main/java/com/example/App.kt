package com.example

import android.app.Application
import com.example.data.local.SecurePrefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Auto-migrate legacy preferences to EncryptedSharedPreferences on startup
            SecurePrefs.migrateFromOldPrefs(this, "sms_gateway_prefs")
        } catch (_: Exception) {}
    }
}
