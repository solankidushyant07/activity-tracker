
package com.coconutshell.activitytracker.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "activity_tracker_settings")

data class ReminderSettings(
    val morningEnabled: Boolean = false,
    val morningTime: String = "08:00",
    val nightEnabled: Boolean = false,
    val nightTime: String = "21:00"
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningTime = stringPreferencesKey("morning_time")
        val nightEnabled = booleanPreferencesKey("night_enabled")
        val nightTime = stringPreferencesKey("night_time")
    }

    val settings: Flow<ReminderSettings> = context.settingsDataStore.data.map { p ->
        ReminderSettings(
            morningEnabled = p[Keys.morningEnabled] ?: false,
            morningTime = p[Keys.morningTime] ?: "08:00",
            nightEnabled = p[Keys.nightEnabled] ?: false,
            nightTime = p[Keys.nightTime] ?: "21:00"
        )
    }

    suspend fun setMorningEnabled(value: Boolean) = context.settingsDataStore.edit { it[Keys.morningEnabled] = value }
    suspend fun setMorningTime(value: String) = context.settingsDataStore.edit { it[Keys.morningTime] = value }
    suspend fun setNightEnabled(value: Boolean) = context.settingsDataStore.edit { it[Keys.nightEnabled] = value }
    suspend fun setNightTime(value: String) = context.settingsDataStore.edit { it[Keys.nightTime] = value }
}
