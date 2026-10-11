package dev.monts.gaca.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** The calls that work before this phone has a credential: is it a GACA server, and pair with it. */
interface SetupApi {
    @GET("setup")
    suspend fun status(): SetupStatusDto

    @POST("devices/pair")
    suspend fun pair(@Body body: PairRequestDto): PairResponseDto
}

@Serializable
data class SetupStatusDto(
    val name: String = "gaca-api",
    val version: String = "",
)

@Serializable
data class PairRequestDto(
    val code: String,
    val name: String,
)

@Serializable
data class PairResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("device_id") val deviceId: String = "",
)
