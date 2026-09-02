package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = System, 1 = Light, 2 = Dark
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val APP_PIN = stringPreferencesKey("app_pin")
        val APP_TITLE = stringPreferencesKey("app_title")
        val BKASH_ENABLED = booleanPreferencesKey("bkash_enabled")
        val NAGAD_ENABLED = booleanPreferencesKey("nagad_enabled")
        val ROCKET_ENABLED = booleanPreferencesKey("rocket_enabled")
        val UPAY_ENABLED = booleanPreferencesKey("upay_enabled")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val MIN_AMOUNT = stringPreferencesKey("min_amount")
        val CUSTOM_HEADER = stringPreferencesKey("custom_header")
    }

    val webhookUrl: Flow<String> = context.dataStore.data.map { it[WEBHOOK_URL] ?: "https://yourdomain.com/api/payment-callback" }
    val apiToken: Flow<String> = context.dataStore.data.map { it[API_TOKEN] ?: "" }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[THEME_MODE] ?: 2 } // Default Dark Mode based on current design
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK] ?: false }
    val appPin: Flow<String> = context.dataStore.data.map { it[APP_PIN] ?: "" }
    val appTitle: Flow<String> = context.dataStore.data.map { it[APP_TITLE] ?: "MFS Automator" }
    
    val bkashEnabled: Flow<Boolean> = context.dataStore.data.map { it[BKASH_ENABLED] ?: true }
    val nagadEnabled: Flow<Boolean> = context.dataStore.data.map { it[NAGAD_ENABLED] ?: true }
    val rocketEnabled: Flow<Boolean> = context.dataStore.data.map { it[ROCKET_ENABLED] ?: true }
    val upayEnabled: Flow<Boolean> = context.dataStore.data.map { it[UPAY_ENABLED] ?: true }
    
    val deviceId: Flow<String> = context.dataStore.data.map { it[DEVICE_ID] ?: "" }
    val minAmount: Flow<String> = context.dataStore.data.map { it[MIN_AMOUNT] ?: "0" }
    val customHeader: Flow<String> = context.dataStore.data.map { it[CUSTOM_HEADER] ?: "" }

    suspend fun saveWebhookUrl(url: String) { context.dataStore.edit { it[WEBHOOK_URL] = url } }
    suspend fun saveApiToken(token: String) { context.dataStore.edit { it[API_TOKEN] = token } }
    suspend fun saveThemeMode(mode: Int) { context.dataStore.edit { it[THEME_MODE] = mode } }
    suspend fun saveAppLock(enabled: Boolean) { context.dataStore.edit { it[APP_LOCK] = enabled } }
    suspend fun saveAppPin(pin: String) { context.dataStore.edit { it[APP_PIN] = pin } }
    suspend fun saveAppTitle(title: String) { context.dataStore.edit { it[APP_TITLE] = title } }
    
    suspend fun saveDeviceId(id: String) { context.dataStore.edit { it[DEVICE_ID] = id } }
    suspend fun saveMinAmount(amount: String) { context.dataStore.edit { it[MIN_AMOUNT] = amount } }
    suspend fun saveCustomHeader(header: String) { context.dataStore.edit { it[CUSTOM_HEADER] = header } }
    
    suspend fun toggleProvider(provider: String, enabled: Boolean) {
        context.dataStore.edit {
            when (provider.lowercase()) {
                "bkash" -> it[BKASH_ENABLED] = enabled
                "nagad" -> it[NAGAD_ENABLED] = enabled
                "rocket" -> it[ROCKET_ENABLED] = enabled
                "upay" -> it[UPAY_ENABLED] = enabled
            }
        }
    }
}
