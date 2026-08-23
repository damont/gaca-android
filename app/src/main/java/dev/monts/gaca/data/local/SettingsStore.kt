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
 * Just the API base URL for now. Production defaults to the Cloudflare
 * Tunnel domain (BuildConfig.DEFAULT_BASE_URL); overriding it here is what
 * lets the app point at a dev box on the LAN instead — see SettingsScreen.
 */
class SettingsStore(private val context: Context) {

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { it[KEY_BASE_URL] = normalized }
    }
}
