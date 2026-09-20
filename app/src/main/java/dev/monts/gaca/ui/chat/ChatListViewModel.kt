package dev.monts.gaca.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.AuthRepository
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Session
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,

    /** For coloring each row and for the "start a chat with…" sheet. */
    val crew: List<CrewMember> = emptyList(),
    val showCrewPicker: Boolean = false,
    val creatingSession: Boolean = false,

    /** One-shot: set once a new session exists, so the screen can navigate into it. */
    val createdSessionId: String? = null,
)

class ChatListViewModel(
    private val sessionsRepository: SessionsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            // Best-effort, same as the Inbox's crew filter row — a failed fetch
            // just means sessions render without a color and the picker is empty.
            try {
                val crew = authRepository.getCrew().crew
                _uiState.update { it.copy(crew = crew) }
            } catch (_: Exception) {
                // no crew color, no picker options
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sessions = sessionsRepository.listSessions()
                _uiState.update { it.copy(sessions = sessions, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun openCrewPicker() = _uiState.update { it.copy(showCrewPicker = true) }
    fun dismissCrewPicker() = _uiState.update { it.copy(showCrewPicker = false) }

    fun startSession(crewName: String) {
        if (_uiState.value.creatingSession) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatingSession = true, error = null) }
            try {
                val session = sessionsRepository.createSession(crew = crewName)
                _uiState.update { it.copy(creatingSession = false, showCrewPicker = false, createdSessionId = session.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(creatingSession = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Called right after the screen acts on [ChatListUiState.createdSessionId] — this
     * ViewModel (and its StateFlow) outlives navigating away and back to this screen,
     * so leaving the id set would re-trigger that one-shot navigation every time you
     * return here, not just the once. */
    fun consumeCreatedSession() = _uiState.update { it.copy(createdSessionId = null) }
}
