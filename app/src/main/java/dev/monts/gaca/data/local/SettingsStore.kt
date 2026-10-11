package dev.monts.gaca.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.monts.gaca.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gaca_settings")
private val KEY_BASE_URL = stringPreferencesKey("base_url")

/**
 * Which server the app talks to — null until someone has set one up. Public builds ship with
 * none; a developer can bake one in with DEFAULT_BASE_URL in local.properties (see build.gradle.kts).
 */
class SettingsStore(private val context: Context) {

    val baseUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL.ifBlank { null }
    }

    /** Expects the normalized form (ends in `/api/`) — see util/ServerAddress.kt. */
    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url }
    }

    /** Back to "no server": the setup screen shows. A baked-in developer default still applies. */
    suspend fun clearBaseUrl() {
        context.dataStore.edit { it.remove(KEY_BASE_URL) }
    }
}
