package dev.monts.gaca.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.HeartbeatChange
import dev.monts.gaca.data.repo.HeartbeatRepository
import dev.monts.gaca.data.repo.HeartbeatSettings
import dev.monts.gaca.data.repo.nextMorning
import dev.monts.gaca.data.repo.pauseForHours
import dev.monts.gaca.data.repo.PushRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class SettingsUiState(
    val baseUrlInput: String = "",

    // "Agent" — api-contract.md §2: GET /agent is the real, dedicated record of
    // whether this account is connected and where. Connecting a new agent
    // (POST /agent/connect) isn't built here yet — see BUILD_NEXT.md.
    val agentLoading: Boolean = true,
    val agentConnected: Boolean = false,
    val agentHost: String? = null,

    // "Your crew" — named profiles under that agent.
    val crewLoading: Boolean = true,
    val crewError: String? = null,
    val crew: List<CrewMember> = emptyList(),
    /** Removing a member deletes its Hermes profile (gaca-api can reach the box), not just GACA's entry. */
    val crewCanCreate: Boolean = false,

    // At most one row's edit form is open at a time.
    val editingCrewName: String? = null,
    val editPortInput: String = "",
    val editApiKeyInput: String = "",
    val editSaving: Boolean = false,

    // Add-crew-member sheet — just a name (+ optional color override). gaca-api
    // now provisions the profile on the box directly (SSH access was granted out
    // of band and is remembered server-side, so the app never collects it) —
    // no port or API key to type in here anymore.
    val showAddCrewSheet: Boolean = false,
    val newCrewName: String = "",
    val newCrewColor: String? = null,
    val newCrewSaving: Boolean = false,

    // "Heartbeat" — the agent's periodic check-in: quiet hours, daily limits, on/off, pause.
    // `heartbeat` is null until the first load succeeds; each change shows at once and is
    // undone (with the server's reason) if the server refuses it.
    val heartbeatLoading: Boolean = true,
    val heartbeat: HeartbeatSettings? = null,
    val heartbeatError: String? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val pushRepository: PushRepository,
    private val heartbeatRepository: HeartbeatRepository,
    /** Rebuilds the app around whatever server is saved now (GacaApp.reconfigure). */
    private val onServerChanged: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.baseUrl.collect { url -> _uiState.update { it.copy(baseUrlInput = url.orEmpty()) } }
        }
        loadAgentStatus()
        loadCrew()
        loadHeartbeat()
    }

    // ── heartbeat ─────────────────────────────────────────────────────

    fun loadHeartbeat() {
        viewModelScope.launch {
            _uiState.update { it.copy(heartbeatLoading = true, heartbeatError = null) }
            try {
                val settings = heartbeatRepository.get()
                _uiState.update { it.copy(heartbeatLoading = false, heartbeat = settings) }
            } catch (e: Exception) {
                _uiState.update { it.copy(heartbeatLoading = false, heartbeatError = e.friendlyMessage()) }
            }
        }
    }

    /** Save one change. The control moves immediately; if the server says no (a 422 with its reason,
     * or no connection) it goes back to what it was and the reason is shown. */
    fun changeHeartbeat(change: HeartbeatChange) {
        val before = _uiState.value.heartbeat ?: return
        _uiState.update { it.copy(heartbeat = before.applying(change), heartbeatError = null) }
        viewModelScope.launch {
            try {
                val saved = heartbeatRepository.update(change, ZoneId.systemDefault())
                _uiState.update { it.copy(heartbeat = saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(heartbeat = before, heartbeatError = e.friendlyMessage()) }
            }
        }
    }

    fun setHeartbeatEnabled(enabled: Boolean) = changeHeartbeat(HeartbeatChange(enabled = enabled))

    fun pauseHeartbeatForHours(hours: Long, now: Instant = Instant.now()) =
        changeHeartbeat(HeartbeatChange(pausedUntil = pauseForHours(now, hours)))

    /** Quiet until the quiet hours' own end, so "until morning" matches the schedule on screen. */
    fun pauseHeartbeatUntilMorning(now: Instant = Instant.now()) {
        val hour = _uiState.value.heartbeat?.quietEndHour ?: return
        changeHeartbeat(HeartbeatChange(pausedUntil = nextMorning(now, ZoneId.systemDefault(), hour)))
    }

    fun resumeHeartbeat() = changeHeartbeat(HeartbeatChange(clearPause = true))

    fun loadAgentStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(agentLoading = true) }
            try {
                val status = authRepository.getAgentStatus()
                _uiState.update { it.copy(agentLoading = false, agentConnected = status.connected, agentHost = status.host) }
            } catch (e: Exception) {
                // Best-effort — the AGENT section falls back to inferring from the crew
                // list (isDefault) if this fails; the crew section still works either way.
                _uiState.update { it.copy(agentLoading = false) }
            }
        }
    }

    /** Unpair this phone and forget the server: the app goes back to the pairing screen. */
    fun disconnect() {
        viewModelScope.launch {
            runCatching { pushRepository.unregisterCurrentToken() } // best effort; the server may be gone
            authRepository.disconnect()
            settingsStore.clearBaseUrl()
            onServerChanged()
        }
    }

    fun loadCrew() {
        viewModelScope.launch {
            _uiState.update { it.copy(crewLoading = true, crewError = null) }
            try {
                val config = authRepository.getCrew()
                _uiState.update { it.copy(crewLoading = false, crew = config.crew, crewCanCreate = config.canCreate) }
            } catch (e: Exception) {
                _uiState.update { it.copy(crewLoading = false, crewError = e.friendlyMessage()) }
            }
        }
    }

    // ── edit ──────────────────────────────────────────────────────────

    fun startEditingCrewMember(member: CrewMember) = _uiState.update {
        it.copy(
            editingCrewName = member.name,
            editPortInput = member.port.toString(),
            editApiKeyInput = "",
        )
    }

    fun cancelEditingCrewMember() = _uiState.update { it.copy(editingCrewName = null) }
    fun setEditPortInput(value: String) = _uiState.update { it.copy(editPortInput = value) }
    fun setEditApiKeyInput(value: String) = _uiState.update { it.copy(editApiKeyInput = value) }

    fun saveEditedCrewMember() {
        val name = _uiState.value.editingCrewName ?: return
        val port = _uiState.value.editPortInput.trim().toIntOrNull()
        val apiKey = _uiState.value.editApiKeyInput.trim()
        if (port == null || apiKey.isEmpty()) {
            _uiState.update { it.copy(crewError = "A valid port and a key (re-entered) are both required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(editSaving = true, crewError = null) }
            try {
                val config = authRepository.updateCrewMember(name, port, apiKey)
                _uiState.update { it.copy(editSaving = false, crew = config.crew, crewCanCreate = config.canCreate, editingCrewName = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(editSaving = false, crewError = e.friendlyMessage()) }
            }
        }
    }

    // ── remove / default ─────────────────────────────────────────────

    fun removeCrewMember(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(crewError = null) }
            try {
                val config = authRepository.removeCrewMember(name)
                _uiState.update { it.copy(crew = config.crew, crewCanCreate = config.canCreate) }
            } catch (e: Exception) {
                _uiState.update { it.copy(crewError = e.friendlyMessage()) }
            }
        }
    }

    fun setDefaultCrewMember(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(crewError = null) }
            try {
                val config = authRepository.setDefaultCrewMember(name)
                _uiState.update { it.copy(crew = config.crew, crewCanCreate = config.canCreate) }
            } catch (e: Exception) {
                _uiState.update { it.copy(crewError = e.friendlyMessage()) }
            }
        }
    }

    // ── add ───────────────────────────────────────────────────────────

    fun openAddCrewSheet() = _uiState.update {
        it.copy(showAddCrewSheet = true, newCrewName = "", newCrewColor = null)
    }

    fun dismissAddCrewSheet() = _uiState.update { it.copy(showAddCrewSheet = false) }

    fun setNewCrewName(value: String) = _uiState.update { it.copy(newCrewName = value) }

    /** `null` means "let the server auto-assign" — tapping the already-selected
     * swatch again goes back to that, rather than forcing a choice. */
    fun selectNewCrewColor(color: String?) = _uiState.update {
        it.copy(newCrewColor = if (it.newCrewColor == color) null else color)
    }

    fun addCrewMember() {
        val name = _uiState.value.newCrewName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(crewError = "A name is required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(newCrewSaving = true, crewError = null) }
            try {
                // No port/API key — gaca-api provisions the profile on the box itself.
                val config = authRepository.addCrewMember(name, color = _uiState.value.newCrewColor)
                _uiState.update { it.copy(newCrewSaving = false, crew = config.crew, crewCanCreate = config.canCreate, showAddCrewSheet = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(newCrewSaving = false, crewError = e.friendlyMessage()) }
            }
        }
    }
}
