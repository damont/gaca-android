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

    // "Your crew" — api-contract.md §2: this account already has an agent
    // connected (the one Hermes box) with crew members (named profiles)
    // under it. Connecting a new agent isn't built here yet — see BUILD_NEXT.md.
    val crewLoading: Boolean = true,
    val crewError: String? = null,
    val crew: List<CrewMember> = emptyList(),

    // At most one row's edit form is open at a time.
    val editingCrewName: String? = null,
    val editPortInput: String = "",
    val editApiKeyInput: String = "",
    val editSaving: Boolean = false,

    // Add-crew-member sheet (Slice 8) — POST /crew auto-assigns a color when
    // `newCrewColor` stays null; picking a swatch overrides that.
    val showAddCrewSheet: Boolean = false,
    val newCrewName: String = "",
    val newCrewPortInput: String = "",
    val newCrewApiKeyInput: String = "",
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
        loadCrew()
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
        it.copy(
            showAddCrewSheet = true,
            newCrewName = "",
            newCrewPortInput = "",
            newCrewApiKeyInput = "",
            newCrewColor = null,
        )
    }

    fun dismissAddCrewSheet() = _uiState.update { it.copy(showAddCrewSheet = false) }

    fun setNewCrewName(value: String) = _uiState.update { it.copy(newCrewName = value) }
    fun setNewCrewPortInput(value: String) = _uiState.update { it.copy(newCrewPortInput = value) }
    fun setNewCrewApiKeyInput(value: String) = _uiState.update { it.copy(newCrewApiKeyInput = value) }

    /** `null` means "let the server auto-assign" — tapping the already-selected
     * swatch again goes back to that, rather than forcing a choice. */
    fun selectNewCrewColor(color: String?) = _uiState.update {
        it.copy(newCrewColor = if (it.newCrewColor == color) null else color)
    }

    fun addCrewMember() {
        val name = _uiState.value.newCrewName.trim()
        val port = _uiState.value.newCrewPortInput.trim().toIntOrNull()
        val apiKey = _uiState.value.newCrewApiKeyInput.trim()
        if (name.isEmpty() || port == null || apiKey.isEmpty()) {
            _uiState.update { it.copy(crewError = "A name, a valid port, and a key are all required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(newCrewSaving = true, crewError = null) }
            try {
                val config = authRepository.addCrewMember(name, port, apiKey, _uiState.value.newCrewColor)
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
