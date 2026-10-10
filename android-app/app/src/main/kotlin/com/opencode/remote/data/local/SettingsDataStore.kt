package com.opencode.remote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.opencode.remote.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val SETTINGS_KEY = stringPreferencesKey("app_settings")

/** 設定資料存取 */
class SettingsDataStore(private val dataStore: DataStore<Preferences>) {
    
    private val gson = Gson()
    
    /** 取得設定流 */
    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is java.io.IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            val json = prefs[SETTINGS_KEY] ?: ""
            if (json.isNotBlank()) {
                try {
                    gson.fromJson(json, AppSettings::class.java)
                } catch (e: Exception) {
                    AppSettings()
                }
            } else {
                AppSettings()
            }
        }
    
    /** 同步取得設定 */
    suspend fun getSettings(): AppSettings = settingsFlow.first()
    
    /** 儲存設定 */
    suspend fun saveSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = gson.toJson(settings)
        }
    }
    
    /** 更新特定欄位 */
    suspend fun updateSettings(block: AppSettings.() -> Unit) {
        val current = getSettings()
        current.block()
        saveSettings(current)
    }
}

/** 建立 DataStore */
fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    return context.preferencesDataStore(name = "opencode_settings")
}