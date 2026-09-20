package dev.monts.gaca.data.repo

import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.remote.CrewDto
import dev.monts.gaca.data.remote.CrewMemberCreateRequest
import dev.monts.gaca.data.remote.CrewMemberUpdateRequest
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.GoogleLoginRequest
import dev.monts.gaca.data.remote.RegisterRequest
import dev.monts.gaca.data.remote.UserLoginRequest
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class User(val id: String, val name: String, val email: String)

/** `color` is a hex string, always present, auto-assigned server-side from a fixed
 * palette unless the caller supplies one — api-contract.md §2. Render it as-is
 * everywhere this crew member shows up: avatar, chat accent, filter chips, its own
 * message bubbles, a settings-row border. */
data class CrewMember(val name: String, val port: Int, val color: String, val isDefault: Boolean)

data class CrewConfig(val host: String?, val crew: List<CrewMember>)

private fun CrewDto.toDomain(): CrewConfig =
    CrewConfig(host, crew.map { CrewMember(it.name, it.port, it.color, it.isDefault) })

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

    /** This account's shared host + every crew member configured under it — see api-contract.md §2, §10.
     * The host itself is owned by the /agent resource now (connect/move), not managed here — this
     * account already has an agent connected, so that step isn't built on Android yet. */
    suspend fun getCrew(): CrewConfig = api.getCrew().toDomain()

    /** The first crew member added becomes the default automatically. `color` is
     * optional — omit it (null) to let the server auto-assign one. */
    suspend fun addCrewMember(name: String, port: Int, apiKey: String, color: String? = null): CrewConfig =
        api.addCrewMember(CrewMemberCreateRequest(name, port, apiKey, color)).toDomain()

    /** Both fields required together — there's no partial update on this endpoint. */
    suspend fun updateCrewMember(name: String, port: Int, apiKey: String): CrewConfig =
        api.updateCrewMember(name, CrewMemberUpdateRequest(port, apiKey)).toDomain()

    /** Rejected by the API if `name` is the default and other crew members remain. */
    suspend fun removeCrewMember(name: String): CrewConfig = api.removeCrewMember(name).toDomain()

    suspend fun setDefaultCrewMember(name: String): CrewConfig = api.setDefaultCrewMember(name).toDomain()
}
