package dev.monts.gaca.ui.newdelegation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.AgentProfile
import dev.monts.gaca.data.repo.AlertsRepository
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.delegateErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewDelegationUiState(
    val noteText: String = "",
    val agentsLoading: Boolean = true,
    val agents: List<AgentProfile> = emptyList(),
    val selectedAgent: String? = null,
    val sending: Boolean = false,
    val error: String? = null,
    /** One-shot: true once the alert has been created and delegated, screen should pop back to the inbox. */
    val done: Boolean = false,
)

/**
 * Starts a delegation the user asked for, rather than one Hermes noticed on its own — see
 * api-contract.md §4: `POST /alerts` only requires the normal session token, so there's nothing
 * agent-only about it. Creates a plain alert, then immediately hands it to `/delegate`; the
 * existing automatic settlement (`run.completed`/`run.failed`, same §4) is what reports the
 * result back onto that alert — no separate "send the response to GACA" step is needed.
 */
class NewDelegationViewModel(
    private val repo: AlertsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewDelegationUiState())
    val uiState: StateFlow<NewDelegationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val agents = authRepository.getAgents().agents
                val default = agents.firstOrNull { it.isDefault }?.name ?: agents.firstOrNull()?.name
                _uiState.update { it.copy(agents = agents, selectedAgent = default, agentsLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(agentsLoading = false, error = e.delegateErrorMessage()) }
            }
        }
    }

    fun setNoteText(text: String) = _uiState.update { it.copy(noteText = text, error = null) }

    fun selectAgent(name: String) = _uiState.update { it.copy(selectedAgent = name) }

    fun send() {
        val note = _uiState.value.noteText.trim()
        val agent = _uiState.value.selectedAgent
        if (note.isEmpty() || agent == null || _uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null) }
            try {
                val title = note.lineSequence().first().take(120)
                val alert = repo.create(kind = "manual.delegation", title = title, body = note, agent = agent)
                repo.delegate(alert.id, instruction = note, suggestedActionIndex = null, agent = agent)
                _uiState.update { it.copy(sending = false, done = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.delegateErrorMessage()) }
            }
        }
    }
}
