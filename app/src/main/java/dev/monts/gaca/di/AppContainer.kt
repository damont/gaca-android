package dev.monts.gaca.di

import android.content.Context
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.remote.AuthInterceptor
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.SseClient
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.BriefingRepository
import dev.monts.gaca.data.repo.ImageRepository
import dev.monts.gaca.data.repo.PushRepository
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.data.repo.SkillsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual DI, built once at process start. The base URL is read once here —
 * changing it in Settings takes effect on next app restart rather than
 * rebuilding Retrofit live, which is a deliberate simplification (see
 * SettingsScreen).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)
    val settingsStore = SettingsStore(appContext)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val baseUrl: String = runBlocking { settingsStore.baseUrl.first() }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val api: GacaApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GacaApi::class.java)

    // Same client, same base URL as [api] above — Retrofit doesn't stream, so
    // the one SSE endpoint (session events) goes over OkHttp directly instead.
    private val sseClient = SseClient(okHttpClient, baseUrl)

    val authRepository = AuthRepository(api, tokenStore)
    val alertsRepository = AlertsRepository(api)
    val pushRepository = PushRepository(api)
    private val imageRepository = ImageRepository(okHttpClient, baseUrl)
    val sessionsRepository = SessionsRepository(api, sseClient, imageRepository)
    val skillsRepository = SkillsRepository(api)
    val briefingRepository = BriefingRepository(api)
}
