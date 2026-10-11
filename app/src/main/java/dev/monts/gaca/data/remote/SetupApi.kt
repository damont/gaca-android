package dev.monts.gaca.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** The few calls that work before there is a session: what the server is, claim it, sign in. */
interface SetupApi {
    @GET("setup")
    suspend fun status(): SetupStatusDto

    @POST("setup/claim")
    suspend fun claim(@Body body: ClaimRequestDto): TokenResponse

    @POST("auth/login")
    suspend fun login(@Body body: UserLoginRequest): TokenResponse
}

@Serializable
data class SetupStatusDto(
    val name: String = "gaca-api",
    val version: String = "",
    /** False only until the one owner exists. */
    val claimed: Boolean,
)

@Serializable
data class ClaimRequestDto(
    val code: String,
    val name: String,
    val email: String,
    val password: String,
)
