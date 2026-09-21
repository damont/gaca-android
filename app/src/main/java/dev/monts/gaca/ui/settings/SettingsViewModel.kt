package dev.monts.gaca.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.PushRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrlInput: String = "",
    val saved: Boolean = false,

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
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val pushRepository: PushRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.baseUrl.collect { url -> _uiState.update { it.copy(baseUrlInput = url) } }
        }
        loadAgentStatus()
        loadCrew()
    }

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

    fun setBaseUrlInput(value: String) = _uiState.update { it.copy(baseUrlInput = value, saved = false) }

    fun saveBaseUrl() {
        viewModelScope.launch {
            settingsStore.setBaseUrl(_uiState.value.baseUrlInput)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun loadCrew() {
        viewModelScope.launch {
            _uiState.update { it.copy(crewLoading = true, crewError = null) }
            try {
                val config = authRepository.getCrew()
                _uiState.update { it.copy(crewLoading = false, crew = config.crew) }
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
                _uiState.update { it.copy(editSaving = false, crew = config.crew, editingCrewName = null) }
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
                _uiState.update { it.copy(crew = config.crew) }
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
                _uiState.update { it.copy(crew = config.crew) }
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
                _uiState.update { it.copy(newCrewSaving = false, crew = config.crew, showAddCrewSheet = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(newCrewSaving = false, crewError = e.friendlyMessage()) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Before clearing the session — unregistering needs a live auth
            // token, and the token itself, to make the call.
            pushRepository.unregisterCurrentToken()
            authRepository.logout()
        }
    }
}
