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
    /** Search text typed on the history screen; the screen filters on it. */
    val query: String = "",
    /** Only chats with this crew member; `null` shows everyone's. */
    val crewFilter: String? = null,
    /** For coloring each row and for the filter pills. */
    val crew: List<CrewMember> = emptyList(),
)

/** History: every past chat, searchable and filterable by crew member. */
class ChatListViewModel(
    private val sessionsRepository: SessionsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            // Best-effort — a failed fetch just means rows render without a color and no pills.
            try {
                val crew = authRepository.getCrew().crew
                _uiState.update { it.copy(crew = crew) }
            } catch (_: Exception) {
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

    fun setQuery(text: String) = _uiState.update { it.copy(query = text) }

    fun setCrewFilter(name: String?) = _uiState.update { it.copy(crewFilter = if (it.crewFilter == name) null else name) }
}
