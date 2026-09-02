package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences by lazy { SecurePrefs.getEncryptedPrefs(context) }

    init {
        // Automatically migrate legacy tokens from plain SharedPreferences to EncryptedSharedPreferences
        try {
            SecurePrefs.migrateFromOldPrefs(context, "sms_gateway_prefs")
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_SALDO_MINIMO = "saldo_minimo"
        private const val KEY_VALOR_MINIMO_ATIVACAO = "valor_minimo_ativacao"
        private const val KEY_VALIDADE_MESES = "validade_meses"
        private const val KEY_SYNC_MODE = "sync_mode"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_GITHUB_BRANCH = "github_branch"
        private const val KEY_GITHUB_PATH = "github_path"
        private const val KEY_FASTAPI_URL = "fastapi_url"
        private const val KEY_FASTAPI_TOKEN = "fastapi_token"
        private const val KEY_AUTO_SEND_SMS = "auto_send_sms"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_CUSTOM_REGEX = "custom_regex"
        private const val KEY_FILTER_OFFICIAL_SENDERS = "filter_official_senders"
        private const val KEY_OFFICIAL_SENDERS = "official_senders"
        private const val KEY_DADOS_VERSION = "dados_version"
        private const val KEY_ULTIMA_ATUALIZACAO_DADOS = "ultima_atualizacao_dados"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        private const val KEY_MAX_REFUND_DAYS = "max_refund_days"
        private const val KEY_SITE_URL = "site_url"
        private const val KEY_LAST_REMINDER_DATE = "last_reminder_date"
        
        private const val KEY_SMS_BINDING_ENABLED = "sms_binding_enabled"
        private const val KEY_DISCOUNT_ENABLED = "discount_enabled"
        private const val KEY_DISCOUNT_TEXT = "discount_text"
        private const val KEY_DISCOUNT_PERCENT = "discount_percent"
        private const val KEY_SETTINGS_PASSWORD = "settings_password"
        private const val KEY_FIREBASE_AUTH_EMAIL = "firebase_auth_email"
        private const val KEY_FIREBASE_AUTH_PASSWORD = "firebase_auth_password"
        private const val KEY_FIREBASE_DB_TARGET = "firebase_db_target"
        private const val KEY_ADMIN_TEMPLATES_JSON = "admin_templates_json"

        const val MODE_GITHUB = "GITHUB"
        const val MODE_FASTAPI = "FASTAPI"
        const val MODE_FIREBASE = "FIREBASE"

        const val FIREBASE_TARGET_RTDB = "RTDB"
        const val FIREBASE_TARGET_FIRESTORE = "FIRESTORE"
        const val FIREBASE_TARGET_BOTH = "BOTH"
    }

    var smsBindingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_BINDING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMS_BINDING_ENABLED, value).apply()

    var discountEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISCOUNT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCOUNT_ENABLED, value).apply()

    var discountText: String
        get() = prefs.getString(KEY_DISCOUNT_TEXT, "DESCONTO") ?: "DESCONTO"
        set(value) = prefs.edit().putString(KEY_DISCOUNT_TEXT, value).apply()

    var discountPercent: Double
        get() = prefs.getFloat(KEY_DISCOUNT_PERCENT, 10.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_DISCOUNT_PERCENT, value.toFloat()).apply()

    var settingsPassword: String
        get() = securePrefs.getString(KEY_SETTINGS_PASSWORD, "1234") ?: "1234"
        set(value) = securePrefs.edit().putString(KEY_SETTINGS_PASSWORD, value).apply()

    var firebaseAuthEmail: String
        get() = securePrefs.getString(KEY_FIREBASE_AUTH_EMAIL, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_FIREBASE_AUTH_EMAIL, value.trim()).apply()

    var firebaseAuthPassword: String
        get() = securePrefs.getString(KEY_FIREBASE_AUTH_PASSWORD, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_FIREBASE_AUTH_PASSWORD, value).apply()

    var firebaseDbTarget: String
        get() = prefs.getString(KEY_FIREBASE_DB_TARGET, FIREBASE_TARGET_RTDB) ?: FIREBASE_TARGET_RTDB
        set(value) = prefs.edit().putString(KEY_FIREBASE_DB_TARGET, value).apply()

    var adminTemplatesJson: String
        get() = prefs.getString(KEY_ADMIN_TEMPLATES_JSON, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ADMIN_TEMPLATES_JSON, value).apply()

    var lastReminderDate: String
        get() = prefs.getString(KEY_LAST_REMINDER_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_REMINDER_DATE, value).apply()

    var dadosVersion: Int
        get() = prefs.getInt(KEY_DADOS_VERSION, 1)
        set(value) = prefs.edit().putInt(KEY_DADOS_VERSION, value).apply()

    var ultimaAtualizacaoDados: String
        get() = prefs.getString(KEY_ULTIMA_ATUALIZACAO_DADOS, "2026-06-29T00:00:00Z") ?: "2026-06-29T00:00:00Z"
        set(value) = prefs.edit().putString(KEY_ULTIMA_ATUALIZACAO_DADOS, value).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, 30)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL_MINUTES, value).apply()

    var backgroundSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, value).apply()

    var maxRefundDays: Int
        get() = prefs.getInt(KEY_MAX_REFUND_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_MAX_REFUND_DAYS, value).apply()

    var siteUrl: String
        get() = prefs.getString(KEY_SITE_URL, "https://SEU_SITE_RENDER") ?: "https://SEU_SITE_RENDER"
        set(value) = prefs.edit().putString(KEY_SITE_URL, value).apply()

    var saldoMinimo: Double
        get() = prefs.getFloat(KEY_SALDO_MINIMO, 1000.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_SALDO_MINIMO, value.toFloat()).apply()

    var valorMinimoAtivacao: Double
        get() = prefs.getFloat(KEY_VALOR_MINIMO_ATIVACAO, 1000.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_VALOR_MINIMO_ATIVACAO, value.toFloat()).apply()

    var validadeMeses: Int
        get() = prefs.getInt(KEY_VALIDADE_MESES, 12)
        set(value) = prefs.edit().putInt(KEY_VALIDADE_MESES, value).apply()

    var syncMode: String
        get() = prefs.getString(KEY_SYNC_MODE, MODE_GITHUB) ?: MODE_GITHUB
        set(value) = prefs.edit().putString(KEY_SYNC_MODE, value).apply()

    var githubToken: String
        get() = securePrefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_GITHUB_TOKEN, value).apply()

    var githubRepo: String
        get() = prefs.getString(KEY_GITHUB_REPO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_REPO, value).apply()

    var githubBranch: String
        get() = prefs.getString(KEY_GITHUB_BRANCH, "main") ?: "main"
        set(value) = prefs.edit().putString(KEY_GITHUB_BRANCH, value).apply()

    var githubPath: String
        get() = prefs.getString(KEY_GITHUB_PATH, "usuarios") ?: "usuarios"
        set(value) = prefs.edit().putString(KEY_GITHUB_PATH, value).apply()

    var fastApiUrl: String
        get() = prefs.getString(KEY_FASTAPI_URL, "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"
        set(value) = prefs.edit().putString(KEY_FASTAPI_URL, value).apply()

    var fastApiToken: String
        get() = securePrefs.getString(KEY_FASTAPI_TOKEN, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_FASTAPI_TOKEN, value).apply()

    var autoSendSms: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND_SMS, value).apply()

    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var filterOfficialSenders: Boolean
        get() = prefs.getBoolean(KEY_FILTER_OFFICIAL_SENDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_OFFICIAL_SENDERS, value).apply()

    var officialSendersList: String
        get() = prefs.getString(KEY_OFFICIAL_SENDERS, "M-Pesa, e-Mola, mpesa, emola") ?: "M-Pesa, e-Mola, mpesa, emola"
        set(value) = prefs.edit().putString(KEY_OFFICIAL_SENDERS, value).apply()

    // Highly robust default regex pattern matching the bank transaction SMS
    // ID da transacao: PP260616.0500.S17516. Recebeste 1,250.00MT de conta 876971842, nome: NICOLAU AFONSO MAGUMANE DADO as 05:00:50 de 16/06/2026. Conteudo: 1250. O saldo da tua conta e 1,253.00MT.
    var customRegex: String
        get() = prefs.getString(KEY_CUSTOM_REGEX, """(?i)ID da transacao:\s*([^\s\.]+)\.?\s+Recebeste\s+([0-9.,]+)\s*(?:MT)?\s+de\s+conta\s+(\d+),\s*nome:\s*(.*?)\s+as\s+([0-9:]+)\s+de\s+([0-9/]+)\..*?O saldo da tua conta e\s+([0-9.,]+)\s*(?:MT)?""") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_REGEX, value).apply()
}
