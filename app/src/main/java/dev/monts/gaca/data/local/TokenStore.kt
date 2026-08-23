package dev.monts.gaca.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "gaca_secure_prefs"
private const val KEY_ACCESS_TOKEN = "access_token"

/**
 * Holds the session JWT. Per api-contract.md §1: no refresh tokens, a 401
 * means send the user back to login — there is nothing to rotate here, just
 * one value to keep encrypted at rest and clear on logout.
 */
class TokenStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Synchronous on purpose: called from [dev.monts.gaca.data.remote.AuthInterceptor] on OkHttp's own thread. */
    fun getTokenSync(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    suspend fun saveToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }
}
