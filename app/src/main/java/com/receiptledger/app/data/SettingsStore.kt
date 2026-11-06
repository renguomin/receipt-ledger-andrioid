package com.receiptledger.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.receiptledger.engine.model.Country
import com.receiptledger.engine.model.Purpose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val country: Country = Country.CA,
    val currency: String = "CAD",
    val geminiApiKey: String = "",
    val geminiModel: String = "",
    val defaultPurpose: Purpose = Purpose.PERSONAL,
)

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            country = prefs[Keys.country]?.let { runCatching { Country.valueOf(it) }.getOrNull() }
                ?: Country.CA,
            currency = prefs[Keys.currency] ?: "CAD",
            geminiApiKey = prefs[Keys.geminiApiKey] ?: "",
            geminiModel = prefs[Keys.geminiModel] ?: "",
            defaultPurpose = prefs[Keys.defaultPurpose]?.let {
                runCatching { Purpose.valueOf(it) }.getOrNull()
            } ?: Purpose.PERSONAL,
        )
    }

    val seenAddNewHint: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.seenAddNewHint] ?: false
    }

    /** Null until the first successful backup. */
    val lastBackupAt: Flow<Long?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.lastBackupAt]?.takeIf { it > 0 }
    }

    val dailyBackupEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.dailyBackupEnabled] ?: false
    }

    /** The backup file chosen in the system picker, so later backups need no picker at all. */
    val backupDocumentUri: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.backupDocumentUri]?.takeIf { it.isNotBlank() }
    }

    suspend fun markAddNewHintSeen() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.seenAddNewHint] = true
        }
    }

    suspend fun setLastBackupAt(atMs: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastBackupAt] = atMs
        }
    }

    suspend fun setDailyBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.dailyBackupEnabled] = enabled
        }
    }

    suspend fun setBackupDocumentUri(uri: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.backupDocumentUri] = uri
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                country = prefs[Keys.country]?.let { runCatching { Country.valueOf(it) }.getOrNull() }
                    ?: Country.CA,
                currency = prefs[Keys.currency] ?: "CAD",
                 geminiApiKey = prefs[Keys.geminiApiKey] ?: "",
                geminiModel = prefs[Keys.geminiModel] ?: "",
                defaultPurpose = prefs[Keys.defaultPurpose]?.let {
                    runCatching { Purpose.valueOf(it) }.getOrNull()
                } ?: Purpose.PERSONAL,
            )
            val next = transform(current)
            prefs[Keys.country] = next.country.name
            prefs[Keys.currency] = next.currency
            prefs[Keys.geminiApiKey] = next.geminiApiKey
            prefs[Keys.geminiModel] = next.geminiModel
            prefs[Keys.defaultPurpose] = next.defaultPurpose.name
            prefs.remove(stringPreferencesKey("business_name"))
            prefs.remove(booleanPreferencesKey("gemini_enabled"))
        }
    }

    private object Keys {
        val country = stringPreferencesKey("country")
        val currency = stringPreferencesKey("currency")
        val geminiApiKey = stringPreferencesKey("gemini_api_key")
        val geminiModel = stringPreferencesKey("gemini_model")
        val defaultPurpose = stringPreferencesKey("default_purpose")
        val seenAddNewHint = booleanPreferencesKey("seen_add_new_hint")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val backupDocumentUri = stringPreferencesKey("backup_document_uri")
        val dailyBackupEnabled = booleanPreferencesKey("daily_backup_enabled")
    }
}
