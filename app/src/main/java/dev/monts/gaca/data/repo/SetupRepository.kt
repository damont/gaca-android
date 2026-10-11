package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.ClaimRequestDto
import dev.monts.gaca.data.remote.SetupApi
import dev.monts.gaca.data.remote.SetupStatusDto
import dev.monts.gaca.data.remote.UserLoginRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Talks to a server the app is not configured for yet, so it has no session and no saved
 * address: every call names the base URL (ending in `/api/`) it is aimed at.
 */
class SetupRepository {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun api(baseUrl: String): SetupApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SetupApi::class.java)

    suspend fun status(baseUrl: String): SetupStatusDto = api(baseUrl).status()

    /** Returns the new session token. */
    suspend fun claim(baseUrl: String, code: String, name: String, email: String, password: String): String =
        api(baseUrl).claim(ClaimRequestDto(code, name, email, password)).accessToken

    suspend fun signIn(baseUrl: String, email: String, password: String): String =
        api(baseUrl).login(UserLoginRequest(email, password)).accessToken
}
