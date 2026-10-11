package dev.monts.gaca.data.repo

import dev.monts.gaca.data.local.TokenStore
import dev.monts.gaca.data.remote.AgentStatusDto
import dev.monts.gaca.data.remote.CrewDto
import dev.monts.gaca.data.remote.CrewMemberCreateRequest
import dev.monts.gaca.data.remote.CrewMemberUpdateRequest
import dev.monts.gaca.data.remote.GacaApi
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class User(val id: String, val name: String, val email: String)

/** `color` is a hex string, always present, auto-assigned server-side from a fixed
 * palette unless the caller supplies one — api-contract.md §2. Render it as-is
 * everywhere this crew member shows up: avatar, chat accent, filter chips, its own
 * message bubbles, a settings-row border. */
data class CrewMember(val name: String, val port: Int, val color: String, val isDefault: Boolean)

data class CrewConfig(
    val host: String?,
    val crew: List<CrewMember>,
    /** True once gaca-api already has SSH access to the box (granted via `access` on
     * some earlier `POST /crew`/`POST /crew/discover` call) — a client can use this to
     * know its "add crew member" form only needs a name, not `access` too. Not yet
     * surfaced in the Add-crew-member sheet; that form has always just asked for a
     * name and let a real `CrewNotConfigured` error explain when that's not enough. */
    val canCreate: Boolean,
)

private fun CrewDto.toDomain(): CrewConfig =
    CrewConfig(host, crew.map { CrewMember(it.name, it.port, it.color, it.isDefault) }, canCreate)

/** GET /agent — a real, dedicated "is an agent connected" record, independent of the
 * crew list (not inferred from it, e.g. via a crew member's `isDefault` flag). */
data class AgentStatus(val connected: Boolean, val host: String?)

private fun AgentStatusDto.toDomain(): AgentStatus = AgentStatus(connected, host)

data class AuthUiState(
    val user: User? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Whether this phone holds a working credential for its server, and the crew calls that need one. */
class AuthRepository(
    private val api: GacaApi,
    private val tokenStore: TokenStore,
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /**
     * Called once at app start: a stored credential is only a claim, so ask the API who it belongs
     * to. Only a definite "no" (401: this device was unpaired) drops it. A server that is merely
     * unreachable or having a bad moment must not unpair the phone, so then the app opens anyway
     * and each screen shows its own error.
     */
    suspend fun restoreSession() {
        if (tokenStore.getTokenSync() == null) {
            _state.value = AuthUiState(user = null, loading = false)
            return
        }
        try {
            val me = api.me()
            _state.value = AuthUiState(user = User(me.id, me.name, me.email), loading = false)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                tokenStore.clear()
                _state.value = AuthUiState(user = null, loading = false)
            } else {
                _state.value = AuthUiState(user = User("", "", ""), loading = false)
            }
        } catch (e: Exception) {
            _state.value = AuthUiState(user = User("", "", ""), loading = false)
        }
    }

    /** Tell the server this phone is leaving (so its credential is revoked), then forget it here. */
    suspend fun disconnect() {
        runCatching { api.unpairThisDevice() } // best effort: the server may be gone
        logout()
    }

    suspend fun logout() {
        tokenStore.clear()
        _state.value = AuthUiState(user = null, loading = false)
    }

    /** This account's shared host + every crew member configured under it — see api-contract.md §2, §10.
     * The host itself is owned by the /agent resource now (connect/move), not managed here — this
     * account already has an agent connected, so that step isn't built on Android yet. */
    suspend fun getCrew(): CrewConfig = api.getCrew().toDomain()

    /** Is this account connected to an agent yet, and where — a real record, not a guess
     * derived from the crew list. Connecting one (POST /agent/connect) isn't built here yet. */
    suspend fun getAgentStatus(): AgentStatus = api.getAgentStatus().toDomain()

    /** The first crew member added becomes the default automatically. `color` is
     * optional — omit it (null) to let the server auto-assign one. `port`/`apiKey`
     * register a profile already set up by hand; omit both and gaca-api provisions
     * a brand new one on the box directly (needs SSH access — an optional `access`
     * field on this same call, remembered server-side after the first time it's
     * given; `CrewConfig.canCreate` says whether that's already true for this
     * account, so this app never has to collect or send it itself). */
    suspend fun addCrewMember(name: String, port: Int? = null, apiKey: String? = null, color: String? = null): CrewConfig =
        api.addCrewMember(CrewMemberCreateRequest(name, port, apiKey, color)).toDomain()

    /** Both fields required together — there's no partial update on this endpoint. */
    suspend fun updateCrewMember(name: String, port: Int, apiKey: String): CrewConfig =
        api.updateCrewMember(name, CrewMemberUpdateRequest(port, apiKey)).toDomain()

    /** Rejected by the API if `name` is the default and other crew members remain. */
    suspend fun removeCrewMember(name: String): CrewConfig = api.removeCrewMember(name).toDomain()

    suspend fun setDefaultCrewMember(name: String): CrewConfig = api.setDefaultCrewMember(name).toDomain()
}
