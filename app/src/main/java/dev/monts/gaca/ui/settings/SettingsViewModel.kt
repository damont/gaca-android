package dev.monts.gaca.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
import dev.monts.gaca.data.repo.AgentProfile
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

    // "Your agents" — api-contract.md §6/§10: every account brings its own
    // Hermes agent(s); there is no shared, app-wide one. Several named agents
    // can share one host, each on its own port.
    val agentsLoading: Boolean = true,
    val agentsError: String? = null,
    val agents: List<AgentProfile> = emptyList(),

    val hostInput: String = "",
    val hostSaving: Boolean = false,

    // At most one row's edit form is open at a time.
    val editingAgentName: String? = null,
    val editPortInput: String = "",
    val editApiKeyInput: String = "",
    val editSaving: Boolean = false,

    val newAgentName: String = "",
    val newAgentPortInput: String = "",
    val newAgentApiKeyInput: String = "",
    val newAgentSaving: Boolean = false,
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
        loadAgents()
    }

    fun setBaseUrlInput(value: String) = _uiState.update { it.copy(baseUrlInput = value, saved = false) }

    fun saveBaseUrl() {
        viewModelScope.launch {
            settingsStore.setBaseUrl(_uiState.value.baseUrlInput)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun loadAgents() {
        viewModelScope.launch {
            _uiState.update { it.copy(agentsLoading = true, agentsError = null) }
            try {
                val config = authRepository.getAgents()
                _uiState.update {
                    it.copy(
                        agentsLoading = false,
                        agents = config.agents,
                        hostInput = config.host ?: it.hostInput,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentsLoading = false, agentsError = e.friendlyMessage()) }
            }
        }
    }

    fun setHostInput(value: String) = _uiState.update { it.copy(hostInput = value) }

    fun saveHost() {
        val host = _uiState.value.hostInput.trim()
        if (host.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(hostSaving = true, agentsError = null) }
            try {
                val config = authRepository.setAgentHost(host)
                _uiState.update { it.copy(hostSaving = false, agents = config.agents, hostInput = config.host ?: host) }
            } catch (e: Exception) {
                _uiState.update { it.copy(hostSaving = false, agentsError = e.friendlyMessage()) }
            }
        }
    }

    // ── add ───────────────────────────────────────────────────────────

    fun setNewAgentName(value: String) = _uiState.update { it.copy(newAgentName = value) }
    fun setNewAgentPortInput(value: String) = _uiState.update { it.copy(newAgentPortInput = value) }
    fun setNewAgentApiKeyInput(value: String) = _uiState.update { it.copy(newAgentApiKeyInput = value) }

    fun addAgent() {
        val name = _uiState.value.newAgentName.trim()
        val port = _uiState.value.newAgentPortInput.trim().toIntOrNull()
        val apiKey = _uiState.value.newAgentApiKeyInput.trim()
        if (name.isEmpty() || port == null || apiKey.isEmpty()) {
            _uiState.update { it.copy(agentsError = "A name, a valid port, and a key are all required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(newAgentSaving = true, agentsError = null) }
            try {
                val config = authRepository.addAgent(name, port, apiKey)
                _uiState.update {
                    it.copy(
                        newAgentSaving = false,
                        agents = config.agents,
                        newAgentName = "",
                        newAgentPortInput = "",
                        newAgentApiKeyInput = "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(newAgentSaving = false, agentsError = e.friendlyMessage()) }
            }
        }
    }

    // ── edit ──────────────────────────────────────────────────────────

    fun startEditingAgent(agent: AgentProfile) = _uiState.update {
        it.copy(
            editingAgentName = agent.name,
            editPortInput = agent.port.toString(),
            editApiKeyInput = "",
        )
    }

    fun cancelEditingAgent() = _uiState.update { it.copy(editingAgentName = null) }
    fun setEditPortInput(value: String) = _uiState.update { it.copy(editPortInput = value) }
    fun setEditApiKeyInput(value: String) = _uiState.update { it.copy(editApiKeyInput = value) }

    fun saveEditedAgent() {
        val name = _uiState.value.editingAgentName ?: return
        val port = _uiState.value.editPortInput.trim().toIntOrNull()
        val apiKey = _uiState.value.editApiKeyInput.trim()
        if (port == null || apiKey.isEmpty()) {
            _uiState.update { it.copy(agentsError = "A valid port and a key (re-entered) are both required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(editSaving = true, agentsError = null) }
            try {
                val config = authRepository.updateAgent(name, port, apiKey)
                _uiState.update { it.copy(editSaving = false, agents = config.agents, editingAgentName = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(editSaving = false, agentsError = e.friendlyMessage()) }
            }
        }
    }

    // ── remove / default ─────────────────────────────────────────────

    fun removeAgent(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(agentsError = null) }
            try {
                val config = authRepository.removeAgent(name)
                _uiState.update { it.copy(agents = config.agents) }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentsError = e.friendlyMessage()) }
            }
        }
    }

    fun setDefaultAgent(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(agentsError = null) }
            try {
                val config = authRepository.setDefaultAgent(name)
                _uiState.update { it.copy(agents = config.agents) }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentsError = e.friendlyMessage()) }
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
