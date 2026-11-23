package dev.monts.gaca.di

import android.content.Context
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.remote.AuthInterceptor
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.SseClient
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.ArtifactRepository
import dev.monts.gaca.data.repo.CloudLinkRepository
import dev.monts.gaca.data.repo.HomeRepository
import dev.monts.gaca.data.repo.JoinRepository
import dev.monts.gaca.data.repo.PublishRepository
import dev.monts.gaca.data.repo.ShareRepository
import dev.monts.gaca.data.repo.TemplatesRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.BriefingRepository
import dev.monts.gaca.data.repo.CrewProfileRepository
import dev.monts.gaca.data.repo.ImageRepository
import dev.monts.gaca.data.repo.PushRepository
import dev.monts.gaca.data.repo.RemindersRepository
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.data.repo.AutomationsRepository
import dev.monts.gaca.data.repo.HeartbeatRepository
import dev.monts.gaca.data.repo.SkillsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual DI. The base URL is read once per container, so pointing the app at a different
 * server means building a new container (GacaApp.reconfigure) rather than rebuilding
 * Retrofit live. [configured] is false until a server has been set up.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)
    val settingsStore = SettingsStore(appContext)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val savedBaseUrl: String? = runBlocking { settingsStore.baseUrl.first() }
    val configured: Boolean = savedBaseUrl != null

    /** The server this app talks to, as typed by a person (no scheme path), or "" if none. */
    val serverAddress: String = savedBaseUrl?.removeSuffix("/api/").orEmpty()

    // Unconfigured, nothing calls the API (the setup screen has its own client), but Retrofit
    // wants some URL to build with.
    private val baseUrl: String = savedBaseUrl ?: "http://localhost/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private fun retrofitFor(client: OkHttpClient): GacaApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GacaApi::class.java)

    val api: GacaApi = retrofitFor(okHttpClient)

    /** For the few calls that block on the server while it works (moving a page to the cloud uploads it, then checks
     * the copy). The normal client gives up after 10 seconds of silence. */
    private val longApi: GacaApi = retrofitFor(
        okHttpClient.newBuilder().readTimeout(5, TimeUnit.MINUTES).callTimeout(6, TimeUnit.MINUTES).build(),
    )

    // Same client, same base URL as [api] above — Retrofit doesn't stream, so
    // the one SSE endpoint (session events) goes over OkHttp directly instead.
    private val sseClient = SseClient(okHttpClient, baseUrl)

    val authRepository = AuthRepository(api, tokenStore)
    val alertsRepository = AlertsRepository(api)
    val pushRepository = PushRepository(api)
    val remindersRepository = RemindersRepository(api)
    private val imageRepository = ImageRepository(okHttpClient, baseUrl)
    val sessionsRepository = SessionsRepository(api, sseClient, imageRepository)
    val skillsRepository = SkillsRepository(api)
    val automationsRepository = AutomationsRepository(api)
    val heartbeatRepository = HeartbeatRepository(api)
    val briefingRepository = BriefingRepository(api)
    val crewProfileRepository = CrewProfileRepository(api)
    val artifactRepository = ArtifactRepository(api, okHttpClient, baseUrl)
    val cloudLinkRepository = CloudLinkRepository(api)
    val templatesRepository = TemplatesRepository(api)
    val shareRepository = ShareRepository(api, longApi)
    val joinRepository = JoinRepository(api)
    val publishRepository = PublishRepository(api)
    val homeRepository = HomeRepository(api, longApi)
}
