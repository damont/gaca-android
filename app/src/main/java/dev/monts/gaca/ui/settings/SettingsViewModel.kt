package dev.monts.gaca.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.local.SettingsStore
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

    // "Your agent" — api-contract.md §6/§10: every account brings its own
    // Hermes; there is no shared, app-wide one.
    val agentLoading: Boolean = true,
    val agentConfigured: Boolean = false,
    val agentCurrentBaseUrl: String? = null,
    val agentBaseUrlInput: String = "",
    val agentApiKeyInput: String = "",
    val agentSaving: Boolean = false,
    val agentSaved: Boolean = false,
    val agentError: String? = null,
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
        loadAgentConfig()
    }

    fun setBaseUrlInput(value: String) = _uiState.update { it.copy(baseUrlInput = value, saved = false) }

    fun saveBaseUrl() {
        viewModelScope.launch {
            settingsStore.setBaseUrl(_uiState.value.baseUrlInput)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun loadAgentConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(agentLoading = true, agentError = null) }
            try {
                val config = authRepository.getAgentConfig()
                _uiState.update {
                    it.copy(
                        agentLoading = false,
                        agentConfigured = config.configured,
                        agentCurrentBaseUrl = config.baseUrl,
                        agentBaseUrlInput = config.baseUrl ?: it.agentBaseUrlInput,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentLoading = false, agentError = e.friendlyMessage()) }
            }
        }
    }

    fun setAgentBaseUrlInput(value: String) = _uiState.update { it.copy(agentBaseUrlInput = value, agentSaved = false) }
    fun setAgentApiKeyInput(value: String) = _uiState.update { it.copy(agentApiKeyInput = value, agentSaved = false) }

    fun saveAgentConfig() {
        val baseUrl = _uiState.value.agentBaseUrlInput.trim()
        val apiKey = _uiState.value.agentApiKeyInput.trim()
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            _uiState.update { it.copy(agentError = "Both the base URL and the key are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(agentSaving = true, agentError = null) }
            try {
                val config = authRepository.setAgentConfig(baseUrl, apiKey)
                _uiState.update {
                    it.copy(
                        agentSaving = false,
                        agentSaved = true,
                        agentConfigured = config.configured,
                        agentCurrentBaseUrl = config.baseUrl,
                        // The key is never echoed back and has no reason to linger on screen once sent.
                        agentApiKeyInput = "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentSaving = false, agentError = e.friendlyMessage()) }
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
