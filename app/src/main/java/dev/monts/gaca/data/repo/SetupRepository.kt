package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.PairRequestDto
import dev.monts.gaca.data.remote.SetupApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Talks to a server this phone is not paired with yet, so it has no credential and no saved
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

    /** Throws if nothing that speaks GACA answers there. */
    suspend fun check(baseUrl: String) {
        api(baseUrl).status()
    }

    /** Trades the one-time code `gaca-api pair` printed for this device's credential. */
    suspend fun pair(baseUrl: String, code: String, deviceName: String): String =
        api(baseUrl).pair(PairRequestDto(code, deviceName)).accessToken
}
