package dev.monts.gaca.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Matches gaca-planning/alerts/api-contract.md. Base URL always ends in
 * `/api/` (see [dev.monts.gaca.data.local.SettingsStore]), so every path here
 * is relative to that and carries no leading slash.
 */
interface GacaApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenResponse

    @POST("auth/login")
    suspend fun login(@Body body: UserLoginRequest): TokenResponse

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): TokenResponse

    @GET("auth/me")
    suspend fun me(): UserResponse

    @GET("auth/agents")
    suspend fun getAgents(): AgentsDto

    @PUT("auth/agent-host")
    suspend fun setAgentHost(@Body body: AgentHostUpdateRequest): AgentsDto

    @POST("auth/agents")
    suspend fun addAgent(@Body body: AgentCreateRequest): AgentsDto

    @PUT("auth/agents/{name}")
    suspend fun updateAgent(@Path("name") name: String, @Body body: AgentUpdateRequest): AgentsDto

    @DELETE("auth/agents/{name}")
    suspend fun removeAgent(@Path("name") name: String): AgentsDto

    @POST("auth/agents/{name}/default")
    suspend fun setDefaultAgent(@Path("name") name: String): AgentsDto

    @POST("alerts")
    suspend fun createAlert(@Body body: AlertCreateRequest): AlertDto

    @GET("alerts")
    suspend fun listAlerts(
        @Query("status") statuses: List<String>? = null,
        @Query("severity") severities: List<String>? = null,
        @Query("agent") agents: List<String>? = null,
        @Query("limit") limit: Int = 50,
    ): AlertListDto

    @GET("alerts/{id}")
    suspend fun getAlert(@Path("id") id: String): AlertDto

    @POST("alerts/{id}/act")
    suspend fun actOnAlert(@Path("id") id: String, @Body body: AlertResolveRequest): AlertDto

    @POST("alerts/{id}/dismiss")
    suspend fun dismissAlert(@Path("id") id: String, @Body body: AlertResolveRequest): AlertDto

    @POST("alerts/{id}/snooze")
    suspend fun snoozeAlert(@Path("id") id: String, @Body body: AlertSnoozeRequest): AlertDto

    @POST("alerts/{id}/delegate")
    suspend fun delegateAlert(@Path("id") id: String, @Body body: AlertDelegateRequest): AlertDto

    @GET("sessions/{id}")
    suspend fun getSession(@Path("id") id: String): SessionDetailDto

    @POST("push/register")
    suspend fun registerPushToken(@Body body: PushTokenRegisterRequest)

    @POST("push/unregister")
    suspend fun unregisterPushToken(@Body body: PushTokenUnregisterRequest)
}
