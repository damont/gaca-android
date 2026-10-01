package dev.monts.gaca.ui.crewdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.monts.gaca.data.repo.CrewProfile
import dev.monts.gaca.data.repo.CrewProfileRepository
import dev.monts.gaca.data.repo.SessionsRepository
import dev.monts.gaca.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CrewDetailUiState(
    val profile: CrewProfile? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** "Change this in chat" — starting that chat is in flight / done and ready to open. */
    val startingChat: Boolean = false,
    val openChatSessionId: String? = null,
)

/** Read-only view of one crew member's setup (`GET /crew/{name}/profile`). Editing is done by
 * talking to that crew member, so the one action here is "start a chat with it." */
class CrewDetailViewModel(
    private val crewName: String,
    private val profiles: CrewProfileRepository,
    private val sessions: SessionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrewDetailUiState())
    val uiState: StateFlow<CrewDetailUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
    }

    fun refresh() = load(showSpinner = false)

    private fun load(showSpinner: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = showSpinner, isRefreshing = !showSpinner, error = null) }
            try {
                val profile = profiles.getProfile(crewName)
                _uiState.update { it.copy(profile = profile, isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun startChat() {
        if (_uiState.value.startingChat) return
        _uiState.update { it.copy(startingChat = true, error = null) }
        viewModelScope.launch {
            try {
                val session = sessions.createSession(crew = crewName)
                _uiState.update { it.copy(startingChat = false, openChatSessionId = session.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(startingChat = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun consumeOpenChat() = _uiState.update { it.copy(openChatSessionId = null) }
}
