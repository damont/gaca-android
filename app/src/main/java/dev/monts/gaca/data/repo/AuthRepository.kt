package dev.monts.gaca.data.repo

import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.remote.AgentCreateRequest
import dev.monts.gaca.data.remote.AgentHostUpdateRequest
import dev.monts.gaca.data.remote.AgentUpdateRequest
import dev.monts.gaca.data.remote.AgentsDto
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.GoogleLoginRequest
import dev.monts.gaca.data.remote.RegisterRequest
import dev.monts.gaca.data.remote.UserLoginRequest
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class User(val id: String, val name: String, val email: String)

data class AgentProfile(val name: String, val port: Int, val isDefault: Boolean)

data class AgentsConfig(val host: String?, val agents: List<AgentProfile>)

private fun AgentsDto.toDomain(): AgentsConfig =
    AgentsConfig(host, agents.map { AgentProfile(it.name, it.port, it.isDefault) })

data class AuthUiState(
    val user: User? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Session state + the four ways to establish one (api-contract.md §1: the app
 * uses /api/auth/login and /api/auth/google; register exists for completeness).
 * Mirrors frontend/src/context/AuthContext.tsx's shape deliberately, since
 * that's the reference the API was validated against.
 */
class AuthRepository(
    private val api: GacaApi,
    private val tokenStore: TokenStore,
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Called once at app start: a stored token is only a claim, so ask the API who it belongs to. */
    suspend fun restoreSession() {
        if (tokenStore.getTokenSync() == null) {
            _state.value = AuthUiState(user = null, loading = false)
            return
        }
        try {
            val me = api.me()
            _state.value = AuthUiState(user = User(me.id, me.name, me.email), loading = false)
        } catch (e: Exception) {
            tokenStore.clear()
            _state.value = AuthUiState(user = null, loading = false)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        _state.value = _state.value.copy(error = null)
        val token = api.login(UserLoginRequest(email, password))
        tokenStore.saveToken(token.accessToken)
        val me = api.me()
        _state.value = AuthUiState(user = User(me.id, me.name, me.email), loading = false)
    }.onFailure { e -> _state.value = _state.value.copy(error = e.friendlyMessage()) }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runCatching {
        _state.value = _state.value.copy(error = null)
        val token = api.register(RegisterRequest(name, email, password))
        tokenStore.saveToken(token.accessToken)
        val me = api.me()
        _state.value = AuthUiState(user = User(me.id, me.name, me.email), loading = false)
    }.onFailure { e -> _state.value = _state.value.copy(error = e.friendlyMessage()) }

    suspend fun googleLogin(idToken: String): Result<Unit> = runCatching {
        _state.value = _state.value.copy(error = null)
        val token = api.googleLogin(GoogleLoginRequest(idToken))
        tokenStore.saveToken(token.accessToken)
        val me = api.me()
        _state.value = AuthUiState(user = User(me.id, me.name, me.email), loading = false)
    }.onFailure { e -> _state.value = _state.value.copy(error = e.friendlyMessage()) }

    suspend fun logout() {
        tokenStore.clear()
        _state.value = AuthUiState(user = null, loading = false)
    }

    /**
     * Clears a leftover error before starting a Google sign-in attempt.
     * Needed specifically because that flow can fail (or be cancelled) in the
     * Credential Manager step, before ever reaching [googleLogin] — which is
     * the only place that would otherwise clear a stale error from an earlier
     * failed email/password attempt.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** This account's shared host + every agent configured under it — see api-contract.md §6, §10. */
    suspend fun getAgents(): AgentsConfig = api.getAgents().toDomain()

    suspend fun setAgentHost(host: String): AgentsConfig = api.setAgentHost(AgentHostUpdateRequest(host)).toDomain()

    /** The first agent added becomes the default automatically. */
    suspend fun addAgent(name: String, port: Int, apiKey: String): AgentsConfig =
        api.addAgent(AgentCreateRequest(name, port, apiKey)).toDomain()

    /** Both fields required together — there's no partial update on this endpoint. */
    suspend fun updateAgent(name: String, port: Int, apiKey: String): AgentsConfig =
        api.updateAgent(name, AgentUpdateRequest(port, apiKey)).toDomain()

    /** Rejected by the API if `name` is the default and other agents remain. */
    suspend fun removeAgent(name: String): AgentsConfig = api.removeAgent(name).toDomain()

    suspend fun setDefaultAgent(name: String): AgentsConfig = api.setDefaultAgent(name).toDomain()
}
