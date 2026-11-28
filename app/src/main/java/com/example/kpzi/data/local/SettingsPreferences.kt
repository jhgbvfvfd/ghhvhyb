package com.example.kpzi.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kpzi.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kpziDataStore by preferencesDataStore(name = "kpzi_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.THAI,
    val safModsUri: String? = null,
    val safSavesUri: String? = null,
    val safLogUri: String? = null,
    val preferredSmapiVersion: String = "4.1.10-android",
    val autoTranslateDescriptions: Boolean = false,
    val totalPlaytimeSeconds: Long = 0L,
    val activePlaytimeStartMs: Long? = null,
    val totalLaunchCount: Int = 0,
    val lastSessionSeconds: Long = 0L,
    val longestSessionSeconds: Long = 0L,
    val lastPlayedEpochMs: Long? = null,
)

data class StoppedPlaySession(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSeconds: Long,
    val activeModCount: Int,
    val launchMode: String,
    val newTotalSeconds: Long,
)

class SettingsPreferences(private val context: Context) {

    val settingsFlow: Flow<UserSettings> = context.kpziDataStore.data.map { prefs ->
        val themeStr = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        val langStr = prefs[KEY_APP_LANGUAGE] ?: AppLanguage.THAI.name
        val language = runCatching { AppLanguage.valueOf(langStr) }.getOrDefault(AppLanguage.THAI)
        val activeStart = prefs[KEY_ACTIVE_PLAYTIME_START_MS]?.takeIf { it > 0L }
        UserSettings(
            themeMode = theme,
            appLanguage = language,
            safModsUri = prefs[KEY_SAF_MODS_URI],
            safSavesUri = prefs[KEY_SAF_SAVES_URI],
            safLogUri = prefs[KEY_SAF_LOG_URI],
            preferredSmapiVersion = prefs[KEY_PREFERRED_SMAPI_VERSION] ?: "4.1.10-android",
            autoTranslateDescriptions = prefs[KEY_AUTO_TRANSLATE] ?: false,
            totalPlaytimeSeconds = prefs[KEY_TOTAL_PLAYTIME_SECONDS] ?: 0L,
            activePlaytimeStartMs = activeStart,
            totalLaunchCount = prefs[KEY_TOTAL_LAUNCH_COUNT] ?: 0,
            lastSessionSeconds = prefs[KEY_LAST_SESSION_SECONDS] ?: 0L,
            longestSessionSeconds = prefs[KEY_LONGEST_SESSION_SECONDS] ?: 0L,
            lastPlayedEpochMs = prefs[KEY_LAST_PLAYED_EPOCH_MS]?.takeIf { it > 0L },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.kpziDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.kpziDataStore.edit { it[KEY_APP_LANGUAGE] = language.name }
    }

    suspend fun setSafModsUri(uri: String?) {
        context.kpziDataStore.edit {
            if (uri == null) it.remove(KEY_SAF_MODS_URI) else it[KEY_SAF_MODS_URI] = uri
        }
    }

    suspend fun setSafSavesUri(uri: String?) {
        context.kpziDataStore.edit {
            if (uri == null) it.remove(KEY_SAF_SAVES_URI) else it[KEY_SAF_SAVES_URI] = uri
        }
    }

    suspend fun setSafLogUri(uri: String?) {
        context.kpziDataStore.edit {
            if (uri == null) it.remove(KEY_SAF_LOG_URI) else it[KEY_SAF_LOG_URI] = uri
        }
    }

    suspend fun setPreferredSmapiVersion(version: String) {
        context.kpziDataStore.edit { it[KEY_PREFERRED_SMAPI_VERSION] = version }
    }

    suspend fun setAutoTranslateDescriptions(enabled: Boolean) {
        context.kpziDataStore.edit { it[KEY_AUTO_TRANSLATE] = enabled }
    }

    suspend fun startPlaytimeSession(
        startEpochMs: Long = System.currentTimeMillis(),
        activeModCount: Int = 0,
        launchMode: String = "Stardew Valley",
    ): Long {
        var resolvedStart = startEpochMs
        context.kpziDataStore.edit { prefs ->
            val existing = prefs[KEY_ACTIVE_PLAYTIME_START_MS]
            if (existing != null && existing > 0L) {
                resolvedStart = existing
            } else {
                prefs[KEY_ACTIVE_PLAYTIME_START_MS] = startEpochMs
                prefs[KEY_ACTIVE_LAUNCH_MODE] = launchMode
                prefs[KEY_ACTIVE_MOD_COUNT] = activeModCount
                prefs[KEY_TOTAL_LAUNCH_COUNT] = (prefs[KEY_TOTAL_LAUNCH_COUNT] ?: 0) + 1
                prefs[KEY_LAST_PLAYED_EPOCH_MS] = startEpochMs
            }
        }
        return resolvedStart
    }

    suspend fun stopPlaytimeSession(nowEpochMs: Long = System.currentTimeMillis()): StoppedPlaySession? {
        var stopped: StoppedPlaySession? = null
        context.kpziDataStore.edit { prefs ->
            val existingTotal = prefs[KEY_TOTAL_PLAYTIME_SECONDS] ?: 0L
            val activeStart = prefs[KEY_ACTIVE_PLAYTIME_START_MS]
            if (activeStart != null && activeStart > 0L) {
                val deltaSeconds = ((nowEpochMs - activeStart).coerceAtLeast(0L)) / 1000L
                val newTotal = existingTotal + deltaSeconds
                val prevLongest = prefs[KEY_LONGEST_SESSION_SECONDS] ?: 0L
                val modCount = prefs[KEY_ACTIVE_MOD_COUNT] ?: 0
                val mode = prefs[KEY_ACTIVE_LAUNCH_MODE] ?: "Stardew Valley"

                prefs[KEY_TOTAL_PLAYTIME_SECONDS] = newTotal
                prefs[KEY_LAST_SESSION_SECONDS] = deltaSeconds
                prefs[KEY_LONGEST_SESSION_SECONDS] = maxOf(prevLongest, deltaSeconds)
                prefs[KEY_LAST_PLAYED_EPOCH_MS] = nowEpochMs
                prefs.remove(KEY_ACTIVE_PLAYTIME_START_MS)
                prefs.remove(KEY_ACTIVE_LAUNCH_MODE)
                prefs.remove(KEY_ACTIVE_MOD_COUNT)

                stopped = StoppedPlaySession(
                    startedAtMs = activeStart,
                    endedAtMs = nowEpochMs,
                    durationSeconds = deltaSeconds,
                    activeModCount = modCount,
                    launchMode = mode,
                    newTotalSeconds = newTotal,
                )
            }
        }
        return stopped
    }

    suspend fun resetPlayStats() {
        context.kpziDataStore.edit { prefs ->
            prefs.remove(KEY_TOTAL_PLAYTIME_SECONDS)
            prefs.remove(KEY_ACTIVE_PLAYTIME_START_MS)
            prefs.remove(KEY_ACTIVE_LAUNCH_MODE)
            prefs.remove(KEY_ACTIVE_MOD_COUNT)
            prefs.remove(KEY_TOTAL_LAUNCH_COUNT)
            prefs.remove(KEY_LAST_SESSION_SECONDS)
            prefs.remove(KEY_LONGEST_SESSION_SECONDS)
            prefs.remove(KEY_LAST_PLAYED_EPOCH_MS)
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_SAF_MODS_URI = stringPreferencesKey("saf_mods_uri")
        val KEY_SAF_SAVES_URI = stringPreferencesKey("saf_saves_uri")
        val KEY_SAF_LOG_URI = stringPreferencesKey("saf_log_uri")
        val KEY_PREFERRED_SMAPI_VERSION = stringPreferencesKey("preferred_smapi_version")
        val KEY_AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        val KEY_TOTAL_PLAYTIME_SECONDS = longPreferencesKey("total_playtime_seconds")
        val KEY_ACTIVE_PLAYTIME_START_MS = longPreferencesKey("active_playtime_start_ms")
        val KEY_ACTIVE_LAUNCH_MODE = stringPreferencesKey("active_launch_mode")
        val KEY_ACTIVE_MOD_COUNT = intPreferencesKey("active_mod_count")
        val KEY_TOTAL_LAUNCH_COUNT = intPreferencesKey("total_launch_count")
        val KEY_LAST_SESSION_SECONDS = longPreferencesKey("last_session_seconds")
        val KEY_LONGEST_SESSION_SECONDS = longPreferencesKey("longest_session_seconds")
        val KEY_LAST_PLAYED_EPOCH_MS = longPreferencesKey("last_played_epoch_ms")
    }
}
